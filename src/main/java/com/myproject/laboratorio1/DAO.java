package com.myproject.laboratorio1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @brief DAO simple para credenciales y operaciones de IO con el origen actual.
 *
 * Responsable de:
 *  - Validar usuario/clave contra la tabla int_usuarios.
 *  - Entregar muestras analogicas/digitales como pares {valor, tMs}.
 *  - Enviar periodos de muestreo (ADC/DIP) y mascara de LEDs.
 *
 * Nota: el origen actual es un SerialProtocolRunner; en el futuro
 * puede reemplazarse por BD o servicio manteniendo esta interfaz.
 */
public class DAO {

    private volatile SerialProtocolRunner runner;
    private static final Logger LOG = Logger.getLogger(DAO.class.getName());

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost/laboratorio_virtual";
    private static final String DEFAULT_DB_USER = "arley";
    private static final String DEFAULT_DB_PASSWORD = "qwerty";

    /**
     * @brief Valida credenciales contra la tabla int_usuarios de la BD configurada.
     * @param usuario nombre de usuario.
     * @param password arreglo de caracteres con la contrasena.
     * @return true si el usuario existe y la clave coincide; false en otro caso.
     */
    public boolean validarUsuario(String usuario, char[] password) {
        if (usuario == null || password == null) {
            return false;
        }

        String userInput = usuario.trim();
        String passwordInput = new String(password);
        Arrays.fill(password, '\0'); // Limpiar referencia en memoria

        if (userInput.isEmpty()) {
            return false;
        }

        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "SELECT clave FROM int_usuarios WHERE email = ? OR nombres = ?";
        LOG.log(Level.FINE, "Validando usuario [{0}] contra BD {1} con user {2}", new Object[]{userInput, url, dbUser});
        System.out.println("Intentando validar usuario '" + userInput + "' contra BD " + url + " con usuario " + dbUser);
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            LOG.log(Level.INFO, "Conexion a la BD abierta ({0})", url);
            System.out.println("Conexion a la BD abierta (" + url + ")");
            ps.setString(1, userInput);
            ps.setString(2, userInput);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String stored = rs.getString("clave");
                    boolean ok = stored != null && stored.equals(passwordInput);
                    LOG.log(Level.INFO, "Usuario [{0}] {1}", new Object[]{userInput, ok ? "autenticado" : "clave incorrecta"});
                    System.out.println("Usuario '" + userInput + "' " + (ok ? "autenticado" : "clave incorrecta"));
                    return ok;
                }
            }
            LOG.log(Level.INFO, "Usuario [{0}] no encontrado en tabla int_usuarios", userInput);
            System.out.println("Usuario '" + userInput + "' no encontrado en tabla int_usuarios");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al validar usuario en BD", e);
            System.out.println("Error al validar usuario en BD: " + e.getMessage());
        }

        return false;
    }

    /**
     * @brief Define la fuente activa de datos/comandos, para tener centralizado la instancia de SerialProtocolRunner
     * @param runner instancia de SerialProtocolRunner.
     */
    public void setRunner(SerialProtocolRunner runner) {
        this.runner = runner;
    }

    /**
     * @brief Indica si existe una fuente activa y conectada (Conexion con la base de Datos en este Caso).
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

    /** Obtiene configuracion de BD desde variable de entorno, propiedad del sistema o un valor por defecto. */
    private static String resolveConfig(String envKey, String sysPropKey, String defaultValue) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty(sysPropKey);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        LOG.log(Level.FINE, "Usando valor por defecto para {0}", sysPropKey);
        return defaultValue;
    }
}
