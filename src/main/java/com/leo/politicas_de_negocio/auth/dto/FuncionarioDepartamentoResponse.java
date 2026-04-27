package com.leo.politicas_de_negocio.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FuncionarioDepartamentoResponse {
    private String id;
    private String nombre;
}
