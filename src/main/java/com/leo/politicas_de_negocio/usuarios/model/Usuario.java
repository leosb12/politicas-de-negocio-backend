package com.leo.politicas_de_negocio.usuarios.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "usuarios")
@CompoundIndex(name = "idx_usuario_correo_activo", def = "{'correo': 1, 'activo': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    private String id;

    private String nombre;
    private String correo;
    private String password;
    private String rol;
    private String departamentoId;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
}