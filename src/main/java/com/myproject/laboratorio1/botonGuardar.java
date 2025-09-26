package com.myproject.laboratorio1;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jfree.data.xy.XYSeries;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;

public class botonGuardar {

    /**
     * Guarda un archivo Excel (.xlsx) con dos hojas:
     *   - "Analogica": columnas [t, y_analogica]
     *   - "Digital":   columnas [t, y_digital]
     * Mantiene el nombre del método por compatibilidad.
     */
    public static void guardarComoCSV(XYSeries serieAnalogica, XYSeries serieDigital, java.awt.Component parent) {
        // Diálogo para escoger archivo .xlsx
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Guardar datos de señales (XLSX)");
        fc.setSelectedFile(new File("senales.xlsx"));
        fc.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx"));

        int result = fc.showSaveDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }

        try (Workbook wb = new XSSFWorkbook()) {
            // Estilos opcionales (negrita para encabezado)
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            // ===== Hoja Analógica =====
            Sheet shA = wb.createSheet("Analogica");
            crearEncabezados(shA, headerStyle, "t", "y_analogica");
            if (serieAnalogica != null && serieAnalogica.getItemCount() > 0) {
                for (int i = 0; i < serieAnalogica.getItemCount(); i++) {
                    Row r = shA.createRow(i + 1);
                    r.createCell(0).setCellValue(serieAnalogica.getX(i).doubleValue());
                    r.createCell(1).setCellValue(serieAnalogica.getY(i).doubleValue());
                }
            } else {
                filaSinDatos(shA);
            }
            shA.autoSizeColumn(0);
            shA.autoSizeColumn(1);

            // ===== Hoja Digital =====
            Sheet shD = wb.createSheet("Digital");
            crearEncabezados(shD, headerStyle, "t", "y_digital");
            if (serieDigital != null && serieDigital.getItemCount() > 0) {
                for (int i = 0; i < serieDigital.getItemCount(); i++) {
                    Row r = shD.createRow(i + 1);
                    r.createCell(0).setCellValue(serieDigital.getX(i).doubleValue());
                    r.createCell(1).setCellValue(serieDigital.getY(i).doubleValue());
                }
            } else {
                filaSinDatos(shD);
            }
            shD.autoSizeColumn(0);
            shD.autoSizeColumn(1);

            // Escribir archivo
            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }

            JOptionPane.showMessageDialog(parent,
                    "Datos guardados en:\n" + file.getAbsolutePath(),
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(parent,
                    "Error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void crearEncabezados(Sheet sh, CellStyle headerStyle, String col1, String col2) {
        Row h = sh.createRow(0);
        Cell c0 = h.createCell(0); c0.setCellValue(col1); c0.setCellStyle(headerStyle);
        Cell c1 = h.createCell(1); c1.setCellValue(col2); c1.setCellStyle(headerStyle);
    }

    private static void filaSinDatos(Sheet sh) {
        Row r = sh.createRow(1);
        r.createCell(0).setCellValue("Sin datos");
        // dejamos la segunda columna vacía para mantener el formato
    }
}

