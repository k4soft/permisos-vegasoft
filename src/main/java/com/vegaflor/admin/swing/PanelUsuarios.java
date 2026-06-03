package com.vegaflor.admin.swing;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class PanelUsuarios extends JPanel {

    private DefaultTableModel modeloUsuarios;
    private JTable tablaUsuarios;
    private TableRowSorter<DefaultTableModel> sorterUsuarios;
    private final JTextField txtBuscarUsuarios = new JTextField();

    private DefaultListModel<String> modeloRolesAsignados  = new DefaultListModel<>();
    private DefaultListModel<String> modeloRolesDisponibles = new DefaultListModel<>();
    private JList<String> listaRolesAsignados   = new JList<>(modeloRolesAsignados);
    private JList<String> listaRolesDisponibles = new JList<>(modeloRolesDisponibles);
    private final java.util.List<String> todosAsig = new java.util.ArrayList<>();
    private final java.util.List<String> todosDisp = new java.util.ArrayList<>();
    private final JTextField txtBuscarAsig = new JTextField();
    private final JTextField txtBuscarDisp = new JTextField();

    private DefaultTableModel modeloRecursos;
    private JTable tablaRecursos;
    private JComboBox<String> cboFiltroApp = new JComboBox<>();
    private java.util.Map<String, Integer> appsMap = new java.util.LinkedHashMap<>();

    private Integer userCodeSel;

    public PanelUsuarios() {
        setLayout(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                panelTablaUsuarios(), panelDetalle());
        split.setDividerLocation(380);
        split.setResizeWeight(0.32);
        add(split, BorderLayout.CENTER);
        cargarAplicaciones();
        cargarUsuarios();
    }

    // ── Panel izquierdo: tabla de usuarios ────────────────────────────────────

    private JPanel panelTablaUsuarios() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(titledBorder("Usuarios"));

        modeloUsuarios = new DefaultTableModel(
                new String[]{"Código", "Login", "Nombre", "Días", "Activo"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaUsuarios = new JTable(modeloUsuarios);
        tablaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaUsuarios.setRowHeight(22);
        colWidth(tablaUsuarios, 0, 60, 60);
        colWidth(tablaUsuarios, 3, 50, 50);
        colWidth(tablaUsuarios, 4, 50, 50);
        sorterUsuarios = new TableRowSorter<>(modeloUsuarios);
        tablaUsuarios.setRowSorter(sorterUsuarios);
        tablaUsuarios.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onUsuarioSel();
        });

        txtBuscarUsuarios.putClientProperty("JTextField.placeholderText", "Buscar por login o nombre...");
        txtBuscarUsuarios.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrarUsuarios(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrarUsuarios(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrarUsuarios(); }
        });
        p.add(txtBuscarUsuarios, BorderLayout.NORTH);
        p.add(new JScrollPane(tablaUsuarios), BorderLayout.CENTER);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nuevo");
        JButton btnEditar   = new JButton("Editar");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnRefresh  = new JButton("Refrescar");
        JButton btnCopiar = new JButton("Copiar config");
        btnNuevo.addActionListener(e -> dialogoUsuario(null));
        btnEditar.addActionListener(e -> { if (userCodeSel != null) dialogoUsuario(userCodeSel); });
        btnEliminar.addActionListener(e -> eliminarUsuario());
        btnCopiar.addActionListener(e -> copiarConfiguracion());
        btnRefresh.addActionListener(e -> cargarUsuarios());
        bar.add(btnNuevo); bar.add(btnEditar); bar.add(btnEliminar);
        bar.addSeparator(); bar.add(btnCopiar);
        bar.addSeparator(); bar.add(btnRefresh);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    // ── Panel derecho: asignación de roles + recursos ─────────────────────────

    private JPanel panelDetalle() {
        JPanel p = new JPanel(new BorderLayout(4, 4));

        // --- roles ---
        JPanel panelRoles = new JPanel(new BorderLayout(4, 4));
        panelRoles.setBorder(titledBorder("Roles del usuario seleccionado"));

        javax.swing.event.DocumentListener buscarDL = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrarRoles(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrarRoles(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrarRoles(); }
        };

        JPanel pAsig = new JPanel(new BorderLayout(0, 4));
        pAsig.setBorder(BorderFactory.createTitledBorder("Asignados"));
        txtBuscarAsig.putClientProperty("JTextField.placeholderText", "Buscar...");
        txtBuscarAsig.getDocument().addDocumentListener(buscarDL);
        pAsig.add(txtBuscarAsig, BorderLayout.NORTH);
        listaRolesAsignados.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pAsig.add(new JScrollPane(listaRolesAsignados), BorderLayout.CENTER);
        JButton btnQuitar = new JButton("Quitar →");
        btnQuitar.addActionListener(e -> quitarRol());
        JPanel surAsig = new JPanel(new FlowLayout(FlowLayout.CENTER));
        surAsig.add(btnQuitar);
        pAsig.add(surAsig, BorderLayout.SOUTH);

        JPanel pDisp = new JPanel(new BorderLayout(0, 4));
        pDisp.setBorder(BorderFactory.createTitledBorder("Disponibles"));
        txtBuscarDisp.putClientProperty("JTextField.placeholderText", "Buscar...");
        txtBuscarDisp.getDocument().addDocumentListener(buscarDL);
        pDisp.add(txtBuscarDisp, BorderLayout.NORTH);
        listaRolesDisponibles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pDisp.add(new JScrollPane(listaRolesDisponibles), BorderLayout.CENTER);
        JButton btnAsignar = new JButton("← Asignar");
        btnAsignar.addActionListener(e -> asignarRol());
        JPanel surDisp = new JPanel(new FlowLayout(FlowLayout.CENTER));
        surDisp.add(btnAsignar);
        pDisp.add(surDisp, BorderLayout.SOUTH);

        JSplitPane splitRoles = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, pAsig, pDisp);
        splitRoles.setResizeWeight(0.5);
        panelRoles.add(splitRoles, BorderLayout.CENTER);
        panelRoles.setPreferredSize(new Dimension(0, 230));

        // --- recursos ---
        JPanel panelRecursos = new JPanel(new BorderLayout(4, 4));
        panelRecursos.setBorder(titledBorder("Recursos del usuario (via roles asignados)"));

        JPanel filtroApp = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtroApp.add(new JLabel("Aplicación:"));
        cboFiltroApp.addActionListener(e -> { if (userCodeSel != null) cargarRecursosUsuario(); });
        filtroApp.add(cboFiltroApp);
        panelRecursos.add(filtroApp, BorderLayout.NORTH);

        modeloRecursos = new DefaultTableModel(
                new String[]{"Tipo", "Recurso", "URL", "Aplicación"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaRecursos = new JTable(modeloRecursos);
        tablaRecursos.setRowHeight(22);
        colWidth(tablaRecursos, 0, 65, 65);
        tablaRecursos.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            private final Color BG_MENU = new Color(220, 232, 250);
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                String tipo = (String) t.getModel().getValueAt(row, 0);
                boolean esMenu = "Menú".equals(tipo);
                setFont(getFont().deriveFont(esMenu ? Font.BOLD : Font.PLAIN));
                if (!sel) setBackground(esMenu ? BG_MENU : Color.WHITE);
                if (col == 1 && !esMenu) setText("   " + val);
                return this;
            }
        });
        panelRecursos.add(new JScrollPane(tablaRecursos), BorderLayout.CENTER);

        JSplitPane splitDer = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelRoles, panelRecursos);
        splitDer.setDividerLocation(235);
        p.add(splitDer, BorderLayout.CENTER);
        return p;
    }

    // ── Eventos ───────────────────────────────────────────────────────────────

    private void filtrarUsuarios() {
        String texto = txtBuscarUsuarios.getText().trim();
        if (texto.isEmpty()) {
            sorterUsuarios.setRowFilter(null);
        } else {
            sorterUsuarios.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto), 1, 2));
        }
    }

    private void onUsuarioSel() {
        int row = tablaUsuarios.getSelectedRow();
        if (row < 0) return;
        int modelRow = tablaUsuarios.convertRowIndexToModel(row);
        userCodeSel = (Integer) modeloUsuarios.getValueAt(modelRow, 0);
        cargarRolesUsuario();
        cargarRecursosUsuario();
    }

    // ── Carga de datos ────────────────────────────────────────────────────────

    private void cargarUsuarios() {
        modeloUsuarios.setRowCount(0);
        String sql = "SELECT UserCode, Login, Description, " +
                     "DATEDIFF(d, LastDateChange, GETDATE()) AS Days, Active " +
                     "FROM DB_Seguridad.Sec_User WITH(NOLOCK) ORDER BY Description";
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                modeloUsuarios.addRow(new Object[]{
                        rs.getInt("UserCode"),
                        rs.getString("Login"),
                        rs.getString("Description"),
                        rs.getInt("Days"),
                        rs.getBoolean("Active") ? "Sí" : "No"
                });
            }
        } catch (SQLException e) {
            error("Error cargando usuarios", e);
        }
    }

    private void filtrarRoles() {
        String fA = txtBuscarAsig.getText().trim().toLowerCase();
        String fD = txtBuscarDisp.getText().trim().toLowerCase();
        modeloRolesAsignados.clear();
        todosAsig.stream().filter(s -> fA.isEmpty() || s.toLowerCase().contains(fA))
                 .forEach(modeloRolesAsignados::addElement);
        modeloRolesDisponibles.clear();
        todosDisp.stream().filter(s -> fD.isEmpty() || s.toLowerCase().contains(fD))
                 .forEach(modeloRolesDisponibles::addElement);
    }

    private void cargarRolesUsuario() {
        todosAsig.clear();
        todosDisp.clear();
        modeloRolesAsignados.clear();
        modeloRolesDisponibles.clear();
        if (userCodeSel == null) return;

        Set<Integer> asignados = new HashSet<>();
        String sqlA = "SELECT r.RoleCode, r.RoleName " +
                      "FROM DB_Seguridad.Sec_Role r WITH(NOLOCK) " +
                      "INNER JOIN DB_Seguridad.Sec_RoleUser ru WITH(NOLOCK) ON r.RoleCode = ru.RoleCode " +
                      "WHERE ru.UserCode = ? ORDER BY r.RoleName";
        try (Connection c = Conexion.get(); PreparedStatement ps = c.prepareStatement(sqlA)) {
            ps.setInt(1, userCodeSel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int code = rs.getInt("RoleCode");
                asignados.add(code);
                todosAsig.add(rs.getString("RoleName") + "  [" + code + "]");
            }
        } catch (SQLException e) { error("Error cargando roles asignados", e); }

        String sqlD = "SELECT RoleCode, RoleName FROM DB_Seguridad.Sec_Role WITH(NOLOCK) " +
                      "WHERE Active = 1 ORDER BY RoleName";
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(sqlD);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (!asignados.contains(rs.getInt("RoleCode")))
                    todosDisp.add(rs.getString("RoleName") + "  [" + rs.getInt("RoleCode") + "]");
            }
        } catch (SQLException e) { error("Error cargando roles disponibles", e); }
        filtrarRoles();
    }

    private void cargarAplicaciones() {
        appsMap.clear();
        appsMap.put("(todas)", null);
        cboFiltroApp.addItem("(todas)");
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT AppCode, AppName FROM DB_Seguridad.Sec_Application WITH(NOLOCK) ORDER BY AppName");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nombre = rs.getString("AppName");
                appsMap.put(nombre, rs.getInt("AppCode"));
                cboFiltroApp.addItem(nombre);
            }
        } catch (SQLException e) { error("Error cargando aplicaciones", e); }
    }

    private void cargarRecursosUsuario() {
        modeloRecursos.setRowCount(0);
        if (userCodeSel == null) return;
        String appSel = (String) cboFiltroApp.getSelectedItem();
        Integer appCode = appsMap.getOrDefault(appSel, null);
        // Subquery so ORDER BY can use computed SortMenu without DISTINCT restriction
        String sql = "SELECT AppName, ResourceName, Url, MainCode, Tipo, SortMenu FROM (" +
                     "SELECT DISTINCT rs.ResourceName, rs.Url, rs.MainCode, a.AppName, " +
                     "CASE WHEN rs.MainCode IS NULL THEN 'Menú' ELSE 'Opción' END AS Tipo, " +
                     "ISNULL(parent.ResourceName, rs.ResourceName) AS SortMenu " +
                     "FROM DB_Seguridad.Sec_Resource rs WITH(NOLOCK) " +
                     "INNER JOIN DB_Seguridad.Sec_ResourceRole rr WITH(NOLOCK) ON rs.ResourceCode = rr.ResourceCode " +
                     "INNER JOIN DB_Seguridad.Sec_Role r  WITH(NOLOCK)         ON rr.RoleCode = r.RoleCode " +
                     "INNER JOIN DB_Seguridad.Sec_RoleUser ru WITH(NOLOCK)     ON r.RoleCode = ru.RoleCode " +
                     "INNER JOIN DB_Seguridad.Sec_Application a WITH(NOLOCK)   ON rs.AppCode = a.AppCode " +
                     "LEFT  JOIN DB_Seguridad.Sec_Resource parent WITH(NOLOCK) ON rs.MainCode = parent.ResourceCode " +
                     "WHERE ru.UserCode = ?" +
                     (appCode != null ? " AND rs.AppCode = " + appCode : "") +
                     ") t ORDER BY AppName, SortMenu, MainCode, ResourceName";
        try (Connection c = Conexion.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userCodeSel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                modeloRecursos.addRow(new Object[]{
                        rs.getString("Tipo"),
                        rs.getString("ResourceName"),
                        rs.getString("Url"),
                        rs.getString("AppName")
                });
            }
        } catch (SQLException e) { error("Error cargando recursos", e); }
    }

    // ── Operaciones ───────────────────────────────────────────────────────────

    private void asignarRol() {
        String sel = listaRolesDisponibles.getSelectedValue();
        if (sel == null || userCodeSel == null) return;
        int roleCode = extractCode(sel);
        try (Connection c = Conexion.get()) {
            try (var psChk = c.prepareStatement(
                    "SELECT 1 FROM DB_Seguridad.Sec_RoleUser WHERE UserCode=? AND RoleCode=?")) {
                psChk.setInt(1, userCodeSel); psChk.setInt(2, roleCode);
                if (psChk.executeQuery().next()) return;
            }
            int nextCode;
            try (var psMax = c.prepareStatement(
                    "SELECT ISNULL(MAX(RoleUserCode), 0) + 1 FROM DB_Seguridad.Sec_RoleUser")) {
                var rsMax = psMax.executeQuery(); rsMax.next();
                nextCode = rsMax.getInt(1);
            }
            try (var ps = c.prepareStatement(
                    "INSERT INTO DB_Seguridad.Sec_RoleUser (RoleUserCode, UserCode, RoleCode) VALUES (?,?,?)")) {
                ps.setInt(1, nextCode); ps.setInt(2, userCodeSel); ps.setInt(3, roleCode);
                ps.executeUpdate();
            }
            cargarRolesUsuario();
            cargarRecursosUsuario();
        } catch (SQLException e) { error("Error asignando rol", e); }
    }

    private void quitarRol() {
        String sel = listaRolesAsignados.getSelectedValue();
        if (sel == null || userCodeSel == null) return;
        if (!confirmar("¿Quitar el rol seleccionado al usuario?")) return;
        int roleCode = extractCode(sel);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM DB_Seguridad.Sec_RoleUser WHERE UserCode=? AND RoleCode=?")) {
            ps.setInt(1, userCodeSel); ps.setInt(2, roleCode);
            ps.executeUpdate();
            cargarRolesUsuario();
            cargarRecursosUsuario();
        } catch (SQLException e) { error("Error quitando rol", e); }
    }

    private void eliminarUsuario() {
        if (userCodeSel == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario primero.");
            return;
        }
        if (!confirmar("¿Eliminar el usuario seleccionado?\nSe eliminarán también sus asignaciones de roles.")) return;
        try (Connection c = Conexion.get()) {
            PreparedStatement ps1 = c.prepareStatement(
                    "DELETE FROM DB_Seguridad.Sec_RoleUser WHERE UserCode=?");
            ps1.setInt(1, userCodeSel); ps1.executeUpdate();
            PreparedStatement ps2 = c.prepareStatement(
                    "DELETE FROM DB_Seguridad.Sec_User WHERE UserCode=?");
            ps2.setInt(1, userCodeSel); ps2.executeUpdate();
            userCodeSel = null;
            cargarUsuarios();
            modeloRolesAsignados.clear();
            modeloRolesDisponibles.clear();
            modeloRecursos.setRowCount(0);
        } catch (SQLException e) { error("Error eliminando usuario", e); }
    }

    private void dialogoUsuario(Integer userCode) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                userCode == null ? "Nuevo Usuario" : "Editar Usuario", true);
        dlg.setSize(420, 320);
        dlg.setLocationRelativeTo(this);

        JTextField txtLogin    = new JTextField(22);
        JTextField txtNombre   = new JTextField(22);
        JPasswordField txtPass = new JPasswordField(22);
        JTextField txtDias     = new JTextField("90", 22);
        JCheckBox chkActivo    = new JCheckBox("Activo", true);

        if (userCode != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT Login, Description, EffectiveDays, Active FROM DB_Seguridad.Sec_User WHERE UserCode=?")) {
                ps.setInt(1, userCode);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    txtLogin.setText(rs.getString("Login"));
                    txtNombre.setText(rs.getString("Description"));
                    txtDias.setText(String.valueOf(rs.getInt("EffectiveDays")));
                    chkActivo.setSelected(rs.getBoolean("Active"));
                }
            } catch (SQLException e) { error("Error cargando usuario", e); }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(5, 5, 5, 5);

        Object[][] campos = {
                {"Login *:", txtLogin},
                {"Nombre *:", txtNombre},
                {userCode == null ? "Contraseña *:" : "Contraseña (vacío = no cambia):", txtPass},
                {"Días vigencia:", txtDias},
                {"", chkActivo}
        };
        for (int i = 0; i < campos.length; i++) {
            gc.gridx = 0; gc.gridy = i; gc.weightx = 0;
            form.add(new JLabel(campos[i][0].toString()), gc);
            gc.gridx = 1; gc.weightx = 1;
            form.add((Component) campos[i][1], gc);
        }

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> dlg.dispose());
        btnGuardar.addActionListener(e -> {
            String login  = txtLogin.getText().trim();
            String nombre = txtNombre.getText().trim();
            String pass   = new String(txtPass.getPassword()).trim();
            if (login.isEmpty() || nombre.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Login y Nombre son obligatorios.");
                return;
            }
            if (userCode == null && pass.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "La contraseña es obligatoria para nuevos usuarios.");
                return;
            }
            try (Connection c = Conexion.get()) {
                if (userCode == null) {
                    int nextCode;
                    try (var psMax = c.prepareStatement(
                            "SELECT ISNULL(MAX(UserCode), 0) + 1 FROM DB_Seguridad.Sec_User")) {
                        var rsMax = psMax.executeQuery();
                        rsMax.next();
                        nextCode = rsMax.getInt(1);
                    }
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO DB_Seguridad.Sec_User (UserCode,Login,[Password],Description,EffectiveDays,LastDateChange,Time,Active) " +
                            "VALUES (?,?,?,?,?,GETDATE(),CONVERT(time,GETDATE()),?)");
                    ps.setInt(1, nextCode); ps.setString(2, login); ps.setString(3, Cypher.encript(pass));
                    ps.setString(4, nombre); ps.setInt(5, intVal(txtDias, 90)); ps.setBoolean(6, chkActivo.isSelected());
                    ps.executeUpdate();
                } else {
                    if (pass.isEmpty()) {
                        PreparedStatement ps = c.prepareStatement(
                                "UPDATE DB_Seguridad.Sec_User SET Login=?,Description=?,EffectiveDays=?,Active=? WHERE UserCode=?");
                        ps.setString(1, login); ps.setString(2, nombre);
                        ps.setInt(3, intVal(txtDias, 90)); ps.setBoolean(4, chkActivo.isSelected());
                        ps.setInt(5, userCode); ps.executeUpdate();
                    } else {
                        PreparedStatement ps = c.prepareStatement(
                                "UPDATE DB_Seguridad.Sec_User SET Login=?,Description=?,EffectiveDays=?,Active=?,[Password]=?,LastDateChange=GETDATE() WHERE UserCode=?");
                        ps.setString(1, login); ps.setString(2, nombre);
                        ps.setInt(3, intVal(txtDias, 90)); ps.setBoolean(4, chkActivo.isSelected());
                        ps.setString(5, Cypher.encript(pass)); ps.setInt(6, userCode); ps.executeUpdate();
                    }
                }
                dlg.dispose();
                cargarUsuarios();
            } catch (SQLException ex) { error("Error guardando usuario", ex); }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void copiarConfiguracion() {
        if (userCodeSel == null) {
            JOptionPane.showMessageDialog(this, "Selecciona el usuario origen primero.");
            return;
        }

        // Cargar lista de usuarios destino (todos menos el origen)
        DefaultListModel<String> modeloDestinos = new DefaultListModel<>();
        java.util.Map<String, Integer> codesMap = new java.util.LinkedHashMap<>();
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT UserCode, Login, Description FROM DB_Seguridad.Sec_User WITH(NOLOCK) " +
                     "WHERE UserCode <> ? ORDER BY Description")) {
            ps.setInt(1, userCodeSel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String item = rs.getString("Description") + "  [" + rs.getString("Login") + "]";
                modeloDestinos.addElement(item);
                codesMap.put(item, rs.getInt("UserCode"));
            }
        } catch (SQLException e) { error("Error cargando usuarios", e); return; }

        JList<String> listaDestinos = new JList<>(modeloDestinos);
        listaDestinos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTextField txtFiltro = new JTextField();
        txtFiltro.putClientProperty("JTextField.placeholderText", "Buscar usuario destino...");
        txtFiltro.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void update() {
                String f = txtFiltro.getText().trim().toLowerCase();
                modeloDestinos.clear();
                codesMap.keySet().stream()
                        .filter(s -> f.isEmpty() || s.toLowerCase().contains(f))
                        .forEach(modeloDestinos::addElement);
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { update(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });

        JCheckBox chkReemplazar = new JCheckBox("Reemplazar roles existentes del destino", false);

        JPanel panel = new JPanel(new BorderLayout(4, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        panel.add(txtFiltro, BorderLayout.NORTH);
        panel.add(new JScrollPane(listaDestinos), BorderLayout.CENTER);
        panel.add(chkReemplazar, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(340, 320));

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Copiar roles a usuario destino", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        String selItem = listaDestinos.getSelectedValue();
        if (selItem == null) {
            JOptionPane.showMessageDialog(this, "Selecciona un usuario destino.");
            return;
        }
        int destCode = codesMap.get(selItem);

        try (Connection c = Conexion.get()) {
            if (chkReemplazar.isSelected()) {
                PreparedStatement psDel = c.prepareStatement(
                        "DELETE FROM DB_Seguridad.Sec_RoleUser WHERE UserCode = ?");
                psDel.setInt(1, destCode);
                psDel.executeUpdate();
            }
            PreparedStatement psCopy = c.prepareStatement(
                    "INSERT INTO DB_Seguridad.Sec_RoleUser (RoleUserCode, UserCode, RoleCode) " +
                    "SELECT (SELECT ISNULL(MAX(RoleUserCode),0) FROM DB_Seguridad.Sec_RoleUser) + ROW_NUMBER() OVER (ORDER BY RoleCode), ?, RoleCode " +
                    "FROM DB_Seguridad.Sec_RoleUser " +
                    "WHERE UserCode = ? AND RoleCode NOT IN " +
                    "(SELECT RoleCode FROM DB_Seguridad.Sec_RoleUser WHERE UserCode = ?)");
            psCopy.setInt(1, destCode);
            psCopy.setInt(2, userCodeSel);
            psCopy.setInt(3, destCode);
            int copiados = psCopy.executeUpdate();
            JOptionPane.showMessageDialog(this, copiados + " rol(es) copiado(s) correctamente.");
            if (destCode == userCodeSel) { cargarRolesUsuario(); cargarRecursosUsuario(); }
        } catch (SQLException e) { error("Error copiando configuración", e); }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private static int extractCode(String item) {
        int s = item.lastIndexOf('['), e = item.lastIndexOf(']');
        return Integer.parseInt(item.substring(s + 1, e).trim());
    }

    private static int intVal(JTextField f, int def) {
        try { return Integer.parseInt(f.getText().trim()); } catch (NumberFormatException e) { return def; }
    }

    private static TitledBorder titledBorder(String title) {
        return BorderFactory.createTitledBorder(title);
    }

    private static void colWidth(JTable t, int col, int min, int max) {
        t.getColumnModel().getColumn(col).setMinWidth(min);
        t.getColumnModel().getColumn(col).setMaxWidth(max);
    }

    private boolean confirmar(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Confirmar",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void error(String msg, Exception e) {
        JOptionPane.showMessageDialog(this, msg + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
