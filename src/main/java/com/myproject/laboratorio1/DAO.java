package com.myproject.laboratorio1;

import java.util.Arrays;

/**
 * Pequenio DAO en memoria para validar credenciales.
 * Sustituir por la fuente real (BD, servicio, etc.) cuando este disponible.
 */
public class DAO {

    private volatile SerialProtocolRunner runner;

    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASSWORD = "1234";

    public boolean validarUsuario(String usuario, char[] password) {
        if (usuario == null || password == null) {
            return false;
        }

        boolean valido = DEFAULT_USER.equals(usuario.trim()) && DEFAULT_PASSWORD.equals(new String(password));
        Arrays.fill(password, '\0'); // Limpiar referencia en memoria
        return valido;
    }

    public void setRunner(SerialProtocolRunner runner) {
        this.runner = runner;
    }

    public boolean hayFuenteActiva() {
        SerialProtocolRunner r = runner;
        return r != null && r.isTransmissionActive();
    }

    /**
     * Fuente de datos para graficacion analogica. Si no hay runner activo devuelve tMs = -1.
     */
    public long[] obtenerMuestraAnalogica(int canal) {
        SerialProtocolRunner r = runner;
        if (r == null || !r.isTransmissionActive()) {
            return new long[]{0L, -1L};
        }
        SerialProtocolRunner.TimedValue tv = r.getAdcValue(canal);
        return new long[]{tv.value, tv.tMs};
    }

    /**
     * Fuente de datos para graficacion digital. Si no hay runner activo devuelve tMs = -1.
     */
    public long[] obtenerMuestraDigital() {
        SerialProtocolRunner r = runner;
        if (r == null || !r.isTransmissionActive()) {
            return new long[]{0L, -1L};
        }
        SerialProtocolRunner.TimedValue tv = r.getDigitalPins();
        return new long[]{tv.value, tv.tMs};
    }

    /**
     * Envía al origen actual (runner o futura BD/servicio) el nuevo periodo de muestreo ADC.
     * @param tsMs periodo en milisegundos (uint16 en protocolo)
     * @return true si se pudo enviar al origen activo; false si no hay conexión.
     */
    public boolean actualizarTsAdc(int tsMs) {
        SerialProtocolRunner r = runner;
        if (r != null && r.isTransmissionActive()) {
            SerialProtocolRunner.commandSetTsAdc(r, tsMs);
            return true;
        }
        return false;
    }

    /**
     * Envía al origen actual (runner o futura BD/servicio) el nuevo periodo de muestreo digital.
     * @param tsMs periodo en milisegundos (uint16 en protocolo)
     * @return true si se pudo enviar al origen activo; false si no hay conexión.
     */
    public boolean actualizarTsDip(int tsMs) {
        SerialProtocolRunner r = runner;
        if (r != null && r.isTransmissionActive()) {
            SerialProtocolRunner.commandSetTsDip(r, tsMs);
            return true;
        }
        return false;
    }

    /**
     * Envía la máscara de LEDs (bits 0-3) al origen actual.
     * @param mask valor 0..255 con la máscara de LEDs.
     * @return true si se envió al microcontrolador; false si no hay conexión.
     */
    public boolean enviarMascaraLeds(int mask) {
        SerialProtocolRunner r = runner;
        if (r != null && r.isTransmissionActive()) {
            SerialProtocolRunner.commandSetLedMask(r, mask);
            return true;
        }
        return false;
    }
}
