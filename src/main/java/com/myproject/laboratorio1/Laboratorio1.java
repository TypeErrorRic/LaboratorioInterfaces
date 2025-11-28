/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.myproject.laboratorio1;

import java.awt.BorderLayout;
import javax.swing.Timer;

import org.jfree.data.xy.XYSeries;

import com.myproject.laboratorio1.botonGuardar;
import java.awt.Color;

/**
 * Interfaz gráfica principal del sistema de supervisión y control de señales.
 * <p>
 * <b>Arquitectura BD-Céntrica:</b> Esta clase implementa la capa de presentación
 * que interactúa exclusivamente con {@link DAO} para acceder a datos. NO tiene
 * comunicación directa con el microcontrolador.
 * </p>
 * 
 * <p><b>Funcionalidades principales:</b></p>
 * <ul>
 *   <li><b>Graficación en tiempo real:</b> Lee datos desde BD cada 200ms y actualiza
 *       gráficas de señales analógicas (0-5V) y digitales (0/1)</li>
 *   <li><b>Gestión de conexión:</b> Inicia/detiene {@link SerialProtocolRunner} y
 *       lo conecta con {@link PersistenceBridge} para sincronización BD-Micro</li>
 *   <li><b>Control de configuración:</b> Permite cambiar tiempos de muestreo ADC/DIP
 *       y estado de LEDs, escribiendo cambios en BD</li>
 *   <li><b>Selección de canales:</b> 8 canales analógicos (ADC0-ADC7) y 4 digitales (DIP0-DIP3)</li>
 *   <li><b>Exportación de datos:</b> Guarda datos graficados en formato CSV</li>
 * </ul>
 * 
 * <p><b>Flujo de graficación:</b></p>
 * <pre>
 * Timer (200ms) → DAO.obtenerUltimaMuestraAnalogica(canal)
 *                      ↓
 *              IntProcesoDataDAO.getLatestAdcData()
 *                      ↓
 *              SELECT FROM int_proceso_vars_data
 *                      ↓
 *          graficaAnalogica.addDato(tiempo, voltaje)
 * </pre>
 * 
 * <p><b>Componentes gráficos:</b></p>
 * <ul>
 *   <li><b>Gráficas:</b> 2 instancias de {@link LineGraph} usando JFreeChart</li>
 *   <li><b>Controles Ts:</b> Campos de texto y botones para cambiar tiempos de muestreo</li>
 *   <li><b>LEDs:</b> 4 JToggleButton para controlar salidas digitales (D8-D11)</li>
 *   <li><b>Puerto serial:</b> JComboBox para seleccionar puerto COM</li>
 *   <li><b>Conexión:</b> Botón que alterna entre "Conectar" y "Desconectar"</li>
 * </ul>
 * 
 * <p><b>Manejo de estado:</b></p>
 * <ul>
 *   <li>Detecta duplicados usando timestamps para evitar graficar el mismo dato</li>
 *   <li>Limpia gráficas al cambiar de canal o reconectar</li>
 *   <li>Detiene timers automáticamente al desconectar</li>
 *   <li>Muestra feedback visual (flash rojo) en errores de comunicación</li>
 * </ul>
 * 
 * @author Arley
 * @version 2.0 - Graficación desde BD
 * @see DAO
 * @see PersistenceBridge
 * @see SerialProtocolRunner
 * @see LineGraph
 */
public class Laboratorio1 extends javax.swing.JFrame {

    /**
     * Gráfica para señales analógicas (0-5V).
     * Lee datos desde BD cada 200ms vía timer.
     */
    private LineGraph graficaAnalogica;
    private LineGraph graficaDigital;
    // Componentes para puerto y arranque
    private javax.swing.JComboBox<String> comboPuertos;
    // Runner compartido para impresión periódica desde main
    private static volatile SerialProtocolRunner sharedRunner;
    private final DAO dao;
    
    public Laboratorio1() {
        this(new DAO());
    }

