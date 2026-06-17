package com.vegaflor.admin.swing;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simula el envío de ingresos de producción que hace la app móvil VegaApp
 * (RFIDIngresoProduccionModel → RegistroRFIDProduccionServiceImpl): autentica
 * contra Cognito (SRP) y hace POST de la lista de ingresos al endpoint RFID.
 *
 * Ambiente de PRUEBAS (token e endpoint), tal como se solicitó.
 */
public final class EnvioIngreproService {

    /**
     * Endpoint RFID (local, ambiente de desarrollo en este equipo):
     * localhost:8083 + "api-produccion/" + "v2/registro-producciones/rfid".
     * (En la app móvil: EndPoint.ApiProduccion.Exportacion.URL_REGISTRO_RFID_PRODUCCIONES)
     */
    private static final String URL_REGISTRO_RFID =
            "http://localhost:8083/api-produccion/v2/registro-producciones/rfid";

    private static final int RFID_ON          = 1; // CON_INGRESO_RFID
    private static final int TIPO_INGRESO_CORTE = 1;
    private static final int ONLINE            = 1;

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private EnvioIngreproService() {}

    /**
     * Dado un conjunto de ids de producción (candidatos), devuelve cuáles YA fueron
     * enviados, según la BD de desarrollo local: son los que figuran como
     * {@code IdIngresoProduccionReal} (cada uno apunta al IdIngresoProduccion original
     * de producción). Consulta acotada a los candidatos (no escanea toda la tabla).
     * El escaneo del panel la usa para no volver a mostrar los enviados.
     */
    public static Set<Integer> idsYaEnviados(Set<Integer> candidatos) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        if (candidatos == null || candidatos.isEmpty()) return ids;

        StringBuilder marcadores = new StringBuilder();
        for (int i = 0; i < candidatos.size(); i++) marcadores.append(i == 0 ? "?" : ",?");
        String sql = "SELECT IdIngresoProduccionReal FROM DB_Produccion.IngresoProduccion "
                   + "WHERE IdIngresoProduccionReal IN (" + marcadores + ")";

