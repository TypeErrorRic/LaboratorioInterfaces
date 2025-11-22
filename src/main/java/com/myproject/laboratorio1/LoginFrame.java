package com.myproject.laboratorio1;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Ventana de inicio de sesion sencilla.
 * Valida el usuario y, al ser correcto, abre la interfaz principal.
 */
public class LoginFrame extends javax.swing.JFrame {

    private final DAO dao;
    private final Runnable onLoginSuccess;

    private JTextField usuarioField;
    private JPasswordField passwordField;
    private JLabel estadoLabel;
    private JLabel logoLabel;
    private static final String LOGO_PATH = "com/imagenes/soloelojo.png"; // logo en src/main/java/com/imagenes

    public LoginFrame(DAO dao, Runnable onLoginSuccess) {
        this.dao = dao;
        this.onLoginSuccess = onLoginSuccess;
        initComponents();
    }

    private void initComponents() {
        setTitle("Ingreso de usuario");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        Color fondo = new Color(243, 246, 250);
        Color card = Color.WHITE;
        Color primario = new Color(33, 150, 243);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(fondo);

        // Header con logo opcional
        logoLabel = new JLabel(" ", JLabel.CENTER);
        logoLabel.setOpaque(true);
        logoLabel.setBackground(fondo);
        logoLabel.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        cargarLogo(LOGO_PATH, 240, 120);
        root.add(logoLabel, BorderLayout.PAGE_START);

        // Card central con los campos
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(card);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12),
                BorderFactory.createLineBorder(new Color(220, 225, 230))
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titulo = new JLabel("Inicio de Sesión");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        titulo.setHorizontalAlignment(JLabel.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(titulo, gbc);

        JLabel usuarioLabel = new JLabel("Usuario");
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(usuarioLabel, gbc);

        usuarioField = new JTextField(15);
        gbc.gridx = 1;
        panel.add(usuarioField, gbc);

        JLabel passwordLabel = new JLabel("Contrasena");
        gbc.gridy++;
        gbc.gridx = 0;
        panel.add(passwordLabel, gbc);

        passwordField = new JPasswordField(15);
        passwordField.addActionListener(e -> intentarIngresar());
        gbc.gridx = 1;
        panel.add(passwordField, gbc);

        JButton ingresarButton = new JButton("Ingresar");
        ingresarButton.setBackground(primario);
        ingresarButton.setForeground(Color.WHITE);
        ingresarButton.setFocusPainted(false);
        ingresarButton.addActionListener(e -> intentarIngresar());
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(ingresarButton, gbc);

        estadoLabel = new JLabel(" ", JLabel.CENTER);
        estadoLabel.setForeground(new Color(200, 0, 0));
        gbc.gridy++;
        panel.add(estadoLabel, gbc);

        root.add(panel, BorderLayout.CENTER);

        getRootPane().setDefaultButton(ingresarButton);
        getContentPane().add(root);
        pack();
        setLocationRelativeTo(null);
    }

    private void intentarIngresar() {
        String usuario = usuarioField.getText();
        char[] password = passwordField.getPassword();

        if (dao.validarUsuario(usuario, password)) {
            estadoLabel.setForeground(new Color(0, 128, 0));
            estadoLabel.setText("Acceso concedido");
            dispose();
            if (onLoginSuccess != null) {
                SwingUtilities.invokeLater(onLoginSuccess);
            }
        } else {
            estadoLabel.setForeground(new Color(200, 0, 0));
            estadoLabel.setText("Credenciales invalidas");
        }

        passwordField.setText("");
    }

    private void cargarLogo(String ruta, int anchoMax, int altoMax) {
        try {
            BufferedImage img = null;
            // 1) Intentar cargar desde el classpath
            InputStream is = getClass().getClassLoader().getResourceAsStream(ruta);
            if (is != null) {
                img = ImageIO.read(is);
            }
            // 2) Intentar ruta de proyecto (src/main/java o target/classes)
            if (img == null) {
                Path candidate = Path.of("src/main/java", ruta);
                if (!Files.exists(candidate)) {
                    candidate = Path.of("target/classes", ruta);
                }
                if (Files.exists(candidate)) {
                    img = ImageIO.read(candidate.toFile());
                }
            }
            if (img != null) {
                Image scaled = img.getScaledInstance(anchoMax, altoMax, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(scaled));
            } else {
                logoLabel.setText("Laboratorio 1");
                logoLabel.setForeground(new Color(90, 99, 110));
            }
        } catch (Exception e) {
            logoLabel.setText("Laboratorio 1");
            logoLabel.setForeground(new Color(90, 99, 110));
        }
    }
}
