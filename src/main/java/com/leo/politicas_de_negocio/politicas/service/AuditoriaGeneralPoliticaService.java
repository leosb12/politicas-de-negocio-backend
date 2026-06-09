package com.leo.politicas_de_negocio.politicas.service;

import com.leo.politicas_de_negocio.colaboracion.model.EventoColaboracionAplicado;
import com.leo.politicas_de_negocio.colaboracion.repository.EventoColaboracionAplicadoRepository;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.dto.PoliticaAuditoriaGeneralResponse;
import com.leo.politicas_de_negocio.politicas.model.PoliticaAuditoria;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaAuditoriaRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditoriaGeneralPoliticaService {

    private final PoliticaNegocioRepository politicaRepository;
    private final PoliticaAuditoriaRepository politicaAuditoriaRepository;
    private final EventoColaboracionAplicadoRepository eventoColaboracionRepository;
    private final InstanciaPoliticaRepository instanciaRepository;
    private final TareaActividadRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;

    public PoliticaAuditoriaGeneralResponse obtenerAuditoriaGeneral(String adminUserId, String politicaId) {
        assertAdmin(adminUserId);
        
        String idPolitica = normalizar(politicaId);
        if (idPolitica == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id de la politica");
        }

        PoliticaNegocio politica = politicaRepository.findById(idPolitica)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + idPolitica));

        // 1. Obtener registros manuales y eventos colaborativos
        List<PoliticaAuditoria> audits = politicaAuditoriaRepository.findByPoliticaIdOrderByFechaDesc(idPolitica);
        List<EventoColaboracionAplicado> collabEvents = eventoColaboracionRepository.findTop50ByPoliticaIdOrderBySecuenciaDesc(idPolitica);

        // Recolectar IDs de todos los usuarios para resolverlos en batch
        Set<String> userIds = new HashSet<>();
        if (normalizar(politica.getCreadoPor()) != null) {
            userIds.add(politica.getCreadoPor());
        }
        audits.stream().map(PoliticaAuditoria::getUsuarioId).filter(Objects::nonNull).forEach(userIds::add);
        collabEvents.stream().map(EventoColaboracionAplicado::getActorUserId).filter(Objects::nonNull).forEach(userIds::add);

        // Obtener instancias y tareas asociadas
        List<InstanciaPolitica> instancias = instanciaRepository.findByPoliticaIdOrderByFechaCreacionDesc(idPolitica);
        instancias.stream().map(InstanciaPolitica::getCreadaPor).filter(Objects::nonNull).forEach(userIds::add);

        List<TareaActividad> tareas = tareaRepository.findByPoliticaIdOrderByFechaCreacionDesc(idPolitica);
        tareas.stream().map(TareaActividad::getAsignadoA).filter(Objects::nonNull).forEach(userIds::add);

        // Resolver usuarios
        Map<String, Usuario> usuariosMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            usuarioRepository.findAllById(userIds).forEach(u -> usuariosMap.put(u.getId(), u));
        }

        // Mapear ediciones del Admin
        List<PoliticaAuditoriaGeneralResponse.EdicionAuditoriaDto> ediciones = new ArrayList<>();
        
        // Agregar logs estructurados
        for (PoliticaAuditoria audit : audits) {
            ediciones.add(PoliticaAuditoriaGeneralResponse.EdicionAuditoriaDto.builder()
                    .id(audit.getId())
                    .tipoAccion(audit.getTipoAccion())
                    .usuarioId(audit.getUsuarioId())
                    .usuarioNombre(audit.getUsuarioNombre())
                    .fecha(audit.getFecha())
                    .detalle(audit.getDetalle())
                    .build());
        }

        // Agregar eventos de canvas
        for (EventoColaboracionAplicado event : collabEvents) {
            String actorId = event.getActorUserId();
            Usuario actor = usuariosMap.get(actorId);
            String actorName = actor != null ? actor.getNombre() : (actorId != null ? actorId : "Editor Anónimo");
            
            ediciones.add(PoliticaAuditoriaGeneralResponse.EdicionAuditoriaDto.builder()
                    .id(event.getId())
                    .tipoAccion("EDICION_CANVAS_COLABORATIVA")
                    .usuarioId(actorId)
                    .usuarioNombre(actorName)
                    .fecha(event.getFechaAplicacion())
                    .detalle(mapCollabEventDetalle(event))
                    .build());
        }

        // Ordenar ediciones desc por fecha
        ediciones.sort((a, b) -> b.getFecha().compareTo(a.getFecha()));

        // Mapear iniciadores
        List<PoliticaAuditoriaGeneralResponse.IniciadorAuditoriaDto> iniciadores = new ArrayList<>();
        for (InstanciaPolitica inst : instancias) {
            Usuario creador = usuariosMap.get(inst.getCreadaPor());
            iniciadores.add(PoliticaAuditoriaGeneralResponse.IniciadorAuditoriaDto.builder()
                    .instanciaId(inst.getId())
                    .codigoTramite(inst.getCodigoTramite() != null ? inst.getCodigoTramite() : inst.getId())
                    .usuarioId(inst.getCreadaPor())
                    .usuarioNombre(creador != null ? creador.getNombre() : "Usuario final")
                    .usuarioCorreo(creador != null ? creador.getCorreo() : "-")
                    .fechaInicio(inst.getFechaCreacion())
                    .estadoInstancia(inst.getEstadoInstancia() != null ? inst.getEstadoInstancia().name() : "INDEFINIDO")
                    .build());
        }

        // Mapear tareas realizadas
        List<PoliticaAuditoriaGeneralResponse.TramiteRealizadoDto> tramitesRealizados = new ArrayList<>();
        for (TareaActividad tarea : tareas) {
            Usuario asignado = usuariosMap.get(tarea.getAsignadoA());
            tramitesRealizados.add(PoliticaAuditoriaGeneralResponse.TramiteRealizadoDto.builder()
                    .instanciaId(tarea.getInstanciaId())
                    .codigoTramite(findCodigoTramite(tarea.getInstanciaId(), instancias))
                    .tareaId(tarea.getId())
                    .nodoId(tarea.getNodoId())
                    .nombreNodo(tarea.getNombreNodo() != null ? tarea.getNombreNodo() : "Paso sin nombre")
                    .funcionarioId(tarea.getAsignadoA())
                    .funcionarioNombre(asignado != null ? asignado.getNombre() : (tarea.getAsignadoA() != null ? tarea.getAsignadoA() : "No asignado"))
                    .fechaInicio(tarea.getFechaInicio() != null ? tarea.getFechaInicio() : tarea.getFechaCreacion())
                    .fechaFin(tarea.getFechaFin())
                    .estadoTarea(tarea.getEstadoTarea() != null ? tarea.getEstadoTarea().name() : "PENDIENTE")
                    .build());
        }

        // Consolidar colaboradores
        Map<String, CollaboratorAggregator> collabMap = new HashMap<>();

        // Registrar creador
        if (normalizar(politica.getCreadoPor()) != null) {
            String cId = politica.getCreadoPor();
            Usuario cUser = usuariosMap.get(cId);
            collabMap.put(cId, new CollaboratorAggregator(
                    cId,
                    cUser != null ? cUser.getNombre() : (politica.getCreadoPorNombre() != null ? politica.getCreadoPorNombre() : "Creador"),
                    cUser != null ? cUser.getCorreo() : "-",
                    cUser != null ? cUser.getRol() : "ADMIN",
                    "Creador",
                    1
            ));
        }

        // Registrar edits manuales
        for (PoliticaAuditoria audit : audits) {
            String uId = audit.getUsuarioId();
            if (normalizar(uId) == null) continue;
            Usuario u = usuariosMap.get(uId);
            CollaboratorAggregator agg = collabMap.computeIfAbsent(uId, k -> new CollaboratorAggregator(
                    uId,
                    u != null ? u.getNombre() : audit.getUsuarioNombre(),
                    u != null ? u.getCorreo() : "-",
                    u != null ? u.getRol() : "ADMIN"
            ));
            agg.addRole("ADMIN");
            agg.addParticipation(audit.getTipoAccion().equals("CREACION") ? "Creador" : "Editor de política");
            agg.incrementActivity();
        }

        // Registrar cambios de canvas colaborativo
        for (EventoColaboracionAplicado event : collabEvents) {
            String uId = event.getActorUserId();
            if (normalizar(uId) == null) continue;
            Usuario u = usuariosMap.get(uId);
            CollaboratorAggregator agg = collabMap.computeIfAbsent(uId, k -> new CollaboratorAggregator(
                    uId,
                    u != null ? u.getNombre() : "Editor de Canvas",
                    u != null ? u.getCorreo() : "-",
                    u != null ? u.getRol() : "ADMIN"
            ));
            agg.addRole("ADMIN");
            agg.addParticipation("Diseñador en canvas");
            agg.incrementActivity();
        }

        // Registrar iniciadores de ejecuciones
        for (InstanciaPolitica inst : instancias) {
            String uId = inst.getCreadaPor();
            if (normalizar(uId) == null) continue;
            Usuario u = usuariosMap.get(uId);
            CollaboratorAggregator agg = collabMap.computeIfAbsent(uId, k -> new CollaboratorAggregator(
                    uId,
                    u != null ? u.getNombre() : "Usuario final",
                    u != null ? u.getCorreo() : "-",
                    u != null ? u.getRol() : "USUARIO"
            ));
            agg.addRole(u != null ? u.getRol() : "USUARIO");
            agg.addParticipation("Iniciador de trámites");
            agg.incrementActivity();
        }

        // Registrar funcionarios ejecutores
        for (TareaActividad tarea : tareas) {
            String uId = tarea.getAsignadoA();
            if (normalizar(uId) == null) continue;
            Usuario u = usuariosMap.get(uId);
            CollaboratorAggregator agg = collabMap.computeIfAbsent(uId, k -> new CollaboratorAggregator(
                    uId,
                    u != null ? u.getNombre() : "Funcionario",
                    u != null ? u.getCorreo() : "-",
                    u != null ? u.getRol() : "FUNCIONARIO"
            ));
            agg.addRole(u != null ? u.getRol() : "FUNCIONARIO");
            agg.addParticipation("Ejecutor de tareas");
            agg.incrementActivity();
        }

        List<PoliticaAuditoriaGeneralResponse.ColaboradorAuditoriaDto> colaboradores = collabMap.values().stream()
                .map(CollaboratorAggregator::toDto)
                .sorted(Comparator.comparing(PoliticaAuditoriaGeneralResponse.ColaboradorAuditoriaDto::getTotalActividades, Comparator.reverseOrder()))
                .toList();

        return PoliticaAuditoriaGeneralResponse.builder()
                .id(politica.getId())
                .nombre(politica.getNombre())
                .descripcion(politica.getDescripcion())
                .estado(politica.getEstado() != null ? politica.getEstado().name() : "BORRADOR")
                .creadoPorId(politica.getCreadoPor())
                .creadoPorNombre(politica.getCreadoPorNombre() != null ? politica.getCreadoPorNombre() : "Admin General")
                .fechaCreacion(politica.getFechaCreacion() != null ? politica.getFechaCreacion() : LocalDateTime.now())
                .ediciones(ediciones)
                .iniciadores(iniciadores)
                .tramitesRealizados(tramitesRealizados)
                .colaboradores(colaboradores)
                .build();
    }

    private String mapCollabEventDetalle(EventoColaboracionAplicado event) {
        String tipo = event.getTipo() != null ? event.getTipo().name() : "ACCION";
        return switch (tipo) {
            case "CREATE_NODE" -> "Creó un elemento en el flujo del canvas";
            case "UPDATE_NODE" -> "Actualizó propiedades de un nodo";
            case "MOVE_NODE" -> "Movió la posición de un nodo en el canvas";
            case "UPDATE_CANVAS_CONFIG" -> "Modificó configuración visual del carril (dimensiones u orientación)";
            case "DELETE_NODE" -> "Eliminó un nodo del flujo";
            case "CREATE_EDGE" -> "Creó una transición entre nodos";
            case "DELETE_EDGE" -> "Eliminó una transición del canvas";
            case "REPLACE_FLOW" -> "Reemplazó el diseño completo con una nueva versión";
            default -> "Editó el canvas colaborativo (Secuencia #" + event.getSecuencia() + ")";
        };
    }

    private String findCodigoTramite(String instanciaId, List<InstanciaPolitica> instancias) {
        if (instanciaId == null) return "-";
        for (InstanciaPolitica inst : instancias) {
            if (instanciaId.equals(inst.getId())) {
                return inst.getCodigoTramite() != null ? inst.getCodigoTramite() : inst.getId();
            }
        }
        return instanciaId;
    }

    private Usuario assertAdmin(String adminUserId) {
        String id = normalizar(adminUserId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }
        Usuario admin = usuarioRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));
        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede realizar esta accion");
        }
        return admin;
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static class CollaboratorAggregator {
        private final String id;
        private final String nombre;
        private final String correo;
        private final Set<String> roles = new HashSet<>();
        private final Set<String> participaciones = new HashSet<>();
        private int activityCount = 0;

        public CollaboratorAggregator(String id, String nombre, String correo, String mainRol) {
            this.id = id;
            this.nombre = nombre != null ? nombre : id;
            this.correo = correo != null ? correo : "-";
            if (mainRol != null) {
                this.roles.add(mainRol.toUpperCase());
            }
        }

        public CollaboratorAggregator(String id, String nombre, String correo, String mainRol, String part, int activityCount) {
            this(id, nombre, correo, mainRol);
            if (part != null) {
                this.participaciones.add(part);
            }
            this.activityCount = activityCount;
        }

        public void addRole(String role) {
            if (role != null) {
                this.roles.add(role.toUpperCase());
            }
        }

        public void addParticipation(String part) {
            if (part != null) {
                this.participaciones.add(part);
            }
        }

        public void incrementActivity() {
            this.activityCount++;
        }

        public PoliticaAuditoriaGeneralResponse.ColaboradorAuditoriaDto toDto() {
            String roleJoined = roles.stream().sorted().collect(Collectors.joining("/"));
            String partJoined = participaciones.isEmpty() 
                    ? "Involucrado" 
                    : participaciones.stream().sorted().collect(Collectors.joining(", "));
            
            return PoliticaAuditoriaGeneralResponse.ColaboradorAuditoriaDto.builder()
                    .usuarioId(id)
                    .nombre(nombre)
                    .correo(correo)
                    .rolEnSistema(roleJoined.isEmpty() ? "USUARIO" : roleJoined)
                    .participacion(partJoined)
                    .totalActividades(activityCount)
                    .build();
        }
    }
}
