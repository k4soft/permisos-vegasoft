package com.vegaflor.admin.swing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Objeto de dominio que mapea la tabla {@code DB_Produccion.IngresoProduccion}
 * tal como existe en el ambiente de PRODUCCIÓN.
 *
 * Nota: las columnas {@code EstadoEnvioWitag} e {@code IdObjetoWitag} no se incluyen aquí.
 */
public class IngresoProduccion {

    private int           idIngresoProduccion; // PK
    private Integer       idTag;
    private LocalDate     fecha;
    private LocalTime     hora;
    private Integer       idPostcosecha;
    private Integer       idProcesoCorte;
    private Integer       idCalidadMipe;
    private Integer       idGradoCalidad;
    private Integer       idGrado;
    private Integer       idFlush;
    private Integer       idCausaCalidad;
    private Integer       idInsumo;
    private int           tallosXRamo;
    private int           ramos;
    private int           tallos;
    private Integer       pedido;
    private Integer       idEmpleado;
    private Integer       idSacarramos;
    private int           idSiembra;
    private Boolean       afectaProyeccion;
    private String        tipoEmpaque;
    private Double        latitude;
    private Double        longitude;
    private LocalDateTime fechaRegistro;
    private String        usuario;
    private LocalDateTime fechaIngreso;
    private LocalTime     horaIngreso;
    private String        usuarioIngreso;

    public int getIdIngresoProduccion()                 { return idIngresoProduccion; }
    public void setIdIngresoProduccion(int v)           { this.idIngresoProduccion = v; }

    public Integer getIdTag()                           { return idTag; }
    public void setIdTag(Integer v)                     { this.idTag = v; }

    public LocalDate getFecha()                         { return fecha; }
    public void setFecha(LocalDate v)                   { this.fecha = v; }

    public LocalTime getHora()                          { return hora; }
    public void setHora(LocalTime v)                    { this.hora = v; }

    public Integer getIdPostcosecha()                   { return idPostcosecha; }
    public void setIdPostcosecha(Integer v)             { this.idPostcosecha = v; }

    public Integer getIdProcesoCorte()                  { return idProcesoCorte; }
    public void setIdProcesoCorte(Integer v)            { this.idProcesoCorte = v; }

    public Integer getIdCalidadMipe()                   { return idCalidadMipe; }
    public void setIdCalidadMipe(Integer v)             { this.idCalidadMipe = v; }

    public Integer getIdGradoCalidad()                  { return idGradoCalidad; }
    public void setIdGradoCalidad(Integer v)            { this.idGradoCalidad = v; }

    public Integer getIdGrado()                         { return idGrado; }
    public void setIdGrado(Integer v)                   { this.idGrado = v; }

    public Integer getIdFlush()                         { return idFlush; }
    public void setIdFlush(Integer v)                   { this.idFlush = v; }

    public Integer getIdCausaCalidad()                  { return idCausaCalidad; }
    public void setIdCausaCalidad(Integer v)            { this.idCausaCalidad = v; }

    public Integer getIdInsumo()                        { return idInsumo; }
    public void setIdInsumo(Integer v)                  { this.idInsumo = v; }

    public int getTallosXRamo()                         { return tallosXRamo; }
    public void setTallosXRamo(int v)                   { this.tallosXRamo = v; }

    public int getRamos()                               { return ramos; }
    public void setRamos(int v)                         { this.ramos = v; }

    public int getTallos()                              { return tallos; }
    public void setTallos(int v)                        { this.tallos = v; }

    public Integer getPedido()                          { return pedido; }
    public void setPedido(Integer v)                    { this.pedido = v; }

    public Integer getIdEmpleado()                      { return idEmpleado; }
    public void setIdEmpleado(Integer v)                { this.idEmpleado = v; }

    public Integer getIdSacarramos()                    { return idSacarramos; }
    public void setIdSacarramos(Integer v)              { this.idSacarramos = v; }

    public int getIdSiembra()                           { return idSiembra; }
    public void setIdSiembra(int v)                     { this.idSiembra = v; }

    public Boolean getAfectaProyeccion()                { return afectaProyeccion; }
    public void setAfectaProyeccion(Boolean v)          { this.afectaProyeccion = v; }

    public String getTipoEmpaque()                      { return tipoEmpaque; }
    public void setTipoEmpaque(String v)                { this.tipoEmpaque = v; }

    public Double getLatitude()                         { return latitude; }
    public void setLatitude(Double v)                   { this.latitude = v; }

    public Double getLongitude()                        { return longitude; }
    public void setLongitude(Double v)                  { this.longitude = v; }

    public LocalDateTime getFechaRegistro()             { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime v)       { this.fechaRegistro = v; }

    public String getUsuario()                          { return usuario; }
    public void setUsuario(String v)                    { this.usuario = v; }

    public LocalDateTime getFechaIngreso()              { return fechaIngreso; }
    public void setFechaIngreso(LocalDateTime v)        { this.fechaIngreso = v; }

    public LocalTime getHoraIngreso()                   { return horaIngreso; }
    public void setHoraIngreso(LocalTime v)             { this.horaIngreso = v; }

    public String getUsuarioIngreso()                   { return usuarioIngreso; }
    public void setUsuarioIngreso(String v)             { this.usuarioIngreso = v; }
}
