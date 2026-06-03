package com.vegaflor.admin.swing;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class PanelPermisos extends JPanel {

    // ── Aplicacion ────────────────────────────────────────────────────────────
    private DefaultTableModel modeloApps;
    private JTable tablaApps;
    private Integer idAppSel;

    // ── Permiso ───────────────────────────────────────────────────────────────
    private DefaultTableModel modeloPermisos;
    private JTable tablaPermisos;

    public PanelPermisos() {
        setLayout(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                panelAplicaciones(), panelPermisos());
        split.setDividerLocation(300);
        split.setResizeWeight(0.28);
        add(split, BorderLayout.CENTER);
        cargarAplicaciones();
    }

    // ── Panel izquierdo: Aplicacion ───────────────────────────────────────────

    private JPanel panelAplicaciones() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Aplicaciones"));

        modeloApps = new DefaultTableModel(new String[]{"Id", "Nombre"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaApps = new JTable(modeloApps);
        tablaApps.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaApps.setRowHeight(22);
        tablaApps.getColumnModel().getColumn(0).setMaxWidth(45);
        tablaApps.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tablaApps.getSelectedRow();
                if (row >= 0) {
                    idAppSel = (Integer) modeloApps.getValueAt(row, 0);
                    cargarPermisos();
                }
            }
        });
        p.add(new JScrollPane(tablaApps), BorderLayout.CENTER);

        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nueva");
        JButton btnEditar   = new JButton("Editar");
        JButton btnEliminar = new JButton("Eliminar");
        btnNuevo.addActionListener(e -> dialogoApp(null));
        btnEditar.addActionListener(e -> { if (idAppSel != null) dialogoApp(idAppSel); });
        btnEliminar.addActionListener(e -> eliminarApp());
        bar.add(btnNuevo); bar.add(btnEditar); bar.add(btnEliminar);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    // ── Panel derecho: Permiso ────────────────────────────────────────────────

    private JPanel panelPermisos() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Permisos de la aplicación seleccionada"));

        modeloPermisos = new DefaultTableModel(
                new String[]{"Id", "Nombre", "Identificador"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tablaPermisos = new JTable(modeloPermisos);
        tablaPermisos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaPermisos.setRowHeight(22);
        tablaPermisos.getColumnModel().getColumn(0).setMaxWidth(45);
        tablaPermisos.getColumnModel().getColumn(2).setMaxWidth(100);
        p.add(new JScrollPane(tablaPermisos), BorderLayout.CENTER);

        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nuevo");
        JButton btnEditar   = new JButton("Editar");
        JButton btnEliminar = new JButton("Eliminar");
        JButton btnRefresh  = new JButton("Refrescar");
        btnNuevo.addActionListener(e -> {
            if (idAppSel == null) { JOptionPane.showMessageDialog(this, "Selecciona una aplicación primero."); return; }
            dialogoPermiso(null);
        });
        btnEditar.addActionListener(e -> {
            int row = tablaPermisos.getSelectedRow();
            if (row >= 0) dialogoPermiso((Integer) modeloPermisos.getValueAt(row, 0));
        });
        btnEliminar.addActionListener(e -> eliminarPermiso());
        btnRefresh.addActionListener(e -> { cargarAplicaciones(); cargarPermisos(); });
        bar.add(btnNuevo); bar.add(btnEditar); bar.add(btnEliminar);
        bar.addSeparator(); bar.add(btnRefresh);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    // ── Carga ─────────────────────────────────────────────────────────────────

    private void cargarAplicaciones() {
        modeloApps.setRowCount(0);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT IdAplicacion, NombreAplicacion FROM DB_Seguridad.Aplicacion WITH(NOLOCK) ORDER BY NombreAplicacion");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                modeloApps.addRow(new Object[]{rs.getInt("IdAplicacion"), rs.getString("NombreAplicacion")});
        } catch (SQLException e) { error("Error cargando aplicaciones", e); }
    }

    private void cargarPermisos() {
        modeloPermisos.setRowCount(0);
        if (idAppSel == null) return;
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT IdPermiso, NombrePermiso, Identificador FROM DB_Seguridad.Permiso WITH(NOLOCK) " +
                     "WHERE IdAplicacion=? ORDER BY NombrePermiso")) {
            ps.setInt(1, idAppSel);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                modeloPermisos.addRow(new Object[]{
                        rs.getInt("IdPermiso"),
                        rs.getString("NombrePermiso"),
                        rs.getInt("Identificador")
                });
        } catch (SQLException e) { error("Error cargando permisos", e); }
    }

    // ── Operaciones Aplicacion ────────────────────────────────────────────────

    private void eliminarApp() {
        if (idAppSel == null) { JOptionPane.showMessageDialog(this, "Selecciona una aplicación."); return; }
        if (!confirmar("¿Eliminar la aplicación?\nSe eliminarán también sus permisos.")) return;
        try (Connection c = Conexion.get()) {
            c.prepareStatement("DELETE FROM DB_Seguridad.RolPermiso WHERE IdPermiso IN " +
                               "(SELECT IdPermiso FROM DB_Seguridad.Permiso WHERE IdAplicacion=" + idAppSel + ")").executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Permiso WHERE IdAplicacion=" + idAppSel).executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Aplicacion WHERE IdAplicacion=" + idAppSel).executeUpdate();
            idAppSel = null;
            cargarAplicaciones();
            modeloPermisos.setRowCount(0);
        } catch (SQLException e) { error("Error eliminando aplicación", e); }
    }

    private void dialogoApp(Integer idApp) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                idApp == null ? "Nueva Aplicación" : "Editar Aplicación", true);
        dlg.setSize(350, 150);
        dlg.setLocationRelativeTo(this);

        JTextField txtNombre = new JTextField(22);
        if (idApp != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT NombreAplicacion FROM DB_Seguridad.Aplicacion WHERE IdAplicacion=?")) {
                ps.setInt(1, idApp);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) txtNombre.setText(rs.getString("NombreAplicacion"));
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
                if (idApp == null) {
                    c.prepareStatement("INSERT INTO DB_Seguridad.Aplicacion (NombreAplicacion) VALUES ('" + nombre + "')").executeUpdate();
                } else {
                    c.prepareStatement("UPDATE DB_Seguridad.Aplicacion SET NombreAplicacion='" + nombre + "' WHERE IdAplicacion=" + idApp).executeUpdate();
                }
                dlg.dispose(); cargarAplicaciones();
            } catch (SQLException ex) { error("Error guardando aplicación", ex); }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Operaciones Permiso ───────────────────────────────────────────────────

    private void eliminarPermiso() {
        int row = tablaPermisos.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Selecciona un permiso."); return; }
        int idPerm = (Integer) modeloPermisos.getValueAt(row, 0);
        if (!confirmar("¿Eliminar el permiso?\nSe quitará de todos los roles que lo tengan.")) return;
        try (Connection c = Conexion.get()) {
            c.prepareStatement("DELETE FROM DB_Seguridad.RolPermiso WHERE IdPermiso=" + idPerm).executeUpdate();
            c.prepareStatement("DELETE FROM DB_Seguridad.Permiso WHERE IdPermiso=" + idPerm).executeUpdate();
            cargarPermisos();
        } catch (SQLException e) { error("Error eliminando permiso", e); }
    }

    private void dialogoPermiso(Integer idPermiso) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                idPermiso == null ? "Nuevo Permiso" : "Editar Permiso", true);
        dlg.setSize(380, 210);
        dlg.setLocationRelativeTo(this);

        JTextField txtNombre      = new JTextField(22);
        JTextField txtIdentificador = new JTextField(22);

        if (idPermiso != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT NombrePermiso, Identificador FROM DB_Seguridad.Permiso WHERE IdPermiso=?")) {
                ps.setInt(1, idPermiso);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    txtNombre.setText(rs.getString("NombrePermiso"));
                    txtIdentificador.setText(String.valueOf(rs.getInt("Identificador")));
                }
            } catch (SQLException e) { /* ignore */ }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(5, 5, 5, 5);

        Object[][] campos = {{"Nombre *:", txtNombre}, {"Identificador *:", txtIdentificador}};
        for (int i = 0; i < campos.length; i++) {
            gc.gridx = 0; gc.gridy = i; gc.weightx = 0; form.add(new JLabel(campos[i][0].toString()), gc);
            gc.gridx = 1; gc.weightx = 1; form.add((Component) campos[i][1], gc);
        }

        JButton btnGuardar  = new JButton("Guardar");
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> dlg.dispose());
        btnGuardar.addActionListener(e -> {
            String nombre = txtNombre.getText().trim();
            String identStr = txtIdentificador.getText().trim();
            if (nombre.isEmpty() || identStr.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Nombre e Identificador son obligatorios.");
                return;
            }
            try {
                int ident = Integer.parseInt(identStr);
                try (Connection c = Conexion.get()) {
                    if (idPermiso == null) {
                        PreparedStatement ps = c.prepareStatement(
                                "INSERT INTO DB_Seguridad.Permiso (NombrePermiso, Identificador, IdAplicacion) VALUES (?,?,?)");
                        ps.setString(1, nombre); ps.setInt(2, ident); ps.setInt(3, idAppSel);
                        ps.executeUpdate();
                    } else {
                        PreparedStatement ps = c.prepareStatement(
                                "UPDATE DB_Seguridad.Permiso SET NombrePermiso=?, Identificador=? WHERE IdPermiso=?");
                        ps.setString(1, nombre); ps.setInt(2, ident); ps.setInt(3, idPermiso);
                        ps.executeUpdate();
                    }
                    dlg.dispose(); cargarPermisos();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dlg, "El Identificador debe ser un número entero.");
            } catch (SQLException ex) { error("Error guardando permiso", ex); }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private boolean confirmar(String msg) {
        return JOptionPane.showConfirmDialog(this, msg, "Confirmar",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void error(String msg, Exception e) {
        JOptionPane.showMessageDialog(this, msg + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
