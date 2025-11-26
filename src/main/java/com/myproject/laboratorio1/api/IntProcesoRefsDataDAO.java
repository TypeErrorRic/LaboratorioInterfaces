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
 * Interfaz DAO para la inserción, selección y borrado de registros en la tabla
 * int_proceso_refs_data. Esta tabla almacena los datos de las variables de salida
 * (referencias) que se envían al sistema embebido (4 salidas digitales para LEDs).
 * </p>
 * 
 * <p><b>Estructura de la tabla int_proceso_refs_data:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_proceso_refs_id: INTEGER (FK a int_proceso_refs)</li>
 *   <li>valor: FLOAT(5,5)</li>
 *   <li>tiempo: FLOAT(5,5)</li>
 *   <li>fecha: DATE</li>
 *   <li>hora: TIME</li>
 * </ul>
 * 
 * <p><b>Uso típico:</b></p>
 * <pre>
 * IntProcesoRefsDataDAO dao = new IntProcesoRefsDataDAO();
 * // Guardar estado de LEDs
 * dao.setLedMask(0b1010); // LED1 y LED3 encendidos
 * // Leer estado actual
 * int mask = dao.getLedMask();
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoRefsDataDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoRefsDataDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoRefsDataDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoRefsDataDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    /**
     * Inserta un registro de datos de referencia en la tabla.
     * 
     * @param refsId  ID de la referencia (FK a int_proceso_refs)
     * @param valor   Valor de la referencia (0 o 1 para digital)
     * @param tiempo  Tiempo de la muestra en segundos
     * @param fecha   Fecha de la muestra
     * @param hora    Hora de la muestra
     * @return ID del registro insertado, o -1 si hubo error
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int insert(int refsId, float valor, float tiempo, Date fecha, Time hora) throws SQLException {
        String sql = "INSERT INTO int_proceso_refs_data (int_proceso_refs_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, refsId);
            ps.setFloat(2, valor);
            ps.setFloat(3, tiempo);
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
    public void setLedMask(int ledMask) throws SQLException {
        float tiempoSeg = System.currentTimeMillis() / 1000.0f;
        Date fecha = new Date(System.currentTimeMillis());
        Time hora = new Time(System.currentTimeMillis());

        String sql = "INSERT INTO int_proceso_refs_data (int_proceso_refs_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            try {
                // Insertar 4 valores de salidas digitales (referencias 1-4)
                for (int i = 0; i < 4; i++) {
                    int bitValue = (ledMask >> i) & 0x01;
                    ps.setInt(1, i + 1); // Asumiendo IDs de referencias 1-4
                    ps.setFloat(2, bitValue);
                    ps.setFloat(3, tiempoSeg);
                    ps.setDate(4, fecha);
                    ps.setTime(5, hora);
                    ps.addBatch();
                }
                
                ps.executeBatch();
                conn.commit();
                LOG.log(Level.INFO, "Máscara de LEDs guardada: {0}", Integer.toBinaryString(ledMask & 0x0F));
                
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
     * Lee los últimos valores de las 4 salidas digitales y los combina en una máscara.
     * 
     * @return Máscara de 4 bits (0-15) con el estado de los LEDs, o null si no hay datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Integer getLedMask() throws SQLException {
        // Obtener el último valor de cada referencia (LED)
        String sql = "SELECT int_proceso_refs_id, valor FROM int_proceso_refs_data " +
                     "WHERE int_proceso_refs_id IN (1, 2, 3, 4) " +
                     "AND id IN (SELECT MAX(id) FROM int_proceso_refs_data WHERE int_proceso_refs_id IN (1, 2, 3, 4) GROUP BY int_proceso_refs_id)";
        
        int mask = 0;
        boolean foundAny = false;
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int refId = rs.getInt("int_proceso_refs_id");
                int valor = Math.round(rs.getFloat("valor"));
                if (refId >= 1 && refId <= 4) {
                    mask |= (valor & 0x01) << (refId - 1);
                    foundAny = true;
                }
            }
        }
        
        return foundAny ? mask : null;
    }

    /**
     * Obtiene las salidas digitales como arreglo de enteros.
     * Método alternativo compatible con PersistenceBridge.
     * 
     * @return Arreglo de 4 enteros con valores 0 o 1, o null si no hay datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int[] getDigitalOutputs() throws SQLException {
        Integer mask = getLedMask();
        if (mask == null) return null;
        
        int[] outputs = new int[4];
        for (int i = 0; i < 4; i++) {
            outputs[i] = (mask >> i) & 0x01;
        }
        return outputs;
    }

    /**
     * Obtiene un registro de datos por su ID.
     * 
     * @param id ID del registro a buscar
     * @return Hashtable con los datos (ID, REFS_ID, VALOR, TIEMPO, FECHA, HORA), o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
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
    public List<Hashtable<String, Object>> selectByReferencia(int refsId) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data WHERE int_proceso_refs_id = ? ORDER BY tiempo";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Obtiene los últimos N datos de una referencia.
     * 
     * @param refsId  ID de la referencia
     * @param limite  Número máximo de registros a obtener
     * @return Lista de Hashtables con los datos ordenados por tiempo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectLatestByReferencia(int refsId, int limite) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data " +
                     "WHERE int_proceso_refs_id = ? ORDER BY id DESC LIMIT ?";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            ps.setInt(2, limite);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(0, mapResultSetToHashtable(rs)); // Agregar al inicio para ordenar ascendente
                }
            }
        }
        return datos;
    }

    /**
     * Obtiene datos de una referencia en un rango de fechas.
     * 
     * @param refsId     ID de la referencia
     * @param fechaInicio Fecha de inicio del rango
     * @param fechaFin    Fecha de fin del rango
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByReferenciaAndDateRange(int refsId, Date fechaInicio, Date fechaFin) throws SQLException {
        String sql = "SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora FROM int_proceso_refs_data " +
                     "WHERE int_proceso_refs_id = ? AND fecha BETWEEN ? AND ? ORDER BY fecha, hora";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            ps.setDate(2, fechaInicio);
            ps.setDate(3, fechaFin);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Elimina un registro de datos por su ID.
     * 
     * @param id ID del registro a eliminar
     * @return true si la eliminación fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Elimina todos los datos de una referencia específica.
     * 
     * @param refsId ID de la referencia
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteByReferencia(int refsId) throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data WHERE int_proceso_refs_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminados {0} registros de la referencia {1}", 
                    new Object[]{affectedRows, refsId});
            return affectedRows;
        }
    }

    /**
     * Elimina datos antiguos de una referencia (anteriores a una fecha).
     * 
     * @param refsId ID de la referencia
     * @param fecha  Fecha límite (se eliminan registros anteriores)
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteOlderThan(int refsId, Date fecha) throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data WHERE int_proceso_refs_id = ? AND fecha < ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, refsId);
            ps.setDate(2, fecha);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminados {0} registros antiguos de la referencia {1}", 
                    new Object[]{affectedRows, refsId});
            return affectedRows;
        }
    }

    /**
     * Elimina todos los datos de todas las referencias.
     * 
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteAll() throws SQLException {
        String sql = "DELETE FROM int_proceso_refs_data";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            int affectedRows = stmt.executeUpdate(sql);
            LOG.log(Level.INFO, "Eliminados {0} registros de int_proceso_refs_data", affectedRows);
            return affectedRows;
        }
    }

    /**
     * Mapea un ResultSet a un Hashtable con los datos del registro.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves ID, REFS_ID, VALOR, TIEMPO, FECHA, HORA
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("REFS_ID", rs.getInt("int_proceso_refs_id"));
        datos.put("VALOR", rs.getFloat("valor"));
        datos.put("TIEMPO", rs.getFloat("tiempo"));
        
        Date fecha = rs.getDate("fecha");
        Time hora = rs.getTime("hora");
        
        if (fecha != null) datos.put("FECHA", fecha);
        if (hora != null) datos.put("HORA", hora);
        
        return datos;
    }
}
