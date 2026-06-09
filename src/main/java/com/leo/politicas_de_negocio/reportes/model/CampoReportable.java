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
public class CampoReportable {
    private String nombreLogico;
    private String campoMongo;
    private String rutaMongo;
    private String tipoDato; // string, number, boolean, date, object, array, objectId
    private String descripcion;
    private List<String> aliases;
    private boolean mostrable;
    private boolean filtrable;
    private boolean agrupable;
    private boolean ordenable;
    private boolean metrico;
    private boolean sensible;
    private boolean reportable;
    private String motivoNoReportable;
    private List<String> operadoresPermitidos;
    private List<String> valoresPermitidos;
    private List<String> normalizaciones;
    private boolean requiereLookup;
    private String entidadRelacionada;
    private String campoRelacionLocal;
    private String campoRelacionDestino;
    private boolean requiereObjectIdConversion;
    private boolean esArray;
    private boolean requiereUnwind;
    private String ejemploUso;
}