    public Laboratorio1(DAO dao) {
        this.dao = (dao != null) ? dao : new DAO();
        initComponents();
        // Instanciar las dos gráficas
        graficaAnalogica = new LineGraph("Señal Analógica", "Tiempo (s)", "Voltaje (V)", 100);
        graficaDigital = new LineGraph("Señal Digital", "Tiempo (s)", "Binario", 100);
        
        // Configurar rangos fijos para los ejes Y
        graficaAnalogica.setFixedYRange(0.0, 5.0);  // 0-5V para analógica
        graficaDigital.setBinaryYAxis();            // Eje binario (solo 0 y 1) para digital
        
        panelSenalAnalogica.add(graficaAnalogica.getChartPanel(), BorderLayout.CENTER);
        panelSenalDigital.add(graficaDigital.getChartPanel(), BorderLayout.CENTER);
        
        // Establecer títulos iniciales basados en las selecciones por defecto de los ComboBox
        actualizarTitulosIniciales();

        // Menú desplegable de puertos y botón Iniciar
        comboPuertos = new javax.swing.JComboBox<>(SerialIO.listPorts());
        panelSuperior.add(new javax.swing.JLabel(" Puerto: "));
        panelSuperior.add(comboPuertos);
        // Cancelar transmision/inicio en curso al cambiar de puerto
        comboPuertos.addActionListener(e -> {
            SerialProtocolRunner r = sharedRunner;
            if (r != null) {
                try { r.close(); } catch (Exception ignored) {}
                sharedRunner = null;
                System.out.println("Transmision cancelada por cambio de puerto");
                PersistenceBridge.get().setSerialRunner(null);
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bg = new javax.swing.JPanel();
        panelSuperior = new javax.swing.JPanel();
        titulo = new javax.swing.JLabel();
        panelCentral = new javax.swing.JPanel();
        panelSenales = new javax.swing.JPanel();
        panelSenalAnalogica = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox<>();
        panelSenalDigital = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        jComboBox2 = new javax.swing.JComboBox<>();
        panelSenalSalida = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jToggleButton1 = new javax.swing.JToggleButton();
        jToggleButton2 = new javax.swing.JToggleButton();
        jToggleButton3 = new javax.swing.JToggleButton();
        jToggleButton4 = new javax.swing.JToggleButton();
        jButton1 = new javax.swing.JButton();
        panelInferior = new javax.swing.JPanel();
        EtiquetaValorMuestreoAnalogico = new javax.swing.JLabel();
        ValorMuestreoAnalogico = new javax.swing.JTextField();
        BotonCambiarMuestreoAnalogico = new javax.swing.JButton();
        TiempoMuestreoActualAnalogico = new javax.swing.JLabel();
        EtiquetaValorMuestreoDigital = new javax.swing.JLabel();
        TiempoMuestreoActualDigital = new javax.swing.JLabel();
        ValorMuestreoDigital = new javax.swing.JTextField();
        BotonCambiarMuestroDigital = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        bg.setBackground(new java.awt.Color(255, 255, 255));
        bg.setPreferredSize(new java.awt.Dimension(1280, 720));
        bg.setLayout(new java.awt.BorderLayout());

        titulo.setFont(new java.awt.Font("Roboto Black", 1, 24)); // NOI18N
        titulo.setText("SUPERVISIÓN Y CONTROL DE SEÑALES");
        panelSuperior.add(titulo);

        bg.add(panelSuperior, java.awt.BorderLayout.PAGE_START);

        panelCentral.setBackground(new java.awt.Color(255, 255, 255));
        panelCentral.setLayout(new java.awt.BorderLayout());

        panelSenales.setLayout(new java.awt.GridLayout(0, 2));

        panelSenalAnalogica.setLayout(new java.awt.BorderLayout());

        jLabel6.setFont(new java.awt.Font("Roboto", 0, 18)); // NOI18N
        jLabel6.setText("Señal analógica");
        jPanel4.add(jLabel6);

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1", "2", "3", "4", "5", "6", "7", "8" }));
        jComboBox1.setPreferredSize(new java.awt.Dimension(40, 22));
        jComboBox1.addActionListener(evt -> jComboBox1ActionPerformed(evt));
        jPanel4.add(jComboBox1);

        panelSenalAnalogica.add(jPanel4, java.awt.BorderLayout.PAGE_START);

        panelSenales.add(panelSenalAnalogica);

        panelSenalDigital.setLayout(new java.awt.BorderLayout());

        jLabel7.setFont(new java.awt.Font("Roboto", 0, 18)); // NOI18N
        jLabel7.setText("Señal digital");
        jPanel2.add(jLabel7);

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1", "2", "3", "4" }));
        jComboBox2.setPreferredSize(new java.awt.Dimension(40, 22));
        jComboBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox2ActionPerformed(evt);
            }
        });
        jPanel2.add(jComboBox2);

        panelSenalDigital.add(jPanel2, java.awt.BorderLayout.PAGE_START);

        panelSenales.add(panelSenalDigital);

        panelCentral.add(panelSenales, java.awt.BorderLayout.CENTER);

        panelSenalSalida.setLayout(new java.awt.BorderLayout());

        jLabel1.setText("Señales de salida:");
        panelSenalSalida.add(jLabel1, java.awt.BorderLayout.PAGE_START);

        jPanel1.setLayout(new javax.swing.BoxLayout(jPanel1, javax.swing.BoxLayout.Y_AXIS));

        jToggleButton1.setText("jToggleButton1");
        jToggleButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jToggleButton1);

