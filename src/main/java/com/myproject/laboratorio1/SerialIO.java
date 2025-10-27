package com.myproject.laboratorio1;

import com.fazecast.jSerialComm.SerialPort;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SerialIO implements AutoCloseable {

    private final SerialPort port;

    /**
     * @param portName  Ej: "COM3" (Windows), "/dev/ttyUSB0" o "/dev/ttyACM0" (Linux), "/dev/tty.usbmodemXXXX" (macOS)
     * @param baudRate  Ej: 9600, 115200, etc.
     * @throws IOException si no se puede abrir el puerto
     */
    public SerialIO(String portName, int baudRate) throws IOException {
        port = SerialPort.getCommPort(portName);
        // Intento preventivo: asegurar que no quede una sesión previa abierta
        try { port.closePort(); } catch (Exception ignored) {}
        port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        // Lectura NO bloqueante
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            throw new IOException("No se pudo abrir el puerto: " + portName);
        }
    }

    /**
     * Drena la entrada hasta que permanezca sin datos durante un periodo "quieto" o hasta un tiempo máximo.
     *
     * @param quietPeriodMs  Tiempo consecutivo sin datos para considerar el canal desocupado.
     * @param maxWaitMs      Tiempo máximo total de espera antes de abortar el drenaje.
     * @return Cantidad total de bytes leídos y descartados.
     * @throws IOException si ocurre un error de E/S con el puerto.
     */
    public int drainInput(long quietPeriodMs, long maxWaitMs) throws IOException {
        ensureOpen();
        long start = System.currentTimeMillis();
        long lastData = start;
        int drained = 0;
        long quiet = Math.max(0L, quietPeriodMs);
        long maxWait = Math.max(0L, maxWaitMs);

        while (true) {
            byte[] chunk = readAvailable();
            if (chunk.length > 0) {
                drained += chunk.length;
                lastData = System.currentTimeMillis();
            } else {
                long now = System.currentTimeMillis();
                if ((now - lastData) >= quiet) break;
                if ((now - start) >= maxWait) break;
                try { Thread.sleep(5); } catch (InterruptedException ignored) { break; }
            }
        }
        return drained;
    }

    /** ENVÍO: devuelve la cantidad de bytes escritos. */
    public int send(byte[] data) throws IOException {
        ensureOpen();
        if (data == null || data.length == 0) return 0;
        long written = port.writeBytes(data, data.length);
        if (written < 0) throw new IOException("Error al escribir en el puerto serie.");
        return (int) written;
    }

    /** Conveniencia para enviar texto (UTF-8). */
    public int send(String text) throws IOException {
        byte[] bytes = (text == null) ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        return send(bytes);
    }

    /**
     * RECEPCIÓN NO BLOQUEANTE: lee y devuelve todos los bytes disponibles ahora.
     * Si no hay datos, devuelve un arreglo vacío (length = 0).
     */
    public byte[] readAvailable() throws IOException {
        ensureOpen();
        int available = port.bytesAvailable();
        if (available <= 0) return new byte[0];

        byte[] buf = new byte[available];
        long read = port.readBytes(buf, available);
        if (read < 0) throw new IOException("Error al leer del puerto serie.");

        if (read == available) return buf;
        return Arrays.copyOf(buf, (int) read);
    }

    /** Opcional: leer como String (UTF-8). */
    public String readAvailableString() throws IOException {
        byte[] data = readAvailable();
        return new String(data, StandardCharsets.UTF_8);
    }

    /** Lee hasta maxBytes o hasta timeoutMs (ms), lo que ocurra primero. */
    public byte[] readUpTo(int maxBytes, long timeoutMs) throws IOException {
        ensureOpen();
        if (maxBytes <= 0) return new byte[0];
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 256));
        while (out.size() < maxBytes && System.currentTimeMillis() <= deadline) {
            byte[] chunk = readAvailable();
            if (chunk.length > 0) {
                int remaining = maxBytes - out.size();
                out.write(chunk, 0, Math.min(remaining, chunk.length));
                if (out.size() >= maxBytes) break;
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ignored) { break; }
            }
        }
        return out.toByteArray();
    }

    /** Checksum XOR de todos los bytes del cuerpo. */
    public static byte xorChecksum(byte[] body) {
        byte chk = 0;
        if (body != null) {
            for (byte b : body) chk ^= b;
        }
        return chk;
    }

    /**
     * Construye y envía comando: 55 AA CMD LEN PAYLOAD CHK
     * Devuelve los bytes de la respuesta (lee hasta maxRespBytes con timeoutMs).
     */
    public byte[] sendCommand(int cmd, byte[] payload, int maxRespBytes, long timeoutMs) throws IOException {
        ensureOpen();
        if (cmd < 0 || cmd > 255) throw new IllegalArgumentException("CMD fuera de rango (0-255)");
        byte[] pl = (payload == null) ? new byte[0] : payload;
        if (pl.length > 255) throw new IllegalArgumentException("Payload demasiado largo (max 255)");

        byte[] body = new byte[2 + pl.length];
        body[0] = (byte) (cmd & 0xFF);
        body[1] = (byte) (pl.length & 0xFF);
        if (pl.length > 0) System.arraycopy(pl, 0, body, 2, pl.length);

        byte chk = xorChecksum(body);

        byte[] packet = new byte[2 + body.length + 1];
        packet[0] = 0x55;
        packet[1] = (byte) 0xAA;
        System.arraycopy(body, 0, packet, 2, body.length);
        packet[packet.length - 1] = chk;

        send(packet);
        return readUpTo(Math.max(0, maxRespBytes), Math.max(0, timeoutMs));
    }

    /** Cierra el puerto. */
    @Override
    public void close() {
        if (port != null && port.isOpen()) {
            port.closePort();
        }
    }

    private void ensureOpen() throws IOException {
        if (port == null || !port.isOpen()) {
            throw new IOException("El puerto no está abierto.");
        }
    }

    /** Utilidad: listar puertos disponibles. */
    public static String[] listPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .toArray(String[]::new);
    }

    /** Cierra forzosamente un puerto por nombre (best-effort). */
    public static void forceClose(String portName) {
        try {
            SerialPort p = SerialPort.getCommPort(portName);
            if (p != null) {
                p.closePort();
            }
        } catch (Exception ignored) {}
    }
}
