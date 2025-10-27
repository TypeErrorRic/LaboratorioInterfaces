package com.myproject.laboratorio1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Orquestador del protocolo serie de la práctica.
 *
 * Administra la conexión al puerto serie, el inicio/paro del streaming,
 * la lectura en segundo plano de las tramas 0x7A 0x7B ... 0x7C y
 * expone utilidades para enviar comandos (LED mask, Ts DIP, Ts ADC).
 *
 * El estilo de documentación sigue el usado en {@link SerialIO} para Doxygen/Javadoc.
 */
public class SerialProtocolRunner {
    // Registro global por puerto para evitar instancias simultáneas que se pisen
    private static final Object REGISTRY_LOCK = new Object();
    private static final Object PENDING_LOCK = new Object();
    private static final java.util.Map<String, SerialProtocolRunner> ACTIVE_BY_PORT = new java.util.HashMap<>();
    private final String port;
    private final int baud;
    private final long defaultTimeoutMs = 500;

    private SerialIO serial;
    private volatile boolean reading;
    private Thread readerThread;
    private volatile boolean connecting;
    private Thread connectThread;
    // Reintentos de comandos (ACK) en segundo plano
    private volatile boolean commandRetryRunning;
    private Thread commandRetryThread;

    // Buffers FIFO separados (capacidad fija con recorte)
    private static final int BUFFER_CAPACITY = 512;
    private final Deque<AdcSample> adcBuffer = new ArrayDeque<>(BUFFER_CAPACITY);
    private final Deque<DigitalSample> digitalBuffer = new ArrayDeque<>(BUFFER_CAPACITY);
    // Marca de tiempo de inicio
    private volatile long t0Ms = -1L;

    // Estado deseado/pending de comandos PC->MCU (compartido a nivel de clase)
    private static volatile Integer pendingLedMask = null;   // 0..255
    private static volatile Integer pendingTsDip = null;     // 0..65535 (LE)
    private static volatile Integer pendingTsAdc = null;     // 0..65535 (LE)

    /**
     * Crea un runner asociado a un puerto y baud rate.
     * Inicia reintentos de conexión en segundo plano sin bloquear la UI.
     *
     * @param port Nombre del puerto. Ej: "COM3", "/dev/ttyUSB0".
     * @param baud Baud rate. Ej: 9600, 115200.
     */
    public SerialProtocolRunner(String port, int baud) {
        this.port = port;
        this.baud = baud;
        // Auto-arranca reintentos de conexión sin bloquear UI
        startTransmissionWithRetryAsync(500);
    }

    // Abre el puerto si no está abierto
    private synchronized void ensureOpen() throws Exception {
        if (serial == null) {
            try { SerialIO.forceClose(port); } catch (Exception ignored) {}
            serial = new SerialIO(port, baud);
            // Dar tiempo al bootloader (Arduino UNO/Nano reinicia al abrir el puerto)
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        }
    }

