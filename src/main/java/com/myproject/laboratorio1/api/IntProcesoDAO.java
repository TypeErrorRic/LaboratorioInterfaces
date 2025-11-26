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
 * Interfaz DAO para la inserción, selección, edición y borrado de registros
 * en la tabla int_proceso. Esta tabla almacena información sobre los procesos
 * del laboratorio virtual.
 * </p>
 * 
 * <p><b>Estructura de la tabla int_proceso:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_proceso_tipo_id: INTEGER (FK a int_proceso_tipo)</li>
 *   <li>nombre: VARCHAR(255)</li>
 *   <li>descripcion: BLOB</li>
 *   <li>tiempo_muestreo: FLOAT(2,2)</li>
 *   <li>archivo_especificaciones: BLOB</li>
 *   <li>archivo_manual: BLOB</li>
 * </ul>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    /**
     * Inserta un nuevo proceso en la tabla int_proceso.
     * 
     * @param nombre          Nombre del proceso
     * @param descripcion     Descripción del proceso (puede ser null)
     * @param tiempoMuestreo  Tiempo de muestreo en segundos
     * @param procesoTipoId   ID del tipo de proceso (FK)
     * @return ID del proceso insertado, o -1 si hubo error
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int insert(String nombre, String descripcion, float tiempoMuestreo, int procesoTipoId) throws SQLException {
        String sql = "INSERT INTO int_proceso (int_proceso_tipo_id, nombre, descripcion, tiempo_muestreo) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, procesoTipoId);
            ps.setString(2, nombre);
            ps.setString(3, descripcion);
            ps.setFloat(4, tiempoMuestreo);
            
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        LOG.log(Level.INFO, "Proceso insertado con ID: {0}", id);
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Obtiene un proceso por su ID.
     * 
     * @param id ID del proceso a buscar
     * @return Hashtable con los datos del proceso (ID, TIPO_ID, NOMBRE, DESCRIPCION, TIEMPO_MUESTREO),
     *         o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_tipo_id, nombre, descripcion, tiempo_muestreo FROM int_proceso WHERE id = ?";
        
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
     * Obtiene un proceso por su nombre.
     * 
     * @param nombre Nombre del proceso a buscar
     * @return Hashtable con los datos del proceso, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectByNombre(String nombre) throws SQLException {
        String sql = "SELECT id, int_proceso_tipo_id, nombre, descripcion, tiempo_muestreo FROM int_proceso WHERE nombre = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, nombre);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todos los procesos de un tipo específico.
     * 
     * @param tipoId ID del tipo de proceso
     * @return Lista de Hashtables con los datos de los procesos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByTipo(int tipoId) throws SQLException {
        String sql = "SELECT id, int_proceso_tipo_id, nombre, descripcion, tiempo_muestreo FROM int_proceso WHERE int_proceso_tipo_id = ?";
        List<Hashtable<String, Object>> procesos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, tipoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    procesos.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return procesos;
    }

    /**
     * Obtiene todos los procesos de la tabla.
     * 
     * @return Lista de Hashtables con los datos de todos los procesos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAll() throws SQLException {
        String sql = "SELECT id, int_proceso_tipo_id, nombre, descripcion, tiempo_muestreo FROM int_proceso";
        List<Hashtable<String, Object>> procesos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                procesos.add(mapResultSetToHashtable(rs));
            }
        }
        return procesos;
    }

    /**
     * Obtiene el tiempo de muestreo de un proceso.
     * 
     * @param procesoId ID del proceso
     * @return Tiempo de muestreo en segundos, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Float getTiempoMuestreo(int procesoId) throws SQLException {
        String sql = "SELECT tiempo_muestreo FROM int_proceso WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getFloat("tiempo_muestreo");
                }
            }
        }
        return null;
    }

    /**
     * Obtiene el tiempo de muestreo del ADC en milisegundos.
     * Este método es usado por PersistenceBridge para configurar el sistema embebido.
     * 
     * @return Tiempo de muestreo en ms (0..65535), o null si no hay proceso activo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Integer getTsAdc() throws SQLException {
        // Obtener el primer proceso activo (o el más reciente)
        String sql = "SELECT tiempo_muestreo FROM int_proceso ORDER BY id DESC LIMIT 1";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                float tiempoSeg = rs.getFloat("tiempo_muestreo");
                return Math.round(tiempoSeg * 1000); // Convertir a milisegundos
            }
        }
        return null;
    }

    /**
     * Obtiene el tiempo de muestreo del DIP en milisegundos.
     * Por defecto es igual al tiempo de muestreo ADC.
     * 
     * @return Tiempo de muestreo en ms (0..65535), o null si no hay proceso activo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Integer getTsDip() throws SQLException {
        return getTsAdc(); // Por defecto, mismo tiempo para ADC y DIP
    }

    /**
     * Actualiza los datos de un proceso existente.
     * 
     * @param id             ID del proceso a actualizar
     * @param nombre         Nuevo nombre
     * @param descripcion    Nueva descripción
     * @param tiempoMuestreo Nuevo tiempo de muestreo
     * @param procesoTipoId  Nuevo tipo de proceso
     * @return true si la actualización fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean update(int id, String nombre, String descripcion, float tiempoMuestreo, int procesoTipoId) throws SQLException {
        String sql = "UPDATE int_proceso SET int_proceso_tipo_id = ?, nombre = ?, descripcion = ?, tiempo_muestreo = ? WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoTipoId);
            ps.setString(2, nombre);
            ps.setString(3, descripcion);
            ps.setFloat(4, tiempoMuestreo);
            ps.setInt(5, id);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Proceso con ID {0} actualizado: {1} filas afectadas", 
                    new Object[]{id, affectedRows});
            return affectedRows > 0;
        }
    }

    /**
     * Actualiza únicamente el tiempo de muestreo de un proceso.
     * 
     * @param id             ID del proceso a actualizar
     * @param tiempoMuestreo Nuevo tiempo de muestreo en segundos
     * @return true si la actualización fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean updateTiempoMuestreo(int id, float tiempoMuestreo) throws SQLException {
        String sql = "UPDATE int_proceso SET tiempo_muestreo = ? WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setFloat(1, tiempoMuestreo);
            ps.setInt(2, id);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Tiempo de muestreo del proceso {0} actualizado a {1}s", 
                    new Object[]{id, tiempoMuestreo});
            return affectedRows > 0;
        }
    }

    /**
     * Elimina un proceso por su ID.
     * 
     * @param id ID del proceso a eliminar
     * @return true si la eliminación fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM int_proceso WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Proceso con ID {0} eliminado: {1} filas afectadas", 
                    new Object[]{id, affectedRows});
            return affectedRows > 0;
        }
    }

    /**
     * Mapea un ResultSet a un Hashtable con los datos del proceso.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves ID, TIPO_ID, NOMBRE, DESCRIPCION, TIEMPO_MUESTREO
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("TIPO_ID", rs.getInt("int_proceso_tipo_id"));
        datos.put("NOMBRE", rs.getString("nombre"));
        
        String descripcion = rs.getString("descripcion");
        if (descripcion != null) {
            datos.put("DESCRIPCION", descripcion);
        }
        
        datos.put("TIEMPO_MUESTREO", rs.getFloat("tiempo_muestreo"));
        return datos;
    }
}
