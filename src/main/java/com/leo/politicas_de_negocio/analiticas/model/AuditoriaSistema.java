package com.leo.politicas_de_negocio.analiticas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "auditoria_sistema")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditoriaSistema {
    @Id
    private String id;
    private String usuarioId;
    private String usuarioNombre;
    private String usuarioCorreo;
    private String rol;
    private String accion; // LOGIN_WEB, LOGIN_MOVIL, LOGOUT, CREAR_POLITICA, CAMBIO_ESTADO_POLITICA, etc.
    private LocalDateTime fecha;
    private String detalle;
    private String ip;
}
