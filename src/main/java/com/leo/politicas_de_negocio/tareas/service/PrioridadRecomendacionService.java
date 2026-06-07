package com.leo.politicas_de_negocio.tareas.service;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrioridadRecomendacionService {

    private final TareaActividadRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;

    @Data
    @Builder
    public static class RecomendacionResult {
        private String prioridad;
        private String recursoRecomendado;
        private String recursoRecomendadoNombre;
        private String motivoRecomendacion;
    }

    public RecomendacionResult analizarPrioridadYRecurso(TareaActividad tarea, InstanciaPolitica instancia) {
        if (tarea.getEstadoTarea() == EstadoTarea.COMPLETADA || tarea.getEstadoTarea() == EstadoTarea.CANCELADA || tarea.getEstadoTarea() == EstadoTarea.RECHAZADA) {
            return RecomendacionResult.builder().prioridad("NORMAL").motivoRecomendacion("Tarea ya cerrada").build();
        }

        int scorePrioridad = 0;
        StringBuilder motivos = new StringBuilder();

        // 1. Tiempo acumulado
        long horas = 0;
        if (tarea.getFechaCreacion() != null) {
            horas = Duration.between(tarea.getFechaCreacion(), LocalDateTime.now()).toHours();
            if (horas >= 48) {
                scorePrioridad += 30;
                motivos.append("Atraso crítico (>48h). ");
            } else if (horas >= 24) {
                scorePrioridad += 15;
                motivos.append("Demora (>24h). ");
            }
        }

        // 2. Riesgo de demora y cuello de botella (desde IA)
        if ("ALTO".equalsIgnoreCase(tarea.getRiesgoDemora())) {
            scorePrioridad += 20;
            motivos.append("Riesgo alto de demora. ");
        }
        if ("SI".equalsIgnoreCase(tarea.getCuelloBotella())) {
            scorePrioridad += 20;
            motivos.append("Detectado como cuello de botella. ");
        }

        // 3. Urgencia del trámite (desde contexto)
        String urgencia = extraerUrgencia(instancia);
        if ("ALTA".equalsIgnoreCase(urgencia) || "URGENTE".equalsIgnoreCase(urgencia)) {
            scorePrioridad += 25;
            motivos.append("Trámite marcado como URGENTE. ");
        }

        // 4. Observaciones previas en la instancia (reprocesos)
        if (tieneObservaciones(instancia)) {
            scorePrioridad += 15;
            motivos.append("Trámite con observaciones previas (posible reproceso). ");
        }

        String prioridadCalculada = "NORMAL";
        if (scorePrioridad >= 50) {
            prioridadCalculada = "ALTA";
        } else if (scorePrioridad >= 20) {
            prioridadCalculada = "MEDIA";
        }
        
        // 5. Prioridad recomendada explícitamente por IA
        if ("ALTA".equalsIgnoreCase(tarea.getPrioridadRecomendada()) && !"ALTA".equals(prioridadCalculada)) {
            prioridadCalculada = "ALTA";
            motivos.append("Recomendación de prioridad ALTA por modelo predictivo IA. ");
        } else if ("MEDIA".equalsIgnoreCase(tarea.getPrioridadRecomendada()) && "NORMAL".equals(prioridadCalculada)) {
            prioridadCalculada = "MEDIA";
            motivos.append("Recomendación de prioridad MEDIA por modelo predictivo IA. ");
        }

        if (motivos.isEmpty()) {
            motivos.append("Proceso regular dentro de los tiempos esperados. ");
        }

        RecomendacionResult result = RecomendacionResult.builder()
                .prioridad(prioridadCalculada)
                .motivoRecomendacion(motivos.toString().trim())
                .build();

        // 6. Carga de trabajo y recomendación de recurso
        recomendarRecurso(tarea, result);

        return result;
    }

    private void recomendarRecurso(TareaActividad tarea, RecomendacionResult result) {
        if ("USUARIO".equalsIgnoreCase(tarea.getResponsableTipo())) {
            result.setRecursoRecomendado(tarea.getResponsableId());
            result.setMotivoRecomendacion(result.getMotivoRecomendacion() + " Asignado directamente a usuario específico.");
            return;
        }

        if ("DEPARTAMENTO".equalsIgnoreCase(tarea.getResponsableTipo())) {
            List<Usuario> funcionarios = usuarioRepository.findAllByDepartamentoId(tarea.getResponsableId());
            if (funcionarios == null || funcionarios.isEmpty()) {
                result.setMotivoRecomendacion(result.getMotivoRecomendacion() + " No hay funcionarios en el departamento.");
                return;
            }

            Usuario mejorCandidato = null;
            long menorCarga = Long.MAX_VALUE;

            for (Usuario u : funcionarios) {
                if (Boolean.FALSE.equals(u.getActivo())) continue;
                if (!"FUNCIONARIO".equalsIgnoreCase(u.getRol())) continue;
                
                long carga = tareaRepository.countByAsignadoAAndEstadoTareaIn(
                        u.getId(),
                        List.of(EstadoTarea.PENDIENTE, EstadoTarea.EN_PROCESO)
                );
                
                if (carga < menorCarga) {
                    menorCarga = carga;
                    mejorCandidato = u;
                }
            }

            if (mejorCandidato != null) {
                result.setRecursoRecomendado(mejorCandidato.getId());
                result.setRecursoRecomendadoNombre(mejorCandidato.getNombre());
                result.setMotivoRecomendacion(result.getMotivoRecomendacion() + " Se recomienda asignar a " + mejorCandidato.getNombre() + " por tener la menor carga actual (" + menorCarga + " tareas activas).");
            } else {
                result.setMotivoRecomendacion(result.getMotivoRecomendacion() + " No se encontraron funcionarios activos para asignar en el departamento.");
            }
        }
    }

    private String extraerUrgencia(InstanciaPolitica instancia) {
        if (instancia == null || instancia.getDatosContexto() == null) return "NORMAL";
        Map<String, Object> ctx = instancia.getDatosContexto();
        
        for (String key : List.of("urgencia", "prioridad", "nivel_urgencia", "nivelUrgencia")) {
            for (Map.Entry<String, Object> entry : ctx.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                    return entry.getValue().toString().toUpperCase();
                }
            }
        }
        return "NORMAL";
    }

    private boolean tieneObservaciones(InstanciaPolitica instancia) {
        if (instancia == null || instancia.getDatosContexto() == null) return false;
        Map<String, Object> ctx = instancia.getDatosContexto();
        for (String key : List.of("observaciones", "comentarios", "motivo_rechazo", "motivoRechazo")) {
            for (Map.Entry<String, Object> entry : ctx.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null && !entry.getValue().toString().trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
