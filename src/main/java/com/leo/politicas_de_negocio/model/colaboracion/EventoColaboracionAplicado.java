package com.leo.politicas_de_negocio.model.colaboracion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "politicas_eventos_colaboracion")
@CompoundIndexes({
        @CompoundIndex(name = "idx_evento_unico_por_politica", def = "{'politicaId': 1, 'eventId': 1}", unique = true),
        @CompoundIndex(name = "idx_evento_por_secuencia", def = "{'politicaId': 1, 'secuencia': -1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoColaboracionAplicado {

    @Id
    private String id;

    private String politicaId;
    private String eventId;
    private String actorUserId;
    private TipoEventoColaboracion tipo;
    private Long secuencia;
    private LocalDateTime fechaAplicacion;
}
