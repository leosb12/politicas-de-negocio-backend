package com.leo.politicas_de_negocio.colaboracion.model;

import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "politicas_snapshots_colaboracion")
@CompoundIndexes({
        @CompoundIndex(name = "idx_snapshot_unico_por_secuencia", def = "{'politicaId': 1, 'secuencia': 1}", unique = true),
        @CompoundIndex(name = "idx_snapshot_reciente", def = "{'politicaId': 1, 'fechaCreacion': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnapshotColaboracionPolitica {

    @Id
    private String id;

    private String politicaId;
    private Long secuencia;
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
    private LocalDateTime fechaCreacion;
}
