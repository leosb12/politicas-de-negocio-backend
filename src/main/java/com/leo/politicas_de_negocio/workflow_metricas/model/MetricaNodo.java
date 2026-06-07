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

@Document(collection = "workflow_metricas_nodos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricaNodo {

    @Id
    private String id;
    
    @Indexed
    private String idInstancia;
    
    @Indexed
    private String idPolitica;
    
    @Indexed
    private String idNodo;
    private String nombreNodo;
    private String tipoNodo;
    
    private String departamento;
    private String funcionarioAsignado;
    
    private String carrilId;
    private String carrilNombre;
    
    private String condicionEntrada;
    private String condicionSalida;
    private String decisionTomada;
    private boolean esRetorno;
    private String nodoAnteriorId;
    private String nodoSiguienteId;
    
    private LocalDateTime fechaEntrada;
    private LocalDateTime fechaSalida;
    
    private Long duracionEnMinutos;
    
    private String estadoNodo;
    private String accionRealizada;
    
    private int documentosRequeridos;
    private int documentosEntregados;
    
    private List<String> observaciones;
}
