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
 * Interfaz DAO para la gestión de registros de la tabla int_usuarios_proceso.
 * Esta tabla relaciona usuarios con procesos, permitiendo la programación
 * del uso de un proceso particular por un usuario específico.
 * </p>
 * 
 * <p><b>Estructura de la tabla int_usuarios_proceso:</b></p>
 * <ul>
 *   <li>int_usuarios_id: INTEGER (FK a int_usuarios)</li>
 *   <li>int_proceso_id: INTEGER (FK a int_proceso)</li>
 *   <li>fecha_inicio: DATE</li>
 *   <li>fecha_fin: DATE</li>
 *   <li>hora_inicio: TIME</li>
 *   <li>hora_fin: TIME</li>
 * </ul>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntUsuariosProcesoDAO {

    private static final Logger LOG = Logger.getLogger(IntUsuariosProcesoDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntUsuariosProcesoDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntUsuariosProcesoDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

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
    public boolean insert(int usuarioId, int procesoId, Date fechaInicio, Date fechaFin, 
                          Time horaInicio, Time horaFin) throws SQLException {
        String sql = "INSERT INTO int_usuarios_proceso (int_usuarios_id, int_proceso_id, fecha_inicio, fecha_fin, hora_inicio, hora_fin) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            ps.setInt(2, procesoId);
            ps.setDate(3, fechaInicio);
            ps.setDate(4, fechaFin);
            ps.setTime(5, horaInicio);
            ps.setTime(6, horaFin);
            
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
    public List<Hashtable<String, Object>> selectByUsuario(int usuarioId) throws SQLException {
        String sql = "SELECT int_usuarios_id, int_proceso_id, fecha_inicio, fecha_fin, hora_inicio, hora_fin " +
                     "FROM int_usuarios_proceso WHERE int_usuarios_id = ?";
        List<Hashtable<String, Object>> asignaciones = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    asignaciones.add(mapResultSetToHashtable(rs));
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
    public List<Hashtable<String, Object>> selectByProceso(int procesoId) throws SQLException {
        String sql = "SELECT int_usuarios_id, int_proceso_id, fecha_inicio, fecha_fin, hora_inicio, hora_fin " +
                     "FROM int_usuarios_proceso WHERE int_proceso_id = ?";
        List<Hashtable<String, Object>> asignaciones = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    asignaciones.add(mapResultSetToHashtable(rs));
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
    public Hashtable<String, Object> selectByUsuarioAndProceso(int usuarioId, int procesoId) throws SQLException {
        String sql = "SELECT int_usuarios_id, int_proceso_id, fecha_inicio, fecha_fin, hora_inicio, hora_fin " +
                     "FROM int_usuarios_proceso WHERE int_usuarios_id = ? AND int_proceso_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, usuarioId);
            ps.setInt(2, procesoId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToHashtable(rs);
                }
            }
        }
        return null;
    }

    /**
     * Obtiene todas las asignaciones.
     * 
     * @return Lista de Hashtables con todas las asignaciones
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectAll() throws SQLException {
        String sql = "SELECT int_usuarios_id, int_proceso_id, fecha_inicio, fecha_fin, hora_inicio, hora_fin " +
                     "FROM int_usuarios_proceso";
        List<Hashtable<String, Object>> asignaciones = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                asignaciones.add(mapResultSetToHashtable(rs));
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
     * @param fechaFin    Nueva fecha de fin
     * @param horaInicio  Nueva hora de inicio
     * @param horaFin     Nueva hora de fin
     * @return true si la actualización fue exitosa, false en caso contrario
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public boolean update(int usuarioId, int procesoId, Date fechaInicio, Date fechaFin, 
                          Time horaInicio, Time horaFin) throws SQLException {
        String sql = "UPDATE int_usuarios_proceso SET fecha_inicio = ?, fecha_fin = ?, hora_inicio = ?, hora_fin = ? " +
                     "WHERE int_usuarios_id = ? AND int_proceso_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setDate(1, fechaInicio);
            ps.setDate(2, fechaFin);
            ps.setTime(3, horaInicio);
            ps.setTime(4, horaFin);
            ps.setInt(5, usuarioId);
            ps.setInt(6, procesoId);
            
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
    public boolean delete(int usuarioId, int procesoId) throws SQLException {
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
    public int deleteByUsuario(int usuarioId) throws SQLException {
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
     * Mapea un ResultSet a un Hashtable con los datos de la asignación.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves USUARIO_ID, PROCESO_ID, FECHA_INICIO, FECHA_FIN, HORA_INICIO, HORA_FIN
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("USUARIO_ID", rs.getInt("int_usuarios_id"));
        datos.put("PROCESO_ID", rs.getInt("int_proceso_id"));
        
        Date fechaInicio = rs.getDate("fecha_inicio");
        Date fechaFin = rs.getDate("fecha_fin");
        Time horaInicio = rs.getTime("hora_inicio");
        Time horaFin = rs.getTime("hora_fin");
        
        if (fechaInicio != null) datos.put("FECHA_INICIO", fechaInicio);
        if (fechaFin != null) datos.put("FECHA_FIN", fechaFin);
        if (horaInicio != null) datos.put("HORA_INICIO", horaInicio);
        if (horaFin != null) datos.put("HORA_FIN", horaFin);
        
        return datos;
    }
}
