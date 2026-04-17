package com.leo.politicas_de_negocio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "departamentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Departamento {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private Boolean activo;
}
