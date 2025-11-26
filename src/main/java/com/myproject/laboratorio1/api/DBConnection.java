package com.myproject.laboratorio1.api;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <b>Descripción</b>
 * <p align="justify">
 * Clase para el establecimiento de la conexión con la base de datos del laboratorio virtual.
 * Implementa el patrón Singleton para mantener una única conexión activa.
 * La conexión puede establecerse mediante parámetros del constructor o mediante
 * un archivo de configuración de texto.
 * </p>
 * 
 * <p><b>Uso con constructor:</b></p>
 * <pre>
 * DBConnection db = DBConnection.getInstance("jdbc:mysql://localhost/laboratorio_virtual", "usuario", "password");
 * Connection conn = db.getConnection();
 * </pre>
 * 
 * <p><b>Uso con archivo de configuración:</b></p>
 * <pre>
 * DBConnection db = DBConnection.getInstanceFromFile("config/db.properties");
 * Connection conn = db.getConnection();
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 1.0
 */
public class DBConnection {

    private static final Logger LOG = Logger.getLogger(DBConnection.class.getName());

    /** URL de conexión por defecto */
    private static final String DEFAULT_URL = "jdbc:mysql://localhost/laboratorio_virtual";
    /** Usuario de BD por defecto */
    private static final String DEFAULT_USER = "arley";
    /** Contraseña de BD por defecto */
    private static final String DEFAULT_PASSWORD = "qwerty";

    private static DBConnection instance;

    private String url;
    private String user;
    private String password;
    private Connection connection;

    /**
     * Constructor privado para establecer conexión con parámetros específicos.
     * 
     * @param url      URL de conexión JDBC (ej: jdbc:mysql://localhost/laboratorio_virtual)
     * @param user     Usuario de la base de datos
     * @param password Contraseña del usuario
     */
    private DBConnection(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        LOG.log(Level.INFO, "DBConnection configurada con URL: {0}, Usuario: {1}", new Object[]{url, user});
    }

    /**
     * Constructor privado por defecto que usa valores predeterminados.
     */
    private DBConnection() {
        this(DEFAULT_URL, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    /**
     * Obtiene la instancia única de DBConnection usando parámetros específicos.
     * Si ya existe una instancia, actualiza los parámetros de conexión.
     * 
     * @param url      URL de conexión JDBC
     * @param user     Usuario de la base de datos
     * @param password Contraseña del usuario
     * @return Instancia única de DBConnection
     */
    public static synchronized DBConnection getInstance(String url, String user, String password) {
        if (instance == null) {
            instance = new DBConnection(url, user, password);
        } else {
            instance.url = url;
            instance.user = user;
            instance.password = password;
            instance.closeConnection();
        }
        return instance;
    }

    /**
     * Obtiene la instancia única de DBConnection usando valores por defecto.
     * 
     * @return Instancia única de DBConnection
     */
    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    /**
     * Obtiene la instancia de DBConnection leyendo la configuración desde un archivo.
     * <p>
     * El archivo debe ser un archivo de propiedades con el siguiente formato:
     * </p>
     * <pre>
     * db.url=jdbc:mysql://localhost/laboratorio_virtual
     * db.user=usuario
     * db.password=password
     * </pre>
     * 
     * @param filePath Ruta al archivo de configuración
     * @return Instancia única de DBConnection configurada desde el archivo
     * @throws IOException Si hay error al leer el archivo
     */
    public static synchronized DBConnection getInstanceFromFile(String filePath) throws IOException {
        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            props.load(reader);
        }

        String url = props.getProperty("db.url", DEFAULT_URL);
        String user = props.getProperty("db.user", DEFAULT_USER);
        String password = props.getProperty("db.password", DEFAULT_PASSWORD);

        LOG.log(Level.INFO, "Configuración cargada desde archivo: {0}", filePath);
        return getInstance(url, user, password);
    }

    /**
     * Obtiene la conexión activa a la base de datos.
     * Si no existe conexión o está cerrada, crea una nueva.
     * 
     * @return Objeto Connection activo
     * @throws SQLException Si hay error al establecer la conexión
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                LOG.log(Level.WARNING, "Driver MySQL (cj) no encontrado, intentando driver legacy");
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException ex) {
                    LOG.log(Level.SEVERE, "No se encontró ningún driver MySQL", ex);
                }
            }
            connection = DriverManager.getConnection(url, user, password);
            LOG.log(Level.INFO, "Conexión establecida exitosamente con: {0}", url);
        }
        return connection;
    }

    /**
     * Cierra la conexión activa a la base de datos.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    LOG.log(Level.INFO, "Conexión cerrada exitosamente");
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Error al cerrar conexión", e);
            }
            connection = null;
        }
    }

    /**
     * Verifica si la conexión está activa.
     * 
     * @return true si la conexión está activa y válida, false en caso contrario
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Obtiene la URL de conexión configurada.
     * 
     * @return URL de conexión JDBC
     */
    public String getUrl() {
        return url;
    }

    /**
     * Obtiene el usuario configurado.
     * 
     * @return Nombre de usuario de la BD
     */
    public String getUser() {
        return user;
    }
}
