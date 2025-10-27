package com.myproject.laboratorio1;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SerialProtocolRunner {
    // Registro global por puerto para evitar instancias simultáneas que se pisen
    private static final Object REGISTRY_LOCK = new Object();
    private static final java.util.Map<String, SerialProtocolRunner> ACTIVE_BY_PORT = new java.util.HashMap<>();
    private final String port;
    private final int baud;
    private final long defaultTimeoutMs = 500;

    private SerialIO serial;
    private volatile boolean reading;
    private Thread readerThread;
    private volatile boolean connecting;
    private Thread connectThread;

    // Buffers FIFO separados (capacidad fija con recorte)
    private static final int BUFFER_CAPACITY = 512;
    private final Deque<AdcSample> adcBuffer = new ArrayDeque<>(BUFFER_CAPACITY);
    private final Deque<DigitalSample> digitalBuffer = new ArrayDeque<>(BUFFER_CAPACITY);
    // Marca de tiempo de inicio
    private volatile long t0Ms = -1L;

    // Estado deseado/pending de comandos PC->MCU cuando la comunicación aún no inició
    private Integer pendingLedMask = null;         // 0..255
    private Integer pendingTsDip = null;           // 0..65535 (LE)
    private Integer pendingTsAdc = null;           // 0..65535 (LE)

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
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
    }

    // Inicia transmisión en hilo: habilita streaming (CMD=0x05, 0x01) y arranca el lector que llena el buffer
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
        t0Ms = System.currentTimeMillis();
        byte[] resp = serial.sendCommand(0x05, new byte[]{ 0x01 }, 64, defaultTimeoutMs);
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
    public synchronized void stopTransmission() {
        try {
            if (serial != null) {
                byte[] resp = serial.sendCommand(0x05, new byte[]{ 0x00 }, 64, defaultTimeoutMs);
                // Validación best-effort; no interrumpe parada si es inválido
                validateResponse(resp);
            }
        } catch (Exception ignored) {}
        reading = false;
    }

    // Cierra el puerto y detiene cualquier lectura en curso
    public synchronized void close() {
        reading = false;
        connecting = false;
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception ignored) {}
        try { if (connectThread != null) connectThread.interrupt(); } catch (Exception ignored) {}
        try { if (serial != null) serial.close(); } catch (Exception ignored) {}
        serial = null;
        connectThread = null;
        try { SerialIO.forceClose(port); } catch (Exception ignored) {}
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        synchronized (REGISTRY_LOCK) {
            SerialProtocolRunner cur = ACTIVE_BY_PORT.get(port);
            if (cur == this) ACTIVE_BY_PORT.remove(port);
        }
    }

    // Devuelve (y consume) el valor ADC del canal [0..7] + tiempo en ms desde start
    public TimedValue getAdcValue(int index) {
        if (index < 0 || index >= 8) throw new IllegalArgumentException("Índice ADC inválido (0-7)");
        AdcSample s;
        synchronized (adcBuffer) { s = adcBuffer.pollFirst(); }
        if (s == null) return new TimedValue(0, -1L);
        return new TimedValue(s.adc[index], s.tMs);
    }

    // Devuelve (y consume) el byte de pines digitales (8 bits) + tiempo en ms desde start
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
        if (resp == null || resp.length < 6) return false;
        int idx = indexOf(resp, new byte[]{0x55, (byte) 0xAB});
        if (idx < 0 || idx + 5 >= resp.length) return false;
        int status = resp[idx + 2] & 0xFF;
        int cmd = resp[idx + 3] & 0xFF;
        int len = resp[idx + 4] & 0xFF;
        int expected = 5 + len + 1;
        if (idx + expected > resp.length) return false;
        int calc = 0;
        calc ^= status; calc ^= cmd; calc ^= len;
        for (int i = 0; i < len; i++) calc ^= (resp[idx + 5 + i] & 0xFF);
        int chk = resp[idx + 5 + len] & 0xFF;
        return (calc & 0xFF) == chk;
    }

    // ========= Comandos PC->MCU (55 AA CMD LEN PAYLOAD CHK) =========
    // Si la comunicación está iniciada: envía ya. Si no: queda pendiente para enviar al iniciar.

    // 0x01 Set LED mask (LEN=1, unsigned). Payload: [mask]
    public synchronized void commandSetLedMask(int mask) {
        int m = mask & 0xFF;
        if (reading && serial != null) {
            try {
                byte[] resp = serial.sendCommand(0x01, new byte[]{ (byte) m }, 64, defaultTimeoutMs);
                validateResponse(resp);
            } catch (Exception e) {
                pendingLedMask = m;
            }
        } else {
            pendingLedMask = m;
        }
    }

    // Aceptar máscara en binario ("10101010" o con prefijo 0b)
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
    public synchronized void commandSetTsDip(int ts) {
        int v = Math.max(0, Math.min(0xFFFF, ts));
        byte lo = (byte) (v & 0xFF);
        byte hi = (byte) ((v >>> 8) & 0xFF);
        if (reading && serial != null) {
            try {
                byte[] resp = serial.sendCommand(0x03, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                validateResponse(resp);
            } catch (Exception e) {
                pendingTsDip = v;
            }
        } else {
            pendingTsDip = v;
        }
    }

    // Overload explícito para unsigned mediante long (ms)
    public synchronized void commandSetTsDip(long tsMs) {
        long cl = Math.max(0L, Math.min(0xFFFFL, tsMs));
        commandSetTsDip((int) cl);
    }

    // (Eliminados) 0x05 Streaming y 0x06 Snapshot: no se usan

    // 0x08 Set Ts ADC (LEN=2, uint16 LE, unsigned ms)
    public synchronized void commandSetTsAdc(int ts) {
        int v = Math.max(0, Math.min(0xFFFF, ts));
        byte lo = (byte) (v & 0xFF);
        byte hi = (byte) ((v >>> 8) & 0xFF);
        if (reading && serial != null) {
            try {
                byte[] resp = serial.sendCommand(0x08, new byte[]{ lo, hi }, 64, defaultTimeoutMs);
                validateResponse(resp);
            } catch (Exception e) {
                pendingTsAdc = v;
            }
        } else {
            pendingTsAdc = v;
        }
    }

    // Overload explícito para unsigned mediante long (ms)
    public synchronized void commandSetTsAdc(long tsMs) {
        long cl = Math.max(0L, Math.min(0xFFFFL, tsMs));
        commandSetTsAdc((int) cl);
    }

    // Envía todos los comandos pendientes acumulados
    private void flushPendingCommands() {
        if (serial == null) return;
        // LED mask
        if (pendingLedMask != null) {
            try { byte[] r = serial.sendCommand(0x01, new byte[]{ (byte)(pendingLedMask & 0xFF) }, 64, defaultTimeoutMs); validateResponse(r); } catch (Exception ignored) {}
            pendingLedMask = null;
        }
        // Ts DIP
        if (pendingTsDip != null) {
            int v = pendingTsDip;
            byte lo = (byte) (v & 0xFF), hi = (byte)((v >>> 8) & 0xFF);
            try { byte[] r = serial.sendCommand(0x03, new byte[]{ lo, hi }, 64, defaultTimeoutMs); validateResponse(r); } catch (Exception ignored) {}
            pendingTsDip = null;
        }
        // Ts ADC
        if (pendingTsAdc != null) {
            int v = pendingTsAdc;
            byte lo = (byte) (v & 0xFF), hi = (byte)((v >>> 8) & 0xFF);
            try { byte[] r = serial.sendCommand(0x08, new byte[]{ lo, hi }, 64, defaultTimeoutMs); validateResponse(r); } catch (Exception ignored) {}
            pendingTsAdc = null;
        }
    }

    // ====== Orquestación de conexión con reintentos (no bloquea el hilo de UI) ======
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

    // Cierra serial/lector sin alterar "connecting" ni interrumpir el hilo de reintentos
    private synchronized void resetForRetry() {
        reading = false;
        try { if (readerThread != null) readerThread.interrupt(); } catch (Exception ignored) {}
        try { if (serial != null) serial.close(); } catch (Exception ignored) {}
        serial = null;
        try { SerialIO.forceClose(port); } catch (Exception ignored) {}
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        // No tocar: connecting, connectThread ni ACTIVE_BY_PORT
    }

    // Opcionales: exponer tamaño de buffers y limpiar manualmente
    public int getBufferedCount() {
        synchronized (adcBuffer) { synchronized (digitalBuffer) { return adcBuffer.size() + digitalBuffer.size(); } }
    }
    public void clearBuffer() {
        synchronized (adcBuffer) { adcBuffer.clear(); }
        synchronized (digitalBuffer) { digitalBuffer.clear(); }
    }

    // Estado de conexion/transmision
    public boolean isTransmissionActive() { return reading; }
    public boolean isConnecting() { return connecting; }
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
