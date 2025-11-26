package com.api;

/**
 * Stub de la API para persistencia de variables.
 * Reemplazar por la implementación real.
 */
public class ProcesoVarsDataDAO {
    /**
     * Inserta una muestra con 8 analógicas y 4 digitales.
     */
    public void insert(long tMs, int[] analog8, int[] digital4) {
        // No-op por defecto (stub)
    }

    /** Alternativa: guardar */
    public void save(long tMs, int[] analog8, int[] digital4) {
        insert(tMs, analog8, digital4);
    }

    /** Alternativa: guardar (ES) */
    public void guardar(long tMs, int[] analog8, int[] digital4) {
        insert(tMs, analog8, digital4);
    }

    /** Firma alternativa por campos separados. */
    public void insert(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7,
                       int d0, int d1, int d2, int d3, long tMs) {
        insert(tMs, new int[]{a0,a1,a2,a3,a4,a5,a6,a7}, new int[]{d0,d1,d2,d3});
    }
}
