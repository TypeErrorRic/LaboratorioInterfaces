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
 * @brief DAO integrado para credenciales y operaciones de IO con persistencia en BD.
 *
 * Responsable de:
 *  - Validar usuario/clave contra la tabla int_usuarios (via IntUsuariosDAO).
 *  - Entregar muestras analogicas/digitales desde SerialProtocolRunner y persistirlas en BD.
 *  - Consultar y enviar periodos de muestreo (ADC/DIP) desde/hacia BD y microcontrolador.
 *  - Enviar mascara de LEDs al microcontrolador y sincronizar con BD.
 *
 * Integra tanto la fuente de datos en tiempo real (SerialProtocolRunner)
 * como la persistencia histórica (API de DAOs).
 */
public class DAO {

    private volatile SerialProtocolRunner runner;
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
     * Establece el ID del proceso activo para persistencia.
     * @param procesoId ID del proceso (ej: 1=Control Nivel, 2=Control Temp, 3=Arduino Uno)
     */
    public void setProcesoActivo(int procesoId) {
        this.procesoActivoId = procesoId;
        try {
            this.procesoDataDAO.setProcesoActivo(procesoId);
            LOG.log(Level.INFO, "Proceso activo configurado a ID: {0}", procesoId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al configurar proceso activo", e);
        }
    }

    /**
     * @brief Valida credenciales contra la tabla int_usuarios usando IntUsuariosDAO.
     * @param usuario nombre de usuario (puede ser email o nombres).
     * @param password arreglo de caracteres con la contrasena.
     * @return true si el usuario existe y la clave coincide; false en otro caso.
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
     * @brief Define la fuente activa de datos/comandos, para tener centralizado la instancia de SerialProtocolRunner
     * @param runner instancia de SerialProtocolRunner.
     */
    public void setRunner(SerialProtocolRunner runner) {
        this.runner = runner;
    }

    /**
     * @brief Indica si existe una fuente activa y conectada (Conexion con la base de Datos en este Caso).
     * @return true si hay runner y transmision activa; false en otro caso.
     */
    public boolean hayFuenteActiva() {
        SerialProtocolRunner r = runner;
        return r != null && r.isTransmissionActive();
    }

    /**
     * @brief Obtiene una muestra analogica del runner activo.
     * Los datos se obtienen en tiempo real del SerialProtocolRunner.
     * La persistencia de todas las muestras se hace automáticamente en SerialProtocolRunner
     * via PersistenceBridge.
     * 
     * @param canal indice de canal ADC (0-7).
     * @return arreglo {valor, tMs}; si no hay runner activo devuelve {0, -1}.
     */
    public long[] obtenerMuestraAnalogica(int canal) {
        SerialProtocolRunner r = runner;
        if (r == null || !r.isTransmissionActive()) {
            return new long[]{0L, -1L};
        }
        SerialProtocolRunner.TimedValue tv = r.getAdcValue(canal);
        return new long[]{tv.value, tv.tMs};
    }

    /**
     * @brief Obtiene una muestra digital del runner activo.
     * Los datos se obtienen en tiempo real del SerialProtocolRunner.
     * La persistencia se hace automáticamente en SerialProtocolRunner via PersistenceBridge.
     * 
     * @return arreglo {valor, tMs}; si no hay runner activo devuelve {0, -1}.
     */
    public long[] obtenerMuestraDigital() {
        SerialProtocolRunner r = runner;
        if (r == null || !r.isTransmissionActive()) {
            return new long[]{0L, -1L};
        }
        SerialProtocolRunner.TimedValue tv = r.getDigitalPins();
        return new long[]{tv.value, tv.tMs};
    }

    /**
     * @brief Envia el periodo de muestreo ADC al microcontrolador y lo persiste en BD.
     * Primero actualiza el valor en la tabla int_proceso, luego lo envía al micro.
     * 
     * @param tsMs periodo en milisegundos (uint16 en protocolo).
     * @return true si se actualizó en BD y se envió al micro; false si hubo error.
     */
    public boolean actualizarTsAdc(int tsMs) {
        // Primero actualizar en BD
        try {
            procesoDAO.updateTiempoMuestreo(procesoActivoId, tsMs);
            LOG.log(Level.INFO, "Ts ADC actualizado en BD: {0} ms", tsMs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al actualizar Ts ADC en BD", e);
            return false;
        }
        
        // Luego enviar al microcontrolador
        SerialProtocolRunner r = runner;
        if (r != null && r.isTransmissionActive()) {
            SerialProtocolRunner.commandSetTsAdc(r, tsMs);
            LOG.log(Level.FINE, "Ts ADC enviado al micro: {0} ms", tsMs);
            return true;
        }
        
        LOG.log(Level.WARNING, "No hay runner activo para enviar Ts ADC");
        return false;
    }

    /**
     * @brief Envia el periodo de muestreo digital (DIP) al microcontrolador y lo persiste en BD.
     * Primero actualiza el valor en la tabla int_proceso (tiempo_muestreo_2), luego lo envía al micro.
     * 
     * @param tsMs periodo en milisegundos (uint16 en protocolo).
     * @return true si se actualizó en BD y se envió al micro; false si hubo error.
     */
    public boolean actualizarTsDip(int tsMs) {
        // Primero actualizar en BD
        try {
            procesoDAO.updateTiempoMuestreo2(procesoActivoId, tsMs);
            LOG.log(Level.INFO, "Ts DIP actualizado en BD: {0} ms", tsMs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al actualizar Ts DIP en BD", e);
            return false;
        }
        
        // Luego enviar al microcontrolador
        SerialProtocolRunner r = runner;
        if (r != null && r.isTransmissionActive()) {
            SerialProtocolRunner.commandSetTsDip(r, tsMs);
            LOG.log(Level.FINE, "Ts DIP enviado al micro: {0} ms", tsMs);
            return true;
        }
        
        LOG.log(Level.WARNING, "No hay runner activo para enviar Ts DIP");
        return false;
    }

    /**
     * @brief Envia la mascara de LEDs (bits 0-3) al microcontrolador y persiste en BD.
     * Actualiza los valores de las referencias digitales (DOUT0-DOUT3) en int_proceso_refs_data.
     * 
     * @param mask valor 0..255 con la mascara de LEDs (solo bits 0-3 se usan).
     * @return true si se persistió en BD y se envió al micro; false si hubo error.
     */
    public boolean enviarMascaraLeds(int mask) {
        // Extraer los 4 bits de la máscara (DOUT0-DOUT3)
        int maskedValue = mask & 0x0F;
        
        // Persistir en BD
        try {
            procesoDataDAO.insertRefsData(maskedValue);
            LOG.log(Level.INFO, "Máscara LEDs persistida en BD: 0x{0}", Integer.toHexString(maskedValue));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error al persistir máscara LEDs en BD", e);
            return false;
        }
        
        // Enviar al microcontrolador
        SerialProtocolRunner r = runner;
        if (r != null && r.isTransmissionActive()) {
            SerialProtocolRunner.commandSetLedMask(r, mask);
            LOG.log(Level.FINE, "Máscara LEDs enviada al micro: 0x{0}", Integer.toHexString(mask));
            return true;
        }
        
        LOG.log(Level.WARNING, "No hay runner activo para enviar máscara LEDs");
        return false;
    }
    
    /**
     * @brief Obtiene el periodo de muestreo ADC configurado en BD para el proceso activo.
     * @return periodo en ms, o 0 si hay error.
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
     * @brief Obtiene el periodo de muestreo DIP configurado en BD para el proceso activo.
     * @return periodo en ms, o 0 si hay error.
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
