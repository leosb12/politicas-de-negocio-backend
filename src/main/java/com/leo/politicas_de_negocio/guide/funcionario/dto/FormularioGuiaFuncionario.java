package com.leo.politicas_de_negocio.guide.funcionario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormularioGuiaFuncionario {

    @Builder.Default
    @JsonProperty("fields")
    private List<CampoFormularioGuiaFuncionario> campos = new ArrayList<>();

    @Builder.Default
    @JsonProperty("missingRequiredFields")
    private List<String> camposObligatoriosFaltantes = new ArrayList<>();
}
