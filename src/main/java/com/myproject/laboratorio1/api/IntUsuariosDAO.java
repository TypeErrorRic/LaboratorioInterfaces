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
 * Interfaz DAO para la gestión de registros de la tabla int_usuarios.
 * Proporciona métodos para inserción, selección, edición y borrado de usuarios,
 * así como filtrado por diversos campos.
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
}
