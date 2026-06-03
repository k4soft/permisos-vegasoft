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

public class PanelRolPermiso extends JPanel {

    // ── Tabla de roles ────────────────────────────────────────────────────────
    private DefaultTableModel modeloRoles;
    private JTable tablaRoles;
    private TableRowSorter<DefaultTableModel> sorterRoles;
    private final JTextField txtBuscarRoles = new JTextField();

    // ── Listas de permisos ────────────────────────────────────────────────────
    private DefaultListModel<String> modeloAsignados   = new DefaultListModel<>();
    private DefaultListModel<String> modeloDisponibles = new DefaultListModel<>();
    private JList<String> listaAsignados   = new JList<>(modeloAsignados);
    private JList<String> listaDisponibles = new JList<>(modeloDisponibles);
    private final java.util.List<String> todosAsig = new java.util.ArrayList<>();
    private final java.util.List<String> todosDisp = new java.util.ArrayList<>();
    private final JTextField txtBuscarAsig = new JTextField();
    private final JTextField txtBuscarDisp = new JTextField();

    private JComboBox<String> cboAplicacion = new JComboBox<>();
    private Map<String, Integer> appsMap    = new LinkedHashMap<>();

    private Integer idRolSel;

    public PanelRolPermiso() {
        setLayout(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                panelTablaRoles(), panelAsignacion());
        split.setDividerLocation(340);
        split.setResizeWeight(0.30);
        add(split, BorderLayout.CENTER);
        cargarAplicaciones();
        cargarRoles();
    }

    // ── Panel izquierdo: tabla de Rol ─────────────────────────────────────────

    private JPanel panelTablaRoles() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Roles"));

