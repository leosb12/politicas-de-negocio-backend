package com.leo.politicas_de_negocio.workflow_metricas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "workflow_metricas_instancias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricaInstancia {

    @Id
    private String id;
    
    @Indexed
    private String idInstancia;
    
    @Indexed
    private String idPolitica;
    private String nombrePolitica;
    private String usuarioSolicitante;
    
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    
    private String estadoActual;
    private String estadoFinal;
    
    private List<String> rutaEjecutada;
    private String rutaEjecutadaCodificada;
    private String rutaEjecutadaLegible;
    private List<String> nodosVisitados;
    private List<String> transicionesEjecutadas;
    private List<String> departamentosParticipantes;
    private List<String> carrilesVisitados;
    private List<String> actividadesVisitadas;
    private List<String> decisionesTomadas;
    private List<String> funcionariosAsignados;
    
    private int cantidadDocumentos;
    private int cantidadObservaciones;
    private int cantidadRechazos;
    private int cantidadReenvios;
    private int cantidadDecisiones;
    private int cantidadForks;
    private int cantidadJoins;
    private int cantidadRetornos;
    private int cantidadReprocesos;
    
    private String nodoMasLento;
    private String carrilMasLento;
    private String actividadMasLenta;
    
    private String prioridad;
    private String resultadoFinal;
    
    private Long duracionTotal; // en minutos
}
