package com.myproject.laboratorio1;

import com.myproject.laboratorio1.api.DBConnection;
import com.myproject.laboratorio1.api.IntUsuariosDAO;
import com.myproject.laboratorio1.api.IntProcesoDAO;
import com.myproject.laboratorio1.api.IntProcesoDataDAO;
import com.myproject.laboratorio1.api.IntProcesoDefinicionesDAO;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object para interacción exclusiva con la base de datos.
 * <p>
 * <b>Arquitectura BD-Céntrica:</b> Esta clase implementa el patrón DAO como capa
 * de acceso exclusivo a la base de datos, sin ninguna interacción directa con el
 * microcontrolador. {@link PersistenceBridge} actúa como intermediario entre BD y micro.
 * </p>
 * 
 * <p><b>Responsabilidades principales:</b></p>
 * <ul>
 *   <li><b>Autenticación:</b> Validar credenciales contra la tabla int_usuarios
 *       utilizando {@link com.myproject.laboratorio1.api.IntUsuariosDAO}</li>
 *   <li><b>Lectura de datos históricos:</b> Consultar últimas muestras analógicas
 *       y digitales desde int_proceso_vars_data para graficación</li>
 *   <li><b>Configuración del proceso:</b> Actualizar tiempos de muestreo (Ts ADC/DIP)
 *       y máscara de LEDs en la base de datos</li>
 *   <li><b>Gestión de proceso activo:</b> Sincronizar el ID del proceso activo entre
 *       DAO y PersistenceBridge</li>
 * </ul>
 * 
 * <p><b>Flujo de datos:</b></p>
 * <pre>
 * GUI → DAO.actualizarTsAdc(500) → BD actualizada
 *                                    ↓
 *                    PersistenceBridge polling detecta cambio
 *                                    ↓
 *                           Comando enviado al micro
 * </pre>
 * 
 * <p><b>DAOs utilizados:</b></p>
 * <ul>
 *   <li>{@link com.myproject.laboratorio1.api.IntUsuariosDAO} - Gestión de usuarios</li>
 *   <li>{@link com.myproject.laboratorio1.api.IntProcesoDAO} - Configuración de procesos</li>
 *   <li>{@link com.myproject.laboratorio1.api.IntProcesoDataDAO} - Datos de variables/referencias</li>
 *   <li>{@link com.myproject.laboratorio1.api.IntProcesoDefinicionesDAO} - Definiciones de proceso</li>
 * </ul>
 * 
 * @author Laboratorio de Interfaces
 * @version 2.0 - Arquitectura BD-Céntrica
 * @see PersistenceBridge
 * @see com.myproject.laboratorio1.api.IntProcesoDataDAO
 */
public class DAO {

    private static final Logger LOG = Logger.getLogger(DAO.class.getName());
    
    // DAOs para interacción con BD
    private final IntUsuariosDAO usuariosDAO;
    private final IntProcesoDAO procesoDAO;
    private final IntProcesoDataDAO procesoDataDAO;
    private final IntProcesoDefinicionesDAO definicionesDAO;
    
    // ID del proceso activo (por defecto Arduino Uno = 3)
    private int procesoActivoId = 3;
    
