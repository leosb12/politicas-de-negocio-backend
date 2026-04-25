package com.leo.politicas_de_negocio.guide.administrador.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.leo.politicas_de_negocio.guide.comun.dto.AccionGuia;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccionSugeridaGuiaAdministrador extends AccionGuia {
}
