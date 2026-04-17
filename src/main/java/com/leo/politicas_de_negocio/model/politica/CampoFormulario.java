package com.leo.politicas_de_negocio.model.politica;

import com.leo.politicas_de_negocio.model.enums.TipoCampo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampoFormulario {
    private String campo;
    private TipoCampo tipo;
}
