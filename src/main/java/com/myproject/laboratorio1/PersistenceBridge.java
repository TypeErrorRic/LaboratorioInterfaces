package com.myproject.laboratorio1;

/**
 * Puente opcional hacia la capa de persistencia (API externa).
 * <p>
 * Esta clase integra el acceso a datos de forma desacoplada para no romper la
 * compilación ni modificar la lógica serial existente. Utiliza reflexión para
 * localizar e invocar las clases de la API si están presentes en el classpath:
 * {@code IntProcesoDataDAO} (para vars_data y refs_data) y {@code IntProcesoDAO}.
 * En caso de no encontrarlas, todas las operaciones son no-op.
 * </p>
 * <ul>
 *   <li>Al recibir datos del micro (8 analógicas + 4 digitales), se invoca
 *       {@code IntProcesoDataDAO} para persistirlos.</li>
 *   <li>Antes de enviar comandos al micro, se consultan tiempos de muestreo
 *       en {@code IntProcesoDAO} y la máscara de salidas en
 *       {@code IntProcesoDataDAO}.</li>
 * </ul>
 */
public final class PersistenceBridge {

    private static final PersistenceBridge INSTANCE = new PersistenceBridge();

    private final Object procesoDataDAO;
    private final Object procesoDAO;

    private PersistenceBridge() {
        this.procesoDataDAO = newInstanceSafe("IntProcesoDataDAO");
        this.procesoDAO = newInstanceSafe("IntProcesoDAO");
        
        // Configurar proceso activo por defecto (Arduino Uno = ID 3)
        if (procesoDataDAO != null) {
            try {
                invokeIfExists(procesoDataDAO, "setProcesoActivo", new Class[]{int.class}, new Object[]{3});
            } catch (Exception ignored) {}
        }
    }

    public static PersistenceBridge get() { return INSTANCE; }

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
            // Intentar getters para tiempo_muestreo (ADC)
            v = (Integer) invokeGetter(procesoDAO, "getTiempoMuestreo"); if (v != null) return clamp16(v);
            v = (Integer) invokeGetter(procesoDAO, "getTsAdc"); if (v != null) return clamp16(v);
            v = (Integer) invokeGetter(procesoDAO, "getTsADC"); if (v != null) return clamp16(v);
            v = (Integer) invokeGetter(procesoDAO, "getPeriodoAdc"); if (v != null) return clamp16(v);
        } catch (Throwable ignored) {}
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
            // Intentar getters para tiempo_muestreo_2 (DIP)
            v = (Integer) invokeGetter(procesoDAO, "getTiempoMuestreo2"); if (v != null) return clamp16(v);
            v = (Integer) invokeGetter(procesoDAO, "getTsDip"); if (v != null) return clamp16(v);
            v = (Integer) invokeGetter(procesoDAO, "getPeriodoDip"); if (v != null) return clamp16(v);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Obtiene la máscara de 4 salidas digitales desde {@code IntProcesoDataDAO}.
     * <p>
     * Intenta primero un getter directo de máscara y, si no, compone la máscara
     * a partir de un arreglo de salidas digitales (boolean/int).
     * </p>
     *
     * @return Máscara 0..255 (bits 0..3) o {@code null} si no está disponible.
     */
    public Integer getDesiredLedMask() {
        if (procesoDataDAO == null) return null;
        try {
            // Intentar máscara directa 0..255 con método getRefsDataLedMask
            Integer m = (Integer) invokeGetter(procesoDataDAO, "getRefsDataLedMask");
            if (m != null) return m & 0xFF;
            
            // Métodos alternativos
            m = (Integer) invokeGetter(procesoDataDAO, "getLedMask");
            if (m != null) return m & 0xFF;
            
            // Intentar arreglo/Lista de 4 booleans/ints
            Object arr = invokeGetter(procesoDataDAO, "getDigitalOutputs");
            if (arr == null) arr = invokeGetter(procesoDataDAO, "getOutputs");
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
        try { return Class.forName("com.myproject.laboratorio1.api." + simpleName); } catch (Throwable ignored) {}
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
