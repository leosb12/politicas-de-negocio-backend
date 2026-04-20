package com.leo.politicas_de_negocio.auth.dto;

import lombok.Data;

@Data
public class RegisterMovilRequest {
    private String nombre;
    private String correo;
    private String password;
}
