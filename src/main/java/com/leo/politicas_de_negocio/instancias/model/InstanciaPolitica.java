package com.leo.politicas_de_negocio.instancias.model;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "instancias_politica")
@CompoundIndex(name = "idx_instancia_creada_por_fecha", def = "{'creadaPor': 1, 'fechaCreacion': -1}")
@CompoundIndex(name = "idx_instancia_estado_fecha", def = "{'estadoInstancia': 1, 'fechaCreacion': -1}")
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

    @Indexed(name = "idx_instancia_fecha_creacion")
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaFinalizacion;

    @Indexed(name = "idx_instancia_creada_por")
    private String creadaPor;
    private String finalizadaPor;

    private Map<String, Object> datosContexto;

    // Para sincronizar nodos JOIN: guarda que ramas ya llegaron por join.
    private Map<String, List<String>> tokensJoin;
}
