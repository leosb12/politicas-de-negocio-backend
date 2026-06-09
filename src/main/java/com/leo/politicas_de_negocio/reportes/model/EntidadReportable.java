package com.leo.politicas_de_negocio.reportes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntidadReportable {
    private String nombreLogico;
    private String coleccionMongo;
    private String claseJava;
    private String descripcion;
    private List<String> aliases;
    private boolean reportable;
    private String motivoNoReportable;
    private List<CampoReportable> campos;
    private List<RelacionReportable> relaciones;
    private List<String> ejemplos;
    private List<String> permisosRequeridos;
    private String fuenteDatos;
    private String tipoFuente; // MONGO, DYNAMO, S3_METADATA, INTERNO
    private int prioridad;
    private String observaciones;
}
