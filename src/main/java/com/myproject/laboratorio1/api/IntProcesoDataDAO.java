package com.myproject.laboratorio1.api;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <b>Descripción</b>
 * <p align="justify">
 * Interfaz DAO agrupada para la inserción, selección y borrado de registros en las tablas
 * int_proceso_refs_data e int_proceso_vars_data. Esta interfaz maneja tanto los datos de
 * variables de entrada (8 analógicas + 4 digitales) como los datos de salida/referencias
 * (4 salidas digitales) del sistema embebido.
 * </p>
 * 
 * <p><b>Tablas manejadas:</b></p>
 * <ul>
 *   <li><b>int_proceso_vars_data:</b> Datos de variables de entrada del proceso</li>
 *   <li><b>int_proceso_refs_data:</b> Datos de referencias/salidas del proceso</li>
 * </ul>
 * 
 * <p><b>Estructura común de las tablas:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_proceso_vars_id o int_proceso_refs_id: INTEGER (FK)</li>
 *   <li>valor: INT(11)</li>
 *   <li>tiempo: INT(11) - Tiempo en milisegundos</li>
 *   <li>fecha: DATE</li>
 *   <li>hora: TIME</li>
 * </ul>
 * 
 * <p><b>Uso típico:</b></p>
 * <pre>
 * IntProcesoDataDAO dao = new IntProcesoDataDAO();
 * 
 * // Insertar datos de variables (entradas)
 * dao.insertVarsData(tMs, new int[]{512, 256, 128, 64, 32, 16, 8, 4}, new int[]{1, 0, 1, 0});
 * 
 * // Insertar datos de referencias (salidas)
 * dao.insertRefsData(0b1010); // LED1 y LED3 encendidos
 * 
 * // Consultar últimos datos
 * List&lt;Hashtable&lt;String, Object&gt;&gt; datos = dao.selectLatestVarsData(1, 100);
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoDataDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoDataDAO.class.getName());
    private final DBConnection dbConnection;
    
    // Caché de IDs de variables por proceso (evita consultas repetidas)
    private Integer currentProcesoId = null;
    private int[] adcVarIds = null;  // IDs de ADC0-ADC7
    private int[] dinVarIds = null;  // IDs de DIN0-DIN3
    private int[] doutRefIds = null; // IDs de DOUT0-DOUT3

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoDataDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoDataDAO() {
        this.dbConnection = DBConnection.getInstance();
    }
    
    /**
     * Configura el proceso activo y carga los IDs de variables/referencias desde la BD.
     * Debe llamarse antes de usar insertVarsData o insertRefsData.
     * 
     * @param procesoId ID del proceso activo
     * @throws SQLException Si hay error consultando la base de datos
     */
    public void setProcesoActivo(int procesoId) throws SQLException {
        if (currentProcesoId != null && currentProcesoId == procesoId) {
            return; // Ya está configurado
        }
        
        currentProcesoId = procesoId;
        adcVarIds = new int[8];
        dinVarIds = new int[4];
        doutRefIds = new int[4];
        
        // Consultar IDs de variables de entrada (ADC y DIN)
        String sqlVars = "SELECT id, nombre FROM int_proceso_vars WHERE int_proceso_id = ? ORDER BY id";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlVars)) {
            
            ps.setInt(1, procesoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    int id = rs.getInt("id");
                    
                    // Mapear ADC0-ADC7
                    for (int i = 0; i < 8; i++) {
                        if (nombre.equals("ADC" + i)) {
                            adcVarIds[i] = id;
                        }
                    }
                    
                    // Mapear DIN0-DIN3
                    for (int i = 0; i < 4; i++) {
                        if (nombre.equals("DIN" + i)) {
                            dinVarIds[i] = id;
                        }
                    }
                }
            }
        }
        
        // Consultar IDs de referencias de salida (DOUT)
        String sqlRefs = "SELECT id, nombre FROM int_proceso_refs WHERE int_proceso_id = ? ORDER BY id";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlRefs)) {
            
            ps.setInt(1, procesoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nombre = rs.getString("nombre");
                    int id = rs.getInt("id");
                    
                    // Mapear DOUT0-DOUT3
                    for (int i = 0; i < 4; i++) {
                        if (nombre.equals("DOUT" + i)) {
                            doutRefIds[i] = id;
                        }
                    }
                }
            }
        }
        
        LOG.log(Level.INFO, "Proceso activo configurado: ID={0}", procesoId);
    }

    // ========================================================================
    // MÉTODOS PARA int_proceso_vars_data (VARIABLES DE ENTRADA)
    // ========================================================================

    /**
     * Inserta un registro de datos de variable en la tabla int_proceso_vars_data.
     * 
     * @param varsId  ID de la variable (FK a int_proceso_vars)
     * @param valor   Valor de la variable
     * @param tiempo  Tiempo de la muestra en milisegundos
     * @param fecha   Fecha de la muestra
     * @param hora    Hora de la muestra
     * @return ID del registro insertado, o -1 si hubo error
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int insertVarsData(int varsId, int valor, int tiempo, Date fecha, Time hora) throws SQLException {
        String sql = "INSERT INTO int_proceso_vars_data (int_proceso_vars_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, varsId);
            ps.setInt(2, valor);
            ps.setInt(3, tiempo);
            ps.setDate(4, fecha);
            ps.setTime(5, hora);
            
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Inserta datos de múltiples variables (8 analógicas + 4 digitales) en una sola operación.
     * Este método es usado para almacenar las muestras recibidas del sistema embebido.
     * 
     * @param tMs   Tiempo relativo de la muestra en milisegundos
     * @param adc8  Arreglo de 8 valores analógicos (uint16)
     * @param dig4  Arreglo de 4 valores digitales (0 o 1)
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public void insertVarsData(long tMs, int[] adc8, int[] dig4) throws SQLException {
        if (adc8 == null || adc8.length < 8 || dig4 == null || dig4.length < 4) {
            LOG.log(Level.WARNING, "Datos inválidos para inserción: adc8 o dig4 no tienen el tamaño correcto");
            return;
        }
        
        // Verificar que el proceso activo esté configurado
        if (currentProcesoId == null || adcVarIds == null || dinVarIds == null) {
            LOG.log(Level.WARNING, "Proceso activo no configurado. Llame a setProcesoActivo() primero. Usando IDs por defecto para proceso 3.");
            // Fallback: usar IDs hardcodeados del proceso Arduino Uno (ID=3)
            setProcesoActivo(3);
        }

        int tiempoMs = (int) tMs;
        Date fecha = new Date(System.currentTimeMillis());
        Time hora = new Time(System.currentTimeMillis());

        String sql = "INSERT INTO int_proceso_vars_data (int_proceso_vars_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            try {
                // Insertar 8 valores analógicos usando IDs dinámicos
                for (int i = 0; i < 8; i++) {
                    if (adcVarIds[i] == 0) {
                        LOG.log(Level.WARNING, "ID de variable ADC{0} no encontrado, saltando", i);
                        continue;
                    }
                    ps.setInt(1, adcVarIds[i]);
                    ps.setInt(2, adc8[i]);
                    ps.setInt(3, tiempoMs);
                    ps.setDate(4, fecha);
                    ps.setTime(5, hora);
                    ps.addBatch();
                }
                
                // Insertar 4 valores digitales usando IDs dinámicos
                for (int i = 0; i < 4; i++) {
                    if (dinVarIds[i] == 0) {
                        LOG.log(Level.WARNING, "ID de variable DIN{0} no encontrado, saltando", i);
                        continue;
                    }
                    ps.setInt(1, dinVarIds[i]);
                    ps.setInt(2, dig4[i]);
                    ps.setInt(3, tiempoMs);
                    ps.setDate(4, fecha);
                    ps.setTime(5, hora);
                    ps.addBatch();
                }
                
                ps.executeBatch();
                conn.commit();
                LOG.log(Level.FINE, "Insertados 12 valores de variables (8 ADC + 4 DIG) en t={0}ms para proceso {1}", new Object[]{tMs, currentProcesoId});
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Obtiene un registro de datos de variable por su ID.
     * 
     * @param id ID del registro a buscar
     * @return Hashtable con los datos, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectVarsDataById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapVarsDataToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todos los datos de una variable específica.
     * 
     * @param varsId ID de la variable
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectVarsDataByVariable(int varsId) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data WHERE int_proceso_vars_id = ? ORDER BY tiempo";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(mapVarsDataToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Obtiene los últimos N datos de una variable.
     * 
     * @param varsId  ID de la variable
     * @param limite  Número máximo de registros
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectLatestVarsData(int varsId, int limite) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data " +
                     "WHERE int_proceso_vars_id = ? ORDER BY id DESC LIMIT ?";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
            ps.setInt(2, limite);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(0, mapVarsDataToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Elimina un registro de datos de variable por su ID.
     * 
     * @param id ID del registro a eliminar
     * @return true si la eliminación fue exitosa
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean deleteVarsData(int id) throws SQLException {
        String sql = "DELETE FROM int_proceso_vars_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Elimina todos los datos de una variable específica.
     * 
     * @param varsId ID de la variable
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteVarsDataByVariable(int varsId) throws SQLException {
        String sql = "DELETE FROM int_proceso_vars_data WHERE int_proceso_vars_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminados {0} registros de la variable {1}", new Object[]{affectedRows, varsId});
            return affectedRows;
        }
    }

    /**
     * Elimina todos los datos de variables.
     * 
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteAllVarsData() throws SQLException {
        String sql = "DELETE FROM int_proceso_vars_data";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            int affectedRows = stmt.executeUpdate(sql);
            LOG.log(Level.INFO, "Eliminados {0} registros de int_proceso_vars_data", affectedRows);
            return affectedRows;
        }
    }

    // ========================================================================
    // MÉTODOS PARA int_proceso_refs_data (REFERENCIAS/SALIDAS)
    // ========================================================================

    /**
     * Inserta un registro de datos de referencia en la tabla int_proceso_refs_data.
     * 
     * @param refsId  ID de la referencia (FK a int_proceso_refs)
     * @param valor   Valor de la referencia (0 o 1 para digital)
     * @param tiempo  Tiempo de la muestra en milisegundos
     * @param fecha   Fecha de la muestra
     * @param hora    Hora de la muestra
     * @return ID del registro insertado, o -1 si hubo error
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int insertRefsData(int refsId, int valor, int tiempo, Date fecha, Time hora) throws SQLException {
        String sql = "INSERT INTO int_proceso_refs_data (int_proceso_refs_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, refsId);
            ps.setInt(2, valor);
            ps.setInt(3, tiempo);
            ps.setDate(4, fecha);
            ps.setTime(5, hora);
            
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Inserta el estado de las 4 salidas digitales (LEDs) en una sola operación.
     * 
     * @param ledMask Máscara de 4 bits con el estado de los LEDs (bits 0-3)
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public void insertRefsData(int ledMask) throws SQLException {
        // Verificar que el proceso activo esté configurado
        if (currentProcesoId == null || doutRefIds == null) {
            LOG.log(Level.WARNING, "Proceso activo no configurado. Llame a setProcesoActivo() primero. Usando IDs por defecto para proceso 3.");
            setProcesoActivo(3);
        }
        
        int tiempoMs = (int) System.currentTimeMillis();
        Date fecha = new Date(System.currentTimeMillis());
        Time hora = new Time(System.currentTimeMillis());

        String sql = "INSERT INTO int_proceso_refs_data (int_proceso_refs_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            try {
                // Insertar 4 valores de salidas digitales usando IDs dinámicos
                for (int i = 0; i < 4; i++) {
                    if (doutRefIds[i] == 0) {
                        LOG.log(Level.WARNING, "ID de referencia DOUT{0} no encontrado, saltando", i);
                        continue;
                    }
                    int bitValue = (ledMask >> i) & 0x01;
                    ps.setInt(1, doutRefIds[i]);
                    ps.setInt(2, bitValue);
                    ps.setInt(3, tiempoMs);
                    ps.setDate(4, fecha);
                    ps.setTime(5, hora);
                    ps.addBatch();
                }
                
                ps.executeBatch();
                conn.commit();
                LOG.log(Level.INFO, "Máscara de LEDs guardada: {0} para proceso {1}", new Object[]{Integer.toBinaryString(ledMask & 0x0F), currentProcesoId});
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Obtiene la máscara de LEDs actual desde la base de datos.
     * 
     * @return Máscara de 4 bits (0-15), o null si no hay datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Integer getRefsDataLedMask() throws SQLException {
        String sql = "SELECT int_proceso_refs_id, valor FROM int_proceso_refs_data " +
                     "WHERE int_proceso_refs_id IN (4, 5, 6, 7) " +
                     "AND id IN (SELECT MAX(id) FROM int_proceso_refs_data WHERE int_proceso_refs_id IN (4, 5, 6, 7) GROUP BY int_proceso_refs_id)";
        
        int mask = 0;
        boolean foundAny = false;
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int refId = rs.getInt("int_proceso_refs_id");
                int valor = Math.round(rs.getFloat("valor"));
                if (refId >= 4 && refId <= 7) {
                    mask |= (valor & 0x01) << (refId - 4);
                    foundAny = true;
                }
            }
        }
        
        return foundAny ? mask : null;
    }

    /**
     * Obtiene un registro de datos de referencia por su ID.
     * 
     * @param id ID del registro a buscar
     * @return Hashtable con los datos, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectRefsDataById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRefsDataToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todos los datos de una referencia específica.
     * 
     * @param refsId ID de la referencia
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectRefsDataByReferencia(int refsId) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data WHERE int_proceso_refs_id = ? ORDER BY tiempo";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(mapRefsDataToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Obtiene los últimos N datos de una referencia.
     * 
     * @param refsId  ID de la referencia
     * @param limite  Número máximo de registros
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectLatestRefsData(int refsId, int limite) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data " +
                     "WHERE int_proceso_refs_id = ? ORDER BY id DESC LIMIT ?";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            ps.setInt(2, limite);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(0, mapRefsDataToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Elimina un registro de datos de referencia por su ID.
     * 
     * @param id ID del registro a eliminar
     * @return true si la eliminación fue exitosa
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean deleteRefsData(int id) throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Elimina todos los datos de una referencia específica.
     * 
     * @param refsId ID de la referencia
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteRefsDataByReferencia(int refsId) throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data WHERE int_proceso_refs_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminados {0} registros de la referencia {1}", new Object[]{affectedRows, refsId});
            return affectedRows;
        }
    }

    /**
     * Elimina todos los datos de referencias.
     * 
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteAllRefsData() throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            int affectedRows = stmt.executeUpdate(sql);
            LOG.log(Level.INFO, "Eliminados {0} registros de int_proceso_refs_data", affectedRows);
            return affectedRows;
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS DE MAPEO
    // ========================================================================

    /**
     * Mapea un ResultSet de vars_data a un Hashtable.
     */
    private Hashtable<String, Object> mapVarsDataToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("VARS_ID", rs.getInt("int_proceso_vars_id"));
        datos.put("VALOR", rs.getInt("valor"));
        datos.put("TIEMPO", rs.getInt("tiempo"));
        
        Date fecha = rs.getDate("fecha");
        Time hora = rs.getTime("hora");
        
        if (fecha != null) datos.put("FECHA", fecha);
        if (hora != null) datos.put("HORA", hora);
        
        return datos;
    }

    /**
     * Mapea un ResultSet de refs_data a un Hashtable.
     */
    private Hashtable<String, Object> mapRefsDataToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("REFS_ID", rs.getInt("int_proceso_refs_id"));
        datos.put("VALOR", rs.getInt("valor"));
        datos.put("TIEMPO", rs.getInt("tiempo"));
        
        Date fecha = rs.getDate("fecha");
        Time hora = rs.getTime("hora");
        
        if (fecha != null) datos.put("FECHA", fecha);
        if (hora != null) datos.put("HORA", hora);
        
        return datos;
    }
}
