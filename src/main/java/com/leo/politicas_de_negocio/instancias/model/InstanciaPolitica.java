package com.leo.politicas_de_negocio.instancias.model;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "instancias_politica")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstanciaPolitica {

    @Id
    private String id;

    private String politicaId;
    private Long politicaVersion;
    private String codigoTramite;

    private EstadoInstancia estadoInstancia;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private String creadaPor;

    private Map<String, Object> datosContexto;

    // Para sincronizar nodos JOIN: guarda que ramas ya llegaron por join.
    private Map<String, List<String>> tokensJoin;
}
