package com.myproject.laboratorio1;

import java.util.Arrays;

/**
 * @brief DAO simple para credenciales y operaciones de IO con el origen actual.
 *
 * Responsable de:
 *  - Validar usuario/clave en memoria.
 *  - Entregar muestras analogicas/digitales como pares {valor, tMs}.
 *  - Enviar periodos de muestreo (ADC/DIP) y mascara de LEDs.
 *
 * Nota: el origen actual es un SerialProtocolRunner; en el futuro
 * puede reemplazarse por BD o servicio manteniendo esta interfaz.
 */
public class DAO {

    private volatile SerialProtocolRunner runner;

    private static final String DEFAULT_USER = "admin";
    private static final String DEFAULT_PASSWORD = "1234";

    /**
     * @brief Valida credenciales basicas.
     * @param usuario nombre de usuario.
     * @param password arreglo de caracteres con la contrasena.
     * @return true si coincide con los valores por defecto.
     */
    public boolean validarUsuario(String usuario, char[] password) {
        if (usuario == null || password == null) {
            return false;
        }

        boolean valido = DEFAULT_USER.equals(usuario.trim()) && DEFAULT_PASSWORD.equals(new String(password));
        Arrays.fill(password, '\0'); // Limpiar referencia en memoria
        return valido;
    }

    /**
     * @brief Define la fuente activa de datos/comandos, para tener centralizado la instancia de SerialProtocolRunner
     * @param runner instancia de SerialProtocolRunner.
     */
    public void setRunner(SerialProtocolRunner runner) {
        this.runner = runner;
    }

    /**
     * @brief Indica si existe una fuente activa y conectada (Conexión con la base de Datos en este Caso).
     * @return true si hay runner y transmision activa; false en otro caso.
     */
    public boolean hayFuenteActiva() {
        SerialProtocolRunner r = runner;
        return r != null && r.isTransmissionActive();
    }

    /**
     * @brief Obtiene una muestra analogica.
     * @param canal indice de canal ADC.
     * @return arreglo {valor, tMs}; si no hay runner activo devuelve {0, -1}.
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
     * @brief Obtiene una muestra digital.
     * @return arreglo {valor, tMs}; si no hay runner activo devuelve {0, -1}.
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
     * @brief Envia el periodo de muestreo ADC.
     * @param tsMs periodo en milisegundos (uint16 en protocolo).
     * @return true si se envio al origen activo; false si no hay conexion.
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
     * @brief Envia el periodo de muestreo digital (DIP).
     * @param tsMs periodo en milisegundos (uint16 en protocolo).
     * @return true si se envio al origen activo; false si no hay conexion.
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
     * @brief Envia la mascara de LEDs (bits 0-3).
     * @param mask valor 0..255 con la mascara de LEDs.
     * @return true si se envio al microcontrolador; false si no hay conexion.
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
