package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PlanConsultaDto {
    private Boolean requiereDatos;
    private String tipoConsulta;
    private List<String> fuentesNecesarias;
    private String entidadPrincipal;
    private String operacion;
    private List<String> camposSolicitados;
    private List<FiltroDto> filtros;
    private List<String> agrupaciones;
    private List<OrdenamientoDto> ordenamiento;
    private Integer limite;
    private Boolean requiereBusquedaSemantica;
    private Boolean requiereAclaracion;
    private String preguntaAclaratoria;
}
