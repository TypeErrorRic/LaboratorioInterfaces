package com.myproject.laboratorio1;

/**
 * Puente opcional hacia la capa de persistencia (API externa).
 * <p>
 * Esta clase integra el acceso a datos de forma desacoplada para no romper la
 * compilación ni modificar la lógica serial existente. Utiliza reflexión para
 * localizar e invocar las clases de la API si están presentes en el classpath:
 * {@code ProcesoVarsDataDAO}, {@code ProcesoDAO} y {@code ProcesoRefsDataDAO}.
 * En caso de no encontrarlas, todas las operaciones son no-op.
 * </p>
 * <ul>
 *   <li>Al recibir datos del micro (8 analógicas + 4 digitales), se invoca
 *       {@code ProcesoVarsDataDAO} para persistirlos.</li>
 *   <li>Antes de enviar comandos al micro, se consultan tiempos de muestreo
 *       en {@code ProcesoDAO} y la máscara de salidas en
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
     * Persiste una muestra recibida (8 analógicas y 4 digitales).
     * <p>
     * Intenta métodos comunes en {@code ProcesoVarsDataDAO} mediante reflexión:
     * {@code insert}, {@code save}, {@code guardar}. Si no se encuentran, intenta
     * una firma alternativa por campos separados.
     * </p>
     *
     * @param tMs        Tiempo relativo de la muestra en milisegundos.
     * @param adc8       Arreglo de 8 valores analógicos (uint16).
     * @param digitalByte Byte con el estado de pines digitales (bits 0..3 usados).
     */
    public void persistSample(long tMs, int[] adc8, int digitalByte) {
        if (procesoVarsDataDAO == null || adc8 == null || adc8.length < 8) return;
        try {
            // Derivar 4 digitales de los 8 bits menos significativos
            int[] dig4 = new int[4];
            for (int i = 0; i < 4; i++) dig4[i] = (digitalByte >>> i) & 0x1;

            // Intentar distintas firmas comunes
            if (invokeIfExists(procesoVarsDataDAO, "insert", new Class[]{ long.class, int[].class, int[].class }, new Object[]{ tMs, adc8, dig4 })) return;
            if (invokeIfExists(procesoVarsDataDAO, "save", new Class[]{ long.class, int[].class, int[].class }, new Object[]{ tMs, adc8, dig4 })) return;
            if (invokeIfExists(procesoVarsDataDAO, "guardar", new Class[]{ long.class, int[].class, int[].class }, new Object[]{ tMs, adc8, dig4 })) return;
            // Alternativas por campos separados
            if (invokeIfExists(procesoVarsDataDAO, "insert", new Class[]{ int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, long.class },
                    new Object[]{ adc8[0], adc8[1], adc8[2], adc8[3], adc8[4], adc8[5], adc8[6], adc8[7], dig4[0], dig4[1], dig4[2], dig4[3], tMs })) return;
        } catch (Throwable ignored) {}
    }

    /**
     * Obtiene el periodo de muestreo del ADC deseado desde {@code ProcesoDAO}.
     *
     * @return Valor en ms (0..65535) o {@code null} si no está disponible.
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
                        System.out.println("Watcher Ts ADC detectó cambio: " + adc + " ms (antes: " + prevAdc + ")");
                        SerialProtocolRunner.commandSetTsAdc(runner, adc);
                    }

                    Integer dip = getDesiredTsDip();
                    Integer prevDip = lastPolledTsDip;
                    if (dip != null && (prevDip == null || prevDip.intValue() != dip.intValue())) {
                        lastPolledTsDip = dip;
                        System.out.println("Watcher Ts DIP detectó cambio: " + dip + " ms (antes: " + prevDip + ")");
                        SerialProtocolRunner.commandSetTsDip(runner, dip);
                    }
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
     * @return Valor en ms (0..65535) o {@code null} si no está disponible.
     */
    public Integer getDesiredTsDip() {
        Integer dbTs = dao.consultarTsDipProceso3();
        if (dbTs != null) return clamp16(dbTs);
        return clamp16(50);
    }

    /**
     * Obtiene la máscara de 4 salidas digitales desde {@code ProcesoRefsDataDAO}.
     * <p>
     * Intenta primero un getter directo de máscara y, si no, compone la máscara
     * a partir de un arreglo de salidas digitales (boolean/int).
     * </p>
     *
     * @return Máscara 0..255 (bits 0..3) o {@code null} si no está disponible.
     */
    public Integer getDesiredLedMask() {
        if (procesoRefsDataDAO == null) return null;
        try {
            // Intentar máscara directa 0..255
            Integer m = (Integer) invokeGetter(procesoRefsDataDAO, "getLedMask");
            if (m != null) return m & 0xFF;
            // Intentar arreglo/Lista de 4 booleans/ints
            Object arr = invokeGetter(procesoRefsDataDAO, "getDigitalOutputs");
            if (arr == null) arr = invokeGetter(procesoRefsDataDAO, "getOutputs");
            if (arr != null) {
                int mask = 0;
                int n = java.lang.reflect.Array.getLength(arr);
                for (int i = 0; i < Math.min(4, n); i++) {
                    Object v = java.lang.reflect.Array.get(arr, i);
                    int bit = 0;
                    if (v instanceof Boolean) bit = ((Boolean) v) ? 1 : 0;
                    else if (v instanceof Number) bit = (((Number) v).intValue() != 0) ? 1 : 0;
                    mask |= (bit & 0x1) << i;
                }
                return mask & 0xFF;
            }
        } catch (Throwable ignored) {}
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
        try { return Class.forName("com.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.api." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("com.myproject.dao." + simpleName); } catch (Throwable ignored) {}
        try { return Class.forName("dao." + simpleName); } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Invoca un método si existe (por nombre y tipos), devolviendo true si se
     * logró ejecutar.
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
     * Invoca un getter sin parámetros por nombre.
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

