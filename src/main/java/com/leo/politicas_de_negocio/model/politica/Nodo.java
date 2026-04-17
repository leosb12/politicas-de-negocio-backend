package com.leo.politicas_de_negocio.model.politica;

import com.leo.politicas_de_negocio.model.enums.TipoNodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private List<CampoFormulario> formulario;
    private List<CondicionDecision> condiciones;
}
