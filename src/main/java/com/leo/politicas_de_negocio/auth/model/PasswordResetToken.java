package com.leo.politicas_de_negocio.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "password_reset_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String usuarioId;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaExpiracion;
    private Boolean usado;
    private LocalDateTime fechaUso;
}