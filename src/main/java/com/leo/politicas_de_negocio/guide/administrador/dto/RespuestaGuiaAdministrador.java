package com.leo.politicas_de_negocio.guide.administrador.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leo.politicas_de_negocio.guide.comun.dto.RespuestaGuia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespuestaGuiaAdministrador extends RespuestaGuia {

    @JsonProperty("suggestedResponsible")
    private ResponsableSugeridoGuiaAdministrador responsableSugerido;

    @Builder.Default
    @JsonProperty("suggestedForm")
    private List<CampoFormularioGuiaAdministrador> formularioSugerido = new ArrayList<>();

    @Builder.Default
    @JsonProperty("detectedIssues")
    private List<ProblemaDetectadoGuiaAdministrador> problemasDetectados = new ArrayList<>();

    @Builder.Default
    @JsonProperty("suggestedActions")
    private List<AccionSugeridaGuiaAdministrador> accionesSugeridas = new ArrayList<>();
}
