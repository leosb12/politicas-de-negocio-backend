package com.leo.politicas_de_negocio.politicas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "politicas_auditoria")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliticaAuditoria {

    @Id
    private String id;

    @Indexed(name = "idx_auditoria_politica_id")
    private String politicaId;

    private LocalDateTime fecha;
    private String tipoAccion; // CREACION, EDICION_METADATOS, EDICION_FLUJO, EDICION_REQUISITOS, CAMBIO_ESTADO
    private String usuarioId;
    private String usuarioNombre;
    private String detalle;
}
