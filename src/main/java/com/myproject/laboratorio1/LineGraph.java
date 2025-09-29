package com.myproject.laboratorio1;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import javax.swing.JPanel;

import javax.swing.*;
import java.awt.*;

/**
 * @author Arley
 */
public class LineGraph {
    // === Atributos ===
    /** Serie de datos que se graficará (pares X, Y). */
    private XYSeries series;
    /** Colección de series, necesaria para construir el dataset del gráfico. */
    private XYSeriesCollection dataset;
    /** Objeto que representa el gráfico en sí (XY Line Chart). */
    private JFreeChart chart;
    /** Panel gráfico que contiene el chart y permite mostrarlo en una interfaz Swing. */
    private ChartPanel chartPanel;
    
    private Timer timer;
    private double tiempo;

    // === Constructor ===
    /**
     * Crea un gráfico de líneas personalizado.
     *
     * @param titulo   Título principal del gráfico.
     * @param ejeX     Nombre (etiqueta) para el eje X.
     * @param ejeY     Nombre (etiqueta) para el eje Y.
     * @param maxPuntos Cantidad máxima de puntos a mostrar en la serie. 
     *                  Si se excede, los más antiguos se eliminan automáticamente.
     */
    public LineGraph(String titulo, String ejeX, String ejeY, int maxPuntos) {
        // Inicializar la serie de datos con un nombre genérico "Datos"
        series = new XYSeries("Datos");
        series.setMaximumItemCount(maxPuntos); // Limita la cantidad de puntos

        // Asociar la serie a un dataset
        dataset = new XYSeriesCollection(series);

        // Crear el gráfico XY de líneas
        chart = ChartFactory.createXYLineChart(
                titulo,  // título del gráfico
                ejeX,    // etiqueta del eje X
                ejeY,    // etiqueta del eje Y
                dataset  // conjunto de datos
        );

        // Crear el panel que contendrá el gráfico para usar en Swing
        chartPanel = new ChartPanel(chart);
    }

    // === Métodos públicos ===
    /**
     * Devuelve el panel con el gráfico para integrarlo en interfaces gráficas Swing.
     *
     * @return JPanel que contiene el gráfico.
     */
    public JPanel getChartPanel() {
        return chartPanel;
    }

    /**
     * Agrega un nuevo dato (x, y) a la serie.
     * Si la cantidad de puntos supera el máximo definido, 
     * el más antiguo se elimina automáticamente.
     *
     * @param x Valor para el eje X.
     * @param y Valor para el eje Y.
     */
    public void addDato(double x, double y) {
        series.add(x, y);
    }

    public XYSeries getSeriexy(){
        return series;
    }
    
    // Metodo para limpiar los datos de la serie
    public void clearData(){
        series.clear();
    }
    
    // Metodo para cambiar el titulo de la grafica
    public void setTitle(String title){
        chart.setTitle(title);
    }
    
    /** Método que inicia la graficación automática de una señal (seno o cuadrada).
     * Parámetros:
     *   tipoSenal → tipo de señal a graficar ("1" o "2").
     *   periodoMs → intervalo en milisegundos entre cada actualización.
     */
    public void iniciarGraficacion(String tipoSenal, int periodoMs) {
        
        // Si existe un Timer en ejecución, este se detiene para evitar múltiples instancias.
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }

        // La serie actual se limpia para comenzar con una nueva graficación.
        series.clear();

        // El tiempo del eje X se reinicia a cero.
        tiempo = 0.0;

        // Se crea un nuevo Timer que ejecuta una acción cada "periodoMs" milisegundos.
        // Este Timer permite añadir datos periódicamente sin bloquear la interfaz gráfica.
        timer = new Timer(periodoMs, e -> {
            
            // Variable que almacenará el valor calculado de la señal.
            double valor = 0.0;

            // Según el tipo de señal recibido como parámetro, se calcula el valor correspondiente.
            switch (tipoSenal.toLowerCase()) {
                case "1":
                    // Genera una señal seno con frecuencia de 0.5 Hz.
                    valor = Math.sin(2 * Math.PI * 0.5 * tiempo);
                    break;

                case "2":
                    // Genera una señal cuadrada: 1.0 si el seno es positivo, -1.0 si es negativo.
                    valor = (Math.sin(2 * Math.PI * 0.5 * tiempo) >= 0) ? 1.0 : -1.0;
                    break;

                default:
                    // Si el tipo de señal no es reconocido, se asigna un valor constante de 0.
                    valor = 0.0;
            }

            // Se añade un nuevo punto a la serie, usando "tiempo" como eje X y "valor" como eje Y.
            addDato(tiempo, valor);

            // El tiempo se incrementa en segundos en función del periodo del Timer.
            tiempo += periodoMs / 1000.0;
        });

        // El Timer se inicia, comenzando así la generación periódica de puntos.
        timer.start();
    }


    // Metodo para detener la graficación
    public void detenerGraficacion() {
        if (timer != null) {
            timer.stop();
        }
    }
    
}
