package com.leo.politicas_de_negocio.auth.dto;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String correo;
    private String passwordActual;
    private String nuevaContrasena;
    private String confirmarNuevaContrasena;
}