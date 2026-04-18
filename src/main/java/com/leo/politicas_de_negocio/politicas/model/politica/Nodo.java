package com.leo.politicas_de_negocio.politicas.model.politica;

import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nodo {
    private String id;
    private TipoNodo tipo;
    private String nombre;

    // Carril visual del diagrama.
    private String departamentoId;

    // Responsable real de ejecucion.
    private String responsableTipo;
    private String responsableId;

    // Coordenadas visuales para sincronizar movimientos en vivo.
    private Double posX;
    private Double posY;

    // Version por nodo para detectar updates estructurales stale.
    private Long version;
    private LocalDateTime fechaActualizacion;

    private List<CampoFormulario> formulario;
    private List<CondicionDecision> condiciones;
}
