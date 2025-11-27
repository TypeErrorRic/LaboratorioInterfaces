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
 * DAO agrupado para la gestión de usuarios y programación de procesos.
 * <p>
 * Gestiona registros de las tablas int_usuarios
 * y la tabla int_usuarios_proceso. Proporciona métodos para la gestión completa
 * de usuarios (inserción, selección, edición y borrado) y la programación de
 * uso de procesos por usuarios específicos.
 * </p>
 * 
 * <p><b>Estructura de la tabla int_usuarios:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_usuarios_tipo_id: INTEGER (FK)</li>
 *   <li>nombres: VARCHAR(255)</li>
 *   <li>apellidos: VARCHAR(255)</li>
 *   <li>email: BLOB</li>
 *   <li>clave: VARCHAR(30)</li>
 * </ul>
 * 
 * <p><b>Estructura de la tabla int_usuarios_proceso:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_usuarios_id: INTEGER (FK a int_usuarios)</li>
 *   <li>int_proceso_id: INTEGER (FK a int_proceso)</li>
 *   <li>fecha: DATE</li>
 *   <li>hora_inicio: TIME</li>
 *   <li>hora_fin: TIME</li>
 *   <li>hits: INTEGER - Contador de accesos</li>
 * </ul>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntUsuariosDAO {

    private static final Logger LOG = Logger.getLogger(IntUsuariosDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntUsuariosDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntUsuariosDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    /**
     * Inserta un nuevo usuario en la tabla int_usuarios.
     * 
     * @param nombres         Nombres del usuario
     * @param apellidos       Apellidos del usuario
     * @param email           Correo electrónico del usuario
     * @param clave           Contraseña del usuario
     * @param usuariosTipoId  ID del tipo de usuario (FK)
     * @return ID del usuario insertado, o -1 si hubo error
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int insert(String nombres, String apellidos, String email, String clave, int usuariosTipoId) throws SQLException {
        String sql = "INSERT INTO int_usuarios (int_usuarios_tipo_id, nombres, apellidos, email, clave) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, usuariosTipoId);
            ps.setString(2, nombres);
            ps.setString(3, apellidos);
            ps.setString(4, email);
            ps.setString(5, clave);
            
            int affectedRows = ps.executeUpdate();
            
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        LOG.log(Level.INFO, "Usuario insertado con ID: {0}", id);
                        return id;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Obtiene un usuario por su ID.
     * 
     * @param id ID del usuario a buscar
     * @return Hashtable con los datos del usuario (NOMBRES, APELLIDOS, EMAIL, CLAVE, TIPO_ID),
     *         o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectById(int id) throws SQLException {
        String sql = "SELECT id, int_usuarios_tipo_id, nombres, apellidos, email, clave FROM int_usuarios WHERE id = ?";
        
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
     * Obtiene un usuario por su email.
     * 
     * @param email Email del usuario a buscar
     * @return Hashtable con los datos del usuario, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectByEmail(String email) throws SQLException {
        String sql = "SELECT id, int_usuarios_tipo_id, nombres, apellidos, email, clave FROM int_usuarios WHERE email = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, email);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene un usuario por su nombre.
     * 
     * @param nombres Nombres del usuario a buscar
     * @return Hashtable con los datos del usuario, o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectByNombres(String nombres) throws SQLException {
        String sql = "SELECT id, int_usuarios_tipo_id, nombres, apellidos, email, clave FROM int_usuarios WHERE nombres = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, nombres);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todos los usuarios de un tipo específico.
     * 
     * @param tipoId ID del tipo de usuario
     * @return Lista de Hashtables con los datos de los usuarios
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByTipo(int tipoId) throws SQLException {
        String sql = "SELECT id, int_usuarios_tipo_id, nombres, apellidos, email, clave FROM int_usuarios WHERE int_usuarios_tipo_id = ?";
        List<Hashtable<String, Object>> usuarios = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, tipoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    usuarios.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return usuarios;
    }

    /**
     * Obtiene todos los usuarios de la tabla.
     * 
     * @return Lista de Hashtables con los datos de todos los usuarios
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAll() throws SQLException {
        String sql = "SELECT id, int_usuarios_tipo_id, nombres, apellidos, email, clave FROM int_usuarios";
        List<Hashtable<String, Object>> usuarios = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                usuarios.add(mapResultSetToHashtable(rs));
            }
        }
        return usuarios;
    }

    /**
     * Valida las credenciales de un usuario contra la base de datos.
     * 
     * @param userInput Email o nombre del usuario
     * @param password  Contraseña a validar
     * @return true si las credenciales son válidas, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean validarCredenciales(String userInput, String password) throws SQLException {
        String sql = "SELECT clave FROM int_usuarios WHERE email = ? OR nombres = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, userInput);
            ps.setString(2, userInput);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedPassword = rs.getString("clave");
                    boolean valid = storedPassword != null && storedPassword.equals(password);
                    LOG.log(Level.INFO, "Validación de usuario [{0}]: {1}", 
                            new Object[]{userInput, valid ? "exitosa" : "fallida"});
                    return valid;
                }
            }
        }
        LOG.log(Level.INFO, "Usuario [{0}] no encontrado", userInput);
        return false;
    }

    /**
     * Actualiza los datos de un usuario existente.
     * 
     * @param id              ID del usuario a actualizar
     * @param nombres         Nuevos nombres
     * @param apellidos       Nuevos apellidos
     * @param email           Nuevo email
     * @param clave           Nueva contraseña
     * @param usuariosTipoId  Nuevo tipo de usuario
     * @return true si la actualización fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean update(int id, String nombres, String apellidos, String email, String clave, int usuariosTipoId) throws SQLException {
        String sql = "UPDATE int_usuarios SET int_usuarios_tipo_id = ?, nombres = ?, apellidos = ?, email = ?, clave = ? WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuariosTipoId);
            ps.setString(2, nombres);
            ps.setString(3, apellidos);
            ps.setString(4, email);
            ps.setString(5, clave);
            ps.setInt(6, id);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Usuario con ID {0} actualizado: {1} filas afectadas", 
                    new Object[]{id, affectedRows});
            return affectedRows > 0;
        }
    }

    /**
     * Elimina un usuario por su ID.
     * 
     * @param id ID del usuario a eliminar
     * @return true si la eliminación fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM int_usuarios WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Usuario con ID {0} eliminado: {1} filas afectadas", 
                    new Object[]{id, affectedRows});
            return affectedRows > 0;
        }
    }

    /**
     * Mapea un ResultSet a un Hashtable con los datos del usuario.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves ID, TIPO_ID, NOMBRES, APELLIDOS, EMAIL, CLAVE
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("TIPO_ID", rs.getInt("int_usuarios_tipo_id"));
        datos.put("NOMBRES", rs.getString("nombres"));
        datos.put("APELLIDOS", rs.getString("apellidos"));
        datos.put("EMAIL", rs.getString("email"));
        datos.put("CLAVE", rs.getString("clave"));
        return datos;
    }

    // ========================================================================
    // MÉTODOS PARA int_usuarios_proceso (PROGRAMACIÓN USUARIO-PROCESO)
    // ========================================================================

    /**
     * Inserta una nueva asignación de usuario a proceso.
     * 
     * @param usuarioId   ID del usuario
     * @param procesoId   ID del proceso
     * @param fechaInicio Fecha de inicio de la asignación
     * @param fechaFin    Fecha de fin de la asignación
     * @param horaInicio  Hora de inicio permitida
     * @param horaFin     Hora de fin permitida
     * @return true si la inserción fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean insertUsuarioProceso(int usuarioId, int procesoId, Date fechaInicio, Date fechaFin, 
                                        Time horaInicio, Time horaFin) throws SQLException {
        String sql = "INSERT INTO int_usuarios_proceso (int_usuarios_id, int_proceso_id, fecha, hora_inicio, hora_fin, hits) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            ps.setInt(2, procesoId);
            ps.setDate(3, fechaInicio);
            ps.setTime(4, horaInicio);
            ps.setTime(5, horaFin);
            ps.setInt(6, 0); // hits inicial en 0
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Asignación usuario-proceso insertada: Usuario {0}, Proceso {1}", 
                    new Object[]{usuarioId, procesoId});
            return affectedRows > 0;
        }
    }

    /**
     * Obtiene todas las asignaciones de un usuario específico.
     * 
     * @param usuarioId ID del usuario
     * @return Lista de Hashtables con los datos de las asignaciones
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectProcesosByUsuario(int usuarioId) throws SQLException {
        String sql = "SELECT id, int_usuarios_id, int_proceso_id, fecha, hora_inicio, hora_fin, hits " +
                     "FROM int_usuarios_proceso WHERE int_usuarios_id = ?";
        List<Hashtable<String, Object>> asignaciones = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    asignaciones.add(mapUsuariosProcesoToHashtable(rs));
                }
            }
        }
        return asignaciones;
    }

    /**
     * Obtiene todas las asignaciones de un proceso específico.
     * 
     * @param procesoId ID del proceso
     * @return Lista de Hashtables con los datos de las asignaciones
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectUsuariosByProceso(int procesoId) throws SQLException {
        String sql = "SELECT id, int_usuarios_id, int_proceso_id, fecha, hora_inicio, hora_fin, hits " +
                     "FROM int_usuarios_proceso WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> asignaciones = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    asignaciones.add(mapUsuariosProcesoToHashtable(rs));
                }
            }
        }
        return asignaciones;
    }

    /**
     * Obtiene una asignación específica por usuario y proceso.
     * 
     * @param usuarioId ID del usuario
     * @param procesoId ID del proceso
     * @return Hashtable con los datos de la asignación, o null si no existe
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectUsuarioProcesoByIds(int usuarioId, int procesoId) throws SQLException {
        String sql = "SELECT id, int_usuarios_id, int_proceso_id, fecha, hora_inicio, hora_fin, hits " +
                     "FROM int_usuarios_proceso WHERE int_usuarios_id = ? AND int_proceso_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            ps.setInt(2, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUsuariosProcesoToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las asignaciones usuario-proceso.
     * 
     * @return Lista de Hashtables con todas las asignaciones
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAllUsuariosProceso() throws SQLException {
        String sql = "SELECT id, int_usuarios_id, int_proceso_id, fecha, hora_inicio, hora_fin, hits " +
                     "FROM int_usuarios_proceso";
        List<Hashtable<String, Object>> asignaciones = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                asignaciones.add(mapUsuariosProcesoToHashtable(rs));
            }
        }
        return asignaciones;
    }

    /**
     * Actualiza una asignación de usuario a proceso.
     * 
     * @param usuarioId   ID del usuario
     * @param procesoId   ID del proceso
     * @param fechaInicio Nueva fecha de inicio
     * @param horaInicio  Nueva hora de inicio
     * @param horaFin     Nueva hora de fin
     * @return true si la actualización fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean updateUsuarioProceso(int usuarioId, int procesoId, Date fechaInicio, 
                                        Time horaInicio, Time horaFin) throws SQLException {
        String sql = "UPDATE int_usuarios_proceso SET fecha = ?, hora_inicio = ?, hora_fin = ? " +
                     "WHERE int_usuarios_id = ? AND int_proceso_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setDate(1, fechaInicio);
            ps.setTime(2, horaInicio);
            ps.setTime(3, horaFin);
            ps.setInt(4, usuarioId);
            ps.setInt(5, procesoId);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Asignación actualizada: Usuario {0}, Proceso {1}", 
                    new Object[]{usuarioId, procesoId});
            return affectedRows > 0;
        }
    }

    /**
     * Elimina una asignación de usuario a proceso.
     * 
     * @param usuarioId ID del usuario
     * @param procesoId ID del proceso
     * @return true si la eliminación fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean deleteUsuarioProceso(int usuarioId, int procesoId) throws SQLException {
        String sql = "DELETE FROM int_usuarios_proceso WHERE int_usuarios_id = ? AND int_proceso_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            ps.setInt(2, procesoId);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Asignación eliminada: Usuario {0}, Proceso {1}", 
                    new Object[]{usuarioId, procesoId});
            return affectedRows > 0;
        }
    }

    /**
     * Elimina todas las asignaciones de un usuario.
     * 
     * @param usuarioId ID del usuario
     * @return Número de asignaciones eliminadas
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteAllProcesosByUsuario(int usuarioId) throws SQLException {
        String sql = "DELETE FROM int_usuarios_proceso WHERE int_usuarios_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminadas {0} asignaciones del usuario {1}", 
                    new Object[]{affectedRows, usuarioId});
            return affectedRows;
        }
    }

    /**
     * Mapea un ResultSet de int_usuarios_proceso a un Hashtable.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con los datos de la asignación
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapUsuariosProcesoToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("USUARIO_ID", rs.getInt("int_usuarios_id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        
        Date fecha = rs.getDate("fecha");
        Time horaInicio = rs.getTime("hora_inicio");
        Time horaFin = rs.getTime("hora_fin");
        
        if (fecha != null) datos.put("FECHA", fecha);
        if (horaInicio != null) datos.put("HORA_INICIO", horaInicio);
        if (horaFin != null) datos.put("HORA_FIN", horaFin);
        datos.put("HITS", rs.getInt("hits"));
        
        return datos;
    }
}
