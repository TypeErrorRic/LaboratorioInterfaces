package com.myproject.laboratorio1.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <b>Descripción</b>
 * <p align="justify">
 * Interfaz DAO para la consulta de registros en la tabla int_proceso_control.
 * Esta tabla almacena los parámetros de control del proceso (tipo de control,
 * parámetros PID, límites, etc.).
 * </p>
 * 
 * <p><b>Estructura de la tabla int_proceso_control:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_proceso_id: INTEGER (FK a int_proceso)</li>
 *   <li>tipo_control: VARCHAR (ej: "PID", "ON-OFF", "Manual")</li>
 *   <li>kp: FLOAT - Ganancia proporcional</li>
 *   <li>ki: FLOAT - Ganancia integral</li>
 *   <li>kd: FLOAT - Ganancia derivativa</li>
 *   <li>setpoint: FLOAT - Valor de referencia</li>
 *   <li>limite_superior: FLOAT</li>
 *   <li>limite_inferior: FLOAT</li>
 *   <li>activo: BOOLEAN/TINYINT</li>
 * </ul>
 * 
 * <p><b>Uso típico:</b></p>
 * <pre>
 * IntProcesoControlDAO dao = new IntProcesoControlDAO();
 * // Obtener control activo para un proceso
 * Hashtable&lt;String, Object&gt; control = dao.selectActiveByProceso(1);
 * if (control != null) {
 *     float kp = (Float) control.get("KP");
 *     float setpoint = (Float) control.get("SETPOINT");
 * }
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoControlDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoControlDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoControlDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoControlDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    /**
     * Obtiene un registro de control por su ID.
     * 
     * @param id ID del registro a buscar
     * @return Hashtable con los datos del control, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectById(int id) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE id = ?";
        
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
     * Obtiene todos los controles de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Lista de Hashtables con los datos de control
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByProceso(int procesoId) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> controles = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    controles.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return controles;
    }

    /**
     * Obtiene el control activo de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Hashtable con los datos del control activo, o null si no hay control activo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectActiveByProceso(int procesoId) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE int_proceso_id = ? AND activo = 1";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene controles por tipo de control.
     * 
     * @param procesoId    ID del proceso
     * @param tipoControl  Tipo de control (ej: "PID", "ON-OFF", "Manual")
     * @return Lista de Hashtables con los datos de control
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByTipoControl(int procesoId, String tipoControl) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE int_proceso_id = ? AND tipo_control = ?";
        List<Hashtable<String, Object>> controles = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            ps.setString(2, tipoControl);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    controles.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return controles;
    }

    /**
     * Obtiene los parámetros PID del control activo de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Arreglo de 3 floats [Kp, Ki, Kd], o null si no hay control activo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public float[] getPIDParameters(int procesoId) throws SQLException {
        Hashtable<String, Object> control = selectActiveByProceso(procesoId);
        if (control == null) return null;
        
        float[] pid = new float[3];
        pid[0] = control.get("KP") != null ? (Float) control.get("KP") : 0.0f;
        pid[1] = control.get("KI") != null ? (Float) control.get("KI") : 0.0f;
        pid[2] = control.get("KD") != null ? (Float) control.get("KD") : 0.0f;
        
        return pid;
    }

    /**
     * Obtiene el setpoint del control activo de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Valor del setpoint, o null si no hay control activo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Float getSetpoint(int procesoId) throws SQLException {
        Hashtable<String, Object> control = selectActiveByProceso(procesoId);
        if (control == null) return null;
        
        return control.get("SETPOINT") != null ? (Float) control.get("SETPOINT") : null;
    }

    /**
     * Obtiene los límites del control activo de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Arreglo de 2 floats [limite_inferior, limite_superior], o null si no hay control activo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public float[] getLimites(int procesoId) throws SQLException {
        Hashtable<String, Object> control = selectActiveByProceso(procesoId);
        if (control == null) return null;
        
        float[] limites = new float[2];
        limites[0] = control.get("LIMITE_INFERIOR") != null ? (Float) control.get("LIMITE_INFERIOR") : Float.MIN_VALUE;
        limites[1] = control.get("LIMITE_SUPERIOR") != null ? (Float) control.get("LIMITE_SUPERIOR") : Float.MAX_VALUE;
        
        return limites;
    }

    /**
     * Cuenta el número de controles de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Número de controles
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int countByProceso(int procesoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM int_proceso_control WHERE int_proceso_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Obtiene todos los tipos de control disponibles en el sistema.
     * 
     * @return Lista de tipos de control únicos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<String> getTiposControlDisponibles() throws SQLException {
        String sql = "SELECT DISTINCT tipo_control FROM int_proceso_control";
        List<String> tipos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String tipo = rs.getString("tipo_control");
                if (tipo != null) {
                    tipos.add(tipo);
                }
            }
        }
        return tipos;
    }

    /**
     * Verifica si existe un control activo para un proceso.
     * 
     * @param procesoId ID del proceso
     * @return true si existe un control activo, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean hasActiveControl(int procesoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM int_proceso_control WHERE int_proceso_id = ? AND activo = 1";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Mapea un ResultSet a un Hashtable con los datos del registro.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves ID, PROCESO_ID, TIPO_CONTROL, KP, KI, KD, SETPOINT, etc.
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        
        datos.put("ID", rs.getInt("id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        
        String tipoControl = rs.getString("tipo_control");
        if (tipoControl != null) datos.put("TIPO_CONTROL", tipoControl);
        
        // Parámetros PID (pueden no existir en todas las configuraciones)
        try {
            float kp = rs.getFloat("kp");
            if (!rs.wasNull()) datos.put("KP", kp);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo kp no encontrado");
        }
        
        try {
            float ki = rs.getFloat("ki");
            if (!rs.wasNull()) datos.put("KI", ki);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo ki no encontrado");
        }
        
        try {
            float kd = rs.getFloat("kd");
            if (!rs.wasNull()) datos.put("KD", kd);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo kd no encontrado");
        }
        
        try {
            float setpoint = rs.getFloat("setpoint");
            if (!rs.wasNull()) datos.put("SETPOINT", setpoint);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo setpoint no encontrado");
        }
        
        try {
            float limSup = rs.getFloat("limite_superior");
            if (!rs.wasNull()) datos.put("LIMITE_SUPERIOR", limSup);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo limite_superior no encontrado");
        }
        
        try {
            float limInf = rs.getFloat("limite_inferior");
            if (!rs.wasNull()) datos.put("LIMITE_INFERIOR", limInf);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo limite_inferior no encontrado");
        }
        
        try {
            boolean activo = rs.getBoolean("activo");
            datos.put("ACTIVO", activo);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo activo no encontrado");
        }
        
        return datos;
    }
}
