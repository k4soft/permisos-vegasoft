package com.vegaflor.admin.swing;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class PanelRecursos extends JPanel {

    private DefaultTableModel modelo;
    private JTable tabla;
    private JComboBox<String> cboApp = new JComboBox<>();
    private Map<String, Integer> appsMap = new LinkedHashMap<>();
    private boolean filtrando = false;

    public PanelRecursos() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Recursos"));

        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtro.add(new JLabel("Aplicación:"));
        cboApp.addActionListener(e -> { if (!filtrando) cargar(); });
        filtro.add(cboApp);
        add(filtro, BorderLayout.NORTH);

        modelo = new DefaultTableModel(
                new String[]{"Código", "Nombre", "Tipo", "URL", "Posición", "Icono", "App", "Activo"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabla = new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(22);
        tabla.getColumnModel().getColumn(0).setMaxWidth(65);
        tabla.getColumnModel().getColumn(2).setMaxWidth(65);
        tabla.getColumnModel().getColumn(4).setMaxWidth(65);
        tabla.getColumnModel().getColumn(7).setMaxWidth(55);
        tabla.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            private final Color BG_MENU = new Color(220, 232, 250);
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object val, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                boolean esMenu = "Menú".equals(t.getModel().getValueAt(row, 2));
                setFont(getFont().deriveFont(esMenu ? Font.BOLD : Font.PLAIN));
                if (!sel) setBackground(esMenu ? BG_MENU : Color.WHITE);
                if (col == 1 && !esMenu) setText("   " + val);
                return this;
            }
        });
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nuevo");
        JButton btnEditar   = new JButton("Editar");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnRefresh  = new JButton("Refrescar");
        btnNuevo.addActionListener(e -> dialogo(null));
        btnEditar.addActionListener(e -> {
            int row = tabla.getSelectedRow();
            if (row >= 0) dialogo((Integer) modelo.getValueAt(row, 0));
        });
        btnEliminar.addActionListener(e -> eliminar());
        btnRefresh.addActionListener(e -> cargar());
        bar.add(btnNuevo); bar.add(btnEditar); bar.add(btnEliminar);
        bar.addSeparator(); bar.add(btnRefresh);
        add(bar, BorderLayout.SOUTH);
        cargarApps();
        setupAutocomplete();
        cargar();
    }

    private void setupAutocomplete() {
        java.util.List<String> todos = new java.util.ArrayList<>(appsMap.keySet());
        cboApp.setEditable(true);
        JTextField editor = (JTextField) cboApp.getEditor().getEditorComponent();
        editor.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                int kc = e.getKeyCode();
                if (kc == java.awt.event.KeyEvent.VK_ENTER ||
                    kc == java.awt.event.KeyEvent.VK_UP   ||
                    kc == java.awt.event.KeyEvent.VK_DOWN ||
                    kc == java.awt.event.KeyEvent.VK_ESCAPE) return;
                String texto = editor.getText().toLowerCase();
                filtrando = true;
                cboApp.removeAllItems();
                for (String item : todos) {
                    if (texto.isEmpty() || item.toLowerCase().contains(texto))
                        cboApp.addItem(item);
                }
                editor.setText(editor.getText()); // restaurar texto del editor
                filtrando = false;
                if (cboApp.getItemCount() > 0) cboApp.showPopup();
            }
        });
    }

    private void cargarApps() {
        appsMap.clear();
        appsMap.put("(todas)", null);
        cboApp.addItem("(todas)");
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT AppCode, AppName FROM DB_Seguridad.Sec_Application WITH(NOLOCK) ORDER BY AppName");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nombre = rs.getString("AppName");
                appsMap.put(nombre, rs.getInt("AppCode"));
                cboApp.addItem(nombre);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cargar() {
        modelo.setRowCount(0);
        String appSel = (String) cboApp.getSelectedItem();
        Integer appCode = appsMap.getOrDefault(appSel, null);
        String sql = "SELECT ResourceCode, ResourceName, MainCode, Url, Position, Icon, AppName, Active FROM (" +
                     "SELECT rs.ResourceCode, rs.ResourceName, rs.MainCode, rs.Url, rs.Position, " +
                     "rs.Icon, a.AppName, rs.Active, " +
                     "ISNULL(parent.ResourceName, rs.ResourceName) AS SortMenu " +
                     "FROM DB_Seguridad.Sec_Resource rs WITH(NOLOCK) " +
                     "LEFT JOIN DB_Seguridad.Sec_Application a WITH(NOLOCK)     ON rs.AppCode = a.AppCode " +
                     "LEFT JOIN DB_Seguridad.Sec_Resource parent WITH(NOLOCK)   ON rs.MainCode = parent.ResourceCode" +
                     (appCode != null ? " WHERE rs.AppCode = " + appCode : "") +
                     ") t ORDER BY AppName, SortMenu, MainCode, Position, ResourceName";
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rs.getInt("MainCode");
                String tipo = rs.wasNull() ? "Menú" : "Opción";
                modelo.addRow(new Object[]{
                        rs.getInt("ResourceCode"),
                        rs.getString("ResourceName"),
                        tipo,
                        rs.getString("Url"),
                        rs.getInt("Position"),
                        rs.getString("Icon"),
                        rs.getString("AppName"),
                        rs.getBoolean("Active") ? "Sí" : "No"
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminar() {
        int row = tabla.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Selecciona un recurso."); return; }
        int code = (Integer) modelo.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "¿Eliminar el recurso?\nSe quitará de todos los roles que lo tengan asignado.",
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection c = Conexion.get()) {
            c.prepareStatement("DELETE FROM DB_Seguridad.Sec_ResourceRole WHERE ResourceCode=" + code).executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Sec_Resource WHERE ResourceCode=" + code).executeUpdate();
            cargar();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void dialogo(Integer resourceCode) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                resourceCode == null ? "Nuevo Recurso" : "Editar Recurso", true);
        dlg.setSize(480, 400);
        dlg.setLocationRelativeTo(this);

        JTextField txtNombre   = new JTextField(25);
        JTextField txtUrl      = new JTextField(25);
        JTextField txtIcono    = new JTextField(25);
        JTextField txtTooltip  = new JTextField(25);
        JTextField txtPosicion = new JTextField("0", 25);
        JTextField txtNumero   = new JTextField("0", 25);
        JComboBox<String> cboApp    = new JComboBox<>();
        JComboBox<String> cboParent = new JComboBox<>();
        JCheckBox chkActivo = new JCheckBox("Activo", true);

        // cargar apps
        Map<String, Integer> appsMap = new LinkedHashMap<>();
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT AppCode, AppName FROM DB_Seguridad.Sec_Application WITH(NOLOCK) ORDER BY AppName");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                appsMap.put(rs.getString("AppName"), rs.getInt("AppCode"));
                cboApp.addItem(rs.getString("AppName"));
            }
        } catch (SQLException e) { /* ignore */ }

        // cargar posibles padres (recursos sin MainCode = menús)
        Map<String, Integer> parentMap = new LinkedHashMap<>();
        cboParent.addItem("(ninguno - es menú raíz)");
        parentMap.put("(ninguno - es menú raíz)", null);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT ResourceCode, ResourceName FROM DB_Seguridad.Sec_Resource WITH(NOLOCK) " +
                     "WHERE MainCode IS NULL ORDER BY ResourceName");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String label = rs.getString("ResourceName") + "  [" + rs.getInt("ResourceCode") + "]";
                parentMap.put(label, rs.getInt("ResourceCode"));
                cboParent.addItem(label);
            }
        } catch (SQLException e) { /* ignore */ }

        if (resourceCode != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT rs.*, a.AppName FROM DB_Seguridad.Sec_Resource rs " +
                         "LEFT JOIN DB_Seguridad.Sec_Application a ON rs.AppCode=a.AppCode WHERE rs.ResourceCode=?")) {
                ps.setInt(1, resourceCode);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    txtNombre.setText(rs.getString("ResourceName"));
                    txtUrl.setText(rs.getString("Url"));
                    txtIcono.setText(rs.getString("Icon"));
                    txtTooltip.setText(rs.getString("ToolTip"));
                    txtPosicion.setText(String.valueOf(rs.getInt("Position")));
                    txtNumero.setText(String.valueOf(rs.getInt("ResourceNumber")));
                    chkActivo.setSelected(rs.getBoolean("Active"));
                    cboApp.setSelectedItem(rs.getString("AppName"));
                    int mc = rs.getInt("MainCode");
                    if (!rs.wasNull()) {
                        parentMap.forEach((k, v) -> { if (v != null && v == mc) cboParent.setSelectedItem(k); });
                    }
                }
            } catch (SQLException e) { /* ignore */ }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 5, 4, 5);

        Object[][] campos = {
                {"Nombre *:", txtNombre}, {"URL:", txtUrl},
                {"Icono:", txtIcono}, {"Tooltip:", txtTooltip},
                {"Posición:", txtPosicion},
                {"Aplicación:", cboApp}, {"Menú padre:", cboParent}, {"", chkActivo}
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
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) { JOptionPane.showMessageDialog(dlg, "El nombre es obligatorio."); return; }
            String appSel = (String) cboApp.getSelectedItem();
            Integer appCode = appSel != null ? appsMap.get(appSel) : null;
            String parentSel = (String) cboParent.getSelectedItem();
            Integer mainCode = parentSel != null ? parentMap.get(parentSel) : null;
            try (Connection c = Conexion.get()) {
                if (resourceCode == null) {
                    int nextNum = 0;
                    if (appCode != null) {
                        try (PreparedStatement psN = c.prepareStatement(
                                "SELECT ISNULL(MAX(ResourceNumber),0)+1 FROM DB_Seguridad.Sec_Resource WHERE AppCode=?")) {
                            psN.setInt(1, appCode);
                            ResultSet rsN = psN.executeQuery();
                            if (rsN.next()) nextNum = rsN.getInt(1);
                        }
                    }
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO DB_Seguridad.Sec_Resource " +
                            "(ResourceCode, ResourceName, ResourceNumber, Url, Position, ToolTip, Icon, Active, MainCode, AppCode) " +
                            "VALUES ((SELECT ISNULL(MAX(ResourceCode),0)+1 FROM DB_Seguridad.Sec_Resource),?,?,?,?,?,?,?,?,?)");
                    ps.setString(1, nombre); ps.setInt(2, nextNum);
                    ps.setString(3, txtUrl.getText().trim()); ps.setInt(4, intVal(txtPosicion, 0));
                    ps.setString(5, truncar(txtTooltip.getText().trim(), 100));
                    ps.setString(6, truncar(txtIcono.getText().trim(), 50));
                    ps.setBoolean(7, chkActivo.isSelected()); ps.setObject(8, mainCode); ps.setObject(9, appCode);
                    ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement(
                            "UPDATE DB_Seguridad.Sec_Resource SET ResourceName=?, ResourceNumber=?, Url=?, " +
                            "Position=?, ToolTip=?, Icon=?, Active=?, MainCode=?, AppCode=? WHERE ResourceCode=?");
                    ps.setString(1, nombre); ps.setInt(2, intVal(txtNumero, 0));
                    ps.setString(3, txtUrl.getText().trim()); ps.setInt(4, intVal(txtPosicion, 0));
                    ps.setString(5, truncar(txtTooltip.getText().trim(), 100));
                    ps.setString(6, truncar(txtIcono.getText().trim(), 50));
                    ps.setBoolean(7, chkActivo.isSelected()); ps.setObject(8, mainCode);
                    ps.setObject(9, appCode); ps.setInt(10, resourceCode);
                    ps.executeUpdate();
                }
                dlg.dispose(); cargar();
            } catch (SQLException ex) {
                String msg = ex.getMessage() != null && ex.getMessage().contains("IX_Sec_Resource")
                        ? "El campo 'Número' ya está en uso para esta Aplicación.\nCambia el valor del campo 'Número'."
                        : "Error: " + ex.getMessage();
                JOptionPane.showMessageDialog(dlg, msg, "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(new JScrollPane(form), BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private static int intVal(JTextField f, int def) {
        try { return Integer.parseInt(f.getText().trim()); } catch (NumberFormatException e) { return def; }
    }

    private static String truncar(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
