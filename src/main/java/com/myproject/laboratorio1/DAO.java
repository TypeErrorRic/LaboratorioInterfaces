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
     * @brief Actualiza el periodo de muestreo ADC en BD (int_process.id=3, columna tiempo_muestreo).
     * @param tsMs periodo en milisegundos.
     * @return true si se actualizo al menos una fila en BD.
     */
    public boolean actualizarTsAdc(int tsMs) {
        return updateProcesoTiempo("tiempo_muestreo", tsMs);
    }

    /**
     * @brief Actualiza el periodo de muestreo digital en BD (int_process.id=3, columna tiempo_muestreo_2).
     * @param tsMs periodo en milisegundos.
     * @return true si se actualizo al menos una fila en BD.
     */
    public boolean actualizarTsDip(int tsMs) {
        return updateProcesoTiempo("tiempo_muestreo_2", tsMs);
    }

    /**
     * @brief Actualiza el estado de un LED (1..4) en la tabla int_proceso_refs para el proceso id=3.
     * @param led numero de LED (1..4).
     * @param valor estado (0 apagado, 1 encendido).
     * @return true si se actualizo al menos una fila en BD; false en otro caso.
     */
    public boolean enviarMascaraLeds(int led, int valor) {
        if (led < 1 || led > 4) {
            LOG.log(Level.WARNING, "LED fuera de rango: {0}", led);
            return false;
        }
        if (valor != 0 && valor != 1) {
            LOG.log(Level.WARNING, "Valor LED invalido (solo 0 o 1): {0}", valor);
            return false;
        }
        // Mapear LED 1..4 a IDs de int_proceso_refs para int_proceso_id=3 (DOUT0..DOUT3 son ids 4..7 en el dump).
        int refId = 3 + led; // LED1->4, LED2->5, LED3->6, LED4->7
        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "INSERT INTO int_proceso_refs_data (int_proceso_refs_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIME)";
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, refId);
            ps.setDouble(2, valor);
            ps.setDouble(3, 0.0); // sin tiempo relativo, se guarda como 0
            int rows = ps.executeUpdate();
            LOG.log(Level.INFO, "LED {0} registrado con valor {1} en int_proceso_refs_data (filas afectadas: {2})", new Object[]{led, valor, rows});
            return rows > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al insertar LED en int_proceso_refs_data", e);
            return false;
        }
    }

    /**
     * Consulta el valor de tiempo_muestreo para int_proceso.id = 3.
     * @return valor en ms o null si no se pudo obtener.
     */
    public Integer consultarTsAdcProceso3() {
        return consultarTsProceso3("tiempo_muestreo");
    }

    /**
     * Consulta el valor de tiempo_muestreo_2 para int_proceso.id = 3.
     * @return valor en ms o null si no se pudo obtener.
     */
    public Integer consultarTsDipProceso3() {
        return consultarTsProceso3("tiempo_muestreo_2");
    }

    private Integer consultarTsProceso3(String column) {
        if (!"tiempo_muestreo".equals(column) && !"tiempo_muestreo_2".equals(column)) {
            LOG.log(Level.WARNING, "Columna no permitida para consulta: {0}", column);
            return null;
        }
        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "SELECT " + column + " FROM int_proceso WHERE id = 3";
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(column);
                LOG.log(Level.FINE, "Leido {0}={1} de int_proceso.id=3", new Object[]{column, v});
                return v;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al consultar " + column + " en int_proceso", e);
        }
        return null;
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

    /**
     * Actualiza el periodo de muestreo en la tabla int_process (id=3).
     * @param columnName nombre de la columna a actualizar.
     * @param tsMs valor en milisegundos.
     * @return true si se actualiz�� al menos una fila.
     */
    private boolean updateProcesoTiempo(String columnName, int tsMs) {
        // Solo permitir columnas esperadas para evitar inyecci��n accidental.
        if (!"tiempo_muestreo".equals(columnName) && !"tiempo_muestreo_2".equals(columnName)) {
            LOG.log(Level.WARNING, "Columna no permitida para actualizar: {0}", columnName);
            return false;
        }

        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "UPDATE int_proceso SET " + columnName + " = ? WHERE id = 3";
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tsMs);
            int rows = ps.executeUpdate();
            LOG.log(Level.INFO, "Actualizado {0} filas en int_process.{1} con valor {2}", new Object[]{rows, columnName, tsMs});
            System.out.println("Actualizado int_process." + columnName + " a " + tsMs + " (filas: " + rows + ")");
            return rows > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al actualizar int_process." + columnName, e);
            System.out.println("Error al actualizar int_process." + columnName + ": " + e.getMessage());
            return false;
        }
    }
}
