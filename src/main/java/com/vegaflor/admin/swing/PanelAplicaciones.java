package com.vegaflor.admin.swing;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class PanelAplicaciones extends JPanel {

    private DefaultTableModel modelo;
    private JTable tabla;

    public PanelAplicaciones() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Aplicaciones"));

        modelo = new DefaultTableModel(
                new String[]{"Código", "Nombre", "Descripción", "Activo"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabla = new JTable(modelo);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(22);
        tabla.getColumnModel().getColumn(0).setMaxWidth(65);
        tabla.getColumnModel().getColumn(3).setMaxWidth(65);
        add(new JScrollPane(tabla), BorderLayout.CENTER);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        JButton btnNuevo    = new JButton("Nueva");
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
        cargar();
    }

    private void cargar() {
        modelo.setRowCount(0);
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT AppCode, AppName, Description, Active FROM DB_Seguridad.Sec_Application WITH(NOLOCK) ORDER BY AppName");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                modelo.addRow(new Object[]{
                        rs.getInt("AppCode"),
                        rs.getString("AppName"),
                        rs.getString("Description"),
                        rs.getBoolean("Active") ? "Sí" : "No"
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void eliminar() {
        int row = tabla.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Selecciona una aplicación."); return; }
        int code = (Integer) modelo.getValueAt(row, 0);
        if (JOptionPane.showConfirmDialog(this, "¿Eliminar la aplicación?",
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (Connection c = Conexion.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM DB_Seguridad.Sec_Application WHERE AppCode=?")) {
            ps.setInt(1, code); ps.executeUpdate(); cargar();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void dialogo(Integer appCode) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                appCode == null ? "Nueva Aplicación" : "Editar Aplicación", true);
        dlg.setSize(380, 230);
        dlg.setLocationRelativeTo(this);

        JTextField txtNombre = new JTextField(22);
        JTextField txtDesc   = new JTextField(22);
        JCheckBox chkActivo  = new JCheckBox("Activo", true);

        if (appCode != null) {
            try (Connection c = Conexion.get();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT AppName, Description, Active FROM DB_Seguridad.Sec_Application WHERE AppCode=?")) {
                ps.setInt(1, appCode);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    txtNombre.setText(rs.getString("AppName"));
                    txtDesc.setText(rs.getString("Description"));
                    chkActivo.setSelected(rs.getBoolean("Active"));
                }
            } catch (SQLException e) { /* ignore */ }
        }

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(6, 5, 6, 5);

        Object[][] campos = {{"Nombre *:", txtNombre}, {"Descripción:", txtDesc}, {"", chkActivo}};
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
            try (Connection c = Conexion.get()) {
                if (appCode == null) {
                    PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO DB_Seguridad.Sec_Application (AppName, Description, Active) VALUES (?,?,?)");
                    ps.setString(1, nombre); ps.setString(2, txtDesc.getText().trim());
                    ps.setBoolean(3, chkActivo.isSelected()); ps.executeUpdate();
                } else {
                    PreparedStatement ps = c.prepareStatement(
                            "UPDATE DB_Seguridad.Sec_Application SET AppName=?, Description=?, Active=? WHERE AppCode=?");
                    ps.setString(1, nombre); ps.setString(2, txtDesc.getText().trim());
                    ps.setBoolean(3, chkActivo.isSelected()); ps.setInt(4, appCode); ps.executeUpdate();
                }
                dlg.dispose(); cargar();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnGuardar); botones.add(btnCancelar);
        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(botones, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }
}
