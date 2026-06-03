package com.vegaflor.admin.swing;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class PanelRoles extends JPanel {

    // ── Tabla de roles ────────────────────────────────────────────────────────
    private DefaultTableModel modeloRoles;
    private JTable tablaRoles;
    private TableRowSorter<DefaultTableModel> sorterRoles;
    private final JTextField txtBuscarRoles = new JTextField();
    private JComboBox<String> cboFiltroRoles = new JComboBox<>();
    private Map<String, Integer> appsMap     = new LinkedHashMap<>();

    // ── Listas de recursos ────────────────────────────────────────────────────
    private DefaultListModel<String> modeloAsig  = new DefaultListModel<>();
    private DefaultListModel<String> modeloDisp  = new DefaultListModel<>();
    private JList<String> listaAsig  = new JList<>(modeloAsig);
    private JList<String> listaDisp  = new JList<>(modeloDisp);
    private JComboBox<String> cboFiltroRecursos  = new JComboBox<>();
    private final java.util.List<String> todosAsig = new java.util.ArrayList<>();
    private final java.util.List<String> todosDisp = new java.util.ArrayList<>();
    private final JTextField txtBuscarAsig = new JTextField();
    private final JTextField txtBuscarDisp = new JTextField();

    private Integer roleCodeSel;

    public PanelRoles() {
        setLayout(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                panelRoles(), panelRecursos());
        split.setDividerLocation(370);
        split.setResizeWeight(0.32);
        add(split, BorderLayout.CENTER);
        cargarAplicaciones();
        cargarRoles(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Panel izquierdo: tabla de roles + filtro por app
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel panelRoles() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Roles"));

        // filtro app + búsqueda
        JPanel norte = new JPanel(new BorderLayout(4, 2));
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtro.add(new JLabel("App:"));
        cboFiltroRoles.addActionListener(e -> {
            String sel = (String) cboFiltroRoles.getSelectedItem();
            cargarRoles(appsMap.getOrDefault(sel, null));
        });
        filtro.add(cboFiltroRoles);
        norte.add(filtro, BorderLayout.NORTH);
        txtBuscarRoles.putClientProperty("JTextField.placeholderText", "Buscar rol...");
        txtBuscarRoles.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrarTablaRoles(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrarTablaRoles(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrarTablaRoles(); }
        });
        norte.add(txtBuscarRoles, BorderLayout.SOUTH);
        p.add(norte, BorderLayout.NORTH);

        // tabla
        modeloRoles = new DefaultTableModel(
                new String[]{"Cód.", "Nombre", "App", "Activo"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaRoles = new JTable(modeloRoles);
        tablaRoles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaRoles.setRowHeight(22);
        tablaRoles.getColumnModel().getColumn(0).setMaxWidth(50);
        tablaRoles.getColumnModel().getColumn(3).setMaxWidth(55);
        sorterRoles = new TableRowSorter<>(modeloRoles);
        tablaRoles.setRowSorter(sorterRoles);
        tablaRoles.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRolSel();
        });
        p.add(new JScrollPane(tablaRoles), BorderLayout.CENTER);

        // toolbar
        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nuevo");
        JButton btnEditar   = new JButton("Editar");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnRefresh  = new JButton("↺");
        btnRefresh.setToolTipText("Refrescar");
        btnNuevo.addActionListener(e -> dialogoRol(null));
        btnEditar.addActionListener(e -> { if (roleCodeSel != null) dialogoRol(roleCodeSel); });
        btnEliminar.addActionListener(e -> eliminarRol());
        btnRefresh.addActionListener(e -> {
            String sel = (String) cboFiltroRoles.getSelectedItem();
            cargarRoles(appsMap.getOrDefault(sel, null));
        });
        bar.add(btnNuevo); bar.add(btnEditar); bar.add(btnEliminar);
        bar.addSeparator(); bar.add(btnRefresh);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Panel derecho: asignación de recursos
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel panelRecursos() {
        JPanel p = new JPanel(new BorderLayout(4, 6));
        p.setBorder(BorderFactory.createTitledBorder("Recursos del rol seleccionado"));

        // filtro de disponibles
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtro.add(new JLabel("Filtrar disponibles por app:"));
        cboFiltroRecursos.addActionListener(e -> { if (roleCodeSel != null) cargarRecursos(); });
        filtro.add(cboFiltroRecursos);
        p.add(filtro, BorderLayout.NORTH);

        // renderer compartido para ambas listas
        javax.swing.ListCellRenderer<String> renderer = (list, value, index, sel, foc) -> {
            JLabel lbl = new JLabel(value == null ? "" : value);
            lbl.setOpaque(true);
            boolean esMenu = value != null && value.startsWith("[M]");
            lbl.setFont(lbl.getFont().deriveFont(esMenu ? Font.BOLD : Font.PLAIN));
            if (sel) {
                lbl.setBackground(list.getSelectionBackground());
                lbl.setForeground(list.getSelectionForeground());
            } else {
                lbl.setBackground(esMenu ? new Color(220, 232, 250) : Color.WHITE);
                lbl.setForeground(list.getForeground());
            }
            if (!esMenu && value != null) lbl.setText("   " + value);
            lbl.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
            return lbl;
        };

        javax.swing.event.DocumentListener buscarDL = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
        };

        // columna asignados
        JPanel colAsig = new JPanel(new BorderLayout(0, 4));
        colAsig.setBorder(BorderFactory.createTitledBorder("Asignados al rol"));
        txtBuscarAsig.putClientProperty("JTextField.placeholderText", "Buscar...");
        txtBuscarAsig.getDocument().addDocumentListener(buscarDL);
        colAsig.add(txtBuscarAsig, BorderLayout.NORTH);
        listaAsig.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaAsig.setCellRenderer(renderer);
        colAsig.add(new JScrollPane(listaAsig), BorderLayout.CENTER);
        JButton btnQuitar = new JButton("Quitar →");
        btnQuitar.addActionListener(e -> quitarRecurso());
        JPanel surAsig = new JPanel(new FlowLayout(FlowLayout.CENTER));
        surAsig.add(btnQuitar);
        colAsig.add(surAsig, BorderLayout.SOUTH);

        // columna disponibles
        JPanel colDisp = new JPanel(new BorderLayout(0, 4));
        colDisp.setBorder(BorderFactory.createTitledBorder("Disponibles"));
        txtBuscarDisp.putClientProperty("JTextField.placeholderText", "Buscar...");
        txtBuscarDisp.getDocument().addDocumentListener(buscarDL);
        colDisp.add(txtBuscarDisp, BorderLayout.NORTH);
        listaDisp.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaDisp.setCellRenderer(renderer);
        colDisp.add(new JScrollPane(listaDisp), BorderLayout.CENTER);
        JButton btnAsignar = new JButton("← Asignar");
        btnAsignar.addActionListener(e -> asignarRecurso());
        JPanel surDisp = new JPanel(new FlowLayout(FlowLayout.CENTER));
        surDisp.add(btnAsignar);
        colDisp.add(surDisp, BorderLayout.SOUTH);

        JSplitPane splitListas = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, colAsig, colDisp);
        splitListas.setResizeWeight(0.5);
        p.add(splitListas, BorderLayout.CENTER);
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Carga de datos
    // ─────────────────────────────────────────────────────────────────────────

    private void cargarAplicaciones() {
        appsMap.clear();
        appsMap.put("(todas)", null);
        cboFiltroRoles.addItem("(todas)");
        cboFiltroRecursos.addItem("(todas)");
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT AppCode, AppName FROM DB_Seguridad.Sec_Application WITH(NOLOCK) ORDER BY AppName");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nombre = rs.getString("AppName");
                appsMap.put(nombre, rs.getInt("AppCode"));
                cboFiltroRoles.addItem(nombre);
                cboFiltroRecursos.addItem(nombre);
            }
        } catch (SQLException e) { error("Error cargando aplicaciones", e); }
    }

    private void cargarRoles(Integer appCode) {
        modeloRoles.setRowCount(0);
        String sql = "SELECT r.RoleCode, r.RoleName, a.AppName, r.Active " +
                     "FROM DB_Seguridad.Sec_Role r WITH(NOLOCK) " +
                     "LEFT JOIN DB_Seguridad.Sec_Application a WITH(NOLOCK) ON r.AppCode = a.AppCode " +
                     (appCode != null ? "WHERE r.AppCode = " + appCode + " " : "") +
                     "ORDER BY r.RoleName";
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                modeloRoles.addRow(new Object[]{
                        rs.getInt("RoleCode"), rs.getString("RoleName"),
                        rs.getString("AppName"), rs.getBoolean("Active") ? "Sí" : "No"
                });
        } catch (SQLException e) { error("Error cargando roles", e); }
    }

    private void filtrarTablaRoles() {
        String texto = txtBuscarRoles.getText().trim();
        if (texto.isEmpty()) {
            sorterRoles.setRowFilter(null);
        } else {
            sorterRoles.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto), 1));
        }
    }

    private void onRolSel() {
        int row = tablaRoles.getSelectedRow();
        if (row < 0) return;
        int modelRow = tablaRoles.convertRowIndexToModel(row);
        roleCodeSel = (Integer) modeloRoles.getValueAt(modelRow, 0);
        cargarRecursos();
    }

    private void filtrar() {
        String fA = txtBuscarAsig.getText().trim().toLowerCase();
        String fD = txtBuscarDisp.getText().trim().toLowerCase();
        modeloAsig.clear();
        todosAsig.stream().filter(s -> fA.isEmpty() || s.toLowerCase().contains(fA))
                 .forEach(modeloAsig::addElement);
        modeloDisp.clear();
        todosDisp.stream().filter(s -> fD.isEmpty() || s.toLowerCase().contains(fD))
                 .forEach(modeloDisp::addElement);
    }

    private void cargarRecursos() {
        todosAsig.clear();
        todosDisp.clear();
        modeloAsig.clear();
        modeloDisp.clear();
        if (roleCodeSel == null) return;

        Set<Integer> asignados = new HashSet<>();
        String sqlA = "SELECT ResourceCode, ResourceName, MainCode, AppName FROM (" +
                      "SELECT rs.ResourceCode, rs.ResourceName, rs.MainCode, a.AppName, " +
                      "ISNULL(parent.ResourceName, rs.ResourceName) AS SortMenu " +
                      "FROM DB_Seguridad.Sec_Resource rs WITH(NOLOCK) " +
                      "INNER JOIN DB_Seguridad.Sec_ResourceRole rr WITH(NOLOCK) ON rs.ResourceCode = rr.ResourceCode " +
                      "INNER JOIN DB_Seguridad.Sec_Application a WITH(NOLOCK)   ON rs.AppCode = a.AppCode " +
                      "LEFT  JOIN DB_Seguridad.Sec_Resource parent WITH(NOLOCK) ON rs.MainCode = parent.ResourceCode " +
                      "WHERE rr.RoleCode = ?) t ORDER BY AppName, SortMenu, MainCode, ResourceName";
        try (Connection c = Conexion.get(); PreparedStatement ps = c.prepareStatement(sqlA)) {
            ps.setInt(1, roleCodeSel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int code = rs.getInt("ResourceCode");
                asignados.add(code);
                rs.getInt("MainCode");
                String tipo = rs.wasNull() ? "[M]" : "[O]";
                todosAsig.add(tipo + " " + rs.getString("ResourceName") +
                        " (" + rs.getString("AppName") + ")  [" + code + "]");
            }
        } catch (SQLException e) { error("Error cargando recursos asignados", e); }

        String appSel = (String) cboFiltroRecursos.getSelectedItem();
        Integer appCodeFiltro = appsMap.getOrDefault(appSel, null);
        String sqlD = "SELECT ResourceCode, ResourceName, MainCode, AppName FROM (" +
                      "SELECT rs.ResourceCode, rs.ResourceName, rs.MainCode, a.AppName, " +
                      "ISNULL(parent.ResourceName, rs.ResourceName) AS SortMenu " +
                      "FROM DB_Seguridad.Sec_Resource rs WITH(NOLOCK) " +
                      "INNER JOIN DB_Seguridad.Sec_Application a WITH(NOLOCK) ON rs.AppCode = a.AppCode " +
                      "LEFT  JOIN DB_Seguridad.Sec_Resource parent WITH(NOLOCK) ON rs.MainCode = parent.ResourceCode " +
                      "WHERE rs.Active = 1" +
                      (appCodeFiltro != null ? " AND rs.AppCode = " + appCodeFiltro : "") +
                      ") t ORDER BY AppName, SortMenu, MainCode, ResourceName";
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(sqlD);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int code = rs.getInt("ResourceCode");
                if (!asignados.contains(code)) {
                    rs.getInt("MainCode");
                    String tipo = rs.wasNull() ? "[M]" : "[O]";
                    todosDisp.add(tipo + " " + rs.getString("ResourceName") +
                            " (" + rs.getString("AppName") + ")  [" + code + "]");
                }
            }
        } catch (SQLException e) { error("Error cargando recursos disponibles", e); }
        filtrar();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operaciones
    // ─────────────────────────────────────────────────────────────────────────

    private void asignarRecurso() {
        String sel = listaDisp.getSelectedValue();
        if (sel == null || roleCodeSel == null) return;
        int resCode = extractCode(sel);
        String sql = "IF NOT EXISTS (SELECT 1 FROM DB_Seguridad.Sec_ResourceRole WHERE RoleCode=? AND ResourceCode=?) " +
                     "INSERT INTO DB_Seguridad.Sec_ResourceRole (ResourceRoleCode, RoleCode, ResourceCode) " +
                     "VALUES ((SELECT ISNULL(MAX(ResourceRoleCode),0)+1 FROM DB_Seguridad.Sec_ResourceRole),?,?)";
        try (Connection c = Conexion.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, roleCodeSel); ps.setInt(2, resCode);
            ps.setInt(3, roleCodeSel); ps.setInt(4, resCode);
            ps.executeUpdate();
            cargarRecursos();
        } catch (SQLException e) { error("Error asignando recurso", e); }
    }

    private void quitarRecurso() {
        String sel = listaAsig.getSelectedValue();
        if (sel == null || roleCodeSel == null) return;
        if (!confirmar("¿Quitar el recurso del rol?")) return;
        int resCode = extractCode(sel);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM DB_Seguridad.Sec_ResourceRole WHERE RoleCode=? AND ResourceCode=?")) {
            ps.setInt(1, roleCodeSel); ps.setInt(2, resCode);
            ps.executeUpdate();
            cargarRecursos();
        } catch (SQLException e) { error("Error quitando recurso", e); }
    }

    private void eliminarRol() {
        if (roleCodeSel == null) { JOptionPane.showMessageDialog(this, "Selecciona un rol."); return; }
        if (!confirmar("¿Eliminar el rol?\nSe eliminarán sus asignaciones de usuarios y recursos.")) return;
        try (Connection c = Conexion.get()) {
            c.prepareStatement("DELETE FROM DB_Seguridad.Sec_RoleUser    WHERE RoleCode=" + roleCodeSel).executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Sec_ResourceRole WHERE RoleCode=" + roleCodeSel).executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Sec_Role         WHERE RoleCode=" + roleCodeSel).executeUpdate();
            roleCodeSel = null;
            String sel = (String) cboFiltroRoles.getSelectedItem();
            cargarRoles(appsMap.getOrDefault(sel, null));
            modeloAsig.clear(); modeloDisp.clear();
        } catch (SQLException e) { error("Error eliminando rol", e); }
    }

    private void dialogoRol(Integer roleCode) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                roleCode == null ? "Nuevo Rol" : "Editar Rol", true);
        dlg.setSize(400, 230);
        dlg.setLocationRelativeTo(this);

        JTextField txtNombre = new JTextField(22);
        JTextField txtDesc   = new JTextField(22);
        JComboBox<String> cboApp = new JComboBox<>();
        JCheckBox chkActivo = new JCheckBox("Activo", true);

        appsMap.forEach((k, v) -> { if (v != null) cboApp.addItem(k); });

        if (roleCode != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT r.RoleName, r.Description, r.Active, a.AppName " +
                         "FROM DB_Seguridad.Sec_Role r LEFT JOIN DB_Seguridad.Sec_Application a ON r.AppCode=a.AppCode " +
                         "WHERE r.RoleCode=?")) {
                ps.setInt(1, roleCode); ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    txtNombre.setText(rs.getString("RoleName"));
                    txtDesc.setText(rs.getString("Description"));
                    chkActivo.setSelected(rs.getBoolean("Active"));
                    cboApp.setSelectedItem(rs.getString("AppName"));
                }
            } catch (SQLException e) { /* ignore */ }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(5, 5, 5, 5);
        Object[][] campos = {{"Nombre *:", txtNombre}, {"Descripción:", txtDesc},
                             {"Aplicación:", cboApp}, {"", chkActivo}};
        for (int i = 0; i < campos.length; i++) {
            gc.gridx = 0; gc.gridy = i; gc.weightx = 0; form.add(new JLabel(campos[i][0].toString()), gc);
            gc.gridx = 1; gc.weightx = 1; form.add((Component) campos[i][1], gc);
        }

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> dlg.dispose());
        btnGuardar.addActionListener(e -> {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) { JOptionPane.showMessageDialog(dlg, "El nombre es obligatorio."); return; }
            String appSel = (String) cboApp.getSelectedItem();
            Integer appCode = appsMap.getOrDefault(appSel, null);
            try (Connection c = Conexion.get()) {
                if (roleCode == null) {
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO DB_Seguridad.Sec_Role (RoleCode, RoleName, Description, Active, AppCode) " +
                            "VALUES ((SELECT ISNULL(MAX(RoleCode),0)+1 FROM DB_Seguridad.Sec_Role),?,?,?,?)");
                    ps.setString(1, nombre); ps.setString(2, txtDesc.getText().trim());
                    ps.setBoolean(3, chkActivo.isSelected()); ps.setObject(4, appCode); ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement(
                            "UPDATE DB_Seguridad.Sec_Role SET RoleName=?, Description=?, Active=?, AppCode=? WHERE RoleCode=?");
                    ps.setString(1, nombre); ps.setString(2, txtDesc.getText().trim());
                    ps.setBoolean(3, chkActivo.isSelected()); ps.setObject(4, appCode); ps.setInt(5, roleCode);
                    ps.executeUpdate();
                }
                dlg.dispose();
                String sel = (String) cboFiltroRoles.getSelectedItem();
                cargarRoles(appsMap.getOrDefault(sel, null));
            } catch (SQLException ex) { error("Error guardando rol", ex); }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────────────────────────────────

    private static int extractCode(String item) {
        int s = item.lastIndexOf('['), e = item.lastIndexOf(']');
        return Integer.parseInt(item.substring(s + 1, e).trim());
    }

    private boolean confirmar(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Confirmar",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void error(String msg, Exception e) {
        JOptionPane.showMessageDialog(this, msg + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