        jToggleButton2.setText("jToggleButton2");
        jToggleButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton2ActionPerformed(evt);
            }
        });
        jPanel1.add(jToggleButton2);

        jToggleButton3.setText("jToggleButton3");
        jToggleButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton3ActionPerformed(evt);
            }
        });
        jPanel1.add(jToggleButton3);

        jToggleButton4.setText("jToggleButton4");
        jToggleButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButton4ActionPerformed(evt);
            }
        });
        jPanel1.add(jToggleButton4);

        jButton1.setText("Conectar");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel1.add(jButton1);

        panelSenalSalida.add(jPanel1, java.awt.BorderLayout.CENTER);

        panelCentral.add(panelSenalSalida, java.awt.BorderLayout.LINE_END);

        bg.add(panelCentral, java.awt.BorderLayout.CENTER);

        EtiquetaValorMuestreoAnalogico.setText("Tiempo de Muestreo");

        ValorMuestreoAnalogico.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        ValorMuestreoAnalogico.setToolTipText("ValorMuestreo");
        ValorMuestreoAnalogico.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ValorMuestreoAnalogicoActionPerformed(evt);
            }
        });

        BotonCambiarMuestreoAnalogico.setText("Cambiar");

        TiempoMuestreoActualAnalogico.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        TiempoMuestreoActualAnalogico.setBorder(javax.swing.BorderFactory.createLineBorder(null));

        EtiquetaValorMuestreoDigital.setText("Tiempo de Muestreo");

        TiempoMuestreoActualDigital.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        TiempoMuestreoActualDigital.setBorder(javax.swing.BorderFactory.createLineBorder(null));

        ValorMuestreoDigital.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        ValorMuestreoDigital.setToolTipText("ValorMuestreo");
        ValorMuestreoDigital.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ValorMuestreoDigitalActionPerformed(evt);
            }
        });

        BotonCambiarMuestroDigital.setText("Cambiar");

        javax.swing.GroupLayout panelInferiorLayout = new javax.swing.GroupLayout(panelInferior);
        panelInferior.setLayout(panelInferiorLayout);
        panelInferiorLayout.setHorizontalGroup(
            panelInferiorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelInferiorLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(EtiquetaValorMuestreoAnalogico)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TiempoMuestreoActualAnalogico, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ValorMuestreoAnalogico, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(BotonCambiarMuestreoAnalogico)
                .addGap(281, 281, 281)
                .addComponent(EtiquetaValorMuestreoDigital)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TiempoMuestreoActualDigital, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ValorMuestreoDigital, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(BotonCambiarMuestroDigital)
                .addContainerGap(387, Short.MAX_VALUE))
        );
        panelInferiorLayout.setVerticalGroup(
            panelInferiorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelInferiorLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelInferiorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(TiempoMuestreoActualDigital, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(panelInferiorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(BotonCambiarMuestroDigital)
                        .addComponent(ValorMuestreoDigital, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(EtiquetaValorMuestreoDigital, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(TiempoMuestreoActualAnalogico, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(panelInferiorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(BotonCambiarMuestreoAnalogico)
                        .addComponent(ValorMuestreoAnalogico, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(EtiquetaValorMuestreoAnalogico, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(83, Short.MAX_VALUE))
        );

        bg.add(panelInferior, java.awt.BorderLayout.PAGE_END);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(bg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(bg, javax.swing.GroupLayout.DEFAULT_SIZE, 501, Short.MAX_VALUE)
        );

        // ====== Botón Guardar CSV ======
        btnGuardar = new javax.swing.JButton();
        btnGuardar.setText("💾 Exportar");
        btnGuardar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGuardarActionPerformed(evt);
            }
        });
        jPanel1.add(btnGuardar);

        BotonCambiarMuestreoAnalogico.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BotonCambiarMuestreoAnalogicoActionPerformed(evt);
            }
        });
        panelInferior.add(BotonCambiarMuestreoAnalogico);

        BotonCambiarMuestroDigital.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                BotonCambiarMuestroDigitalActionPerformed(evt);
            }
        });
        panelInferior.add(BotonCambiarMuestroDigital);

        //Actualizar Botones:
        jToggleButton1.setSelected(false);
        actualizarToggle(jToggleButton1);
        jToggleButton2.setSelected(false);
        actualizarToggle(jToggleButton2);
        jToggleButton3.setSelected(false);
        actualizarToggle(jToggleButton3);
        jToggleButton4.setSelected(false);
        actualizarToggle(jToggleButton4);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void actualizarToggle(javax.swing.JToggleButton b) {
        boolean encendido = b.isSelected();

        if (encendido) {
            b.setText("ON");
            b.setBackground(new Color(46, 204, 113)); // verde
            b.setForeground(Color.WHITE);
        } else {
            b.setText("OFF");
            b.setBackground(new Color(231, 76, 60)); // rojo
            b.setForeground(Color.WHITE);
        }

        // Para que el color de fondo se vea con varios Look&Feels
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
    }
    
    private void flashError(java.awt.Component c, Runnable restore) {
        Color originalBg = c.getBackground();
        Color originalFg = c.getForeground();
        c.setBackground(new Color(231, 76, 60)); // rojo
        c.setForeground(Color.WHITE);
        if (c instanceof javax.swing.AbstractButton ab) {
            ab.setOpaque(true);
            ab.setContentAreaFilled(true);
        }
        Timer t = new Timer(3000, e -> {
            if (restore != null) {
                restore.run();
            } else {
                c.setBackground(originalBg);
                c.setForeground(originalFg);
            }
            ((Timer) e.getSource()).stop();
        });
        t.setRepeats(false);
        t.start();
    }

    /**
     * Envía el estado actual de los 4 LEDs al microcontrolador.
     * Construye una máscara de 4 bits basada en el estado de los toggle buttons
     * y envía el comando 0x01 (Set LED mask) al microcontrolador.
     */
    private boolean enviarEstadoLEDs(javax.swing.JToggleButton source) {
        // Construir máscara de 4 bits basada en el estado de los 4 toggle buttons
        // Bit 0 = LED0 (D8) = jToggleButton1
        // Bit 1 = LED1 (D9) = jToggleButton2
        // Bit 2 = LED2 (D10) = jToggleButton3
        // Bit 3 = LED3 (D11) = jToggleButton4
        int mask = 0;
        if (jToggleButton1.isSelected()) mask |= 0b0001;  // LED0 (D8)
        if (jToggleButton2.isSelected()) mask |= 0b0010;  // LED1 (D9)
        if (jToggleButton3.isSelected()) mask |= 0b0100;  // LED2 (D10)
        if (jToggleButton4.isSelected()) mask |= 0b1000;  // LED3 (D11)
        
        // Debugging: Imprimir información del comando
        String binaryMask = String.format("%4s", Integer.toBinaryString(mask)).replace(' ', '0');
        System.out.println("CMD Set LED Mask: 0x" + String.format("%02X", mask) + " (0b" + binaryMask + ")");
        System.out.println(
            "LEDs: [LED0=" + ((mask & 0b0001) != 0 ? "ON" : "OFF") + " LED1=" + ((mask & 0b0010) != 0 ? "ON" : "OFF") + 
            " LED2=" + ((mask & 0b0100) != 0 ? "ON" : "OFF") + " LED3=" + ((mask & 0b1000) != 0 ? "ON" : "OFF") + "]");
        
        // Enviar comando al microcontrolador
        boolean enviado = dao.enviarMascaraLeds(mask);
        if (enviado) {
            System.out.println("  Estado: Comando enviado");
        } else {
            System.out.println("  Estado: Sin conexion - comando no enviado");
            boolean prevSelected = !source.isSelected(); // revertir al estado previo al click
            flashError(source, () -> {
                source.setSelected(prevSelected);
                actualizarToggle(source);
            });
        }
        return enviado;
    }


    private void btnGuardarActionPerformed(java.awt.event.ActionEvent evt) {
        botonGuardar.guardarComoCSV(graficaAnalogica.getSeriexy(), graficaDigital.getSeriexy(), this);
    }
    
    private void jToggleButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        // Actualizar apariencia del botón
        actualizarToggle(jToggleButton1);
        // Enviar estado de todos los LEDs al microcontrolador
        enviarEstadoLEDs(jToggleButton1);
    }

    private void jToggleButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        // Actualizar apariencia del botón
        actualizarToggle(jToggleButton2);
        // Enviar estado de todos los LEDs al microcontrolador
        enviarEstadoLEDs(jToggleButton2);
    }

    private void jToggleButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        // Actualizar apariencia del botón
        actualizarToggle(jToggleButton3);
        // Enviar estado de todos los LEDs al microcontrolador
        enviarEstadoLEDs(jToggleButton3);
    }

    private void jToggleButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButton1ActionPerformed
        // Actualizar apariencia del botón
        actualizarToggle(jToggleButton4);
        // Enviar estado de todos los LEDs al microcontrolador
        enviarEstadoLEDs(jToggleButton4);
    }

    private Timer timerGraficaAnalogica;
    private Timer timerGraficaDigital;
    private int currentAnalogSignalIndex = 0;
    private int currentDigitalSignalIndex = 0;
    
    // Para detectar cambios: último timestamp graficado
    private long lastAdcPlottedTimestamp = 0;
    private long lastDigitalPlottedTimestamp = 0;
    
    // Para detectar duplicados: último valor recibido
    private long lastAdcReceivedTimestamp = 0;
    private long lastDigitalReceivedTimestamp = 0;

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        // Verificar si ya hay una conexión activa
        if (sharedRunner != null && sharedRunner.isTransmissionActive()) {
            // Desconectar
            desconectar();
        } else {
            // Conectar
            conectar();
        }
    }//GEN-LAST:event_jButton1ActionPerformed
    
    private void conectar() {
        String port = (comboPuertos != null) ? (String) comboPuertos.getSelectedItem() : null;
        if (port == null || port.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Seleccione un puerto COM válido.");
            return;
        }
        
        // Cambiar botón a "Conectando..."
        jButton1.setText("Conectando...");
        jButton1.setEnabled(false);
        
        // Cerrar runner previo si existe
        SerialProtocolRunner prev = sharedRunner;
        if (prev != null) {
            try { prev.close(); } catch (Exception ignored) {}
            sharedRunner = null;
            PersistenceBridge.get().setSerialRunner(null);
        }
        
        // Limpiar datos previos
        graficaAnalogica.clearData();
        graficaDigital.clearData();
        
        // Detener timers previos si existen
        if (timerGraficaAnalogica != null && timerGraficaAnalogica.isRunning()) {
            timerGraficaAnalogica.stop();
        }
        if (timerGraficaDigital != null && timerGraficaDigital.isRunning()) {
            timerGraficaDigital.stop();
        }
        
        // Reiniciar variables
        lastAdcPlottedTimestamp = 0;
        lastDigitalPlottedTimestamp = 0;
        lastAdcReceivedTimestamp = 0;
        lastDigitalReceivedTimestamp = 0;
        
        // Obtener índices de señales seleccionadas
        currentAnalogSignalIndex = jComboBox1.getSelectedIndex();
        currentDigitalSignalIndex = jComboBox2.getSelectedIndex();
        
        // Crear y arrancar el runner
        SerialProtocolRunner r = new SerialProtocolRunner(port, 115200);
        sharedRunner = r;
        // Conectar PersistenceBridge con SerialProtocolRunner
        PersistenceBridge.get().setSerialRunner(r);
        r.startTransmissionWithRetryAsync(500);
        
        // Esperar asincrónicamente hasta que la transmisión esté activa
        new Thread(() -> {
            SerialProtocolRunner rr = r;
            while (sharedRunner == rr && !rr.isTransmissionActive() && rr.isConnecting()) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) { return; }
            }
            
            // Actualizar UI en el hilo de Swing
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (sharedRunner == rr && rr.isTransmissionActive()) {
                    System.out.println("Transmision lista en " + rr.getPort());
                    jButton1.setText("Desconectar");
                    jButton1.setEnabled(true);
                    
                    // Iniciar timers de graficacion
                    iniciarTimers();
                } else {
                    jButton1.setText("Conectar");
                    jButton1.setEnabled(true);
                    sharedRunner = null;
                    PersistenceBridge.get().setSerialRunner(null);
                }
            });
        }, "Protocol-StartWait").start();
    }
    
    private void desconectar() {
        // Detener timers
        if (timerGraficaAnalogica != null && timerGraficaAnalogica.isRunning()) {
            timerGraficaAnalogica.stop();
        }
        if (timerGraficaDigital != null && timerGraficaDigital.isRunning()) {
            timerGraficaDigital.stop();
        }
        
        // Cerrar runner
        SerialProtocolRunner r = sharedRunner;
        if (r != null) {
            try { 
                r.stopTransmission();
                r.close(); 
            } catch (Exception ex) {
                System.err.println("Error al desconectar: " + ex.getMessage());
            }
            sharedRunner = null;
            PersistenceBridge.get().setSerialRunner(null);
        }
        
        // Actualizar botón
        jButton1.setText("Conectar");
        System.out.println("Desconectado del puerto");
    }
    
    private void iniciarTimers() {
        // Timer para graficar datos analógicos desde la base de datos
        timerGraficaAnalogica = new Timer(200, e -> {
            // Leer datos desde la base de datos
            long[] adcData = dao.obtenerUltimaMuestraAnalogica(currentAnalogSignalIndex);
            long tMs = adcData[1];
            
            if (tMs >= 0 && tMs != lastAdcReceivedTimestamp) {
                int adcValue = (int) adcData[0];
                
                // Actualizar último timestamp recibido
                lastAdcReceivedTimestamp = tMs;
                
                // Convertir valor ADC a voltaje (0-1023 -> 0-5V)
                double voltaje = (adcValue / 1023.0) * 5.0;
                graficaAnalogica.addDato(tMs / 1000.0, voltaje);  // Convertir ms a s
                
                long dt = (lastAdcPlottedTimestamp > 0L) ? (tMs - lastAdcPlottedTimestamp) : 0L;
                lastAdcPlottedTimestamp = tMs;
                System.out.println(String.format("AN%d=%d(t=%dms dT=%dms) [BD]", 
                    currentAnalogSignalIndex + 1, adcValue, tMs, dt));
            }
        });
        
        // Timer para graficar datos digitales desde la base de datos
        timerGraficaDigital = new Timer(200, e -> {
            // Leer datos desde la base de datos
            long[] digitalData = dao.obtenerUltimaMuestraDigital();
            long tMs = digitalData[1];
            
            if (tMs >= 0 && tMs != lastDigitalReceivedTimestamp) {
                int dipNibble = (int) digitalData[0];
                
                // Extraer el bit correspondiente al DIP seleccionado (0-3)
                int bitValue = (dipNibble >> currentDigitalSignalIndex) & 0x01;
                
                // Actualizar último timestamp recibido
                lastDigitalReceivedTimestamp = tMs;
                
                graficaDigital.addDato(tMs / 1000.0, bitValue);  // Convertir ms a s
                
                long dt = (lastDigitalPlottedTimestamp > 0L) ? (tMs - lastDigitalPlottedTimestamp) : 0L;
                lastDigitalPlottedTimestamp = tMs;
                System.out.println(String.format("DIP%d=%d(t=%dms dT=%dms) [BD]", 
                    currentDigitalSignalIndex + 1, bitValue, tMs, dt));
            }
        });
        
        timerGraficaAnalogica.start();
        timerGraficaDigital.start();
    }

    private void jComboBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox1ActionPerformed
        // Obtener la selección del ComboBox1 (señales analógicas)
        String seleccion = (String) jComboBox1.getSelectedItem();
        graficaAnalogica.clearData();
        
        // Obtener el nombre de la señal basado en la selección
        String nombreSenal = obtenerNombreSenal(seleccion, true); // true para analógica
        
        // Cambiar el título de la gráfica analógica
        graficaAnalogica.setTitle(nombreSenal);
        
        // Actualizar el índice de la señal actual para la graficación en tiempo real
        currentAnalogSignalIndex = jComboBox1.getSelectedIndex();
        
        System.out.println("Señal analógica seleccionada: " + nombreSenal);
    }//GEN-LAST:event_jComboBox1ActionPerformed

    private void ValorMuestreoAnalogicoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ValorMuestreoAnalogicoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ValorMuestreoAnalogicoActionPerformed

    private void ValorMuestreoDigitalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ValorMuestreoDigitalActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ValorMuestreoDigitalActionPerformed

    private void jComboBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox2ActionPerformed
        // Obtener la selección del ComboBox2 (señales digitales)
        String seleccion = (String) jComboBox2.getSelectedItem();

        graficaDigital.clearData();
        
        // Obtener el nombre de la señal basado en la selección
        String nombreSenal = obtenerNombreSenal(seleccion, false); // false para digital
        
        // Cambiar el título de la gráfica digital
        graficaDigital.setTitle(nombreSenal);
        
        // Actualizar el índice de la señal actual para la graficación en tiempo real
        currentDigitalSignalIndex = jComboBox2.getSelectedIndex();
        
        System.out.println("Señal digital seleccionada: " + nombreSenal);
    }//GEN-LAST:event_jComboBox2ActionPerformed

    private void BotonCambiarMuestreoAnalogicoActionPerformed(java.awt.event.ActionEvent evt) {
        // Capturar el valor ingresado en el campo de texto
        String valorIngresado = ValorMuestreoAnalogico.getText().trim();
        
        // Validar que no esté vacío
        if (!valorIngresado.isEmpty()) {
            try {
                // Validar que sea un número entero válido y positivo
                int valor = Integer.parseInt(valorIngresado);
                
                if (valor > 0) {
                    // Actualizar la etiqueta que muestra el tiempo de muestreo actual
                    TiempoMuestreoActualAnalogico.setText(valorIngresado + " ms");
                    
                    // Limpiar el campo de texto después de actualizar
                    ValorMuestreoAnalogico.setText("");
                    
                    // Enviar comando al microcontrolador (CMD 0x08: Set Ts ADC)
                    boolean enviado = dao.actualizarTsAdc(valor);
                    System.out.println("CMD Set Ts ADC: " + valor + " ms");
                    if (enviado) {
                        System.out.println("  Estado: Comando enviado al microcontrolador");
                    } else {
                        System.out.println("  Estado: Sin conexion - comando no enviado");
                        flashError(BotonCambiarMuestreoAnalogico, null);
                    }
                } else {
                    // Manejar error si el valor no es positivo
                    javax.swing.JOptionPane.showMessageDialog(this, 
                        "El tiempo de muestreo debe ser un valor positivo (mayor que 0)", 
                        "Valor inválido", 
                        javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                // Manejar error si el valor no es un número válido
                javax.swing.JOptionPane.showMessageDialog(this, 
                    "Por favor ingrese un valor numérico entero válido (en ms)", 
                    "Error de formato", 
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // Manejar caso de campo vacío
            javax.swing.JOptionPane.showMessageDialog(this, 
                "Por favor ingrese un valor", 
                "Campo vacío", 
                javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private void BotonCambiarMuestroDigitalActionPerformed(java.awt.event.ActionEvent evt) {
        // Capturar el valor ingresado en el campo de texto
        String valorIngresado = ValorMuestreoDigital.getText().trim();
        
        // Validar que no esté vacío
        if (!valorIngresado.isEmpty()) {
            try {
                // Validar que sea un número entero válido y positivo
                int valor = Integer.parseInt(valorIngresado);
                
                if (valor > 0) {
                    // Actualizar la etiqueta que muestra el tiempo de muestreo actual
                    TiempoMuestreoActualDigital.setText(valorIngresado + " ms");
                    
                    // Limpiar el campo de texto después de actualizar
                    ValorMuestreoDigital.setText("");
                    
                    // Enviar comando al microcontrolador (CMD 0x03: Set Ts DIP)
                    boolean enviado = dao.actualizarTsDip(valor);
                    System.out.println("CMD Set Ts DIP: " + valor + " ms");
                    if (enviado) {
                        System.out.println("  Estado: Comando enviado al microcontrolador");
                    } else {
                        System.out.println("  Estado: Sin conexion - comando no enviado");
                        flashError(BotonCambiarMuestroDigital, null);
                    }
                } else {
                    // Manejar error si el valor no es positivo
                    javax.swing.JOptionPane.showMessageDialog(this, 
                        "El tiempo de muestreo debe ser un valor positivo (mayor que 0)", 
                        "Valor inválido", 
                        javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                // Manejar error si el valor no es un número válido
                javax.swing.JOptionPane.showMessageDialog(this, 
                    "Por favor ingrese un valor numérico entero válido (en ms)", 
                    "Error de formato", 
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        } else {
            // Manejar caso de campo vacío
            javax.swing.JOptionPane.showMessageDialog(this, 
                "Por favor ingrese un valor", 
                "Campo vacío", 
                javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Actualiza los títulos de las gráficas basado en las selecciones por defecto de los ComboBox.
     * Se llama al inicializar la aplicación.
     */
    private void actualizarTitulosIniciales() {
        // Obtener selecciones por defecto y actualizar títulos
        String seleccionAnalogica = (String) jComboBox1.getSelectedItem();
        String seleccionDigital = (String) jComboBox2.getSelectedItem();
        
        if (seleccionAnalogica != null) {
            String nombreAnalogica = obtenerNombreSenal(seleccionAnalogica, true);
            graficaAnalogica.setTitle(nombreAnalogica);
        }
        
        if (seleccionDigital != null) {
            String nombreDigital = obtenerNombreSenal(seleccionDigital, false);
            graficaDigital.setTitle(nombreDigital);
        }
    }

    /**
     * Método auxiliar que mapea las selecciones numéricas del ComboBox a títulos simples con números.
     * 
     * @param seleccion El valor seleccionado en el ComboBox (como String)
     * @param esAnalogica true si es para señal analógica, false si es para señal digital
     * @return String con el título que incluye solo el número de la señal
     */
    private String obtenerNombreSenal(String seleccion, boolean esAnalogica) {
        if (seleccion == null) {
            return esAnalogica ? "Señal Analógica" : "Señal Digital";
        }
        
        if (esAnalogica) {
            // Título simple para señales analógicas
            return "Señal Analógica " + seleccion;
        } else {
            // Título simple para señales digitales
            return "Señal Digital " + seleccion;
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Laboratorio1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Laboratorio1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Laboratorio1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Laboratorio1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        final DAO dao = new DAO();
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                LoginFrame login = new LoginFrame(dao, () -> {
                    Laboratorio1 mainFrame = new Laboratorio1(dao);
                    mainFrame.setLocationRelativeTo(null);
                    mainFrame.setVisible(true);
                });
                login.setLocationRelativeTo(null);
                login.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton BotonCambiarMuestreoAnalogico;
    private javax.swing.JButton BotonCambiarMuestroDigital;
    private javax.swing.JLabel EtiquetaValorMuestreoAnalogico;
    private javax.swing.JLabel EtiquetaValorMuestreoDigital;
    private javax.swing.JLabel TiempoMuestreoActualAnalogico;
    private javax.swing.JLabel TiempoMuestreoActualDigital;
    private javax.swing.JTextField ValorMuestreoAnalogico;
    private javax.swing.JTextField ValorMuestreoDigital;
    private javax.swing.JPanel bg;
    private javax.swing.JButton jButton1;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JComboBox<String> jComboBox2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JToggleButton jToggleButton1;
    private javax.swing.JToggleButton jToggleButton2;
    private javax.swing.JToggleButton jToggleButton3;
    private javax.swing.JToggleButton jToggleButton4;
    private javax.swing.JPanel panelCentral;
    private javax.swing.JPanel panelInferior;
    private javax.swing.JPanel panelSenalAnalogica;
    private javax.swing.JPanel panelSenalDigital;
    private javax.swing.JPanel panelSenalSalida;
    private javax.swing.JPanel panelSenales;
    private javax.swing.JPanel panelSuperior;
    private javax.swing.JLabel titulo;
    private javax.swing.JButton btnGuardar;
    // End of variables declaration//GEN-END:variables
}
