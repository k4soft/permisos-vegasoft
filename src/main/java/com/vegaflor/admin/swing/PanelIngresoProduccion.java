package com.vegaflor.admin.swing;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Panel "Ingrepro": consulta nativa de los ingresos de producción en el ambiente
 * de PRODUCCIÓN ({@code DB_Produccion.IngresoProduccion}).
 *
 * En la lista (izquierda) se muestran únicamente IdIngresoProduccion e IdSiembra,
 * pero cada fila construye el objeto completo {@link IngresoProduccion}, cuyo
 * detalle se muestra a la derecha al seleccionar.
 */
public class PanelIngresoProduccion extends JPanel {

    private DefaultTableModel modelo;
    private JTable tabla;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar = new JTextField(18);
    private JLabel lblSeleccion = new JLabel("Seleccionados: 0");

    /** Índices de columna del modelo de la lista. */
    private static final int COL_SEL = 0, COL_ID = 1, COL_TAG = 2, COL_SIEMBRA = 3;

    /** Refresco automático de la lista cada 20 minutos. */
    private static final int REFRESCO_MS = 20 * 60 * 1000;
    private Timer timerRefresco;

    private DefaultTableModel modeloDetalle;

    /** Esta funcionalidad siempre consulta producción, sin importar el entorno seleccionado. */
    private static final String ENTORNO = "prod";

    /** Objetos completos indexados por IdIngresoProduccion. */
    private final Map<Integer, IngresoProduccion> objetos = new LinkedHashMap<>();

    public PanelIngresoProduccion() {
        setLayout(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                panelLista(), panelDetalle());
        split.setDividerLocation(320);
        split.setResizeWeight(0.30);
        add(split, BorderLayout.CENTER);
        cargar();

        // Refresco automático cada 20 minutos.
        timerRefresco = new Timer(REFRESCO_MS, e -> cargar());
        timerRefresco.start();
    }

    // ── Panel izquierdo: lista de ingresos ─────────────────────────────────────

    private JPanel panelLista() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Ingresos de producción"));

        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtro.add(new JLabel("Buscar:"));
        filtro.add(txtBuscar);
        p.add(filtro, BorderLayout.NORTH);

