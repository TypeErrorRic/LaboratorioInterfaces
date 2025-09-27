package com.myproject.laboratorio1;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import javax.swing.JPanel;

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
}
