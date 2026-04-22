package com.leo.politicas_de_negocio.instancias.service;

import com.leo.politicas_de_negocio.instancias.dto.CrearInstanciaRequest;
import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.workflow.service.WorkflowEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstanciaPoliticaService {

    private final InstanciaPoliticaRepository instanciaRepository;
    private final PoliticaNegocioRepository politicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialInstanciaService historialService;
    private final WorkflowEngineService workflowEngineService;
    private final TareaActividadRepository tareaRepository;

    public InstanciaPolitica crearInstancia(String actorUserId, CrearInstanciaRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos de la instancia");
        }

        String politicaId = normalizarTexto(request.getPoliticaId());
        if (politicaId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar politicaId");
        }

        PoliticaNegocio politica = politicaRepository.findById(politicaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Politica no encontrada con ID: " + politicaId));

        if (politica.getEstado() != EstadoPolitica.ACTIVA) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Solo se puede iniciar una instancia con una politica ACTIVA");
        }

        LocalDateTime now = LocalDateTime.now();
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .politicaId(politica.getId())
                .politicaVersion(politica.getSecuenciaColaboracion() != null ? politica.getSecuenciaColaboracion() : 0L)
                .codigoTramite(generarCodigoTramite(request.getCodigoTramite()))
                .estadoInstancia(EstadoInstancia.EN_CURSO)
                .fechaCreacion(now)
                .fechaActualizacion(now)
                .creadaPor(actor.getId())
                .datosContexto(copiarMapa(request.getDatosContexto()))
                .tokensJoin(new HashMap<>())
                .build();

        instancia = instanciaRepository.save(instancia);

        historialService.registrar(
                instancia.getId(),
                null,
                "INSTANCIA_CREADA",
                actor.getId(),
                "Instancia creada usando politica " + politica.getId()
        );

        workflowEngineService.iniciarInstancia(instancia, politica, actor.getId());

        return instanciaRepository.findById(instancia.getId()).orElse(instancia);
    }

    public InstanciaPolitica obtenerPorId(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        InstanciaPolitica instancia = buscarInstancia(instanciaId);
        validarAccesoLectura(actor, instancia);
        return instancia;
    }

    public InstanciaDetalleResponse obtenerDetallePorId(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        InstanciaPolitica instancia = buscarInstancia(instanciaId);
        validarAccesoLectura(actor, instancia);

        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId()).orElse(null);
        return construirDetalleInstancia(instancia, politica);
    }

    public List<InstanciaPolitica> listar(String actorUserId, EstadoInstancia estadoInstancia) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        boolean esAdmin = "ADMIN".equalsIgnoreCase(actor.getRol());

        if (estadoInstancia != null) {
            List<InstanciaPolitica> porEstado = instanciaRepository.findByEstadoInstanciaOrderByFechaCreacionDesc(estadoInstancia);
            if (esAdmin) {
                return porEstado;
            }
            return porEstado.stream()
                    .filter(instancia -> actor.getId().equals(instancia.getCreadaPor()))
                    .toList();
        }

        if (esAdmin) {
            return instanciaRepository.findAllByOrderByFechaCreacionDesc();
        }

        return instanciaRepository.findByCreadaPorOrderByFechaCreacionDesc(actor.getId());
    }

    public List<InstanciaDetalleResponse> listarDetalle(String actorUserId, EstadoInstancia estadoInstancia) {
        return listar(actorUserId, estadoInstancia).stream()
                .map(instancia -> {
                    PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId()).orElse(null);
                    return construirDetalleInstancia(instancia, politica);
                })
                .toList();
    }

    public List<HistorialInstancia> obtenerHistorial(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        if (!"ADMIN".equalsIgnoreCase(actor.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "El historial de instancia solo esta disponible para administradores");
        }
        InstanciaPolitica instancia = buscarInstancia(instanciaId);
        validarAccesoLectura(actor, instancia);
        return historialService.listarPorInstancia(instanciaId);
    }

    private InstanciaPolitica buscarInstancia(String instanciaId) {
        String id = normalizarTexto(instanciaId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id de instancia");
        }

        return instanciaRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Instancia no encontrada con ID: " + id));
    }

    private void validarAccesoLectura(Usuario actor, InstanciaPolitica instancia) {
        if ("ADMIN".equalsIgnoreCase(actor.getRol())) {
            return;
        }

        if (actor.getId().equals(instancia.getCreadaPor())) {
            return;
        }

        if (actorParticipaEnInstancia(actor, instancia.getId())) {
            return;
        }

        throw new ApiException(HttpStatus.FORBIDDEN,
                "No tiene permisos para consultar esta instancia");
    }

    private boolean actorParticipaEnInstancia(Usuario actor, String instanciaId) {
        if (tareaRepository.existsByInstanciaIdAndAsignadoA(instanciaId, actor.getId())) {
            return true;
        }

        if (tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId(
                instanciaId,
                "USUARIO",
                actor.getId()
        )) {
            return true;
        }

        String departamentoId = normalizarTexto(actor.getDepartamentoId());
        return departamentoId != null
                && tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId(
                    instanciaId,
                    "DEPARTAMENTO",
                    departamentoId
                );
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
                .fechaFinalizacion(instancia.getFechaFinalizacion())
                .creadaPor(instancia.getCreadaPor())
                .creadaPorNombre(resolverNombreUsuario(instancia.getCreadaPor()))
                .finalizadaPor(instancia.getFinalizadaPor())
                .finalizadaPorNombre(resolverNombreUsuario(instancia.getFinalizadaPor()))
                .datosContexto(instancia.getDatosContexto())
                .tokensJoin(instancia.getTokensJoin())
                .totalTareas(totalTareas)
                .tareasAbiertas(tareasAbiertas)
                .tareasCompletadas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.COMPLETADA))
                .tareasCanceladas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.CANCELADA))
                .tareasRechazadas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.RECHAZADA))
                .build();
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

    private Usuario assertUsuarioActivo(String userId) {
        String actorId = normalizarTexto(userId);
        if (actorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-User-Id");
        }

        return usuarioRepository.findByIdAndActivo(actorId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
    }

    private String generarCodigoTramite(String codigoSolicitado) {
        String codigo = normalizarTexto(codigoSolicitado);
        if (codigo != null) {
            return codigo;
        }
        return "TRM-" + System.currentTimeMillis();
    }

    private Map<String, Object> copiarMapa(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
