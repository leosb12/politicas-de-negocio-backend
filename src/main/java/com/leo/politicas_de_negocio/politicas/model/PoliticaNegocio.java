package com.leo.politicas_de_negocio.politicas.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoPolitica;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "politicas_negocio")
@CompoundIndex(name = "idx_politica_estado", def = "{'estado': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliticaNegocio {

    @Id
    private String id;
    
    private String nombre;
    private String descripcion;
    private String categoria;
    private String descripcionClasificacion;
    private List<String> palabrasClave;
    private List<String> intencionesEjemplo;
    private List<String> requisitosSugeridos;
    private EstadoPolitica estado;
    private TipoPolitica tipoPolitica;
    private String departamentoInicioId;
    private Boolean requierePago;
    private BigDecimal montoPago;
    private String monedaPago;
    private String descripcionPago;

    @JsonIgnore
    private Boolean fueActivada;
    
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
    private List<CampoFormulario> requisitosIniciales;

    // Configuracion visual compartida del canvas de diseño.
    private String laneOrientation;
    private Double laneWidth;
    private Double laneHeight;

    // Secuencia global de eventos colaborativos aplicados sobre esta politica.
    private Long secuenciaColaboracion;
    private LocalDateTime fechaUltimaColaboracion;
    
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
}
