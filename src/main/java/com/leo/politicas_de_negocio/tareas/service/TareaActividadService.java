package com.leo.politicas_de_negocio.tareas.service;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.dto.HistorialEventoResponse;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.instancias.service.HistorialInstanciaService;
import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.dto.CompletarTareaRequest;
import com.leo.politicas_de_negocio.tareas.dto.TareaDetalleResponse;
import com.leo.politicas_de_negocio.tareas.dto.TareaMiaResponse;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.workflow.service.WorkflowEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TareaActividadService {

    private static final List<EstadoTarea> ESTADOS_ABIERTOS = List.of(
            EstadoTarea.PENDIENTE,
            EstadoTarea.EN_PROCESO
    );

        private static final List<EstadoTarea> ESTADOS_COMPLETADAS_VISIBLES = List.of(
            EstadoTarea.COMPLETADA
        );

    private final TareaActividadRepository tareaRepository;
    private final InstanciaPoliticaRepository instanciaRepository;
    private final PoliticaNegocioRepository politicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialInstanciaService historialService;
    private final WorkflowEngineService workflowEngineService;

    public List<TareaActividad> listarMisTareas(String actorUserId) {
        Usuario actor = assertUsuarioActivo(actorUserId);

        Map<String, TareaActividad> indice = new LinkedHashMap<>();

        List<TareaActividad> directas = tareaRepository
                .findByResponsableTipoAndResponsableIdAndEstadoTareaInOrderByFechaCreacionAsc(
                        "USUARIO",
                        actor.getId(),
                        ESTADOS_ABIERTOS
                );
        for (TareaActividad tarea : directas) {
            indice.putIfAbsent(tarea.getId(), tarea);
        }

        if (normalizarTexto(actor.getDepartamentoId()) != null) {
            List<TareaActividad> departamentales = tareaRepository
                    .findByResponsableTipoAndResponsableIdAndEstadoTareaInOrderByFechaCreacionAsc(
                            "DEPARTAMENTO",
                            actor.getDepartamentoId(),
                            ESTADOS_ABIERTOS
                    );

            for (TareaActividad tarea : departamentales) {
                if (tarea.getEstadoTarea() == EstadoTarea.EN_PROCESO
                        && normalizarTexto(tarea.getAsignadoA()) != null
                        && !actor.getId().equals(tarea.getAsignadoA())) {
                    continue;
                }
                indice.putIfAbsent(tarea.getId(), tarea);
            }
        }

            // Mantiene visibles las tareas que el actor completo, aunque ya no esten abiertas.
            List<TareaActividad> completadasPropias = tareaRepository
                .findByAsignadoAAndEstadoTareaInOrderByFechaCreacionAsc(
                    actor.getId(),
                    ESTADOS_COMPLETADAS_VISIBLES
                );
            for (TareaActividad tarea : completadasPropias != null ? completadasPropias : List.<TareaActividad>of()) {
                indice.putIfAbsent(tarea.getId(), tarea);
            }

        List<TareaActividad> resultado = new ArrayList<>(indice.values());
        resultado.sort(Comparator.comparing(TareaActividad::getFechaCreacion, Comparator.nullsLast(LocalDateTime::compareTo)));
        return resultado;
    }

    public List<TareaMiaResponse> listarPorInstancia(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        String id = normalizarTexto(instanciaId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar la instancia");
        }

        if (!instanciaRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Instancia no encontrada con ID: " + id);
        }

        validarAccesoLecturaInstancia(actor, id);

        return construirResumenTareas(tareaRepository.findByInstanciaIdOrderByFechaCreacionAsc(id));
    }

    public List<TareaMiaResponse> listarMisTareasResumen(String actorUserId) {
        return construirResumenTareas(listarMisTareas(actorUserId));
    }

    private List<TareaMiaResponse> construirResumenTareas(List<TareaActividad> tareas) {
        Map<String, InstanciaPolitica> cacheInstancias = new HashMap<>();
        Map<String, PoliticaNegocio> cachePoliticas = new HashMap<>();

        List<TareaMiaResponse> respuesta = new ArrayList<>();
        for (TareaActividad tarea : tareas) {
            InstanciaPolitica instancia = obtenerInstancia(cacheInstancias, tarea.getInstanciaId());
            PoliticaNegocio politica = obtenerPolitica(cachePoliticas, tarea.getPoliticaId());

                Map<String, Object> resumenFormulario = resumirContexto(tarea.getFormularioRespuesta());
                Map<String, Object> resumenContexto = !resumenFormulario.isEmpty()
                    ? resumenFormulario
                    : resumirContexto(instancia != null ? instancia.getDatosContexto() : null);

            respuesta.add(TareaMiaResponse.builder()
                    .id(tarea.getId())
                    .nombreActividad(tarea.getNombreNodo())
                    .estadoTarea(tarea.getEstadoTarea())
                    .instanciaId(tarea.getInstanciaId())
                    .politicaId(tarea.getPoliticaId())
                    .politicaNombre(politica != null ? politica.getNombre() : null)
                    .fechaCreacion(tarea.getFechaCreacion())
                    .fechaInicio(tarea.getFechaInicio())
                    .prioridad(calcularPrioridad(tarea))
                    .responsableActual(normalizarTexto(tarea.getAsignadoA()) != null ? tarea.getAsignadoA() : tarea.getResponsableId())
                    .responsableTipo(tarea.getResponsableTipo())
                    .responsableId(tarea.getResponsableId())
                    .codigoTramite(instancia != null ? instancia.getCodigoTramite() : null)
                    .estadoInstancia(instancia != null ? instancia.getEstadoInstancia() : null)
                    .contextoResumen(resumenContexto.isEmpty() ? null : resumenContexto)
                    .build());
        }
        return respuesta;
    }

    public TareaDetalleResponse obtenerDetalleTarea(String actorUserId, String tareaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        TareaActividad tarea = buscarTarea(tareaId);

        validarPermisoLecturaTarea(actor, tarea);
        validarAccesoLecturaInstancia(actor, tarea.getInstanciaId());

        InstanciaPolitica instancia = instanciaRepository.findById(tarea.getInstanciaId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Instancia no encontrada para tarea " + tarea.getId()));

        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId()).orElse(null);

        InstanciaDetalleResponse instanciaDetalle = construirDetalleInstancia(instancia, politica);
        List<HistorialEventoResponse> historialRelevante = "ADMIN".equalsIgnoreCase(actor.getRol())
            ? construirHistorialRelevante(instancia.getId(), tarea.getId())
            : List.of();

        return TareaDetalleResponse.builder()
                .id(tarea.getId())
                .estadoTarea(tarea.getEstadoTarea())
                .fechaCreacion(tarea.getFechaCreacion())
                .fechaInicio(tarea.getFechaInicio())
                .fechaFin(tarea.getFechaFin())
                .asignadoA(tarea.getAsignadoA())
            .asignadoANombre(resolverNombreUsuario(tarea.getAsignadoA()))
                .observaciones(tarea.getObservaciones())
                .actividad(TareaDetalleResponse.ActividadTareaResponse.builder()
                        .nodoId(tarea.getNodoId())
                        .nombreActividad(tarea.getNombreNodo())
                        .responsableTipo(tarea.getResponsableTipo())
                        .responsableId(tarea.getResponsableId())
                        .formularioDefinicion(tarea.getFormularioDefinicion())
                        .build())
                .formularioRespuesta(tarea.getFormularioRespuesta())
                .instancia(instanciaDetalle)
                .politica(TareaDetalleResponse.PoliticaResumenResponse.builder()
                        .id(politica != null ? politica.getId() : null)
                        .nombre(politica != null ? politica.getNombre() : null)
                        .descripcion(politica != null ? politica.getDescripcion() : null)
                        .estado(politica != null ? politica.getEstado() : null)
                        .build())
                .historialRelevante(historialRelevante)
                .build();
    }

    public TareaActividad tomarTarea(String actorUserId, String tareaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        TareaActividad tarea = buscarTarea(tareaId);

        validarPermisoEjecucion(actor, tarea);
        validarTareaTomable(tarea, actor.getId());

        LocalDateTime now = LocalDateTime.now();
        if (tarea.getFechaInicio() == null) {
            tarea.setFechaInicio(now);
        }
        tarea.setEstadoTarea(EstadoTarea.EN_PROCESO);
        tarea.setAsignadoA(actor.getId());

        TareaActividad guardada = tareaRepository.save(tarea);

        instanciaRepository.findById(guardada.getInstanciaId()).ifPresent(instancia -> {
            instancia.setFechaActualizacion(now);
            instanciaRepository.save(instancia);
        });

        historialService.registrar(
                guardada.getInstanciaId(),
                guardada.getId(),
                "TAREA_TOMADA",
                actor.getId(),
                "Tarea tomada por el actor"
        );
        return guardada;
    }

    public TareaActividad completarTarea(String actorUserId, String tareaId, CompletarTareaRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        TareaActividad tarea = buscarTarea(tareaId);

        validarPermisoEjecucion(actor, tarea);
        validarTareaCompletables(tarea, actor.getId());

        InstanciaPolitica instancia = instanciaRepository.findById(tarea.getInstanciaId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Instancia no encontrada para tarea " + tarea.getId()));

        if (instancia.getEstadoInstancia() == EstadoInstancia.FINALIZADA
                || instancia.getEstadoInstancia() == EstadoInstancia.CANCELADA) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede completar una tarea de una instancia cerrada");
        }

        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Politica no encontrada para la instancia " + instancia.getId()));

        validarCriterioContinuidadPolitica(instancia, politica, actor.getId());

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> respuesta = copiarMapa(request != null ? request.getFormularioRespuesta() : null);
        validarRespuestaCompleta(tarea, respuesta);

        if (tarea.getFechaInicio() == null) {
            tarea.setFechaInicio(now);
        }
        tarea.setFechaFin(now);
        tarea.setAsignadoA(actor.getId());
        tarea.setEstadoTarea(EstadoTarea.COMPLETADA);
        tarea.setFormularioRespuesta(respuesta);
        tarea.setObservaciones(normalizarTexto(request != null ? request.getObservaciones() : null));

        TareaActividad guardada = tareaRepository.save(tarea);

        if (instancia.getDatosContexto() == null) {
            instancia.setDatosContexto(new HashMap<>());
        }
        instancia.getDatosContexto().putAll(respuesta);
        instancia.setFechaActualizacion(now);
        instanciaRepository.save(instancia);

        historialService.registrar(
                instancia.getId(),
                guardada.getId(),
                "TAREA_COMPLETADA",
                actor.getId(),
                "Tarea completada en nodo " + guardada.getNodoId()
        );

        workflowEngineService.avanzarDesdeNodo(
                instancia,
                politica,
                guardada.getNodoId(),
                actor.getId(),
                instancia.getDatosContexto()
        );

        return tareaRepository.findById(guardada.getId()).orElse(guardada);
    }

    private void validarPermisoEjecucion(Usuario actor, TareaActividad tarea) {
        String responsableTipo = normalizarTexto(tarea.getResponsableTipo());
        String responsableId = normalizarTexto(tarea.getResponsableId());

        if (responsableTipo == null || responsableId == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La tarea no tiene responsable configurado");
        }

        String tipo = responsableTipo.toUpperCase(Locale.ROOT);
        switch (tipo) {
            case "USUARIO" -> {
                if (!actor.getId().equals(responsableId)) {
                    throw new ApiException(HttpStatus.FORBIDDEN,
                            "La tarea corresponde a otro usuario");
                }
            }
            case "DEPARTAMENTO" -> {
                if (!responsableId.equals(normalizarTexto(actor.getDepartamentoId()))) {
                    throw new ApiException(HttpStatus.FORBIDDEN,
                            "La tarea corresponde a otro departamento");
                }
            }
            default -> throw new ApiException(HttpStatus.CONFLICT,
                    "responsableTipo invalido en tarea: " + responsableTipo);
        }
    }

    private void validarPermisoLecturaTarea(Usuario actor, TareaActividad tarea) {
        if ("ADMIN".equalsIgnoreCase(actor.getRol())) {
            return;
        }
        validarPermisoEjecucion(actor, tarea);
    }

    private void validarAccesoLecturaInstancia(Usuario actor, String instanciaId) {
        if ("ADMIN".equalsIgnoreCase(actor.getRol())) {
            return;
        }

        if (tareaRepository.existsByInstanciaIdAndAsignadoA(instanciaId, actor.getId())) {
            return;
        }

        if (tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId(
                instanciaId,
                "USUARIO",
                actor.getId())) {
            return;
        }

        String departamentoId = normalizarTexto(actor.getDepartamentoId());
        if (departamentoId != null
                && tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId(
                    instanciaId,
                    "DEPARTAMENTO",
                    departamentoId)) {
            return;
        }

        throw new ApiException(HttpStatus.FORBIDDEN,
                "No tiene permisos para consultar datos de esta instancia");
    }

    private void validarTareaTomable(TareaActividad tarea, String actorUserId) {
        if (tarea.getEstadoTarea() != EstadoTarea.PENDIENTE && tarea.getEstadoTarea() != EstadoTarea.EN_PROCESO) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Solo se puede tomar una tarea en estado PENDIENTE o EN_PROCESO");
        }

        String asignadoActual = normalizarTexto(tarea.getAsignadoA());
        if (tarea.getEstadoTarea() == EstadoTarea.EN_PROCESO
                && asignadoActual != null
                && !asignadoActual.equals(actorUserId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La tarea ya esta en proceso por otro usuario");
        }
    }

    private void validarTareaCompletables(TareaActividad tarea, String actorUserId) {
        if (tarea.getEstadoTarea() != EstadoTarea.PENDIENTE && tarea.getEstadoTarea() != EstadoTarea.EN_PROCESO) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Solo se puede completar una tarea en estado PENDIENTE o EN_PROCESO");
        }

        String asignadoActual = normalizarTexto(tarea.getAsignadoA());
        if (tarea.getEstadoTarea() == EstadoTarea.EN_PROCESO
                && asignadoActual != null
                && !asignadoActual.equals(actorUserId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La tarea esta en proceso por otro usuario");
        }
    }

    private TareaActividad buscarTarea(String tareaId) {
        String id = normalizarTexto(tareaId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar la tarea");
        }

        return tareaRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Tarea no encontrada con ID: " + id));
    }

    private Usuario assertUsuarioActivo(String userId) {
        String actorId = normalizarTexto(userId);
        if (actorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-User-Id");
        }

        return usuarioRepository.findByIdAndActivo(actorId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
    }

    private void validarCriterioContinuidadPolitica(
            InstanciaPolitica instancia,
            PoliticaNegocio politica,
            String actorUserId
    ) {
        Long versionInstancia = instancia.getPoliticaVersion() != null ? instancia.getPoliticaVersion() : 0L;
        Long versionActual = politica.getSecuenciaColaboracion() != null ? politica.getSecuenciaColaboracion() : 0L;

        if (!versionActual.equals(versionInstancia)) {
            if (politica.getEstado() != EstadoPolitica.ACTIVA) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "No se puede completar la tarea por cambio de version de la politica");
            }

            instancia.setPoliticaVersion(versionActual);
            instancia.setFechaActualizacion(LocalDateTime.now());

            historialService.registrar(
                instancia.getId(),
                null,
                "INSTANCIA_CONTINUIDAD_VERSION_ACTUALIZADA",
                actorUserId,
                "La instancia se sincronizo automaticamente de la version "
                    + versionInstancia + " a la version " + versionActual + " de la politica"
            );
        }

        if (politica.getEstado() != EstadoPolitica.ACTIVA) {
            historialService.registrar(
                    instancia.getId(),
                    null,
                    "POLITICA_NO_ACTIVA_CONTINUIDAD",
                    actorUserId,
                    "La politica esta en estado " + politica.getEstado()
                            + " y la instancia continua con la version " + versionInstancia
            );
        }
    }

    private InstanciaDetalleResponse construirDetalleInstancia(InstanciaPolitica instancia, PoliticaNegocio politica) {
        long totalTareas = tareaRepository.countByInstanciaId(instancia.getId());
        long tareasAbiertas = tareaRepository.countByInstanciaIdAndEstadoTareaIn(
                instancia.getId(),
                List.of(EstadoTarea.PENDIENTE, EstadoTarea.EN_PROCESO)
        );

        return InstanciaDetalleResponse.builder()
                .id(instancia.getId())
                .politicaId(instancia.getPoliticaId())
                .politicaNombre(politica != null ? politica.getNombre() : null)
                .politicaDescripcion(politica != null ? politica.getDescripcion() : null)
                .politicaEstado(politica != null ? politica.getEstado() : null)
                .politicaVersion(instancia.getPoliticaVersion())
                .codigoTramite(instancia.getCodigoTramite())
                .estadoInstancia(instancia.getEstadoInstancia())
                .fechaCreacion(instancia.getFechaCreacion())
                .fechaActualizacion(instancia.getFechaActualizacion())
                .creadaPor(instancia.getCreadaPor())
                .creadaPorNombre(resolverNombreUsuario(instancia.getCreadaPor()))
                .datosContexto(instancia.getDatosContexto())
                .tokensJoin(instancia.getTokensJoin())
                .totalTareas(totalTareas)
                .tareasAbiertas(tareasAbiertas)
                .tareasCompletadas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.COMPLETADA))
                .tareasCanceladas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.CANCELADA))
                .tareasRechazadas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.RECHAZADA))
                .build();
    }

    private List<HistorialEventoResponse> construirHistorialRelevante(String instanciaId, String tareaId) {
        List<HistorialInstancia> historial = historialService.listarPorInstancia(instanciaId);
        List<HistorialEventoResponse> relevantes = new ArrayList<>();

        for (HistorialInstancia evento : historial) {
            boolean esEventoDeTarea = tareaId.equals(normalizarTexto(evento.getTareaId()));
            boolean esEventoGlobal = normalizarTexto(evento.getTareaId()) == null;
            if (!esEventoDeTarea && !esEventoGlobal) {
                continue;
            }

            relevantes.add(HistorialEventoResponse.builder()
                    .id(evento.getId())
                    .instanciaId(evento.getInstanciaId())
                    .tareaId(evento.getTareaId())
                    .accion(evento.getAccion())
                    .usuario(evento.getUsuario())
                    .fecha(evento.getFecha())
                    .detalle(evento.getDetalle())
                    .build());
        }

        return relevantes;
    }

    private InstanciaPolitica obtenerInstancia(Map<String, InstanciaPolitica> cache, String instanciaId) {
        String id = normalizarTexto(instanciaId);
        if (id == null) {
            return null;
        }
        if (!cache.containsKey(id)) {
            cache.put(id, instanciaRepository.findById(id).orElse(null));
        }
        return cache.get(id);
    }

    private PoliticaNegocio obtenerPolitica(Map<String, PoliticaNegocio> cache, String politicaId) {
        String id = normalizarTexto(politicaId);
        if (id == null) {
            return null;
        }
        if (!cache.containsKey(id)) {
            cache.put(id, politicaRepository.findById(id).orElse(null));
        }
        return cache.get(id);
    }

    private String calcularPrioridad(TareaActividad tarea) {
        if (tarea.getFechaCreacion() == null || tarea.getEstadoTarea() == null) {
            return "NORMAL";
        }

        if (tarea.getEstadoTarea() == EstadoTarea.COMPLETADA
                || tarea.getEstadoTarea() == EstadoTarea.CANCELADA
                || tarea.getEstadoTarea() == EstadoTarea.RECHAZADA) {
            return "NORMAL";
        }

        long horas = java.time.Duration.between(tarea.getFechaCreacion(), LocalDateTime.now()).toHours();
        if (horas >= 48) {
            return "ALTA";
        }
        if (horas >= 24) {
            return "MEDIA";
        }
        return "NORMAL";
    }

    private Map<String, Object> resumirContexto(Map<String, Object> contexto) {
        if (contexto == null || contexto.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        int agregados = 0;
        for (Map.Entry<String, Object> entry : contexto.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            resumen.put(entry.getKey(), entry.getValue());
            agregados++;
            if (agregados >= 5) {
                break;
            }
        }
        return resumen;
    }

    private void validarRespuestaCompleta(TareaActividad tarea, Map<String, Object> respuesta) {
        List<CampoFormulario> definicion = tarea.getFormularioDefinicion();
        if (definicion == null || definicion.isEmpty()) {
            return;
        }

        List<String> faltantes = new ArrayList<>();
        for (CampoFormulario campo : definicion) {
            String clave = normalizarTexto(campo != null ? campo.getCampo() : null);
            if (clave == null) {
                continue;
            }

            if (esValorFormularioVacio(respuesta.get(clave))) {
                faltantes.add(clave);
            }
        }

        if (!faltantes.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Todos los campos son obligatorios excepto observaciones. Faltan: " + String.join(", ", faltantes)
            );
        }
    }

    private boolean esValorFormularioVacio(Object value) {
        if (value == null) {
            return true;
        }

        if (value instanceof String texto) {
            return normalizarTexto(texto) == null;
        }

        if (value instanceof Map<?, ?> mapa) {
            return mapa.isEmpty();
        }

        if (value instanceof List<?> lista) {
            return lista.isEmpty();
        }

        return false;
    }

    private Map<String, Object> copiarMapa(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
    }

    private String resolverNombreUsuario(String userId) {
        String id = normalizarTexto(userId);
        if (id == null) {
            return null;
        }

        return usuarioRepository.findById(id)
                .map(Usuario::getNombre)
                .map(this::normalizarTexto)
                .orElse(null);
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
