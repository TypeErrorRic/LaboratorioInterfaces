package com.myproject.laboratorio1;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puente bidireccional exclusivo entre la base de datos y el microcontrolador.
 * <p>
 * <b>Patrón Singleton:</b> Esta clase implementa el patrón Singleton para garantizar
 * una única instancia que gestiona toda la comunicación BD ↔ Micro.
 * </p>
 * 
 * <p><b>Arquitectura de comunicación:</b></p>
 * <pre>
 * ┌──────────────┐      ┌──────────────────┐      ┌──────────────┐
 * │   Base de    │◄────►│ PersistenceBridge│◄────►│    Micro     │
 * │    Datos     │      │   (Singleton)    │      │ controlador  │
 * └──────────────┘      └──────────────────┘      └──────────────┘
 *      ▲                        ▲                        ▲
 *      │                        │                        │
 *   DAOs API            Thread Polling          SerialProtocolRunner
 * </pre>
 * 
 * <p><b>Dirección BD → Micro (Polling):</b></p>
 * <ul>
 *   <li>Thread daemon ejecuta cada 500ms consultando cambios en BD</li>
 *   <li>Detecta modificaciones en tiempo_muestreo (ADC), tiempo_muestreo_2 (DIP)
 *       y máscara de LEDs en int_proceso_refs_data</li>
 *   <li>Envía comandos al micro solo si hay cambios (evita tráfico innecesario)</li>
 *   <li>Usa reflexión para invocar métodos de IntProcesoDAO e IntProcesoDataDAO</li>
 * </ul>
 * 
 * <p><b>Dirección Micro → BD (Persistencia):</b></p>
 * <ul>
 *   <li>Método {@link #persistSample(int, int[], int[])} recibe muestras del micro</li>
 *   <li>Inserta 8 valores analógicos en int_proceso_vars_data (ADC0-ADC7)</li>
 *   <li>Inserta 4 valores digitales en int_proceso_vars_data (DIN0-DIN3)</li>
 *   <li>Registra timestamp en milisegundos para cada muestra</li>
 * </ul>
 * 
 * <p><b>Manejo robusto de errores:</b></p>
 * <ul>
 *   <li>Todos los métodos de reflexión capturan InvocationTargetException</li>
 *   <li>SQLException se registra en nivel FINE (esperadas por desconexiones)</li>
 *   <li>El thread de polling nunca se detiene por errores de BD</li>
 *   <li>Retorna valores por defecto (null) cuando no hay datos disponibles</li>
 * </ul>
 * 
 * <p><b>Uso típico:</b></p>
 * <pre>
 * // Configurar SerialProtocolRunner
 * PersistenceBridge.get().setSerialRunner(runner);
 * 
 * // Cambiar proceso activo
 * PersistenceBridge.get().setProcesoActivo(3); // Arduino Uno
 * 
 * // El polling y persistencia son automáticos
 * </pre>
 * 
 * @author Laboratorio de Interfaces
 * @version 2.0 - Arquitectura BD-Céntrica con Polling
 * @see SerialProtocolRunner
 * @see DAO
 * @see com.myproject.laboratorio1.api.IntProcesoDAO
 * @see com.myproject.laboratorio1.api.IntProcesoDataDAO
 */
public final class PersistenceBridge {

    private static final Logger LOG = Logger.getLogger(PersistenceBridge.class.getName());
    private static final PersistenceBridge INSTANCE = new PersistenceBridge();

    private final Object procesoDataDAO;
    private final Object procesoDAO;
    
    // ID del proceso activo (por defecto Arduino Uno = 3)
    private volatile int procesoActivoId = 3;
    
    // SerialProtocolRunner para comunicación con el micro
    private volatile SerialProtocolRunner serialRunner;
    
    // Thread de polling para detectar cambios en BD
    private volatile Thread pollingThread;
    private volatile boolean pollingActive = false;
    private static final long POLLING_INTERVAL_MS = 500; // Revisar BD cada 500ms
    
    // Últimos valores enviados al micro (para detectar cambios)
    private volatile Integer lastSentTsAdc = null;
    private volatile Integer lastSentTsDip = null;
    private volatile Integer lastSentLedMask = null;

    private PersistenceBridge() {
        this.procesoDataDAO = newInstanceSafe("IntProcesoDataDAO");
        this.procesoDAO = newInstanceSafe("IntProcesoDAO");
        
        // Configurar proceso activo por defecto (Arduino Uno = ID 3)
        if (procesoDataDAO != null) {
            try {
                invokeIfExists(procesoDataDAO, "setProcesoActivo", new Class[]{int.class}, new Object[]{3});
            } catch (Exception e) {
                LOG.log(Level.WARNING, "No se pudo configurar proceso activo", e);
            }
        }
    }

    public static PersistenceBridge get() { return INSTANCE; }
    
    /**
     * Establece el ID del proceso activo.
     * @param procesoId ID del proceso (1=Control Nivel, 2=Control Temp, 3=Arduino Uno)
     */
    public void setProcesoActivo(int procesoId) {
        this.procesoActivoId = procesoId;
        // También configurar en IntProcesoDataDAO si existe
        if (procesoDataDAO != null) {
            try {
                invokeIfExists(procesoDataDAO, "setProcesoActivo", new Class[]{int.class}, new Object[]{procesoId});
                LOG.log(Level.INFO, "Proceso activo configurado a ID: {0}", procesoId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "No se pudo configurar proceso activo en DAO", e);
            }
        }
    }
    
    /**
     * Configura el SerialProtocolRunner para comunicación con el microcontrolador.
     * Inicia el polling automático de cambios en BD.
     * 
     * @param runner Instancia de SerialProtocolRunner ya conectada
     */
    public void setSerialRunner(SerialProtocolRunner runner) {
        this.serialRunner = runner;
        if (runner != null && !pollingActive) {
            startPolling();
        } else if (runner == null && pollingActive) {
            stopPolling();
        }
    }
    
    /**
     * Inicia el thread de polling que revisa BD periódicamente y envía cambios al micro.
     */
    private synchronized void startPolling() {
        if (pollingActive) return;
        
        pollingActive = true;
        pollingThread = new Thread(() -> {
            LOG.info("PersistenceBridge: Polling iniciado");
            while (pollingActive) {
                try {
                    pollDatabaseAndSendToMicro();
                    Thread.sleep(POLLING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error en polling de BD", e);
                }
            }
            LOG.info("PersistenceBridge: Polling detenido");
        }, "PersistenceBridge-Polling");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }
    
    /**
     * Detiene el thread de polling.
     */
    private synchronized void stopPolling() {
        pollingActive = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }
    }
    
    /**
     * Revisa BD para detectar cambios en Ts ADC, Ts DIP y máscara LEDs,
     * y los envía al microcontrolador si hay cambios.
     */
    private void pollDatabaseAndSendToMicro() {
        SerialProtocolRunner runner = this.serialRunner;
        if (runner == null || !runner.isTransmissionActive()) {
            return;
        }
        
        try {
            // Revisar Ts ADC
            Integer tsAdc = getDesiredTsAdc();
            if (tsAdc != null && !tsAdc.equals(lastSentTsAdc)) {
                SerialProtocolRunner.commandSetTsAdc(runner, tsAdc);
                lastSentTsAdc = tsAdc;
                LOG.log(Level.FINE, "Ts ADC actualizado en micro: {0} ms", tsAdc);
            }
            
            // Revisar Ts DIP
            Integer tsDip = getDesiredTsDip();
            if (tsDip != null && !tsDip.equals(lastSentTsDip)) {
                SerialProtocolRunner.commandSetTsDip(runner, tsDip);
                lastSentTsDip = tsDip;
                LOG.log(Level.FINE, "Ts DIP actualizado en micro: {0} ms", tsDip);
            }
            
            // Revisar máscara LEDs
            Integer ledMask = getDesiredLedMask();
            if (ledMask != null && !ledMask.equals(lastSentLedMask)) {
                SerialProtocolRunner.commandSetLedMask(runner, ledMask);
                lastSentLedMask = ledMask;
                LOG.log(Level.FINE, "Máscara LEDs actualizada en micro: 0x{0}", Integer.toHexString(ledMask));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error al enviar cambios al microcontrolador", e);
        }
    }

    /**
     * Persiste una muestra recibida (8 analógicas y 4 digitales).
     * <p>
     * Intenta métodos comunes en {@code IntProcesoDataDAO} mediante reflexión:
     * {@code insertVarsData}. Si no se encuentran, intenta firmas alternativas.
     * </p>
     * <p>
     * <b>Nota:</b> Los pines digitales están codificados en el nibble alto (bits 4-7)
     * del byte digital recibido del microcontrolador. Se realiza un shift de 4 bits
     * a la derecha para extraer DIN0-DIN3.
     * </p>
     *
     * @param tMs        Tiempo relativo de la muestra en milisegundos.
     * @param adc8       Arreglo de 8 valores analógicos (uint16).
     * @param digitalByte Byte con el estado de pines digitales (DIN0-DIN3 en bits 4-7).
     */
    public void persistSample(long tMs, int[] adc8, int digitalByte) {
        if (procesoDataDAO == null || adc8 == null || adc8.length < 8) return;
        try {
            // Los pines digitales están en los bits 4-7 del byte (nibble alto)
            // Hacer shift de 4 bits a la derecha para mover bits 4-7 a posiciones 0-3
            int digitalNibble = (digitalByte >>> 4) & 0x0F;
            
            // Derivar 4 digitales de los bits 0-3 del nibble desplazado
            int[] dig4 = new int[4];
            for (int i = 0; i < 4; i++) dig4[i] = (digitalNibble >>> i) & 0x1;

            // Intentar método insertVarsData de IntProcesoDataDAO
            if (invokeIfExists(procesoDataDAO, "insertVarsData", new Class[]{ long.class, int[].class, int[].class }, new Object[]{ tMs, adc8, dig4 })) return;
            
            // Métodos alternativos por compatibilidad
            if (invokeIfExists(procesoDataDAO, "insert", new Class[]{ long.class, int[].class, int[].class }, new Object[]{ tMs, adc8, dig4 })) return;
            if (invokeIfExists(procesoDataDAO, "save", new Class[]{ long.class, int[].class, int[].class }, new Object[]{ tMs, adc8, dig4 })) return;
        } catch (Throwable ignored) {}
    }

    /**
     * Obtiene el periodo de muestreo del ADC deseado desde {@code IntProcesoDAO}.
     *
     * @return Valor en ms (0..65535) o {@code null} si no está disponible.
     */
    public Integer getDesiredTsAdc() {
        if (procesoDAO == null) return null;
        try {
            Integer v;
            // Intentar getters para tiempo_muestreo (ADC) con ID de proceso
            v = (Integer) invokeIfExistsWithReturn(procesoDAO, "getTiempoMuestreo", new Class[]{int.class}, new Object[]{procesoActivoId});
            if (v != null) return clamp16(v);
            
            // Métodos alternativos sin ID
            v = (Integer) invokeGetter(procesoDAO, "getTsAdc"); 
            if (v != null) return clamp16(v);
            v = (Integer) invokeGetter(procesoDAO, "getTsADC"); 
            if (v != null) return clamp16(v);
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Error al obtener Ts ADC desde BD", e);
        }
        return null;
    }

    /**
     * Obtiene el periodo de muestreo del DIP deseado desde {@code IntProcesoDAO}.
     *
     * @return Valor en ms (0..65535) o {@code null} si no está disponible.
     */
    public Integer getDesiredTsDip() {
        if (procesoDAO == null) return null;
        try {
            Integer v;
            // Intentar getters para tiempo_muestreo_2 (DIP) con ID de proceso
            v = (Integer) invokeIfExistsWithReturn(procesoDAO, "getTiempoMuestreo2", new Class[]{int.class}, new Object[]{procesoActivoId});
            if (v != null) return clamp16(v);
            
            // Métodos alternativos sin ID
            v = (Integer) invokeGetter(procesoDAO, "getTsDip"); 
            if (v != null) return clamp16(v);
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Error al obtener Ts DIP desde BD", e);
        }
        return null;
    }

    /**
     * Obtiene la máscara de 4 salidas digitales desde {@code IntProcesoDataDAO}.
     * Lee el último valor guardado en int_proceso_refs_data.
     *
     * @return Máscara 0..15 (bits 0..3) o {@code null} si no está disponible.
     */
    public Integer getDesiredLedMask() {
        if (procesoDataDAO == null) return null;
        try {
            // Método principal: getRefsDataLedMask
            Integer m = (Integer) invokeGetter(procesoDataDAO, "getRefsDataLedMask");
            if (m != null) return m & 0x0F;
            
            // Métodos alternativos
            m = (Integer) invokeGetter(procesoDataDAO, "getLedMask");
            if (m != null) return m & 0x0F;
        } catch (Throwable e) {
            LOG.log(Level.FINE, "Error al obtener máscara LEDs desde BD", e);
        }
        return null;
    }

    /**
     * Instancia una clase por nombre simple, probando varios paquetes comunes
     * sin fallar la ejecución si no existe.
     */
    private static Object newInstanceSafe(String simpleName) {
        try {
            Class<?> cls = tryFindClass(simpleName);
            if (cls == null) return null;
            return cls.getDeclaredConstructor().newInstance();
        } catch (Throwable ignored) { return null; }
    }

    /**
     * Busca una clase intentando distintos paquetes habituales.
     */
    private static Class<?> tryFindClass(String simpleName) {
        try { return Class.forName(simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.laboratorio1.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.dao." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("dao." + simpleName); } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Invoca un método si existe (por nombre y tipos), devolviendo true si se
     * logró ejecutar. Maneja correctamente InvocationTargetException para evitar
     * propagar SQLException u otras excepciones del método invocado.
     */
    private static boolean invokeIfExists(Object target, String method, Class<?>[] types, Object[] args) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method, types);
            m.setAccessible(true);
            m.invoke(target, args);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (java.lang.reflect.InvocationTargetException e) {
            // El método fue invocado pero lanzó una excepción (probablemente SQLException)
            Throwable cause = e.getCause();
            if (cause instanceof java.sql.SQLException) {
                LOG.log(Level.FINE, "SQLException al ejecutar " + method + ": " + cause.getMessage(), cause);
            } else {
                LOG.log(Level.WARNING, "Error al ejecutar " + method, e);
            }
            return false;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error de reflexión al invocar " + method, e);
            return false;
        }
    }

    /**
     * Invoca un método si existe y devuelve su resultado, o null si no existe o hay error.
     * Maneja correctamente InvocationTargetException y otras excepciones.
     */
    private static Object invokeIfExistsWithReturn(Object target, String method, Class<?>[] types, Object[] args) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (java.lang.reflect.InvocationTargetException e) {
            // El método existe pero lanzó una excepción (ej: SQLException)
            LOG.log(Level.FINE, "Error al invocar " + method + ": " + e.getCause(), e.getCause());
            return null;
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error inesperado al invocar " + method, e);
            return null;
        }
    }

    /**
     * Invoca un getter sin parámetros por nombre. Maneja correctamente
     * InvocationTargetException para evitar propagar excepciones del método invocado.
     */
    private static Object invokeGetter(Object target, String method) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (java.lang.reflect.InvocationTargetException e) {
            // El método existe pero lanzó una excepción (ej: SQLException)
            Throwable cause = e.getCause();
            if (cause instanceof java.sql.SQLException) {
                LOG.log(Level.FINE, "SQLException al invocar getter " + method + ": " + cause.getMessage(), cause);
            } else {
                LOG.log(Level.WARNING, "Error al invocar getter " + method, e);
            }
            return null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error de reflexión al invocar getter " + method, e);
            return null;
        }
    }

    /**
     * Limita un entero al rango de 16 bits sin signo (0..65535).
     */
    private static Integer clamp16(Integer v) {
        if (v == null) return null;
        int x = Math.max(0, Math.min(0xFFFF, v));
        return x;
    }
}
