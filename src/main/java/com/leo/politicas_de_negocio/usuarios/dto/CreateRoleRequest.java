package com.leo.politicas_de_negocio.usuarios.dto;

import lombok.Data;

@Data
public class CreateRoleRequest {
    private String nombre;
    private String descripcion;
}