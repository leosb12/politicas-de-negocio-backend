package com.leo.politicas_de_negocio.notifications.application;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.notifications.application.model.PushDataPayload;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNotificationService.class);

    private static final String ACTION_OPEN_TRAMITE = "OPEN_TRAMITE";
    private static final String ACTION_OPEN_TASK = "OPEN_TASK";

    private final PushNotificationService pushNotificationService;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;

    public void notificarTramiteIniciado(InstanciaPolitica instancia, PoliticaNegocio politica) {
        String creadorId = normalizar(instancia != null ? instancia.getCreadaPor() : null);
        if (creadorId == null) {
            return;
        }

        enviarAUsuarios(Set.of(creadorId), mensaje(
                "Tramite iniciado",
                "Tu tramite " + etiquetaTramite(instancia, politica) + " fue iniciado.",
                "TRAMITE_INICIADO",
                instancia != null ? instancia.getId() : null,
                null,
                ACTION_OPEN_TRAMITE
        ));
    }

    public void notificarTareaCreada(InstanciaPolitica instancia, PoliticaNegocio politica, TareaActividad tarea) {
        if (tarea == null) {
            return;
        }

        Set<String> responsables = resolverUsuariosResponsables(tarea);
        String tipoResponsable = normalizar(tarea.getResponsableTipo());
        String type = "DEPARTAMENTO".equalsIgnoreCase(tipoResponsable)
                ? "TRAMITE_CAMBIO_DEPARTAMENTO"
                : "TAREA_ASIGNADA";

        enviarAUsuarios(responsables, mensaje(
                tituloTareaAsignada(tarea),
                cuerpoTareaAsignada(instancia, politica, tarea),
                type,
                instancia != null ? instancia.getId() : tarea.getInstanciaId(),
                tarea.getId(),
                ACTION_OPEN_TASK
        ));

        String creadorId = normalizar(instancia != null ? instancia.getCreadaPor() : null);
        if (creadorId != null && !responsables.contains(creadorId)) {
            enviarAUsuarios(Set.of(creadorId), mensaje(
                    tituloActividadIniciada(tarea),
                    cuerpoActividadIniciada(instancia, politica, tarea),
                    type,
                    instancia.getId(),
                    tarea.getId(),
                    ACTION_OPEN_TRAMITE
            ));
        }
    }

    public void notificarTareaCompletada(
            InstanciaPolitica instancia,
            PoliticaNegocio politica,
            TareaActividad tarea,
            String actorUserId
    ) {
        String creadorId = normalizar(instancia != null ? instancia.getCreadaPor() : null);
        if (creadorId == null || creadorId.equals(normalizar(actorUserId))) {
            return;
        }

        enviarAUsuarios(Set.of(creadorId), mensaje(
                "Actividad completada",
                "Se completo " + etiquetaActividad(tarea) + " del tramite " + etiquetaTramite(instancia, politica) + ".",
                "TAREA_COMPLETADA",
                instancia.getId(),
                tarea != null ? tarea.getId() : null,
                ACTION_OPEN_TRAMITE
        ));
    }

    public void notificarTramiteFinalizado(InstanciaPolitica instancia, PoliticaNegocio politica) {
        Set<String> destinatarios = new LinkedHashSet<>();
        agregarSiPresente(destinatarios, instancia != null ? instancia.getCreadaPor() : null);
        agregarSiPresente(destinatarios, instancia != null ? instancia.getFinalizadaPor() : null);

        enviarAUsuarios(destinatarios, mensaje(
                "Tramite finalizado",
                "El tramite " + etiquetaTramite(instancia, politica) + " finalizo correctamente.",
                "TRAMITE_FINALIZADO",
                instancia != null ? instancia.getId() : null,
                null,
                ACTION_OPEN_TRAMITE
        ));
    }

    private Set<String> resolverUsuariosResponsables(TareaActividad tarea) {
        Set<String> userIds = new LinkedHashSet<>();
        String tipoResponsable = normalizar(tarea.getResponsableTipo());
        String responsableId = normalizar(tarea.getResponsableId());
        if (tipoResponsable == null || responsableId == null) {
            return userIds;
        }

        if ("USUARIO".equalsIgnoreCase(tipoResponsable)) {
            agregarSiUsuarioActivo(userIds, responsableId);
            return userIds;
        }

        if ("DEPARTAMENTO".equalsIgnoreCase(tipoResponsable)) {
            usuarioRepository.findAllByDepartamentoId(responsableId).stream()
                    .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()))
                    .map(Usuario::getId)
                    .forEach(userId -> agregarSiPresente(userIds, userId));
        }

        return userIds;
    }

    private void agregarSiUsuarioActivo(Set<String> userIds, String userId) {
        String normalized = normalizar(userId);
        if (normalized == null) {
            return;
        }

        usuarioRepository.findByIdAndActivo(normalized, true)
                .map(Usuario::getId)
                .ifPresent(id -> agregarSiPresente(userIds, id));
    }

    private PushNotificationMessage mensaje(
            String title,
            String body,
            String type,
            String tramiteId,
            String tareaId,
            String action
    ) {
        return PushNotificationMessage.builder()
                .title(title)
                .body(body)
                .data(PushDataPayload.builder()
                        .type(type)
                        .tramiteId(tramiteId)
                        .tareaId(tareaId)
                        .action(action)
                        .build())
                .build();
    }

    private void enviarAUsuarios(Set<String> userIds, PushNotificationMessage message) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        for (String userId : userIds) {
            String normalized = normalizar(userId);
            if (normalized == null) {
                continue;
            }

            try {
                pushNotificationService.sendToUser(normalized, message);
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar push al usuario {}", normalized, ex);
            }
        }
    }

    private String tituloTareaAsignada(TareaActividad tarea) {
        String tipoResponsable = normalizar(tarea.getResponsableTipo());
        if ("DEPARTAMENTO".equalsIgnoreCase(tipoResponsable)) {
            return "Nuevo tramite para el departamento";
        }
        return "Nueva tarea asignada";
    }

    private String cuerpoTareaAsignada(InstanciaPolitica instancia, PoliticaNegocio politica, TareaActividad tarea) {
        String tipoResponsable = normalizar(tarea.getResponsableTipo());
        if ("DEPARTAMENTO".equalsIgnoreCase(tipoResponsable)) {
            return "El tramite " + etiquetaTramite(instancia, politica)
                    + " paso a " + etiquetaDepartamento(tarea.getResponsableId()) + ".";
        }
        return "Tienes una nueva actividad en " + etiquetaTramite(instancia, politica) + ": "
                + etiquetaActividad(tarea) + ".";
    }

    private String tituloActividadIniciada(TareaActividad tarea) {
        String tipoResponsable = normalizar(tarea != null ? tarea.getResponsableTipo() : null);
        if ("DEPARTAMENTO".equalsIgnoreCase(tipoResponsable)) {
            return "Tramite enviado a otro departamento";
        }
        return "Nueva actividad del tramite";
    }

    private String cuerpoActividadIniciada(InstanciaPolitica instancia, PoliticaNegocio politica, TareaActividad tarea) {
        String tipoResponsable = normalizar(tarea != null ? tarea.getResponsableTipo() : null);
        if ("DEPARTAMENTO".equalsIgnoreCase(tipoResponsable)) {
            return "El tramite " + etiquetaTramite(instancia, politica)
                    + " paso a " + etiquetaDepartamento(tarea.getResponsableId()) + ".";
        }
        return "El tramite " + etiquetaTramite(instancia, politica)
                + " avanzo a " + etiquetaActividad(tarea) + ".";
    }

    private String etiquetaTramite(InstanciaPolitica instancia, PoliticaNegocio politica) {
        String codigo = normalizar(instancia != null ? instancia.getCodigoTramite() : null);
        if (codigo != null) {
            return codigo;
        }

        String politicaNombre = normalizar(politica != null ? politica.getNombre() : null);
        if (politicaNombre != null) {
            return politicaNombre;
        }

        String instanciaId = normalizar(instancia != null ? instancia.getId() : null);
        return instanciaId != null ? instanciaId : "solicitado";
    }

    private String etiquetaActividad(TareaActividad tarea) {
        String nombre = normalizar(tarea != null ? tarea.getNombreNodo() : null);
        return nombre != null ? nombre : "la actividad";
    }

    private String etiquetaDepartamento(String departamentoId) {
        String id = normalizar(departamentoId);
        if (id == null) {
            return "el departamento responsable";
        }

        return departamentoRepository.findById(id)
                .map(Departamento::getNombre)
                .map(this::normalizar)
                .orElse("el departamento responsable");
    }

    private void agregarSiPresente(Set<String> valores, String value) {
        String normalized = normalizar(value);
        if (normalized != null) {
            valores.add(normalized);
        }
    }

    private String normalizar(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
