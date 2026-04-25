package com.leo.politicas_de_negocio.guide.administrador.dto;

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
public class NodoSeleccionadoGuiaAdministrador {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String tipo;

    @JsonProperty("name")
    private String nombre;

    @JsonProperty("department")
    private String departamento;

    @JsonProperty("responsible")
    private String responsable;

    @JsonProperty("responsibleType")
    private String tipoResponsable;

    @Builder.Default
    @JsonProperty("formFields")
    private List<CampoFormularioGuiaAdministrador> camposFormulario = new ArrayList<>();

    @Builder.Default
    @JsonProperty("incomingNodes")
    private List<String> nodosEntrantes = new ArrayList<>();

    @Builder.Default
    @JsonProperty("outgoingNodes")
    private List<String> nodosSalientes = new ArrayList<>();
}