        try (Connection c = Conexion.get("local");
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            for (Integer id : candidatos) ps.setInt(idx++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /**
     * Autentica, envía los ingresos al web service y, por cada respuesta sin error,
     * actualiza en producción el {@code IdIngresoProduccionReal} del registro nuevo
     * (el {@code idIngresoProduccion} que devuelve el servicio) con el id que se ve
     * en pantalla / se envió ({@code idIngresoMovil}). Devuelve las respuestas con
     * el estado de la actualización anotado.
     */
    public static List<RespuestaProduccion> enviar(List<IngresoProduccion> ingresos) throws Exception {
        String token = CognitoAuth.getIdToken();

        List<IngreproDTO> cuerpo = new ArrayList<>(ingresos.size());
        for (IngresoProduccion o : ingresos) cuerpo.add(mapear(o));
        String json = GSON.toJson(cuerpo);
        System.out.println("[Ingrepro] Body enviado al WS: " + json); // log temporal de diagnóstico

        // MAX de la BD local ANTES de enviar: los registros con IdIngresoProduccion mayor
        // serán los que inserte el web service (su id no viene en la respuesta).
        int maxIdAntes = maxIdLocal();

        HttpRequest req = HttpRequest.newBuilder(URI.create(URL_REGISTRO_RFID))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
            throw new IllegalStateException("Web service RFID -> HTTP " + resp.statusCode() + ": " + resp.body());

        CustomResponse<List<RespuestaProduccion>> body = GSON.fromJson(
                resp.body(),
                new TypeToken<CustomResponse<List<RespuestaProduccion>>>() {}.getType());

        List<RespuestaProduccion> respuestas =
                (body != null && body.valor != null) ? body.valor : new ArrayList<>();
        actualizarIdsReales(ingresos, respuestas, maxIdAntes);
        return respuestas;
    }

    /**
     * UPDATE en la BD de DESARROLLO LOCAL (donde el web service local insertó el
     * registro): a cada registro insertado le fija {@code IdIngresoProduccionReal} con
     * el id que se ve en pantalla / se envió. Solo para respuestas exitosas.
     *
     * Como la respuesta NO trae el IdIngresoProduccion del registro nuevo, se identifica
     * por los ids creados después del envío (IdIngresoProduccion > maxIdAntes), en orden,
     * que corresponden a los envíos exitosos en el mismo orden. (Si el WS llegara a
     * devolver {@code idIngresoProduccion}, se usa ese directamente.)
     */
    private static void actualizarIdsReales(List<IngresoProduccion> ingresos,
                                            List<RespuestaProduccion> respuestas, int maxIdAntes) {
        String upd = "UPDATE DB_Produccion.IngresoProduccion "
                   + "SET IdIngresoProduccionReal = ? WHERE IdIngresoProduccion = ?";
        try (Connection c = Conexion.get("local")) {
            List<Integer> nuevos = idsNuevos(c, maxIdAntes); // registros insertados por el WS, en orden
            int k = 0;
            try (PreparedStatement ps = c.prepareStatement(upd)) {
                for (int i = 0; i < respuestas.size(); i++) {
                    RespuestaProduccion r = respuestas.get(i);
                    if (r.mensajeError != null && !r.mensajeError.isBlank()) continue;

                    // Valor a guardar = id en pantalla (el que enviamos como idIngresoMovil).
                    Integer idReal = (i < ingresos.size())
                            ? ingresos.get(i).getIdIngresoProduccion()
                            : r.idIngresoMovil;

                    // Registro insertado: el que devuelva el WS o, si no viene, el siguiente nuevo del local.
                    Integer idInsertado = r.idIngresoProduccion;
                    if (idInsertado == null) {
                        idInsertado = (k < nuevos.size()) ? nuevos.get(k) : null;
                        k++;
                    }

                    if (idReal == null || idInsertado == null) {
                        r.errorActualizacion = "No se pudo determinar el registro insertado en local";
                        continue;
                    }
                    try {
                        ps.setInt(1, idReal);
                        ps.setInt(2, idInsertado);
                        int filas = ps.executeUpdate();
                        r.idRealActualizado = filas > 0;
                        r.idIngresoProduccion = idInsertado; // deja registrado el id real usado
                        if (filas == 0)
                            r.errorActualizacion = "No se encontró IdIngresoProduccion " + idInsertado + " en local";
                    } catch (SQLException e) {
                        r.errorActualizacion = e.getMessage();
                    }
                }
            }
        } catch (SQLException e) {
            for (RespuestaProduccion r : respuestas) {
                if (!r.idRealActualizado && r.errorActualizacion == null)
                    r.errorActualizacion = "Error actualizando en la BD local: " + e.getMessage();
            }
        }
    }

    /** MAX(IdIngresoProduccion) en la BD local; 0 si la tabla está vacía. */
    private static int maxIdLocal() throws SQLException {
        String sql = "SELECT ISNULL(MAX(IdIngresoProduccion), 0) FROM DB_Produccion.IngresoProduccion";
        try (Connection c = Conexion.get("local");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Ids creados en local después del envío (IdIngresoProduccion > maxIdAntes), en orden ascendente. */
    private static List<Integer> idsNuevos(Connection c, int maxIdAntes) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT IdIngresoProduccion FROM DB_Produccion.IngresoProduccion "
                   + "WHERE IdIngresoProduccion > ? ORDER BY IdIngresoProduccion ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, maxIdAntes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /** Mapea el registro leído de producción al DTO que espera el web service (igual que el móvil). */
    private static IngreproDTO mapear(IngresoProduccion o) {
        IngreproDTO d = new IngreproDTO();
        d.idIngresoMovil       = o.getIdIngresoProduccion();
        d.fecha                = o.getFecha() != null ? o.getFecha().toString() : null;        // yyyy-MM-dd
        d.hora                 = o.getHora()  != null ? o.getHora().toString()  : null;        // HH:mm:ss
        d.idPostcosecha        = o.getIdPostcosecha();
        d.idProcesoCorte       = o.getIdProcesoCorte();
        d.idCalidadMipe        = o.getIdCalidadMipe();
        d.idFlush              = o.getIdFlush();
        d.idGradoCalidad       = o.getIdGradoCalidad();
        d.idInsumo             = o.getIdInsumo();
        d.tallosXRamo          = o.getTallosXRamo();
        d.ramos                = o.getRamos();
        d.tallos               = o.getTallos();
        d.pedido               = o.getPedido() != null ? String.valueOf(o.getPedido()) : null;
        d.idEmpleadoSacarramos = o.getIdSacarramos();
        d.idEmpleadoCortador   = o.getIdEmpleado();
        // El backend anula cortador/sacarramos si exigeEmpleado==0, y si ==1 exige que
        // ambos sean positivos. Por eso enviamos 1 solo cuando el origen trae ambos.
        boolean cortadorOk   = o.getIdEmpleado()  != null && o.getIdEmpleado()  > 0;
        boolean sacarramosOk = o.getIdSacarramos() != null && o.getIdSacarramos() > 0;
        d.exigeEmpleado        = (cortadorOk && sacarramosOk) ? 1 : 0;
        d.idSiembra            = o.getIdSiembra();
        d.idGrado              = o.getIdGrado();
        d.idTag                = o.getIdTag();
        d.tipoEmpaque          = o.getTipoEmpaque();
        d.usuario              = o.getUsuario();
        d.latitude             = o.getLatitude()  != null ? o.getLatitude()  : 0d;
        d.longitude            = o.getLongitude() != null ? o.getLongitude() : 0d;
        d.rfid                 = RFID_ON;
        d.tipoIngreso          = TIPO_INGRESO_CORTE;
        d.online               = ONLINE;
        d.sincronizado         = false;
        return d;
    }

    // ── DTOs (nombres de campo idénticos a los de la entidad Ingrepro del móvil) ──

    /** Cuerpo JSON enviado por cada ingreso. */
    static class IngreproDTO {
        Integer idIngresoMovil;
        String  fecha;
        String  hora;
        Integer idPostcosecha;
        Integer idProcesoCorte;
        Integer idCalidadMipe;
        Integer idFlush;
        Integer idGradoCalidad;
        Integer idInsumo;
        int     tallosXRamo;
        int     ramos;
        int     tallos;
        String  pedido;
        int     exigeEmpleado;
        Integer idEmpleadoSacarramos;
        Integer idEmpleadoCortador;
        Integer idSiembra;
        Integer idGrado;
        String  nombreVariedad;
        String  nombreBloque;
        String  nombreCapuchon;
        String  tipoEmpaque;
        String  usuario;
        double  latitude;
        double  longitude;
        Integer idTag;
        Integer semanas;
        Integer idFinca;
        String  nombreFinca;
        String  mensaje;
        int     rfid;
        Integer tipoIngreso;
        boolean sincronizado;
        int     online;
    }

    /** Envoltura de respuesta: {"valor":[ ... ]}. */
    static class CustomResponse<T> {
        T valor;
    }

    /** Respuesta del servicio por cada ingreso enviado. */
    static class RespuestaProduccion {
        Integer idIngresoMovil;        // eco del id enviado (= id en pantalla / IdIngresoProduccionReal)
        Integer idIngresoProduccion;   // id NUEVO del registro insertado por el web service
        Integer idTag;
        String  fechaIngresoProduccion;
        String  horaIngresoProduccion;
        String  mensajeError;

        // Estado de la actualización local de IdIngresoProduccionReal (no viene del servicio).
        transient boolean idRealActualizado;
        transient String  errorActualizacion;
    }
}