        modeloRoles = new DefaultTableModel(
                new String[]{"Id", "Nombre"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaRoles = new JTable(modeloRoles);
        tablaRoles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaRoles.setRowHeight(22);
        tablaRoles.getColumnModel().getColumn(0).setMaxWidth(55);
        sorterRoles = new TableRowSorter<>(modeloRoles);
        tablaRoles.setRowSorter(sorterRoles);
        tablaRoles.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRolSel();
        });
        txtBuscarRoles.putClientProperty("JTextField.placeholderText", "Buscar rol...");
        txtBuscarRoles.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrarTablaRoles(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrarTablaRoles(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrarTablaRoles(); }
        });
        p.add(txtBuscarRoles, BorderLayout.NORTH);
        p.add(new JScrollPane(tablaRoles), BorderLayout.CENTER);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nuevo");
        JButton btnEditar   = new JButton("Editar");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnRefresh  = new JButton("↺");
        btnRefresh.setToolTipText("Refrescar");
        btnNuevo.addActionListener(e -> dialogoRol(null));
        btnEditar.addActionListener(e -> { if (idRolSel != null) dialogoRol(idRolSel); });
        btnEliminar.addActionListener(e -> eliminarRol());
        btnRefresh.addActionListener(e -> cargarRoles());
        bar.add(btnNuevo); bar.add(btnEditar); bar.add(btnEliminar);
        bar.addSeparator(); bar.add(btnRefresh);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    // ── Panel derecho: asignación de permisos ─────────────────────────────────

    private JPanel panelAsignacion() {
        JPanel p = new JPanel(new BorderLayout(4, 6));
        p.setBorder(BorderFactory.createTitledBorder("Permisos del rol seleccionado"));

        // filtro por aplicación (filtra disponibles)
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtro.add(new JLabel("Filtrar disponibles por app:"));
        cboAplicacion.addItem("(todas)");
        cboAplicacion.addActionListener(e -> { if (idRolSel != null) cargarPermisosRol(); });
        filtro.add(cboAplicacion);
        p.add(filtro, BorderLayout.NORTH);

        javax.swing.event.DocumentListener buscarDL = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
        };

        // columna asignados
        JPanel colAsig = new JPanel(new BorderLayout(0, 4));
        colAsig.setBorder(BorderFactory.createTitledBorder("Asignados"));
        txtBuscarAsig.putClientProperty("JTextField.placeholderText", "Buscar...");
        txtBuscarAsig.getDocument().addDocumentListener(buscarDL);
        colAsig.add(txtBuscarAsig, BorderLayout.NORTH);
        listaAsignados.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        colAsig.add(new JScrollPane(listaAsignados), BorderLayout.CENTER);
        JButton btnQuitar = new JButton("Quitar →");
        btnQuitar.addActionListener(e -> quitarPermiso());
        JPanel surAsig = new JPanel(new FlowLayout(FlowLayout.CENTER));
        surAsig.add(btnQuitar);
        colAsig.add(surAsig, BorderLayout.SOUTH);

        // columna disponibles
        JPanel colDisp = new JPanel(new BorderLayout(0, 4));
        colDisp.setBorder(BorderFactory.createTitledBorder("Disponibles"));
        txtBuscarDisp.putClientProperty("JTextField.placeholderText", "Buscar...");
        txtBuscarDisp.getDocument().addDocumentListener(buscarDL);
        colDisp.add(txtBuscarDisp, BorderLayout.NORTH);
        listaDisponibles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        colDisp.add(new JScrollPane(listaDisponibles), BorderLayout.CENTER);
        JButton btnAsignar = new JButton("← Asignar");
        btnAsignar.addActionListener(e -> asignarPermiso());
        JPanel surDisp = new JPanel(new FlowLayout(FlowLayout.CENTER));
        surDisp.add(btnAsignar);
        colDisp.add(surDisp, BorderLayout.SOUTH);

        JSplitPane splitListas = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, colAsig, colDisp);
        splitListas.setResizeWeight(0.5);
        p.add(splitListas, BorderLayout.CENTER);
        return p;
    }

    // ── Carga ─────────────────────────────────────────────────────────────────

    private void cargarAplicaciones() {
        appsMap.clear();
        appsMap.put("(todas)", null);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT IdAplicacion, NombreAplicacion FROM DB_Seguridad.Aplicacion WITH(NOLOCK) ORDER BY NombreAplicacion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nombre = rs.getString("NombreAplicacion");
                appsMap.put(nombre, rs.getInt("IdAplicacion"));
                cboAplicacion.addItem(nombre);
            }
        } catch (SQLException e) { error("Error cargando aplicaciones", e); }
    }

    private void cargarRoles() {
        modeloRoles.setRowCount(0);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT IdRol, NombreRol FROM DB_Seguridad.Rol WITH(NOLOCK) ORDER BY NombreRol");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                modeloRoles.addRow(new Object[]{rs.getInt("IdRol"), rs.getString("NombreRol")});
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
        idRolSel = (Integer) modeloRoles.getValueAt(modelRow, 0);
        cargarPermisosRol();
    }

    private void filtrar() {
        String fA = txtBuscarAsig.getText().trim().toLowerCase();
        String fD = txtBuscarDisp.getText().trim().toLowerCase();
        modeloAsignados.clear();
        todosAsig.stream().filter(s -> fA.isEmpty() || s.toLowerCase().contains(fA))
                 .forEach(modeloAsignados::addElement);
        modeloDisponibles.clear();
        todosDisp.stream().filter(s -> fD.isEmpty() || s.toLowerCase().contains(fD))
                 .forEach(modeloDisponibles::addElement);
    }

    private void cargarPermisosRol() {
        todosAsig.clear();
        todosDisp.clear();
        modeloAsignados.clear();
        modeloDisponibles.clear();
        if (idRolSel == null) return;

        String appSel = (String) cboAplicacion.getSelectedItem();
        Integer idApp = appsMap.getOrDefault(appSel, null);

        Set<Integer> asignados = new HashSet<>();
        String sqlA = "SELECT PE.IdPermiso, PE.NombrePermiso, AP.NombreAplicacion " +
                      "FROM DB_Seguridad.Permiso PE WITH(NOLOCK) " +
                      "JOIN DB_Seguridad.RolPermiso RP WITH(NOLOCK) ON PE.IdPermiso = RP.IdPermiso " +
                      "JOIN DB_Seguridad.Aplicacion AP WITH(NOLOCK) ON PE.IdAplicacion = AP.IdAplicacion " +
                      "WHERE RP.IdRol = ?" +
                      (idApp != null ? " AND PE.IdAplicacion = " + idApp : "") +
                      " ORDER BY AP.NombreAplicacion, PE.NombrePermiso";
        try (Connection c = Conexion.get(); PreparedStatement ps = c.prepareStatement(sqlA)) {
            ps.setInt(1, idRolSel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("IdPermiso");
                asignados.add(id);
                todosAsig.add(
                        rs.getString("NombrePermiso") + " (" + rs.getString("NombreAplicacion") + ")  [" + id + "]");
            }
        } catch (SQLException e) { error("Error cargando permisos asignados", e); }

        String sqlD = "SELECT PE.IdPermiso, PE.NombrePermiso, AP.NombreAplicacion " +
                      "FROM DB_Seguridad.Permiso PE WITH(NOLOCK) " +
                      "JOIN DB_Seguridad.Aplicacion AP WITH(NOLOCK) ON PE.IdAplicacion = AP.IdAplicacion " +
                      "WHERE 1=1" +
                      (idApp != null ? " AND PE.IdAplicacion = " + idApp : "") +
                      " ORDER BY AP.NombreAplicacion, PE.NombrePermiso";
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(sqlD);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("IdPermiso");
                if (!asignados.contains(id))
                    todosDisp.add(
                            rs.getString("NombrePermiso") + " (" + rs.getString("NombreAplicacion") + ")  [" + id + "]");
            }
        } catch (SQLException e) { error("Error cargando permisos disponibles", e); }
        filtrar();
    }

    // ── Operaciones ───────────────────────────────────────────────────────────

    private void asignarPermiso() {
        String sel = listaDisponibles.getSelectedValue();
        if (sel == null || idRolSel == null) return;
        int idPermiso = extractCode(sel);
        String sql = "IF NOT EXISTS (SELECT 1 FROM DB_Seguridad.RolPermiso WHERE IdRol=? AND IdPermiso=?) " +
                     "INSERT INTO DB_Seguridad.RolPermiso (IdRol, IdPermiso) VALUES (?,?)";
        try (Connection c = Conexion.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idRolSel); ps.setInt(2, idPermiso);
            ps.setInt(3, idRolSel); ps.setInt(4, idPermiso);
            ps.executeUpdate();
            cargarPermisosRol();
        } catch (SQLException e) { error("Error asignando permiso", e); }
    }

    private void quitarPermiso() {
        String sel = listaAsignados.getSelectedValue();
        if (sel == null || idRolSel == null) return;
        if (!confirmar("¿Quitar el permiso del rol?")) return;
        int idPermiso = extractCode(sel);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM DB_Seguridad.RolPermiso WHERE IdRol=? AND IdPermiso=?")) {
            ps.setInt(1, idRolSel); ps.setInt(2, idPermiso);
            ps.executeUpdate();
            cargarPermisosRol();
        } catch (SQLException e) { error("Error quitando permiso", e); }
    }

    private void eliminarRol() {
        if (idRolSel == null) { JOptionPane.showMessageDialog(this, "Selecciona un rol."); return; }
        if (!confirmar("¿Eliminar el rol?\nSe eliminarán sus permisos asignados.")) return;
        try (Connection c = Conexion.get()) {
            c.prepareStatement("DELETE FROM DB_Seguridad.RolPermiso WHERE IdRol=" + idRolSel).executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Rol WHERE IdRol=" + idRolSel).executeUpdate();
            idRolSel = null;
            cargarRoles();
            modeloAsignados.clear();
            modeloDisponibles.clear();
        } catch (SQLException e) { error("Error eliminando rol", e); }
    }

    private void dialogoRol(Integer idRol) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                idRol == null ? "Nuevo Rol" : "Editar Rol", true);
        dlg.setSize(350, 160);
        dlg.setLocationRelativeTo(this);

        JTextField txtNombre = new JTextField(22);
        if (idRol != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT NombreRol FROM DB_Seguridad.Rol WHERE IdRol=?")) {
                ps.setInt(1, idRol);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) txtNombre.setText(rs.getString("NombreRol"));
            } catch (SQLException e) { /* ignore */ }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6, 5, 6, 5);
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0; form.add(new JLabel("Nombre *:"), gc);
        gc.gridx = 1; gc.weightx = 1; form.add(txtNombre, gc);

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> dlg.dispose());
        btnGuardar.addActionListener(e -> {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty()) { JOptionPane.showMessageDialog(dlg, "El nombre es obligatorio."); return; }
            try (Connection c = Conexion.get()) {
                if (idRol == null) {
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO DB_Seguridad.Rol (NombreRol) VALUES (?)");
                    ps.setString(1, nombre); ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement(
                            "UPDATE DB_Seguridad.Rol SET NombreRol=? WHERE IdRol=?");
                    ps.setString(1, nombre); ps.setInt(2, idRol); ps.executeUpdate();
                }
                dlg.dispose(); cargarRoles();
            } catch (SQLException ex) { error("Error guardando rol", ex); }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

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
