package com.myproject.laboratorio1.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Logger;

/**
 * <b>Descripción</b>
 * <p align="justify">
 * Interfaz DAO para la selección de registros en la tabla int_proceso_refs.
 * Esta tabla define las variables de salida/referencias de un proceso (4 salidas digitales).
 * Solo se permite la selección de registros (lectura).
 * </p>
 * 
 * <p><b>Estructura de la tabla int_proceso_refs:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_proceso_id: INTEGER (FK a int_proceso)</li>
 *   <li>nombre: VARCHAR(80)</li>
 *   <li>descripcion: BLOB</li>
 *   <li>max_2: FLOAT(5,5)</li>
 *   <li>max: FLOAT(5,5)</li>
 * </ul>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoRefsDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoRefsDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoRefsDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoRefsDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    /**
     * Obtiene una referencia por su ID.
     * 
     * @param id ID de la referencia a buscar
     * @return Hashtable con los datos de la referencia (ID, PROCESO_ID, NOMBRE, DESCRIPCION, MAX_2, MAX),
     *         o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, max FROM int_proceso_refs WHERE id = ?";
        
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
     * Obtiene todas las referencias de un proceso específico.
     * 
     * @param procesoId ID del proceso
     * @return Lista de Hashtables con los datos de las referencias
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByProceso(int procesoId) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, max FROM int_proceso_refs WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> referencias = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    referencias.add(mapResultSetToHashtable(rs));
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
     * @return Hashtable con los datos de la referencia, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectByNombreAndProceso(int procesoId, String nombre) throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, max FROM int_proceso_refs WHERE int_proceso_id = ? AND nombre = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            ps.setString(2, nombre);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las referencias de la tabla.
     * 
     * @return Lista de Hashtables con los datos de todas las referencias
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAll() throws SQLException {
        String sql = "SELECT id, int_proceso_id, nombre, descripcion, max_2, max FROM int_proceso_refs";
        List<Hashtable<String, Object>> referencias = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                referencias.add(mapResultSetToHashtable(rs));
            }
        }
        return referencias;
    }

    /**
     * Cuenta el número de referencias de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Número de referencias asociadas al proceso
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int countByProceso(int procesoId) throws SQLException {
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

    /**
     * Obtiene los IDs de todas las referencias de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Lista de IDs de las referencias
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Integer> getIdsByProceso(int procesoId) throws SQLException {
        String sql = "SELECT id FROM int_proceso_refs WHERE int_proceso_id = ? ORDER BY id";
        List<Integer> ids = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                }
            }
        }
        return ids;
    }

    /**
     * Mapea un ResultSet a un Hashtable con los datos de la referencia.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves ID, PROCESO_ID, NOMBRE, DESCRIPCION, MAX_2, MAX
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        datos.put("NOMBRE", rs.getString("nombre"));
        
        String descripcion = rs.getString("descripcion");
        if (descripcion != null) {
            datos.put("DESCRIPCION", descripcion);
        }
        
        datos.put("MAX_2", rs.getFloat("max_2"));
        datos.put("MAX", rs.getFloat("max"));
        return datos;
    }
}
