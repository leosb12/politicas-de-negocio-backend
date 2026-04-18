package com.leo.politicas_de_negocio.usuarios.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private String id;
    private String nombre;
    private String correo;
    private String rol;
    private String departamentoId;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
}