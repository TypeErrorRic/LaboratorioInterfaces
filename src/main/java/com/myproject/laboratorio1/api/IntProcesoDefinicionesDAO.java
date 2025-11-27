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
 * Interfaz DAO agrupada para la selección (solo lectura) de registros en las tablas
 * int_proceso_refs, int_proceso_control e int_proceso_vars. Esta interfaz permite
 * consultar las definiciones y configuraciones de las variables de entrada, salida
 * y parámetros de control de los procesos.
 * </p>
 * 
 * <p><b>Tablas manejadas (solo SELECT):</b></p>
 * <ul>
 *   <li><b>int_proceso_refs:</b> Definición de variables de salida/referencias (4 digitales)</li>
 *   <li><b>int_proceso_vars:</b> Definición de variables de entrada (8 analógicas + 4 digitales)</li>
 *   <li><b>int_proceso_control:</b> Parámetros de control del proceso (PID, ON-OFF, etc.)</li>
 * </ul>
 * 
 * <p><b>Estructura de int_proceso_refs:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK)</li>
 *   <li>int_proceso_id: INTEGER (FK)</li>
 *   <li>nombre: VARCHAR(80)</li>
 *   <li>descripcion: VARCHAR(500)</li>
 *   <li>max_2: INT(11)</li>
 *   <li>min: INT(11)</li>
 * </ul>
 * 
 * <p><b>Estructura de int_proceso_vars:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK)</li>
 *   <li>int_proceso_id: INTEGER (FK)</li>
 *   <li>nombre: VARCHAR(80)</li>
 *   <li>descripcion: VARCHAR(500)</li>
 *   <li>max_2: INT(11)</li>
 *   <li>min: INT(11)</li>
 * </ul>
 * 
 * <p><b>Estructura de int_proceso_control:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK)</li>
 *   <li>int_proceso_id: INTEGER (FK)</li>
 *   <li>nombre: VARCHAR(80)</li>
 *   <li>descripcion: BLOB</li>
 *   <li>parametro1: FLOAT(5,5)</li>
 *   <li>parametro2: FLOAT(5,5)</li>
 *   <li>parametro3: FLOAT(5,5)</li>
 *   <li>parametro4: FLOAT(5,5)</li>
 * </ul>
 * 
 * <p><b>Uso típico:</b></p>
 * <pre>
 * IntProcesoDefinicionesDAO dao = new IntProcesoDefinicionesDAO();
 * 
 * // Obtener variables de un proceso
 * List&lt;Hashtable&lt;String, Object&gt;&gt; vars = dao.selectVarsByProceso(3);
 * 
 * // Obtener referencias de un proceso
 * List&lt;Hashtable&lt;String, Object&gt;&gt; refs = dao.selectRefsByProceso(3);
 * 
 * // Obtener configuración de control
 * Hashtable&lt;String, Object&gt; control = dao.selectControlByProceso(1);
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoDefinicionesDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoDefinicionesDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoDefinicionesDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoDefinicionesDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    // ========================================================================
    // MÉTODOS PARA int_proceso_refs (REFERENCIAS/SALIDAS)
    // ========================================================================

    /**
     * Obtiene una referencia por su ID.
     * 
     * @param id ID de la referencia
     * @return Hashtable con los datos de la referencia, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectRefsById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_refs WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRefsToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las referencias de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Lista de Hashtables con las referencias
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectRefsByProceso(int procesoId) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_refs WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> referencias = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    referencias.add(mapRefsToHashtable(rs));
                }
            }
        }
        return referencias;
    }

    /**
     * Obtiene una referencia por nombre y proceso.
     * 
     * @param procesoId ID del proceso
     * @param nombre    Nombre de la referencia
     * @return Hashtable con los datos, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectRefsByNombreAndProceso(int procesoId, String nombre) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_refs WHERE int_proceso_id = ? AND nombre = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            ps.setString(2, nombre);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRefsToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las referencias de la base de datos.
     * 
     * @return Lista de Hashtables con todas las referencias
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAllRefs() throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_refs";
        List<Hashtable<String, Object>> referencias = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                referencias.add(mapRefsToHashtable(rs));
            }
        }
        return referencias;
    }

    /**
     * Cuenta el número de referencias de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Número de referencias
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int countRefsByProceso(int procesoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM int_proceso_refs WHERE int_proceso_id = ?";
        
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

    // ========================================================================
    // MÉTODOS PARA int_proceso_vars (VARIABLES DE ENTRADA)
    // ========================================================================

    /**
     * Obtiene una variable por su ID.
     * 
     * @param id ID de la variable
     * @return Hashtable con los datos de la variable, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectVarsById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_vars WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapVarsToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las variables de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Lista de Hashtables con las variables
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectVarsByProceso(int procesoId) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_vars WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> variables = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variables.add(mapVarsToHashtable(rs));
                }
            }
        }
        return variables;
    }

    /**
     * Obtiene una variable por nombre y proceso.
     * 
     * @param procesoId ID del proceso
     * @param nombre    Nombre de la variable
     * @return Hashtable con los datos, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectVarsByNombreAndProceso(int procesoId, String nombre) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_vars WHERE int_proceso_id = ? AND nombre = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            ps.setString(2, nombre);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapVarsToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las variables de la base de datos.
     * 
     * @return Lista de Hashtables con todas las variables
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAllVars() throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, min FROM int_proceso_vars";
        List<Hashtable<String, Object>> variables = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                variables.add(mapVarsToHashtable(rs));
            }
        }
        return variables;
    }

    /**
     * Cuenta el número de variables de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Número de variables
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int countVarsByProceso(int procesoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM int_proceso_vars WHERE int_proceso_id = ?";
        
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

    // ========================================================================
    // MÉTODOS PARA int_proceso_control (PARÁMETROS DE CONTROL)
    // ========================================================================

    /**
     * Obtiene un registro de control por su ID.
     * 
     * @param id ID del registro
     * @return Hashtable con los datos del control, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectControlById(int id) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapControlToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todos los controles de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Lista de Hashtables con los controles
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectControlByProceso(int procesoId) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> controles = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    controles.add(mapControlToHashtable(rs));
                }
            }
        }
        return controles;
    }

    /**
     * Obtiene un control por nombre y proceso.
     * 
     * @param procesoId ID del proceso
     * @param nombre    Nombre del control
     * @return Hashtable con los datos, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectControlByNombreAndProceso(int procesoId, String nombre) throws SQLException {
        String sql = "SELECT * FROM int_proceso_control WHERE int_proceso_id = ? AND nombre = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            ps.setString(2, nombre);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapControlToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todos los controles de la base de datos.
     * 
     * @return Lista de Hashtables con todos los controles
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAllControl() throws SQLException {
        String sql = "SELECT * FROM int_proceso_control";
        List<Hashtable<String, Object>> controles = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                controles.add(mapControlToHashtable(rs));
            }
        }
        return controles;
    }

    /**
     * Cuenta el número de controles de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Número de controles
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int countControlByProceso(int procesoId) throws SQLException {
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

    // ========================================================================
    // MÉTODOS PRIVADOS DE MAPEO
    // ========================================================================

    /**
     * Mapea un ResultSet de int_proceso_refs a un Hashtable.
     */
    private Hashtable<String, Object> mapRefsToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        datos.put("NOMBRE", rs.getString("nombre"));
        
        String descripcion = rs.getString("descripcion");
        if (descripcion != null) {
            datos.put("DESCRIPCION", descripcion);
        }
        
        datos.put("MAX", rs.getInt("max_2"));
        datos.put("MIN", rs.getInt("min"));
        return datos;
    }

    /**
     * Mapea un ResultSet de int_proceso_vars a un Hashtable.
     */
    private Hashtable<String, Object> mapVarsToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        datos.put("NOMBRE", rs.getString("nombre"));
        
        String descripcion = rs.getString("descripcion");
        if (descripcion != null) {
            datos.put("DESCRIPCION", descripcion);
        }
        
        datos.put("MAX", rs.getInt("max_2"));
        datos.put("MIN", rs.getInt("min"));
        return datos;
    }

    /**
     * Mapea un ResultSet de int_proceso_control a un Hashtable.
     */
    private Hashtable<String, Object> mapControlToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        
        datos.put("ID", rs.getInt("id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        datos.put("NOMBRE", rs.getString("nombre"));
        
        // Descripción (BLOB)
        try {
            String descripcion = rs.getString("descripcion");
            if (descripcion != null) {
                datos.put("DESCRIPCION", descripcion);
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo descripcion no encontrado o error al leer");
        }
        
        // Parámetros (pueden tener diferentes nombres según el tipo de control)
        try {
            float param1 = rs.getFloat("parametro1");
            if (!rs.wasNull()) datos.put("PARAMETRO1", param1);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo parametro1 no encontrado");
        }
        
        try {
            float param2 = rs.getFloat("parametro2");
            if (!rs.wasNull()) datos.put("PARAMETRO2", param2);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo parametro2 no encontrado");
        }
        
        try {
            float param3 = rs.getFloat("parametro3");
            if (!rs.wasNull()) datos.put("PARAMETRO3", param3);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo parametro3 no encontrado");
        }
        
        try {
            float param4 = rs.getFloat("parametro4");
            if (!rs.wasNull()) datos.put("PARAMETRO4", param4);
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Campo parametro4 no encontrado");
        }
        
        return datos;
    }
}
