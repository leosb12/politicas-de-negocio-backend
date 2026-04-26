package com.leo.politicas_de_negocio.guide.usuario_movil.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leo.politicas_de_negocio.guide.comun.dto.SolicitudGuia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolicitudGuiaUsuarioMovil extends SolicitudGuia {

    @Builder.Default
    @JsonProperty("context")
    private ContextoGuiaUsuarioMovil contexto = new ContextoGuiaUsuarioMovil();
}
