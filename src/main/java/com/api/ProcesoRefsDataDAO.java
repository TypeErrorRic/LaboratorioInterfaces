package com.api;

/**
 * Stub de la API para referencias/salidas digitales.
 * Reemplazar por la implementación real.
 */
public class ProcesoRefsDataDAO {
    /** Máscara directa de LEDs 0..255 (usa bits 0..3). */
    public Integer getLedMask() { return null; }

    /** Alternativa: arreglo de 4 salidas digitales (boolean/int). */
    public Object getDigitalOutputs() { return null; }

    /** Alternativa: alias genérico. */
    public Object getOutputs() { return getDigitalOutputs(); }
}
