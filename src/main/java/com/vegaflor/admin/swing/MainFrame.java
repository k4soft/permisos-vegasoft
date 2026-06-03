package com.vegaflor.admin.swing;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("Admin Seguridad — VegaSoft");
        setSize(1200, 750);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.addTab("Usuarios",          new PanelUsuarios());
        tabs.addTab("Roles (Sec)",       new PanelRoles());
        tabs.addTab("Recursos",          new PanelRecursos());
        tabs.addTab("Aplicaciones (Sec)", new PanelAplicaciones());
        tabs.addTab("Roles / Permisos",  new PanelRolPermiso());
        tabs.addTab("Permisos",          new PanelPermisos());

        add(tabs);
    }
}
