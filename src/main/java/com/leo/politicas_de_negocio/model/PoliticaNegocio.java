package com.leo.politicas_de_negocio.model;

import com.leo.politicas_de_negocio.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.model.politica.Conexion;
import com.leo.politicas_de_negocio.model.politica.Nodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "politicas_negocio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliticaNegocio {

    @Id
    private String id;
    
    private String nombre;
    private String descripcion;
    private EstadoPolitica estado;
    
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
    
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
