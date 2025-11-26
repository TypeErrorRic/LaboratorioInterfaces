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
 * @class DAO
 * @brief Puerta de acceso a la capa de datos del laboratorio.
 *
 * Responsable de:
 *  - Validar usuario/clave contra la tabla int_usuarios.
 *  - Entregar muestras analogicas/digitales como pares {valor, tMs}.
 *  - Enviar periodos de muestreo (ADC/DIP) y mascara de LEDs.
 *
 * El origen actual es un SerialProtocolRunner; en el futuro puede reemplazarse
 * por BD o servicio manteniendo esta interfaz.
 */
public class DAO {

    private volatile SerialProtocolRunner runner;
    private static final Logger LOG = Logger.getLogger(DAO.class.getName());

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost/laboratorio_virtual";
    private static final String DEFAULT_DB_USER = "arley";
    private static final String DEFAULT_DB_PASSWORD = "qwerty";
    private static final int[] ADC_VAR_IDS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] DIGITAL_VAR_IDS = {18, 19, 20, 21};
    private volatile long lastRefsDataId = -1L;
    private final long[] lastAnalogDataIds = new long[ADC_VAR_IDS.length];
    private final long[] lastDigitalDataIds = new long[DIGITAL_VAR_IDS.length];

    /**
     * @brief Construye el DAO inicializando caches de ids.
     */
    public DAO() {
        Arrays.fill(lastAnalogDataIds, -1L);
        Arrays.fill(lastDigitalDataIds, -1L);
    }

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
     * @brief Define la fuente activa de datos/comandos para SerialProtocolRunner.
     * @param runner instancia de SerialProtocolRunner.
     */
    public void setRunner(SerialProtocolRunner runner) {
        this.runner = runner;
    }

    /**
     * @brief Indica si existe una fuente activa y conectada.
     * @return true si hay runner y transmision activa; false en otro caso.
     */
    public boolean hayFuenteActiva() {
        SerialProtocolRunner r = runner;
        return r != null && r.isTransmissionActive();
    }

    /**
     * @brief Obtiene la ultima muestra analogica disponible para un canal.
     * @param canal indice de canal ADC.
     * @return arreglo {valor, tMs}; null si no hay datos nuevos o el canal es invalido.
     */
    public synchronized long[] obtenerMuestraAnalogica(int canal) {
        if (canal < 0 || canal >= ADC_VAR_IDS.length) {
            return null;
        }
        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "SELECT id, valor, tiempo FROM int_proceso_vars_data WHERE int_proceso_vars_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ADC_VAR_IDS[canal]);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long rowId = rs.getLong("id");
                if (rowId == lastAnalogDataIds[canal]) {
                    return null; // sin cambios
                }
                lastAnalogDataIds[canal] = rowId;
                long valor = Math.round(rs.getDouble("valor"));
                long tMs = Math.round(rs.getDouble("tiempo"));
                return new long[]{valor, tMs};
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al consultar int_proceso_vars_data (ADC)", e);
            return null;
        }
    }

    /**
     * @brief Obtiene la ultima muestra digital disponible para un canal.
     * @param canal indice de canal digital.
     * @return arreglo {valor, tMs}; null si no hay datos nuevos o el canal es invalido.
     */
    public synchronized long[] obtenerMuestraDigital(int canal) {
        if (canal < 0 || canal >= DIGITAL_VAR_IDS.length) {
            return null;
        }
        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "SELECT id, valor, tiempo FROM int_proceso_vars_data WHERE int_proceso_vars_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, DIGITAL_VAR_IDS[canal]);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long rowId = rs.getLong("id");
                if (rowId == lastDigitalDataIds[canal]) {
                    return null; // sin cambios
                }
                lastDigitalDataIds[canal] = rowId;
                long valor = Math.round(rs.getDouble("valor"));
                long tMs = Math.round(rs.getDouble("tiempo"));
                return new long[]{valor, tMs};
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al consultar int_proceso_vars_data (DIG)", e);
            return null;
        }
    }

    /**
     * @brief Persiste una muestra completa (8 analogicas + 4 digitales) en int_proceso_vars_data.
     * @param adc8 valores ADC (longitud minima 8).
     * @param dig4 valores digitales (longitud minima 4, bits 0/1).
     * @param tMs  tiempo relativo de la muestra en milisegundos.
     * @return true si se insertaron todas las filas, false en caso de error o datos insuficientes.
     */
    public boolean persistVarsData(int[] adc8, int[] dig4, long tMs) {
        if (adc8 == null || adc8.length < 8 || dig4 == null || dig4.length < 4) {
            LOG.log(Level.WARNING, "Datos insuficientes para persistir muestra (adc8={0}, dig4={1})", new Object[]{adc8, dig4});
            return false;
        }

        final int[] adcIds = ADC_VAR_IDS; // int_proceso_vars_id para ADC0..ADC7 (proceso id=3)
        final int[] digIds = DIGITAL_VAR_IDS;                 // int_proceso_vars_id para DIN0..DIN3 (proceso id=3)

        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "INSERT INTO int_proceso_vars_data (int_proceso_vars_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIME)";
        int tMsInt = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, tMs));
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword)) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < 8; i++) {
                    ps.setInt(1, adcIds[i]);
                    ps.setInt(2, adc8[i]);
                    ps.setInt(3, tMsInt);
                    ps.addBatch();
                }
                for (int i = 0; i < 4; i++) {
                    ps.setInt(1, digIds[i]);
                    ps.setInt(2, dig4[i]);
                    ps.setInt(3, tMsInt);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al insertar muestra en int_proceso_vars_data", e);
            return false;
        }
    }

    /**
     * @brief Actualiza el periodo de muestreo ADC en BD (int_proceso.id=3, columna tiempo_muestreo).
     * @param tsMs periodo en milisegundos.
     * @return true si se actualizo al menos una fila en BD.
     */
    public boolean actualizarTsAdc(int tsMs) {
        return updateProcesoTiempo("tiempo_muestreo", tsMs);
    }

    /**
     * @brief Actualiza el periodo de muestreo digital en BD (int_proceso.id=3, columna tiempo_muestreo_2).
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
     * @brief Consulta la ultima mascara de LEDs registrada.
     *
     * Revisa si hay una nueva fila en int_proceso_refs_data y devuelve el LED y valor asociados.
     * Usa el int_proceso_refs_id para mapear al LED (restando 3).
     *
     * @return arreglo {led (1..4), valor (0/1)} o null si no hay cambios.
     */
    public synchronized int[] consultarNuevaMascaraLeds() {
        String url = resolveConfig("LAB_DB_URL", "laboratorio.db.url", DEFAULT_DB_URL);
        String dbUser = resolveConfig("LAB_DB_USER", "laboratorio.db.user", DEFAULT_DB_USER);
        String dbPassword = resolveConfig("LAB_DB_PASSWORD", "laboratorio.db.password", DEFAULT_DB_PASSWORD);

        String sql = "SELECT id, int_proceso_refs_id, valor FROM int_proceso_refs_data ORDER BY id DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            long rowId = rs.getLong("id");
            if (rowId == lastRefsDataId) {
                return null; // sin cambios desde la ultima lectura
            }
            lastRefsDataId = rowId;

            int refId = rs.getInt("int_proceso_refs_id");
            int ledIndex = refId - 3; // DOUT0..3 estan en ids 4..7
            if (ledIndex < 1 || ledIndex > 4) {
                LOG.log(Level.FINE, "Fila int_proceso_refs_data ignorada (ref fuera de rango): {0}", refId);
                return null;
            }
            double valor = rs.getDouble("valor");
            int bit = (Math.abs(valor) > 0.0001) ? 1 : 0;
            return new int[]{ledIndex, bit};
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al consultar int_proceso_refs_data", e);
            return null;
        }
    }

    /**
     * @brief Consulta el valor de tiempo_muestreo para int_proceso.id = 3.
     * @return valor en ms o null si no se pudo obtener.
     */
    public Integer consultarTsAdcProceso3() {
        return consultarTsProceso3("tiempo_muestreo");
    }

    /**
     * @brief Consulta el valor de tiempo_muestreo_2 para int_proceso.id = 3.
     * @return valor en ms o null si no se pudo obtener.
     */
    public Integer consultarTsDipProceso3() {
        return consultarTsProceso3("tiempo_muestreo_2");
    }

    /**
     * @brief Consulta el periodo de muestreo para el proceso 3.
     * @param column nombre de columna permitido (tiempo_muestreo o tiempo_muestreo_2).
     * @return valor en ms o null si no se pudo obtener.
     */
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

    /**
     * @brief Obtiene configuracion de BD desde variable de entorno, propiedad del sistema o un valor por defecto.
     * @param envKey nombre de la variable de entorno.
     * @param sysPropKey clave de propiedad del sistema.
     * @param defaultValue valor por defecto si no se encuentra configuracion.
     * @return cadena con la configuracion resuelta.
     */
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
     * @brief Actualiza el periodo de muestreo en la tabla int_proceso (id=3).
     * @param columnName nombre de la columna a actualizar.
     * @param tsMs valor en milisegundos.
     * @return true si se actualizo al menos una fila.
     */
    private boolean updateProcesoTiempo(String columnName, int tsMs) {
        // Solo permitir columnas esperadas para evitar inyeccion accidental.
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
