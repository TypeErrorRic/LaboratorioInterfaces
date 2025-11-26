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
 * int_proceso_vars_data. Esta tabla almacena los datos de las variables de entrada
 * provenientes del sistema embebido (8 analógicas y 4 digitales).
 * </p>
 * 
 * <p><b>Estructura de la tabla int_proceso_vars_data:</b></p>
 * <ul>
 *   <li>id: INTEGER (PK, Auto-increment)</li>
 *   <li>int_proceso_vars_id: INTEGER (FK a int_proceso_vars)</li>
 *   <li>valor: FLOAT(5,5)</li>
 *   <li>tiempo: FLOAT(5,5)</li>
 *   <li>fecha: DATE</li>
 *   <li>hora: TIME</li>
 * </ul>
 * 
 * <p><b>Uso típico:</b></p>
 * <pre>
 * IntProcesoVarsDataDAO dao = new IntProcesoVarsDataDAO();
 * // Insertar datos de 8 canales analógicos y 4 digitales
 * dao.insert(System.currentTimeMillis(), new int[]{512, 256, 128, 64, 32, 16, 8, 4}, new int[]{1, 0, 1, 0});
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class IntProcesoVarsDataDAO {

    private static final Logger LOG = Logger.getLogger(IntProcesoVarsDataDAO.class.getName());
    private final DBConnection dbConnection;

    /**
     * Constructor que recibe una instancia de DBConnection.
     * 
     * @param dbConnection Instancia de conexión a la base de datos
     */
    public IntProcesoVarsDataDAO(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    /**
     * Constructor por defecto que usa la instancia singleton de DBConnection.
     */
    public IntProcesoVarsDataDAO() {
        this.dbConnection = DBConnection.getInstance();
    }

    /**
     * Inserta un registro de datos de variable en la tabla.
     * 
     * @param varsId  ID de la variable (FK a int_proceso_vars)
     * @param valor   Valor de la variable
     * @param tiempo  Tiempo de la muestra en segundos
     * @param fecha   Fecha de la muestra
     * @param hora    Hora de la muestra
     * @return ID del registro insertado, o -1 si hubo error
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int insert(int varsId, float valor, float tiempo, Date fecha, Time hora) throws SQLException {
        String sql = "INSERT INTO int_proceso_vars_data (int_proceso_vars_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            ps.setInt(1, varsId);
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
     * Inserta datos de múltiples variables (8 analógicas + 4 digitales) en una sola operación.
     * Este método es usado para almacenar las muestras recibidas del sistema embebido.
     * 
     * @param tMs   Tiempo relativo de la muestra en milisegundos
     * @param adc8  Arreglo de 8 valores analógicos (uint16)
     * @param dig4  Arreglo de 4 valores digitales (0 o 1)
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public void insert(long tMs, int[] adc8, int[] dig4) throws SQLException {
        if (adc8 == null || adc8.length < 8 || dig4 == null || dig4.length < 4) {
            LOG.log(Level.WARNING, "Datos inválidos para inserción: adc8 o dig4 no tienen el tamaño correcto");
            return;
        }

        float tiempoSeg = tMs / 1000.0f;
        Date fecha = new Date(System.currentTimeMillis());
        Time hora = new Time(System.currentTimeMillis());

        String sql = "INSERT INTO int_proceso_vars_data (int_proceso_vars_id, valor, tiempo, fecha, hora) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            
            try {
                // Insertar 8 valores analógicos (variables 1-8)
                for (int i = 0; i < 8; i++) {
                    ps.setInt(1, i + 1); // Asumiendo IDs de variables 1-8 para analógicas
                    ps.setFloat(2, adc8[i]);
                    ps.setFloat(3, tiempoSeg);
                    ps.setDate(4, fecha);
                    ps.setTime(5, hora);
                    ps.addBatch();
                }
                
                // Insertar 4 valores digitales (variables 9-12)
                for (int i = 0; i < 4; i++) {
                    ps.setInt(1, i + 9); // Asumiendo IDs de variables 9-12 para digitales
                    ps.setFloat(2, dig4[i]);
                    ps.setFloat(3, tiempoSeg);
                    ps.setDate(4, fecha);
                    ps.setTime(5, hora);
                    ps.addBatch();
                }
                
                ps.executeBatch();
                conn.commit();
                LOG.log(Level.FINE, "Insertados 12 valores de variables (8 ADC + 4 DIG) en t={0}ms", tMs);
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Inserta datos de variables con todos los valores separados.
     * Método alternativo compatible con PersistenceBridge.
     * 
     * @param adc0 Valor analógico canal 0
     * @param adc1 Valor analógico canal 1
     * @param adc2 Valor analógico canal 2
     * @param adc3 Valor analógico canal 3
     * @param adc4 Valor analógico canal 4
     * @param adc5 Valor analógico canal 5
     * @param adc6 Valor analógico canal 6
     * @param adc7 Valor analógico canal 7
     * @param dig0 Valor digital canal 0
     * @param dig1 Valor digital canal 1
     * @param dig2 Valor digital canal 2
     * @param dig3 Valor digital canal 3
     * @param tMs  Tiempo relativo en milisegundos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public void insert(int adc0, int adc1, int adc2, int adc3, int adc4, int adc5, int adc6, int adc7,
                       int dig0, int dig1, int dig2, int dig3, long tMs) throws SQLException {
        int[] adc8 = {adc0, adc1, adc2, adc3, adc4, adc5, adc6, adc7};
        int[] dig4 = {dig0, dig1, dig2, dig3};
        insert(tMs, adc8, dig4);
    }

    /**
     * Método save como alias de insert para compatibilidad con PersistenceBridge.
     * 
     * @param tMs   Tiempo relativo de la muestra en milisegundos
     * @param adc8  Arreglo de 8 valores analógicos
     * @param dig4  Arreglo de 4 valores digitales
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public void save(long tMs, int[] adc8, int[] dig4) throws SQLException {
        insert(tMs, adc8, dig4);
    }

    /**
     * Método guardar como alias de insert para compatibilidad.
     * 
     * @param tMs   Tiempo relativo de la muestra en milisegundos
     * @param adc8  Arreglo de 8 valores analógicos
     * @param dig4  Arreglo de 4 valores digitales
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public void guardar(long tMs, int[] adc8, int[] dig4) throws SQLException {
        insert(tMs, adc8, dig4);
    }

    /**
     * Obtiene un registro de datos por su ID.
     * 
     * @param id ID del registro a buscar
     * @return Hashtable con los datos (ID, VARS_ID, VALOR, TIEMPO, FECHA, HORA), o null si no se encuentra
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public Hashtable<String, Object> selectById(int id) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data WHERE id = ?";
        
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
     * Obtiene todos los datos de una variable específica.
     * 
     * @param varsId ID de la variable
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByVariable(int varsId) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data WHERE int_proceso_vars_id = ? ORDER BY tiempo";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    datos.add(mapResultSetToHashtable(rs));
                }
            }
        }
        return datos;
    }

    /**
     * Obtiene los últimos N datos de una variable para graficar.
     * 
     * @param varsId  ID de la variable
     * @param limite  Número máximo de registros a obtener
     * @return Lista de Hashtables con los datos ordenados por tiempo
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectLatestByVariable(int varsId, int limite) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data " +
                     "WHERE int_proceso_vars_id = ? ORDER BY id DESC LIMIT ?";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
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
     * Obtiene datos de una variable en un rango de fechas.
     * 
     * @param varsId     ID de la variable
     * @param fechaInicio Fecha de inicio del rango
     * @param fechaFin    Fecha de fin del rango
     * @return Lista de Hashtables con los datos
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public List<Hashtable<String, Object>> selectByVariableAndDateRange(int varsId, Date fechaInicio, Date fechaFin) throws SQLException {
        String sql = "SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora FROM int_proceso_vars_data " +
                     "WHERE int_proceso_vars_id = ? AND fecha BETWEEN ? AND ? ORDER BY fecha, hora";
        List<Hashtable<String, Object>> datos = new ArrayList<>();
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
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
        String sql = "DELETE FROM int_proceso_vars_data WHERE id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, id);
            
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Elimina todos los datos de una variable específica.
     * 
     * @param varsId ID de la variable
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteByVariable(int varsId) throws SQLException {
        String sql = "DELETE FROM int_proceso_vars_data WHERE int_proceso_vars_id = ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminados {0} registros de la variable {1}", 
                    new Object[]{affectedRows, varsId});
            return affectedRows;
        }
    }

    /**
     * Elimina datos antiguos de una variable (anteriores a una fecha).
     * 
     * @param varsId ID de la variable
     * @param fecha  Fecha límite (se eliminan registros anteriores)
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteOlderThan(int varsId, Date fecha) throws SQLException {
        String sql = "DELETE FROM int_proceso_vars_data WHERE int_proceso_vars_id = ? AND fecha < ?";
        
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, varsId);
            ps.setDate(2, fecha);
            
            int affectedRows = ps.executeUpdate();
            LOG.log(Level.INFO, "Eliminados {0} registros antiguos de la variable {1}", 
                    new Object[]{affectedRows, varsId});
            return affectedRows;
        }
    }

    /**
     * Elimina todos los datos de todas las variables.
     * 
     * @return Número de registros eliminados
     * @throws SQLException Si hay error en la operación de base de datos
     */
    public int deleteAll() throws SQLException {
        String sql = "DELETE FROM int_proceso_vars_data";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            int affectedRows = stmt.executeUpdate(sql);
            LOG.log(Level.INFO, "Eliminados {0} registros de int_proceso_vars_data", affectedRows);
            return affectedRows;
        }
    }

    /**
     * Mapea un ResultSet a un Hashtable con los datos del registro.
     * 
     * @param rs ResultSet posicionado en un registro válido
     * @return Hashtable con las llaves ID, VARS_ID, VALOR, TIEMPO, FECHA, HORA
     * @throws SQLException Si hay error al leer el ResultSet
     */
    private Hashtable<String, Object> mapResultSetToHashtable(ResultSet rs) throws SQLException {
        Hashtable<String, Object> datos = new Hashtable<>();
        datos.put("ID", rs.getInt("id"));
        datos.put("VARS_ID", rs.getInt("int_proceso_vars_id"));
        datos.put("VALOR", rs.getFloat("valor"));
        datos.put("TIEMPO", rs.getFloat("tiempo"));
        
        Date fecha = rs.getDate("fecha");
        Time hora = rs.getTime("hora");
        
        if (fecha != null) datos.put("FECHA", fecha);
        if (hora != null) datos.put("HORA", hora);
        
        return datos;
    }
}
