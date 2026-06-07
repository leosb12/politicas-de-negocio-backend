package com.leo.politicas_de_negocio.reportes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reporte_generado")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteGenerado {

    @Id
    private String id;
    
    private String usuarioAdminId;
    private String textoOriginal;
    private String textoTranscrito;
    private String jsonInterpretado;
    private String planConsulta;
    private String respuestaFinal;
    private String entidadPrincipal;
    private String intencionDetectada;
    private String tipoConsulta;
    private String formatoSalida;
    private String visualizacion;
    private LocalDateTime fechaGeneracion;
    private String estado;
    private Integer cantidadResultados;
    private Boolean requiereAclaracion;
    private String preguntaAclaratoria;
    private String archivoGeneradoUrl;
    private Double confianzaModelo;
    private String motorUsado;
}
