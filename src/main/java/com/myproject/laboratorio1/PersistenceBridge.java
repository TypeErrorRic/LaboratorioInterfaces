package com.myproject.laboratorio1;

/**
 * Puente opcional hacia la capa de persistencia (API externa).
 * <p>
 * Esta clase integra el acceso a datos de forma desacoplada para no romper la
 * compilaciÃƒÂ³n ni modificar la lÃƒÂ³gica serial existente. Utiliza reflexiÃƒÂ³n para
 * localizar e invocar las clases de la API si estÃƒÂ¡n presentes en el classpath:
 * {@code ProcesoVarsDataDAO}, {@code ProcesoDAO} y {@code ProcesoRefsDataDAO}.
 * En caso de no encontrarlas, todas las operaciones son no-op.
 * </p>
 * <ul>
 *   <li>Al recibir datos del micro (8 analÃƒÂ³gicas + 4 digitales), se invoca
 *       {@code ProcesoVarsDataDAO} para persistirlos.</li>
 *   <li>Antes de enviar comandos al micro, se consultan tiempos de muestreo
 *       en {@code ProcesoDAO} y la mÃƒÂ¡scara de salidas en
 *       {@code ProcesoRefsDataDAO}.</li>
 * </ul>
 */
public final class PersistenceBridge {

    private static final PersistenceBridge INSTANCE = new PersistenceBridge();

    private final DAO dao;
    private volatile boolean tsWatcherRunning;
    private Thread tsWatcherThread;
    private volatile Integer lastPolledTsAdc = null;
    private volatile Integer lastPolledTsDip = null;
    private volatile int lastLedMaskFromRefs = 0;
    private final Object procesoVarsDataDAO;
    private final Object procesoDAO;
    private final Object procesoRefsDataDAO;

    private PersistenceBridge() {
        this.dao = new DAO();
        this.procesoVarsDataDAO = newInstanceSafe("ProcesoVarsDataDAO");
        this.procesoDAO = newInstanceSafe("ProcesoDAO");
        this.procesoRefsDataDAO = newInstanceSafe("ProcesoRefsDataDAO");
    }

    public static PersistenceBridge get() { return INSTANCE; }

    /**
     * Persiste una muestra recibida (8 analógicas y 4 digitales) usando DAO directo.
     *
     * @param tMs        Tiempo relativo de la muestra en milisegundos.
     * @param adc8       Arreglo de 8 valores analógicos (uint16).
     * @param digitalByte Byte con el estado de pines digitales (bits 0..3 usados).
     */
    public void persistSample(long tMs, int[] adc8, int digitalByte) {
        if (adc8 == null || adc8.length < 8) return;
        int[] dig4 = new int[4];
        for (int i = 0; i < 4; i++) dig4[i] = (digitalByte >>> i) & 0x1;
        try { dao.persistVarsData(adc8, dig4, tMs); } catch (Throwable ignored) {}
    }

    /**
     * Obtiene el periodo de muestreo del ADC deseado desde {@code ProcesoDAO}.
     *
     * @return Valor en ms (0..65535) o {@code null} si no estÃƒÂ¡ disponible.
     */
    public Integer getDesiredTsAdc() {
        Integer dbTs = dao.consultarTsAdcProceso3();
        if (dbTs != null) return clamp16(dbTs);
        return clamp16(50);// valor por defecto en ms
    }

    public synchronized void startTsWatcher(SerialProtocolRunner runner, long periodMs) {
        if (runner == null) return;
        if (tsWatcherRunning && tsWatcherThread != null) return; // ya iniciado
        long sleep = (periodMs <= 0) ? 1000L : periodMs;
        tsWatcherRunning = true;
        tsWatcherThread = new Thread(() -> {
            while (tsWatcherRunning) {
                try {
                    Integer adc = getDesiredTsAdc();
                    Integer prevAdc = lastPolledTsAdc;
                    if (adc != null && (prevAdc == null || prevAdc.intValue() != adc.intValue())) {
                        lastPolledTsAdc = adc;
                        System.out.println("Watcher Ts ADC detectÃƒÂ³ cambio: " + adc + " ms (antes: " + prevAdc + ")");
                        SerialProtocolRunner.commandSetTsAdc(runner, adc);
                    }

                    Integer dip = getDesiredTsDip();
                    Integer prevDip = lastPolledTsDip;
                    if (dip != null && (prevDip == null || prevDip.intValue() != dip.intValue())) {
                        lastPolledTsDip = dip;
                        System.out.println("Watcher Ts DIP detectï¿½ï¿½ cambio: " + dip + " ms (antes: " + prevDip + ")");
                        SerialProtocolRunner.commandSetTsDip(runner, dip);
                    }
                    getDesiredLedMask(runner);
                } catch (Exception ignored) {}
                try { Thread.sleep(sleep); } catch (InterruptedException ie) { break; }
            }
        }, "TsWatcher");
        tsWatcherThread.setDaemon(true);
        tsWatcherThread.start();
    }
    /**
     * Obtiene el periodo de muestreo del DIP deseado desde {@code ProcesoDAO}.
     *
     * @return Valor en ms (0..65535) o {@code null} si no estÃƒÂ¡ disponible.
     */
    public Integer getDesiredTsDip() {
        Integer dbTs = dao.consultarTsDipProceso3();
        if (dbTs != null) return clamp16(dbTs);
        return clamp16(50);
    }

    /**
     * Obtiene la mÃƒÂ¡scara de 4 salidas digitales desde {@code ProcesoRefsDataDAO}.
     * <p>
     * Intenta primero un getter directo de mÃƒÂ¡scara y, si no, compone la mÃƒÂ¡scara
     * a partir de un arreglo de salidas digitales (boolean/int).
     * </p>
     *
     * @return MÃƒÂ¡scara 0..255 (bits 0..3) o {@code null} si no estÃƒÂ¡ disponible.
     */
    public void getDesiredLedMask(SerialProtocolRunner runner) {
        int[] change = dao.consultarNuevaMascaraLeds();
        if (change != null && change.length >= 2) {
            int led = change[0];
            int bit = change[1] & 0x1;
            int pos = led - 1;
            lastLedMaskFromRefs = (lastLedMaskFromRefs & ~(1 << pos)) | (bit << pos);
            if (runner != null) SerialProtocolRunner.commandSetLedMask(runner, lastLedMaskFromRefs);
            System.out.println(lastLedMaskFromRefs);
        }
    }

    /**
     * Instancia una clase por nombre simple, probando varios paquetes comunes
     * sin fallar la ejecuciÃƒÂ³n si no existe.
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
        try { return Class.forName("com.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.dao." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("dao." + simpleName); } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Invoca un mÃƒÂ©todo si existe (por nombre y tipos), devolviendo true si se
     * logrÃƒÂ³ ejecutar.
     */
    private static boolean invokeIfExists(Object target, String method, Class<?>[] types, Object[] args) throws Exception {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method, types);
            m.setAccessible(true);
            m.invoke(target, args);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Invoca un getter sin parÃƒÂ¡metros por nombre.
     */
    private static Object invokeGetter(Object target, String method) throws Exception {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
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