    // Inicia transmisión en hilo: habilita streaming (CMD=0x05, 0x01) y arranca el lector que llena el buffer
    /**
     * Inicia la transmisión en streaming.
     *
     * Abre el puerto si es necesario, envía el comando de habilitar streaming (CMD=0x05, payload=0x01)
     * y arranca el hilo lector que parsea tramas y llena los buffers.
     *
     * @throws Exception si no se puede abrir el puerto o si el ACK es inválido.
     */
    public synchronized void startTransmission() throws Exception {
        // Si ya estamos leyendo con esta instancia, no volver a iniciar
        if (reading) return;

        // Garantiza exclusividad por puerto: cierra otra instancia previa si existe
        synchronized (REGISTRY_LOCK) {
            SerialProtocolRunner prev = ACTIVE_BY_PORT.get(port);
            if (prev != null && prev != this) {
                try { prev.close(); } catch (Exception ignored) {}
                ACTIVE_BY_PORT.remove(port);
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
            ACTIVE_BY_PORT.put(port, this);
        }

        try {
        ensureOpen();
        // Asegurar que el canal esté desocupado antes de esperar el ACK
        try { if (serial != null) serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
        t0Ms = System.currentTimeMillis();
        // Dar mas margen para el ACK inicial (MCU puede estar arrancando)
        byte[] resp = serial.sendCommand(0x05, new byte[]{ 0x01 }, 64, Math.max(1500L, defaultTimeoutMs));
        if (!validateResponse(resp)) {
            throw new IllegalStateException("ACK inválido al habilitar streaming");
        }
        if (!reading) {
            reading = true;
            readerThread = new Thread(this::readLoop, "Serial-FrameReader");
            readerThread.setDaemon(true);
            readerThread.start();
            // Enviar comandos pendientes encolados antes de iniciar
            flushPendingCommands();
        }
        } catch (Exception e) { try { resetForRetry(); } catch (Exception ignored) {} throw e; }
    }

    // Detiene transmisión y mantiene el puerto abierto
    /**
     * Detiene la transmisión en streaming y mantiene el puerto abierto.
     * Envía el comando de deshabilitar streaming (CMD=0x05, payload=0x00).
     */
    public synchronized void stopTransmission() {
        try {
            if (serial != null) {
                // Vaciar canal previo al ACK de stop para evitar mezclar streaming
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] resp = serial.sendCommand(0x05, new byte[]{ 0x00 }, 64, defaultTimeoutMs);
                // Validación best-effort; no interrumpe parada si es inválido
                validateResponse(resp);
            }
        } catch (Exception ignored) {}
        reading = false;
    }

    // Inicia un hilo que reintenta comandos pendientes hasta recibir ACK válido
    private void ensureCommandRetryWorker() {
        synchronized (this) {
            if (commandRetryRunning) return;
            commandRetryRunning = true;
        }
        Thread t = new Thread(this::commandRetryLoop, "Serial-CmdRetry");
        t.setDaemon(true);
        commandRetryThread = t;
        t.start();
    }

    private static boolean hasPendingCommands() {
        synchronized (PENDING_LOCK) {
            return pendingLedMask != null || pendingTsDip != null || pendingTsAdc != null;
        }
    }

    private void commandRetryLoop() {
        try {
            while (true) {
                // Salir si no hay trabajo
                boolean anyPending = hasPendingCommands();
                if (!anyPending) break;

                // Si no hay transmisión activa o no hay IO, esperar un poco
                if (!reading || serial == null) {
                    try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
                    continue;
                }

                boolean didWork = false;

                // LED mask
                Integer led = null;
                synchronized (PENDING_LOCK) { if (pendingLedMask != null) led = pendingLedMask; }
                if (led != null) {
                    didWork = true;
                    try {
                        try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                        byte[] resp = serial.sendCommand(0x01, new byte[]{ (byte)(led & 0xFF) }, 64, defaultTimeoutMs);
                        boolean ok = validateResponse(resp);
                        if (ok) {
                            synchronized (PENDING_LOCK) {
                                if (pendingLedMask != null && (pendingLedMask & 0xFF) == (led & 0xFF)) pendingLedMask = null;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Ts DIP
                Integer tsDip = null;
                synchronized (PENDING_LOCK) { if (pendingTsDip != null) tsDip = pendingTsDip; }
                if (tsDip != null) {
                    didWork = true;
                    int v = tsDip;
                    byte lo = (byte) (v & 0xFF), hi = (byte)((v >>> 8) & 0xFF);
                    try {
                        try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                        byte[] resp = serial.sendCommand(0x03, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                        boolean ok = validateResponse(resp);
                        if (ok) {
                            synchronized (PENDING_LOCK) {
                                if (pendingTsDip != null && pendingTsDip == v) pendingTsDip = null;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Ts ADC
                Integer tsAdc = null;
                synchronized (PENDING_LOCK) { if (pendingTsAdc != null) tsAdc = pendingTsAdc; }
                if (tsAdc != null) {
                    didWork = true;
                    int v = tsAdc;
                    byte lo = (byte) (v & 0xFF), hi = (byte)((v >>> 8) & 0xFF);
                    try {
                        try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                        byte[] resp = serial.sendCommand(0x08, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                        boolean ok = validateResponse(resp);
                        if (ok) {
                            synchronized (PENDING_LOCK) {
                                if (pendingTsAdc != null && pendingTsAdc == v) pendingTsAdc = null;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (!didWork) {
                    // Nada que enviar ahora; pequeña pausa
                    try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
                }
            }
        } finally {
            synchronized (this) { commandRetryRunning = false; }
        }
    }

    // Cierra el puerto y detiene cualquier lectura en curso
    /**
     * Cierra y limpia recursos.
     *
     * Detiene la lectura, cancela reintentos, cierra el puerto serie y
     * libera el registro de instancia activa para el puerto.
     */
    public synchronized void close() {
        reading = false;
        connecting = false;
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception ignored) {}
        try { if (connectThread != null) connectThread.interrupt(); } catch (Exception ignored) {}
        try { if (commandRetryThread != null) commandRetryThread.interrupt(); } catch (Exception ignored) {}
        try { if (serial != null) serial.close(); } catch (Exception ignored) {}
        serial = null;
        connectThread = null;
        commandRetryThread = null;
        try { SerialIO.forceClose(port); } catch (Exception ignored) {}
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        synchronized (REGISTRY_LOCK) {
            SerialProtocolRunner cur = ACTIVE_BY_PORT.get(port);
            if (cur == this) ACTIVE_BY_PORT.remove(port);
        }
    }

    // Devuelve (y consume) el valor ADC del canal [0..7] + tiempo en ms desde start
    /**
     * Devuelve (y consume) el valor ADC del canal indicado.
     *
     * @param index Índice del canal ADC [0..7].
     * @return TimedValue con el valor y el tiempo relativo en ms desde el inicio; si no hay datos, devuelve tMs=-1.
     * @throws IllegalArgumentException si el índice está fuera de rango.
     */
    public TimedValue getAdcValue(int index) {
        if (index < 0 || index >= 8) throw new IllegalArgumentException("Índice ADC inválido (0-7)");
        AdcSample s;
        synchronized (adcBuffer) { s = adcBuffer.pollFirst(); }
        if (s == null) return new TimedValue(0, -1L);
        return new TimedValue(s.adc[index], s.tMs);
    }

    // Devuelve (y consume) el byte de pines digitales (8 bits) + tiempo en ms desde start
    /**
     * Devuelve (y consume) el estado de pines digitales (8 bits).
     *
     * @return TimedValue con el byte de pines (LSB=d0) y tiempo relativo en ms; si no hay datos, devuelve tMs=-1.
     */
    public TimedValue getDigitalPins() {
        DigitalSample s;
        synchronized (digitalBuffer) { s = digitalBuffer.pollFirst(); }
        if (s == null) return new TimedValue(0, -1L);
        return new TimedValue(s.digital & 0xFF, s.tMs);
    }

    // Bucle lector: acumula, extrae tramas 7A 7B ... 7C y actualiza estados
    private void readLoop() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (reading) {
            try {
                byte[] chunk = serial.readAvailable();
                if (chunk.length > 0) {
                    buf.write(chunk, 0, chunk.length);
                    byte[] all = buf.toByteArray();
                    List<byte[]> frames = findFrames(all);
                    if (!frames.isEmpty()) {
                        // Procesar TODAS las tramas encontradas, capturando y almacenando cada una
                        for (byte[] f : frames) {
                            Frame parsed = parseFrame(f);
                            if (parsed != null) {
                                long nowMs = System.currentTimeMillis();
                                long tMs = (t0Ms >= 0) ? Math.max(0, nowMs - t0Ms) : 0;
                                // Insertar en buffers separados
                                synchronized (adcBuffer) {
                                    if (adcBuffer.size() >= BUFFER_CAPACITY) adcBuffer.clear();
                                    int[] adcCopy = new int[parsed.adc.length];
                                    System.arraycopy(parsed.adc, 0, adcCopy, 0, parsed.adc.length);
                                    adcBuffer.addLast(new AdcSample(tMs, adcCopy));
                                }
                                synchronized (digitalBuffer) {
                                    if (digitalBuffer.size() >= BUFFER_CAPACITY) digitalBuffer.clear();
                                    digitalBuffer.addLast(new DigitalSample(tMs, parsed.digital));
                                }
                            }
                        }
                        // Mantener solo bytes después de la última trama completa
                        byte[] last = frames.get(frames.size() - 1);
                        int lastEnd = indexOf(all, last) + last.length;
                        buf.reset();
                        if (lastEnd < all.length) buf.write(all, lastEnd, all.length - lastEnd);
                    }
                } else {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) { break; }
                }
            } catch (IOException ioe) {
                System.err.println("Puerto desconectado o error de E/S: " + ioe.getMessage());
                reading = false;
                break;
            } catch (Exception e) {
                System.err.println("Error en readLoop: " + e.getMessage());
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }
    }

    // Helpers mínimos para tramas
    private static int indexOf(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        int start = Math.max(0, fromIndex);
        outer: for (int i = start; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static List<byte[]> findFrames(byte[] buf) {
        List<byte[]> frames = new ArrayList<>();
        if (buf == null || buf.length == 0) return frames;
        int i = 0;
        while (true) {
            int start = indexOf(buf, new byte[]{0x7A, 0x7B}, i);
            if (start < 0) break;
            // Se requiere longitud completa y byte de cierre 0x7C al final (posición 19)
            if (start + 20 > buf.length) {
                // No hay suficientes bytes aún, esperar más datos
                break;
            }
            int tail = buf[start + 19] & 0xFF;
            if (tail == 0x7C) {
                byte[] f = new byte[20];
                System.arraycopy(buf, start, f, 0, 20);
                frames.add(f);
                // Avanzar al siguiente posible frame después de este
                i = start + 20;
            } else {
                // Cierre no encontrado tras 20 bytes: descartar este header y buscar el siguiente
                i = start + 1;
            }
        }
        return frames;
    }

    private static Frame parseFrame(byte[] frame) {
        // Validación estricta de trama: longitud y encabezados; verifica tail si está presente
        if (frame == null || frame.length != 20) return null;
        if (frame[0] != 0x7A || frame[1] != 0x7B) return null;
        if (frame[19] != 0x7C) return null;
        int digital = frame[2] & 0xFF;
        int[] vals = new int[8];
        for (int i = 0; i < 8; i++) {
            int lo = frame[3 + i * 2] & 0xFF;
            int hi = frame[3 + i * 2 + 1] & 0xFF;
            vals[i] = lo | (hi << 8);
        }
        return new Frame(digital, vals);
    }

    // Valida respuesta con encabezado 55 AB ... CHK (XOR de [status,cmd,len]+payload)
    private static boolean validateResponse(byte[] resp) {
        if (resp == null) {
            System.out.println("validateResponse: resp=null");
            return false;
        }
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < resp.length; i++) {
            hex.append(String.format("%02X", resp[i]));
            if (i < resp.length - 1) hex.append(' ');
        }
        if (resp.length < 6) {
            System.out.println("validateResponse: too short len=" + resp.length + " bytes=" + hex);
            return false;
        }
        int idx = indexOf(resp, new byte[]{0x55, (byte) 0xAB});
        if (idx < 0 || idx + 5 >= resp.length) {
            System.out.println("validateResponse: header not found or short; len=" + resp.length + " bytes=" + hex);
            return false;
        }
        int status = resp[idx + 2] & 0xFF;
        int cmd = resp[idx + 3] & 0xFF;
        int len = resp[idx + 4] & 0xFF;
        int expected = 5 + len + 1;
        if (idx + expected > resp.length) {
            System.out.println("validateResponse: incomplete payload; idx=" + idx + " lenField=" + len + " expected=" + expected + " total=" + resp.length + " bytes=" + hex);
            return false;
        }
        int calc = 0;
        calc ^= status; calc ^= cmd; calc ^= len;
        for (int i = 0; i < len; i++) calc ^= (resp[idx + 5 + i] & 0xFF);
        int chk = resp[idx + 5 + len] & 0xFF;
        boolean ok = ((calc & 0xFF) == chk);
        System.out.println("ACK Recibido.");
        return ok;
    }

    // ========= Comandos PC->MCU (55 AA CMD LEN PAYLOAD CHK) =========
    // Si la comunicación está iniciada: envía ya. Si no: queda pendiente para enviar al iniciar.

    // Wrappers estáticos para invocar con una instancia explícita o dejar valores pendientes si runner==null
    public static void commandSetLedMask(SerialProtocolRunner runner, int mask) {
        if (runner != null) { runner.commandSetLedMask(mask); return; }
        synchronized (PENDING_LOCK) { pendingLedMask = mask & 0xFF; }
    }
    public static void commandSetLedMask(SerialProtocolRunner runner, String binaryMask) {
        if (runner != null) { runner.commandSetLedMask(binaryMask); return; }
        int m = parseBinaryMask(binaryMask);
        synchronized (PENDING_LOCK) { pendingLedMask = m & 0xFF; }
    }
    public static void commandSetTsDip(SerialProtocolRunner runner, int ts) {
        if (runner != null) { runner.commandSetTsDip(ts); return; }
        int v = Math.max(0, Math.min(0xFFFF, ts));
        synchronized (PENDING_LOCK) { pendingTsDip = v; }
    }
    public static void commandSetTsDip(SerialProtocolRunner runner, long tsMs) {
        if (runner != null) { runner.commandSetTsDip(tsMs); return; }
        long cl = Math.max(0L, Math.min(0xFFFFL, tsMs));
        synchronized (PENDING_LOCK) { pendingTsDip = (int) cl; }
    }
    public static void commandSetTsAdc(SerialProtocolRunner runner, int ts) {
        if (runner != null) { runner.commandSetTsAdc(ts); return; }
        int v = Math.max(0, Math.min(0xFFFF, ts));
        synchronized (PENDING_LOCK) { pendingTsAdc = v; }
    }
    public static void commandSetTsAdc(SerialProtocolRunner runner, long tsMs) {
        if (runner != null) { runner.commandSetTsAdc(tsMs); return; }
        long cl = Math.max(0L, Math.min(0xFFFFL, tsMs));
        synchronized (PENDING_LOCK) { pendingTsAdc = (int) cl; }
    }

    // 0x01 Set LED mask (LEN=1, unsigned). Payload: [mask]
    /**
     * Envía el comando 0x01 para establecer la máscara de LEDs.
     * Si no hay transmisión activa, se encola para enviarse al iniciar.
     *
     * @param mask Máscara de 8 bits (0..255).
     */
    public synchronized void commandSetLedMask(int mask) {
        int m = mask & 0xFF;
        boolean ok = false;
        if (reading && serial != null) {
            try {
                // Evitar basura de streaming antes de capturar el ACK
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] resp = serial.sendCommand(0x01, new byte[]{ (byte) m }, 64, defaultTimeoutMs);
                ok = validateResponse(resp);
            } catch (Exception ignored) {}
        }
        if (!ok) {
            synchronized (PENDING_LOCK) { pendingLedMask = m; }
            if (reading && serial != null) ensureCommandRetryWorker();
        }
    }

    // Aceptar máscara en binario ("10101010" o con prefijo 0b)
    /**
     * Envía la máscara de LEDs expresada en binario.
     * Acepta formato "10101010" o con prefijo "0b".
     *
     * @param binaryMask Cadena de bits (máx. 8).
     * @throws IllegalArgumentException si el formato no es válido.
     */
    public synchronized void commandSetLedMask(String binaryMask) {
        int m = parseBinaryMask(binaryMask);
        commandSetLedMask(m);
    }

    private static int parseBinaryMask(String binaryMask) {
        if (binaryMask == null) throw new IllegalArgumentException("Máscara binaria nula");
        String s = binaryMask.trim();
        if (s.startsWith("0b") || s.startsWith("0B")) s = s.substring(2);
        if (s.isEmpty()) throw new IllegalArgumentException("Máscara binaria vacía");
        if (!s.matches("[01]+")) throw new IllegalArgumentException("Máscara debe contener solo 0 y 1");
        if (s.length() > 8) throw new IllegalArgumentException("Máscara binaria de máximo 8 bits");
        int val = Integer.parseInt(s, 2);
        return val & 0xFF;
    }

    // 0x03 Set Ts DIP (LEN=2, uint16 LE, unsigned ms)
    /**
     * Envía el comando 0x03 para configurar el periodo de muestreo del DIP.
     * Si no hay transmisión activa, se encola para enviarse al iniciar.
     *
     * @param ts Periodo en ms (uint16, 0..65535).
     */
    public synchronized void commandSetTsDip(int ts) {
        int v = Math.max(0, Math.min(0xFFFF, ts));
        byte lo = (byte) (v & 0xFF);
        byte hi = (byte) ((v >>> 8) & 0xFF);
        boolean ok = false;
        if (reading && serial != null) {
            try {
                // Evitar basura de streaming antes de capturar el ACK
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] resp = serial.sendCommand(0x03, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                ok = validateResponse(resp);
            } catch (Exception ignored) {}
        }
        if (!ok) {
            synchronized (PENDING_LOCK) { pendingTsDip = v; }
            if (reading && serial != null) ensureCommandRetryWorker();
        }
    }

    // Overload explícito para unsigned mediante long (ms)
    /**
     * Sobrecarga para valores sin signo utilizando long.
     *
     * @param tsMs Periodo en ms (0..65535).
     */
    public synchronized void commandSetTsDip(long tsMs) {
        long cl = Math.max(0L, Math.min(0xFFFFL, tsMs));
        commandSetTsDip((int) cl);
    }

    // (Eliminados) 0x05 Streaming y 0x06 Snapshot: no se usan

    // 0x08 Set Ts ADC (LEN=2, uint16 LE, unsigned ms)
    /**
     * Envía el comando 0x08 para configurar el periodo de muestreo del ADC.
     * Si no hay transmisión activa, se encola para enviarse al iniciar.
     *
     * @param ts Periodo en ms (uint16, 0..65535).
     */
    public synchronized void commandSetTsAdc(int ts) {
        int v = Math.max(0, Math.min(0xFFFF, ts));
        byte lo = (byte) (v & 0xFF);
        byte hi = (byte) ((v >>> 8) & 0xFF);
        boolean ok = false;
        if (reading && serial != null) {
            try {
                // Evitar basura de streaming antes de capturar el ACK
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] resp = serial.sendCommand(0x08, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                ok = validateResponse(resp);
            } catch (Exception ignored) {}
        }
        if (!ok) {
            synchronized (PENDING_LOCK) { pendingTsAdc = v; }
            if (reading && serial != null) ensureCommandRetryWorker();
        }
    }

    // Overload explícito para unsigned mediante long (ms)
    /**
     * Sobrecarga para valores sin signo utilizando long.
     *
     * @param tsMs Periodo en ms (0..65535).
     */
    public synchronized void commandSetTsAdc(long tsMs) {
        long cl = Math.max(0L, Math.min(0xFFFFL, tsMs));
        commandSetTsAdc((int) cl);
    }

    // Envía todos los comandos pendientes acumulados
    private void flushPendingCommands() {
        if (serial == null) return;
        // LED mask
        Integer pLed;
        synchronized (PENDING_LOCK) { pLed = pendingLedMask; }
        if (pLed != null) {
            try {
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] r = serial.sendCommand(0x01, new byte[]{ (byte)(pLed & 0xFF) }, 64, defaultTimeoutMs);
                if (validateResponse(r)) {
                    synchronized (PENDING_LOCK) { if (pendingLedMask != null && (pendingLedMask & 0xFF) == (pLed & 0xFF)) pendingLedMask = null; }
                } else {
                    ensureCommandRetryWorker();
                }
            } catch (Exception ignored) { ensureCommandRetryWorker(); }
        }
        // Ts DIP
        Integer pDip;
        synchronized (PENDING_LOCK) { pDip = pendingTsDip; }
        if (pDip != null) {
            int v = pDip;
            byte lo = (byte) (v & 0xFF), hi = (byte)((v >>> 8) & 0xFF);
            try {
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] r = serial.sendCommand(0x03, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                if (validateResponse(r)) {
                    synchronized (PENDING_LOCK) { if (pendingTsDip != null && pendingTsDip == v) pendingTsDip = null; }
                } else {
                    ensureCommandRetryWorker();
                }
            } catch (Exception ignored) { ensureCommandRetryWorker(); }
        }
        // Ts ADC
        Integer pAdc;
        synchronized (PENDING_LOCK) { pAdc = pendingTsAdc; }
        if (pAdc != null) {
            int v = pAdc;
            byte lo = (byte) (v & 0xFF), hi = (byte)((v >>> 8) & 0xFF);
            try {
                try { serial.drainInput(30, Math.max(150L, defaultTimeoutMs / 4)); } catch (Exception ignored) {}
                byte[] r = serial.sendCommand(0x08, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                if (validateResponse(r)) {
                    synchronized (PENDING_LOCK) { if (pendingTsAdc != null && pendingTsAdc == v) pendingTsAdc = null; }
                } else {
                    ensureCommandRetryWorker();
                }
            } catch (Exception ignored) { ensureCommandRetryWorker(); }
        }
    }

    // ====== Orquestación de conexión con reintentos (no bloquea el hilo de UI) ======
    /**
     * Inicia la transmisión con reintentos en segundo plano.
     *
     * Crea un hilo que intentará conectar y llamar a startTransmission(),
     * esperando entre intentos el tiempo indicado.
     *
     * @param retryDelayMs Tiempo entre intentos en ms (mínimo 500ms si se pasa <= 0).
     */
    public void startTransmissionWithRetryAsync(long retryDelayMs) {
        long delay = (retryDelayMs <= 0) ? 500L : retryDelayMs;
        synchronized (this) {
            if (reading || connecting) return;
            connecting = true;
        }
        Thread t = new Thread(() -> {
            int attempt = 1;
            try {
                while (true) {
                    if (!connecting || Thread.currentThread().isInterrupted()) {
                        System.out.println("Reintentos cancelados para " + port);
                        break;
                    }
                    System.out.println("Intentando conectar a " + port + " (intento " + attempt + ")...");
                    try {
                        startTransmission();
                        System.out.println("Transmision iniciada en " + port + " (intento " + attempt + ")");
                        break;
                    } catch (Exception e) {
                        System.err.println("Error de conexion en " + port + " (intento " + attempt + "): " + e.getMessage());
                        // Cerrar IO sin cancelar la bandera de reintentos
                        try { resetForRetry(); } catch (Exception ignored) {}
                        attempt++;
                        try { Thread.sleep(delay); } catch (InterruptedException ie) { System.out.println("Reintento interrumpido para " + port); break; }
                    }
                }
            } finally {
                connecting = false;
            }
        }, "Serial-ConnectRetry-" + port);
        t.setDaemon(true);
        this.connectThread = t;
        t.start();
    }

    // Deja el puerto abierto pero detiene lector/estados para volver a intentar sin resetear el MCU
    private synchronized void resetForRetry() {
        reading = false;
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception ignored) {}
        // No cerrar el puerto: evitar reset repetido en placas que reinician al abrir
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        // No tocar: connecting, connectThread ni ACTIVE_BY_PORT
    }

    // Opcionales: exponer tamaño de buffers y limpiar manualmente
    /**
     * Devuelve el número de elementos actualmente encolados en los buffers.
     *
     * @return Cantidad total de muestras pendientes (ADC + digitales).
     */
    public int getBufferedCount() {
        synchronized (adcBuffer) { synchronized (digitalBuffer) { return adcBuffer.size() + digitalBuffer.size(); } }
    }
    /**
     * Limpia manualmente los buffers de ADC y digitales.
     */
    public void clearBuffer() {
        synchronized (adcBuffer) { adcBuffer.clear(); }
        synchronized (digitalBuffer) { digitalBuffer.clear(); }
    }

    // Estado de conexion/transmision
    /**
     * Indica si el hilo de lectura/streaming está activo.
     * @return true si está leyendo; false en caso contrario.
     */
    public boolean isTransmissionActive() {
        if (!reading) return false;

        // 1) Verificar que el puerto siga listado por el SO
        boolean listed = false;
        try {
            String[] ports = SerialIO.listPorts();
            if (ports != null) {
                for (String p : ports) {
                    if (p != null && p.equalsIgnoreCase(port)) { listed = true; break; }
                }
            }
        } catch (Exception ignored) {}
        if (!listed) {
            reading = false;
            return false;
        }

        // 2) Verificar que el puerto siga operativo
        if (serial == null) {
            reading = false;
            return false;
        }
        try {
            if (!serial.isAlive()) {
                reading = false;
            }
        } catch (Exception ignored) {
            reading = false;
        }
        return reading;
    }
    /**
     * Indica si hay un proceso de conexión con reintentos en curso.
     * @return true si se está intentando conectar; false en caso contrario.
     */
    public boolean isConnecting() { return connecting; }
    /**
     * Devuelve el nombre del puerto asociado a esta instancia.
     * @return Nombre del puerto (por ejemplo, "COM3").
     */
    public String getPort() { return port; }

    private static class Frame {
        final int digital;
        final int[] adc;
        Frame(int digital, int[] adc) { this.digital = digital; this.adc = adc; }
    }

    private static class AdcSample {
        final long tMs;
        final int[] adc;
        AdcSample(long tMs, int[] adc) { this.tMs = tMs; this.adc = adc; }
    }

    private static class DigitalSample {
        final long tMs;
        final int digital;
        DigitalSample(long tMs, int digital) { this.tMs = tMs; this.digital = digital; }
    }

    public static class TimedValue {
        public final int value;
        public final long tMs;
        public TimedValue(int value, long tMs) { this.value = value; this.tMs = tMs; }
    }
}
