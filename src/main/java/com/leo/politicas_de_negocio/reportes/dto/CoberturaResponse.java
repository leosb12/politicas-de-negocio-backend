package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;

@Data
public class CoberturaResponse {
    private int coleccionesDetectadas;
    private int coleccionesReportables;
    private int coleccionesNoReportables;
    private int camposDetectados;
    private int camposReportables;
    private int camposSensiblesExcluidos;
    private int aliasesConfigurados;
    private int relacionesConfiguradas;
    private int camposAnidados;
    private int camposArray;
    private List<String> entidadesSinRepository;
    private List<String> entidadesSinRelaciones;
    private List<String> advertencias;
}
