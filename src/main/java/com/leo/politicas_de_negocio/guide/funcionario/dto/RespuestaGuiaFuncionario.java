package com.leo.politicas_de_negocio.guide.funcionario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leo.politicas_de_negocio.guide.comun.dto.AccionGuia;
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
public class RespuestaGuiaFuncionario extends RespuestaGuia {

    @Builder.Default
    @JsonProperty("formHelp")
    private List<AyudaCampoGuiaFuncionario> ayudaCampos = new ArrayList<>();

    @Builder.Default
    @JsonProperty("missingFields")
    private List<CampoFaltanteGuiaFuncionario> camposFaltantes = new ArrayList<>();

    @JsonProperty("prioritySuggestion")
    private SugerenciaPrioridadGuiaFuncionario sugerenciaPrioridad;

    @JsonProperty("nextStepExplanation")
    private String explicacionSiguientePaso;

    @Builder.Default
    @JsonProperty("suggestedActions")
    private List<AccionGuia> accionesSugeridas = new ArrayList<>();
}