    /**
     * Constructor que inicializa los DAOs necesarios.
     */
    public DAO() {
        DBConnection dbConn = DBConnection.getInstance();
        this.usuariosDAO = new IntUsuariosDAO(dbConn);
        this.procesoDAO = new IntProcesoDAO(dbConn);
        this.procesoDataDAO = new IntProcesoDataDAO(dbConn);
        this.definicionesDAO = new IntProcesoDefinicionesDAO(dbConn);
        
        // Configurar proceso activo en IntProcesoDataDAO
        try {
            this.procesoDataDAO.setProcesoActivo(procesoActivoId);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "No se pudo configurar proceso activo", e);
        }
    }
    
    /**
     * Establece el proceso activo para todas las operaciones de persistencia.
     * <p>
     * Este método configura el contexto de operación para la sesión actual,
     * definiendo qué proceso (experimento) será el objetivo de todas las
     * operaciones de lectura/escritura en la base de datos.
     * </p>
     * <p>
     * <b>Sincronización automática:</b> También actualiza el proceso activo en
     * {@link PersistenceBridge} para que el polling y persistencia automática
     * operen en el contexto correcto.
     * </p>
     * 
     * @param procesoId ID del proceso a activar (ej: 1=Control Nivel, 2=Control Temp, 3=Arduino Uno)
     * @see com.myproject.laboratorio1.api.IntProcesoDataDAO#setProcesoActivo(int)
     * @see PersistenceBridge#setProcesoActivo(int)
     */
    public void setProcesoActivo(int procesoId) {
        this.procesoActivoId = procesoId;
        try {
            this.procesoDataDAO.setProcesoActivo(procesoId);
            // Sincronizar con PersistenceBridge
            PersistenceBridge.get().setProcesoActivo(procesoId);
            LOG.log(Level.INFO, "Proceso activo configurado a ID: {0}", procesoId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al configurar proceso activo", e);
        }
    }

    /**
     * Valida credenciales de usuario contra la tabla int_usuarios.
     * <p>
     * Este método utiliza {@link com.myproject.laboratorio1.api.IntUsuariosDAO}
     * para verificar si las credenciales proporcionadas coinciden con algún
     * registro en la base de datos. Limpia el arreglo de contraseña de la
     * memoria por seguridad.
     * </p>
     * 
     * @param usuario nombre de usuario o email (se hace trim())
     * @param password arreglo de caracteres con la contraseña (se limpia después)
     * @return {@code true} si las credenciales son válidas, {@code false} en caso contrario
     * @throws NullPointerException si usuario o password son null
     * @see com.myproject.laboratorio1.api.IntUsuariosDAO#validarCredenciales(String, String)
     */
    public boolean validarUsuario(String usuario, char[] password) {
        if (usuario == null || password == null) {
            return false;
        }

        String userInput = usuario.trim();
        String passwordInput = new String(password);
        Arrays.fill(password, '\0'); // Limpiar referencia en memoria

        if (userInput.isEmpty()) {
            return false;
        }

        try {
            return usuariosDAO.validarCredenciales(userInput, passwordInput);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al validar usuario", e);
            return false;
        }
    }

    /**
     * Verifica si existe conexión activa con la base de datos.
     * <p>
     * Intenta obtener una conexión desde {@link com.myproject.laboratorio1.api.DBConnection}
     * para validar que el pool de conexiones está operativo.
     * </p>
     * 
     * @return {@code true} si la conexión a BD está disponible, {@code false} en caso contrario
     * @see com.myproject.laboratorio1.api.DBConnection#getConnection()
     */
    public boolean hayConexionBD() {
        try {
            return DBConnection.getInstance().getConnection() != null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error al verificar conexión BD", e);
            return false;
        }
    }

    /**
     * Obtiene la última muestra analógica registrada para un canal específico.
     * <p>
     * Consulta el registro más reciente de int_proceso_vars_data para el canal
     * ADC indicado. Utilizado por los timers de graficación cada 200ms.
     * </p>
     * 
     * @param canal índice del canal ADC (0-7, correspondiente a ADC0-ADC7)
     * @return arreglo de 2 elementos: [valor_adc, tiempo_ms]. Si no hay datos
     *         registrados, retorna {0, -1}
     * @see com.myproject.laboratorio1.api.IntProcesoDataDAO#getLatestAdcData(int)
     * @see Laboratorio1#iniciarTimers()
     */
    public long[] obtenerUltimaMuestraAnalogica(int canal) {
        try {
            return procesoDataDAO.getLatestAdcData(canal);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error al obtener muestra analógica desde BD", e);
            return new long[]{0L, -1L};
        }
    }

    /**
     * Obtiene la última muestra digital registrada (estado de DIP switches).
     * <p>
     * Consulta los registros más recientes de int_proceso_vars_data para los
     * 4 DIP switches (DIN0-DIN3) y los compone en un nibble de 4 bits.
     * Utilizado por el timer de graficación digital cada 200ms.
     * </p>
     * 
     * @return arreglo de 2 elementos: [nibble_4bits, tiempo_ms]. El nibble tiene
     *         el formato: bit0=DIN0, bit1=DIN1, bit2=DIN2, bit3=DIN3.
     *         Si no hay datos, retorna {0, -1}
     * @see com.myproject.laboratorio1.api.IntProcesoDataDAO#getLatestDigitalData()
     * @see Laboratorio1#iniciarTimers()
     */
    public long[] obtenerUltimaMuestraDigital() {
        try {
            return procesoDataDAO.getLatestDigitalData();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error al obtener muestra digital desde BD", e);
            return new long[]{0L, -1L};
        }
    }

    /**
     * Actualiza el periodo de muestreo ADC en la base de datos.
     * <p>
     * <b>Flujo de sincronización automática:</b>
     * </p>
     * <pre>
     * 1. DAO.actualizarTsAdc(500) → UPDATE int_proceso SET tiempo_muestreo=500
     * 2. PersistenceBridge polling (cada 500ms) detecta cambio
     * 3. Envía comando 0x08 (Set Ts ADC) al microcontrolador
     * 4. Micro actualiza su periodo de muestreo ADC
     * </pre>
     * <p>
     * La sincronización es automática gracias al thread de polling de
     * {@link PersistenceBridge#pollDatabaseAndSendToMicro()}.
     * </p>
     * 
     * @param tsMs periodo de muestreo en milisegundos (debe ser &gt; 0)
     * @return {@code true} si la actualización en BD fue exitosa, {@code false} en caso contrario
     * @see com.myproject.laboratorio1.api.IntProcesoDAO#updateTiempoMuestreo(int, int)
     * @see PersistenceBridge#pollDatabaseAndSendToMicro()
     */
    public boolean actualizarTsAdc(int tsMs) {
        try {
            boolean resultado = procesoDAO.updateTiempoMuestreo(procesoActivoId, tsMs);
            if (resultado) {
                LOG.log(Level.INFO, "Ts ADC actualizado en BD: {0} ms", tsMs);
            }
            return resultado;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al actualizar Ts ADC en BD", e);
            return false;
        }
    }

    /**
     * Actualiza el periodo de muestreo digital (DIP switches) en la base de datos.
     * <p>
     * <b>Flujo de sincronización automática:</b>
     * </p>
     * <pre>
     * 1. DAO.actualizarTsDip(1000) → UPDATE int_proceso SET tiempo_muestreo_2=1000
     * 2. PersistenceBridge polling (cada 500ms) detecta cambio
     * 3. Envía comando 0x03 (Set Ts DIP) al microcontrolador
     * 4. Micro actualiza su periodo de muestreo de DIP switches
     * </pre>
     * <p>
     * La sincronización es automática gracias al thread de polling de
     * {@link PersistenceBridge#pollDatabaseAndSendToMicro()}.
     * </p>
     * 
     * @param tsMs periodo de muestreo en milisegundos (debe ser &gt; 0)
     * @return {@code true} si la actualización en BD fue exitosa, {@code false} en caso contrario
     * @see com.myproject.laboratorio1.api.IntProcesoDAO#updateTiempoMuestreo2(int, int)
     * @see PersistenceBridge#pollDatabaseAndSendToMicro()
     */
    public boolean actualizarTsDip(int tsMs) {
        try {
            boolean resultado = procesoDAO.updateTiempoMuestreo2(procesoActivoId, tsMs);
            if (resultado) {
                LOG.log(Level.INFO, "Ts DIP actualizado en BD: {0} ms", tsMs);
            }
            return resultado;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al actualizar Ts DIP en BD", e);
            return false;
        }
    }

    /**
     * Actualiza la máscara de LEDs en la base de datos.
     * <p>
     * <b>Flujo de sincronización automática:</b>
     * </p>
     * <pre>
     * 1. DAO.enviarMascaraLeds(0b1010) → INSERT int_proceso_refs_data con valor=10
     * 2. PersistenceBridge polling (cada 500ms) detecta cambio
     * 3. Envía comando 0x01 (Set LED Mask) al microcontrolador
     * 4. Micro actualiza estado de los 4 LEDs (D8-D11)
     * </pre>
     * <p>
     * La máscara es un nibble de 4 bits donde cada bit controla un LED:
     * </p>
     * <ul>
     *   <li>Bit 0: LED0 (D8)</li>
     *   <li>Bit 1: LED1 (D9)</li>
     *   <li>Bit 2: LED2 (D10)</li>
     *   <li>Bit 3: LED3 (D11)</li>
     * </ul>
     * 
     * @param mask máscara de 4 bits (0-15) que representa el estado de los LEDs
     * @return {@code true} si la inserción en BD fue exitosa, {@code false} en caso contrario
     * @see com.myproject.laboratorio1.api.IntProcesoDataDAO#insertRefsData(int)
     * @see PersistenceBridge#pollDatabaseAndSendToMicro()
     */
    public boolean enviarMascaraLeds(int mask) {
        int maskedValue = mask & 0x0F;
        
        try {
            procesoDataDAO.insertRefsData(maskedValue);
            LOG.log(Level.INFO, "Máscara LEDs persistida en BD: 0x{0}", Integer.toHexString(maskedValue));
            return true;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al persistir máscara LEDs en BD", e);
            return false;
        }
    }
    
    /**
     * Obtiene el periodo de muestreo ADC configurado en la base de datos.
     * <p>
     * Consulta el campo {@code tiempo_muestreo} de la tabla {@code int_proceso}
     * para el proceso activo actual.
     * </p>
     * 
     * @return periodo de muestreo en milisegundos, o 0 si ocurre un error
     * @see com.myproject.laboratorio1.api.IntProcesoDAO#getTiempoMuestreo(int)
     */
    public int obtenerTsAdcDesdeBD() {
        try {
            return procesoDAO.getTiempoMuestreo(procesoActivoId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al obtener Ts ADC desde BD", e);
            return 0;
        }
    }
    
    /**
     * Obtiene el periodo de muestreo digital (DIP switches) configurado en la base de datos.
     * <p>
     * Consulta el campo {@code tiempo_muestreo_2} de la tabla {@code int_proceso}
     * para el proceso activo actual.
     * </p>
     * 
     * @return periodo de muestreo en milisegundos, o 0 si ocurre un error
     * @see com.myproject.laboratorio1.api.IntProcesoDAO#getTiempoMuestreo2(int)
     */
    public int obtenerTsDipDesdeBD() {
        try {
            return procesoDAO.getTiempoMuestreo2(procesoActivoId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al obtener Ts DIP desde BD", e);
            return 0;
        }
    }
}
