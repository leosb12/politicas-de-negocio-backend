package com.leo.politicas_de_negocio.instancias.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "historial_instancia")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialInstancia {

    @Id
    private String id;

    private String instanciaId;
    private String tareaId;
    private String accion;
    private String usuario;
    private LocalDateTime fecha;
    private String detalle;
}