        modelo = new DefaultTableModel(new String[]{"Sel", "IdIngreso", "IdTag", "IdSiembra"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == COL_SEL; }
            @Override public Class<?> getColumnClass(int c) {
                return c == COL_SEL ? Boolean.class : Object.class;
            }
        };
        tabla = new JTable(modelo);
        // Selección de fila para ver el detalle; el marcado masivo va por la columna check.
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(22);
        tabla.getColumnModel().getColumn(COL_SEL).setMaxWidth(40);
        tabla.getColumnModel().getColumn(COL_SEL).setPreferredWidth(40);
        sorter = new TableRowSorter<>(modelo);
        tabla.setRowSorter(sorter);
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = tabla.getSelectedRow();
                if (row >= 0) {
                    int id = (Integer) modelo.getValueAt(tabla.convertRowIndexToModel(row), COL_ID);
                    mostrarDetalle(objetos.get(id));
                }
            }
        });
        // Mantiene actualizado el contador cuando se marca/desmarca un check.
        modelo.addTableModelListener(e -> {
            if (e.getColumn() == COL_SEL || e.getColumn() == javax.swing.event.TableModelEvent.ALL_COLUMNS)
                actualizarContador();
        });
        p.add(new JScrollPane(tabla), BorderLayout.CENTER);

        txtBuscar.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { filtrar(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filtrar(); }
        });

        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        JButton btnRefresh = new JButton("Refrescar");
        btnRefresh.addActionListener(e -> cargar());
        JButton btnTodos = new JButton("Seleccionar todo");
        btnTodos.addActionListener(e -> marcarVisibles(true));
        JButton btnNinguno = new JButton("Limpiar");
        btnNinguno.addActionListener(e -> marcarVisibles(false));
        JButton btnEnviar = new JButton("Enviar");
        btnEnviar.addActionListener(e -> enviarSeleccionados());
        bar.add(btnRefresh);
        bar.addSeparator();
        bar.add(btnTodos);
        bar.add(btnNinguno);
        bar.add(btnEnviar);
        bar.add(Box.createHorizontalGlue());
        bar.add(lblSeleccion);
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    /** Marca o desmarca el check de todas las filas actualmente visibles (respeta el filtro). */
    private void marcarVisibles(boolean valor) {
        for (int viewRow = 0; viewRow < tabla.getRowCount(); viewRow++) {
            int modelRow = tabla.convertRowIndexToModel(viewRow);
            modelo.setValueAt(valor, modelRow, COL_SEL);
        }
        actualizarContador();
    }

    private void actualizarContador() {
        lblSeleccion.setText("Seleccionados: " + idsSeleccionados().size());
    }

    /** Ids de los ingresos marcados con el check (selección uno a uno o masiva). */
    public java.util.List<Integer> idsSeleccionados() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int r = 0; r < modelo.getRowCount(); r++) {
            if (Boolean.TRUE.equals(modelo.getValueAt(r, COL_SEL)))
                ids.add((Integer) modelo.getValueAt(r, COL_ID));
        }
        return ids;
    }

    /**
     * Envía los ingresos seleccionados al web service de producción (RFID),
     * simulando lo que hace la app móvil VegaApp. Sirve tanto para un solo ingreso
     * (un check marcado, o la fila resaltada si no hay ninguno) como para un envío
     * masivo (varios checks). La autenticación y el POST se hacen en segundo plano.
     */
    private void enviarSeleccionados() {
        java.util.List<Integer> ids = idsSeleccionados();
        if (ids.isEmpty()) {
            int row = tabla.getSelectedRow();
            if (row >= 0)
                ids = java.util.List.of((Integer) modelo.getValueAt(tabla.convertRowIndexToModel(row), COL_ID));
        }
        if (ids.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Marca al menos un ingreso (check) para enviar.",
                    "Envío", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
                "¿Enviar " + ids.size() + " ingreso(s) al web service?",
                "Confirmar envío", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;

        java.util.List<IngresoProduccion> seleccionados = new java.util.ArrayList<>();
        for (Integer id : ids) {
            IngresoProduccion o = objetos.get(id);
            if (o != null) seleccionados.add(o);
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<java.util.List<EnvioIngreproService.RespuestaProduccion>, Void>() {
            @Override
            protected java.util.List<EnvioIngreproService.RespuestaProduccion> doInBackground() throws Exception {
                return EnvioIngreproService.enviar(seleccionados);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    java.util.List<EnvioIngreproService.RespuestaProduccion> respuestas = get();
                    mostrarResultadoEnvio(respuestas);
                    cargar(); // refresca: los enviados con éxito ya no estarán "no confirmados"
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    error("Error enviando ingresos al web service", cause);
                }
            }
        }.execute();
    }

    private void mostrarResultadoEnvio(java.util.List<EnvioIngreproService.RespuestaProduccion> respuestas) {
        int okCount = 0, actualizados = 0;
        StringBuilder problemas = new StringBuilder();
        for (EnvioIngreproService.RespuestaProduccion r : respuestas) {
            boolean conError = r.mensajeError != null && !r.mensajeError.isBlank();
            if (conError) {
                problemas.append("• ").append(r.idIngresoMovil).append(": ").append(r.mensajeError).append('\n');
            } else {
                okCount++;
                if (r.idRealActualizado) actualizados++;
                else if (r.errorActualizacion != null)
                    problemas.append("• ").append(r.idIngresoMovil)
                             .append(" (IdReal): ").append(r.errorActualizacion).append('\n');
            }
        }
        StringBuilder msg = new StringBuilder();
        msg.append("Enviados correctamente: ").append(okCount).append(" de ").append(respuestas.size()).append('\n');
        msg.append("IdIngresoProduccionReal actualizados: ").append(actualizados).append('\n');
        if (problemas.length() > 0) msg.append("\nProblemas:\n").append(problemas);
        JOptionPane.showMessageDialog(this, msg.toString(), "Resultado del envío",
                problemas.length() > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private void filtrar() {
        String texto = txtBuscar.getText().trim();
        sorter.setRowFilter(texto.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto)));
    }

    // ── Panel derecho: detalle del objeto completo ─────────────────────────────

    private JPanel panelDetalle() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder("Detalle del ingreso seleccionado"));

        modeloDetalle = new DefaultTableModel(new String[]{"Campo", "Valor"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tablaDetalle = new JTable(modeloDetalle);
        tablaDetalle.setRowHeight(22);
        tablaDetalle.getColumnModel().getColumn(0).setPreferredWidth(160);
        tablaDetalle.getColumnModel().getColumn(0).setMaxWidth(220);
        p.add(new JScrollPane(tablaDetalle), BorderLayout.CENTER);
        return p;
    }

    private void mostrarDetalle(IngresoProduccion o) {
        modeloDetalle.setRowCount(0);
        if (o == null) return;
        fila("IdIngresoProduccion", o.getIdIngresoProduccion());
        fila("IdTag",               o.getIdTag());
        fila("Fecha",               o.getFecha());
        fila("Hora",                o.getHora());
        fila("IdPostcosecha",       o.getIdPostcosecha());
        fila("IdProcesoCorte",      o.getIdProcesoCorte());
        fila("IdCalidadMipe",       o.getIdCalidadMipe());
        fila("IdGradoCalidad",      o.getIdGradoCalidad());
        fila("IdGrado",             o.getIdGrado());
        fila("IdFlush",             o.getIdFlush());
        fila("IdCausaCalidad",      o.getIdCausaCalidad());
        fila("IdInsumo",            o.getIdInsumo());
        fila("TallosXRamo",         o.getTallosXRamo());
        fila("Ramos",               o.getRamos());
        fila("Tallos",              o.getTallos());
        fila("Pedido",              o.getPedido());
        fila("IdEmpleado",          o.getIdEmpleado());
        fila("IdSacarramos",        o.getIdSacarramos());
        fila("IdSiembra",           o.getIdSiembra());
        fila("AfectaProyeccion",    o.getAfectaProyeccion());
        fila("TipoEmpaque",         o.getTipoEmpaque());
        fila("Latitude",            o.getLatitude());
        fila("Longitude",           o.getLongitude());
        fila("FechaRegistro",       o.getFechaRegistro());
        fila("Usuario",             o.getUsuario());
        fila("FechaIngreso",        o.getFechaIngreso());
        fila("HoraIngreso",         o.getHoraIngreso());
        fila("UsuarioIngreso",      o.getUsuarioIngreso());
    }

    private void fila(String campo, Object valor) {
        modeloDetalle.addRow(new Object[]{campo, valor == null ? "" : String.valueOf(valor)});
    }

    // ── Consulta nativa ────────────────────────────────────────────────────────

    /** Cantidad de ingresos a traer de producción (los más recientes). */
    private static final int ULTIMOS = 40;

    private static final String SQL =
            "SELECT TOP (" + ULTIMOS + ") IdIngresoProduccion, IdTag, Fecha, Hora, IdPostcosecha, IdProcesoCorte, " +
            "IdCalidadMipe, IdGradoCalidad, IdGrado, IdFlush, IdCausaCalidad, IdInsumo, " +
            "TallosXRamo, Ramos, Tallos, Pedido, IdEmpleado, IdSacarramos, IdSiembra, " +
            "AfectaProyeccion, TipoEmpaque, Latitude, Longitude, FechaRegistro, Usuario, " +
            "FechaIngreso, HoraIngreso, UsuarioIngreso " +
            "FROM DB_Produccion.IngresoProduccion WITH(NOLOCK) " +
            // "No confirmados": el ingreso aún no tiene FechaIngreso registrada.
            // Solo los que tengan IdTag en el rango 1..3000 (BETWEEN excluye los NULL).
            // Se traen solo los últimos 40 (por PK, índice clustered DESC) — consulta acotada.
            "WHERE FechaIngreso IS NULL " +
            "AND IdTag BETWEEN 1 AND 3000 " +
            "ORDER BY IdIngresoProduccion DESC";

    /**
     * Carga asíncrona: la consulta a producción se ejecuta en un hilo aparte
     * (SwingWorker) para no bloquear el EDT — de lo contrario la ventana no
     * alcanza a pintarse mientras responde la conexión remota.
     */
    private void cargar() {
        // Conserva los checks marcados para volver a aplicarlos tras recargar
        // (el refresco automático no debe perder la selección del usuario).
        java.util.Set<Integer> marcadosPrevios = new java.util.HashSet<>(idsSeleccionados());

        modelo.setRowCount(0);
        modeloDetalle.setRowCount(0);
        objetos.clear();
        actualizarContador();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<java.util.List<IngresoProduccion>, Void>() {
            @Override
            protected java.util.List<IngresoProduccion> doInBackground() throws Exception {
                // 1) Últimos 40 ingresos no confirmados de producción (consulta acotada por TOP).
                java.util.List<IngresoProduccion> ultimos = new java.util.ArrayList<>();
                try (Connection c = Conexion.get(ENTORNO);
                     PreparedStatement ps = c.prepareStatement(SQL);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ultimos.add(mapear(rs));
                }
                // 2) De esos 40, descarta los que ya se enviaron (consulta acotada a esos ids en la BD local).
                java.util.Set<Integer> ids = new java.util.HashSet<>();
                for (IngresoProduccion o : ultimos) ids.add(o.getIdIngresoProduccion());
                java.util.Set<Integer> yaEnviados = EnvioIngreproService.idsYaEnviados(ids);

                java.util.List<IngresoProduccion> lista = new java.util.ArrayList<>();
                for (IngresoProduccion o : ultimos)
                    if (!yaEnviados.contains(o.getIdIngresoProduccion())) lista.add(o);
                return lista;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    for (IngresoProduccion o : get()) {
                        objetos.put(o.getIdIngresoProduccion(), o);
                        boolean marcado = marcadosPrevios.contains(o.getIdIngresoProduccion());
                        modelo.addRow(new Object[]{marcado, o.getIdIngresoProduccion(), o.getIdTag(), o.getIdSiembra()});
                    }
                    actualizarContador();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    error("Error cargando ingresos de producción", cause);
                }
            }
        }.execute();
    }

    private IngresoProduccion mapear(ResultSet rs) throws SQLException {
        IngresoProduccion o = new IngresoProduccion();
        o.setIdIngresoProduccion(rs.getInt("IdIngresoProduccion"));
        o.setIdTag(getInteger(rs, "IdTag"));
        o.setFecha(rs.getObject("Fecha", java.time.LocalDate.class));
        o.setHora(rs.getObject("Hora", java.time.LocalTime.class));
        o.setIdPostcosecha(getInteger(rs, "IdPostcosecha"));
        o.setIdProcesoCorte(getInteger(rs, "IdProcesoCorte"));
        o.setIdCalidadMipe(getInteger(rs, "IdCalidadMipe"));
        o.setIdGradoCalidad(getInteger(rs, "IdGradoCalidad"));
        o.setIdGrado(getInteger(rs, "IdGrado"));
        o.setIdFlush(getInteger(rs, "IdFlush"));
        o.setIdCausaCalidad(getInteger(rs, "IdCausaCalidad"));
        o.setIdInsumo(getInteger(rs, "IdInsumo"));
        o.setTallosXRamo(rs.getInt("TallosXRamo"));
        o.setRamos(rs.getInt("Ramos"));
        o.setTallos(rs.getInt("Tallos"));
        o.setPedido(getInteger(rs, "Pedido"));
        o.setIdEmpleado(getInteger(rs, "IdEmpleado"));
        o.setIdSacarramos(getInteger(rs, "IdSacarramos"));
        o.setIdSiembra(rs.getInt("IdSiembra"));
        o.setAfectaProyeccion(getBoolean(rs, "AfectaProyeccion"));
        o.setTipoEmpaque(rs.getString("TipoEmpaque"));
        o.setLatitude(getDouble(rs, "Latitude"));
        o.setLongitude(getDouble(rs, "Longitude"));
        o.setFechaRegistro(rs.getObject("FechaRegistro", java.time.LocalDateTime.class));
        o.setUsuario(rs.getString("Usuario"));
        o.setFechaIngreso(rs.getObject("FechaIngreso", java.time.LocalDateTime.class));
        o.setHoraIngreso(rs.getObject("HoraIngreso", java.time.LocalTime.class));
        o.setUsuarioIngreso(rs.getString("UsuarioIngreso"));
        return o;
    }

    private static Integer getInteger(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static Double getDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Boolean getBoolean(ResultSet rs, String col) throws SQLException {
        boolean v = rs.getBoolean(col);
        return rs.wasNull() ? null : v;
    }

    private void error(String msg, Throwable e) {
        JOptionPane.showMessageDialog(this, msg + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
}
