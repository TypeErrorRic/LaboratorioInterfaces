package com.myproject.laboratorio1;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.jfree.data.xy.XYSeries;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;

public class botonGuardar {

    public static void guardarComoCSV(XYSeries serieAnalogica, XYSeries serieDigital, java.awt.Component parent) {
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

        // 🔑 aquí garantizamos que no se sobrescriba
        file = obtenerArchivoDisponible(file);

        try (Workbook wb = new XSSFWorkbook()) {
            // Crear hojas...
            Sheet shA = wb.createSheet("Analogica");
            crearEncabezados(shA, "t", "y_analogica");
            if (serieAnalogica != null && serieAnalogica.getItemCount() > 0) {
                for (int i = 0; i < serieAnalogica.getItemCount(); i++) {
                    Row r = shA.createRow(i + 1);
                    r.createCell(0).setCellValue(serieAnalogica.getX(i).doubleValue());
                    r.createCell(1).setCellValue(serieAnalogica.getY(i).doubleValue());
                }
            } else {
                filaSinDatos(shA);
            }

            Sheet shD = wb.createSheet("Digital");
            crearEncabezados(shD, "t", "y_digital");
            if (serieDigital != null && serieDigital.getItemCount() > 0) {
                for (int i = 0; i < serieDigital.getItemCount(); i++) {
                    Row r = shD.createRow(i + 1);
                    r.createCell(0).setCellValue(serieDigital.getX(i).doubleValue());
                    r.createCell(1).setCellValue(serieDigital.getY(i).doubleValue());
                }
            } else {
                filaSinDatos(shD);
            }

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

    // ===== Métodos auxiliares =====
    private static void crearEncabezados(Sheet sh, String col1, String col2) {
        Row h = sh.createRow(0);
        h.createCell(0).setCellValue(col1);
        h.createCell(1).setCellValue(col2);
    }

    private static void filaSinDatos(Sheet sh) {
        Row r = sh.createRow(1);
        r.createCell(0).setCellValue("Sin datos");
    }

    /**
     * Devuelve un archivo disponible. 
     * Si file.xlsx existe, devuelve file_1.xlsx, luego file_2.xlsx, etc.
     */
    private static File obtenerArchivoDisponible(File file) {
        File candidate = file;
        int counter = 1;
        while (candidate.exists()) {
            String name = file.getName();
            int dot = name.lastIndexOf(".");
            String base = (dot == -1) ? name : name.substring(0, dot);
            String ext  = (dot == -1) ? ""   : name.substring(dot);
            candidate = new File(file.getParent(), base + "_" + counter + ext);
            counter++;
        }
        return candidate;
    }
}
