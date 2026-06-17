package com.vegaflor.admin.swing;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private JTabbedPane tabs;
    private JLabel lblUrl;

    public MainFrame() {
        setTitle("Admin Seguridad — VegaSoft");
        setSize(1200, 750);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        add(buildTopBar(), BorderLayout.NORTH);
        tabs = buildTabs();
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        bar.setBackground(new Color(45, 45, 48));

        JLabel lbl = new JLabel("Entorno:");
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JComboBox<String> cboEntorno = new JComboBox<>(new String[]{"local", "dev", "prod"});
        cboEntorno.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cboEntorno.setSelectedItem(Conexion.getEntorno());
        cboEntorno.addActionListener(e -> cambiarEntorno((String) cboEntorno.getSelectedItem()));

        lblUrl = new JLabel(Conexion.getUrl());
        lblUrl.setForeground(new Color(160, 200, 255));
        lblUrl.setFont(new Font("Segoe UI", Font.ITALIC, 12));

        bar.add(lbl);
        bar.add(cboEntorno);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(lblUrl);
        return bar;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane t = new JTabbedPane(JTabbedPane.TOP);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.addTab("Usuarios",           new PanelUsuarios());
        t.addTab("Roles (Sec)",        new PanelRoles());
        t.addTab("Recursos",           new PanelRecursos());
        t.addTab("Aplicaciones (Sec)", new PanelAplicaciones());
        t.addTab("Roles / Permisos",   new PanelRolPermiso());
        t.addTab("Permisos",           new PanelPermisos());
        t.addTab("Ingrepro",           new PanelIngresoProduccion());
        return t;
    }

    private void cambiarEntorno(String nuevoEntorno) {
        if (nuevoEntorno.equals(Conexion.getEntorno())) return;
        Conexion.setEntorno(nuevoEntorno);
        setTitle("Admin Seguridad — VegaSoft  [" + nuevoEntorno.toUpperCase() + "]");
        lblUrl.setText(Conexion.getUrl());

        remove(tabs);
        tabs = buildTabs();
        add(tabs, BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
