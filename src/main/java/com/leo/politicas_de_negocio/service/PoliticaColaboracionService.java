package com.leo.politicas_de_negocio.service;

import com.leo.politicas_de_negocio.dto.politica.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEstadoResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEventoRequest;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEventoResponse;
import com.leo.politicas_de_negocio.exception.ApiException;
import com.leo.politicas_de_negocio.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.model.Usuario;
import com.leo.politicas_de_negocio.model.colaboracion.EventoColaboracionAplicado;
import com.leo.politicas_de_negocio.model.colaboracion.SnapshotColaboracionPolitica;
import com.leo.politicas_de_negocio.model.colaboracion.TipoEventoColaboracion;
import com.leo.politicas_de_negocio.model.politica.Conexion;
import com.leo.politicas_de_negocio.model.politica.Nodo;
import com.leo.politicas_de_negocio.repository.EventoColaboracionAplicadoRepository;
import com.leo.politicas_de_negocio.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.repository.SnapshotColaboracionPoliticaRepository;
import com.leo.politicas_de_negocio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoliticaColaboracionService {

    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final PoliticaNegocioService politicaNegocioService;
    private final UsuarioRepository usuarioRepository;
    private final EventoColaboracionAplicadoRepository eventoRepository;
    private final SnapshotColaboracionPoliticaRepository snapshotRepository;

    private final ConcurrentMap<String, ReentrantLock> locksPorPolitica = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> politicasSuciasParaSnapshot = new ConcurrentHashMap<>();

    public ColaboracionEstadoResponse obtenerEstadoActual(String adminUserId, String politicaId) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = politicaNegocioRepository.findById(politicaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + politicaId));

        return toEstadoResponse(politica);
    }

    public List<EventoColaboracionAplicado> obtenerHistorialReciente(String adminUserId, String politicaId) {
        assertAdmin(adminUserId);
        return eventoRepository.findTop50ByPoliticaIdOrderBySecuenciaDesc(politicaId);
    }

    public ColaboracionEventoResponse aplicarEvento(String politicaId, ColaboracionEventoRequest request) {
        validarSolicitudEvento(request);
        assertAdmin(request.getActorUserId());

        ReentrantLock lock = locksPorPolitica.computeIfAbsent(politicaId, key -> new ReentrantLock());
        lock.lock();
        try {
            String eventId = normalizarTexto(request.getEventId());
            Optional<EventoColaboracionAplicado> eventoPrevio = eventoRepository.findByPoliticaIdAndEventId(politicaId, eventId);
            if (eventoPrevio.isPresent()) {
                Long secuencia = eventoPrevio.get().getSecuencia();
                return ColaboracionEventoResponse.builder()
                        .politicaId(politicaId)
                        .eventId(eventId)
                        .actorUserId(request.getActorUserId())
                        .tipo(request.getTipo())
                        .secuencia(secuencia)
                        .estado("DUPLICADO")
                        .detalle("Evento ya aplicado previamente")
                        .serverTimestamp(LocalDateTime.now())
                        .build();
            }

            PoliticaNegocio politica = politicaNegocioRepository.findById(politicaId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + politicaId));

            validarSecuenciaEsperada(request, politica);

            ResultadoAplicacion resultado = aplicarEventoEnMemoria(politica, request);

            PoliticaNegocio persistidaFlujo = politicaNegocioService.guardarFlujo(
                    request.getActorUserId(),
                    politicaId,
                    resultado.flujoActualizado()
            );

            long siguienteSecuencia = secuenciaActual(persistidaFlujo) + 1;
            persistidaFlujo.setSecuenciaColaboracion(siguienteSecuencia);
            LocalDateTime now = LocalDateTime.now();
            persistidaFlujo.setFechaUltimaColaboracion(now);
            persistidaFlujo.setFechaActualizacion(now);
            politicaNegocioRepository.save(persistidaFlujo);

            registrarEventoAplicado(politicaId, eventId, request, siguienteSecuencia, now);
            politicasSuciasParaSnapshot.merge(politicaId, siguienteSecuencia, Math::max);

            return ColaboracionEventoResponse.builder()
                    .politicaId(politicaId)
                    .eventId(eventId)
                    .actorUserId(request.getActorUserId())
                    .tipo(request.getTipo())
                    .secuencia(siguienteSecuencia)
                    .estado("APLICADO")
                    .detalle(resultado.detalle())
                    .nodeId(resultado.nodeId())
                    .nodeVersion(resultado.nodeVersion())
                    .posX(request.getTipo() == TipoEventoColaboracion.MOVE_NODE ? request.getPosX() : null)
                    .posY(request.getTipo() == TipoEventoColaboracion.MOVE_NODE ? request.getPosY() : null)
                    .nodo(resultado.nodo())
                    .conexion(resultado.conexion())
                        .nodos(request.getTipo() == TipoEventoColaboracion.REPLACE_FLOW
                            ? clonarNodos(resultado.flujoActualizado().getNodos())
                            : null)
                        .conexiones(request.getTipo() == TipoEventoColaboracion.REPLACE_FLOW
                            ? clonarConexiones(resultado.flujoActualizado().getConexiones())
                            : null)
                    .serverTimestamp(now)
                    .build();
        } catch (DuplicateKeyException ex) {
            Optional<EventoColaboracionAplicado> eventoPrevio = eventoRepository
                    .findByPoliticaIdAndEventId(politicaId, request.getEventId());
            if (eventoPrevio.isPresent()) {
                return ColaboracionEventoResponse.builder()
                        .politicaId(politicaId)
                        .eventId(request.getEventId())
                        .actorUserId(request.getActorUserId())
                        .tipo(request.getTipo())
                        .secuencia(eventoPrevio.get().getSecuencia())
                        .estado("DUPLICADO")
                        .detalle("Evento repetido detectado por idempotencia")
                        .serverTimestamp(LocalDateTime.now())
                        .build();
            }
            throw ex;
        } finally {
            lock.unlock();
        }
    }

    @Scheduled(fixedDelayString = "${app.collab.snapshot-interval-ms:5000}")
    public void persistirSnapshotsPendientes() {
        for (String politicaId : new ArrayList<>(politicasSuciasParaSnapshot.keySet())) {
            Long secuenciaMarcada = politicasSuciasParaSnapshot.remove(politicaId);
            if (secuenciaMarcada == null) {
                continue;
            }

            try {
                guardarSnapshotSiHaceFalta(politicaId);
            } catch (Exception ex) {
                log.warn("No se pudo generar snapshot colaborativo de politica {}: {}", politicaId, ex.getMessage());
            }
        }
    }

    private void guardarSnapshotSiHaceFalta(String politicaId) {
        PoliticaNegocio politica = politicaNegocioRepository.findById(politicaId).orElse(null);
        if (politica == null) {
            return;
        }

        long secuenciaActual = secuenciaActual(politica);
        if (secuenciaActual <= 0) {
            return;
        }

        Optional<SnapshotColaboracionPolitica> ultimoSnapshot =
                snapshotRepository.findTopByPoliticaIdOrderBySecuenciaDesc(politicaId);
        if (ultimoSnapshot.isPresent() && Objects.equals(ultimoSnapshot.get().getSecuencia(), secuenciaActual)) {
            return;
        }

        snapshotRepository.save(SnapshotColaboracionPolitica.builder()
                .politicaId(politicaId)
                .secuencia(secuenciaActual)
                .nodos(clonarNodos(politica.getNodos()))
                .conexiones(clonarConexiones(politica.getConexiones()))
                .fechaCreacion(LocalDateTime.now())
                .build());
    }

    private ResultadoAplicacion aplicarEventoEnMemoria(PoliticaNegocio politica, ColaboracionEventoRequest request) {
        List<Nodo> nodos = clonarNodos(politica.getNodos());
        List<Conexion> conexiones = clonarConexiones(politica.getConexiones());
        LocalDateTime now = LocalDateTime.now();

        return switch (request.getTipo()) {
            case CREATE_NODE -> aplicarCreateNode(nodos, conexiones, request, now);
            case UPDATE_NODE -> aplicarUpdateNode(nodos, conexiones, request, now);
            case MOVE_NODE -> aplicarMoveNode(nodos, conexiones, request, now);
            case DELETE_NODE -> aplicarDeleteNode(nodos, conexiones, request);
            case CREATE_EDGE -> aplicarCreateEdge(nodos, conexiones, request);
            case DELETE_EDGE -> aplicarDeleteEdge(nodos, conexiones, request);
            case REPLACE_FLOW -> aplicarReplaceFlow(request, now);
        };
    }

    private ResultadoAplicacion aplicarCreateNode(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request,
            LocalDateTime now
    ) {
        if (request.getNodo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CREATE_NODE requiere el objeto nodo");
        }

        Nodo nuevo = clonarNodo(request.getNodo());
        String nodeId = normalizarTexto(nuevo.getId());
        if (nodeId == null) {
            nodeId = UUID.randomUUID().toString();
        }
        if (indiceNodo(nodos, nodeId) >= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un nodo con id " + nodeId);
        }

        nuevo.setId(nodeId);
        if (nuevo.getVersion() == null || nuevo.getVersion() < 0) {
            nuevo.setVersion(0L);
        }
        nuevo.setFechaActualizacion(now);
        nodos.add(nuevo);

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
                nodeId,
                nuevo.getVersion(),
                nuevo,
                null,
                "Nodo creado"
        );
    }

    private ResultadoAplicacion aplicarUpdateNode(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request,
            LocalDateTime now
    ) {
        if (request.getNodo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPDATE_NODE requiere el objeto nodo");
        }

        Nodo patch = clonarNodo(request.getNodo());
        String nodeId = normalizarTexto(request.getNodeId());
        if (nodeId == null) {
            nodeId = normalizarTexto(patch.getId());
        }
        if (nodeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPDATE_NODE requiere nodeId");
        }

        int index = indiceNodo(nodos, nodeId);
        if (index < 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nodo no encontrado: " + nodeId);
        }

        Nodo actual = nodos.get(index);
        long versionActual = versionNodo(actual);
        // Last-write-wins para edicion concurrente de campos en el mismo nodo.
        Nodo actualizado = mezclarNodoConLww(actual, patch, nodeId, versionActual + 1, now);
        nodos.set(index, actualizado);

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
                nodeId,
                actualizado.getVersion(),
                actualizado,
                null,
                "Nodo actualizado"
        );
    }

    private ResultadoAplicacion aplicarMoveNode(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request,
            LocalDateTime now
    ) {
        String nodeId = normalizarTexto(request.getNodeId());
        if (nodeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MOVE_NODE requiere nodeId");
        }

        int index = indiceNodo(nodos, nodeId);
        if (index < 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nodo no encontrado: " + nodeId);
        }

        Nodo nodo = nodos.get(index);
        if (request.getPosX() == null && request.getPosY() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MOVE_NODE requiere posX y/o posY");
        }

        // Last Write Wins para layout visual.
        if (request.getPosX() != null) {
            nodo.setPosX(request.getPosX());
        }
        if (request.getPosY() != null) {
            nodo.setPosY(request.getPosY());
        }

        nodo.setVersion(versionNodo(nodo) + 1);
        nodo.setFechaActualizacion(now);

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
                nodeId,
                nodo.getVersion(),
                nodo,
                null,
                "Nodo movido"
        );
    }

    private ResultadoAplicacion aplicarDeleteNode(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request
    ) {
        String nodeId = normalizarTexto(request.getNodeId());
        if (nodeId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DELETE_NODE requiere nodeId");
        }

        int index = indiceNodo(nodos, nodeId);
        if (index < 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Nodo no encontrado: " + nodeId);
        }

        Nodo actual = nodos.get(index);
        long versionActual = versionNodo(actual);
        if (request.getExpectedNodeVersion() != null && request.getExpectedNodeVersion() < versionActual) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Nodo desactualizado: version actual " + versionActual + ", expected " + request.getExpectedNodeVersion()
            );
        }

        nodos.remove(index);
        conexiones.removeIf(c -> nodeId.equals(c.getOrigen()) || nodeId.equals(c.getDestino()));

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
                nodeId,
                null,
                null,
                null,
                "Nodo eliminado"
        );
    }

    private ResultadoAplicacion aplicarCreateEdge(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request
    ) {
        Conexion conexion = clonarConexion(request.getConexion());
        if (conexion == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CREATE_EDGE requiere el objeto conexion");
        }

        String origen = normalizarTexto(conexion.getOrigen());
        String destino = normalizarTexto(conexion.getDestino());
        String puertoOrigen = normalizarPuerto(conexion.getPuertoOrigen());
        String puertoDestino = normalizarPuerto(conexion.getPuertoDestino());
        if (origen == null || destino == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CREATE_EDGE requiere origen y destino");
        }

        if (indiceNodo(nodos, origen) < 0 || indiceNodo(nodos, destino) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se puede crear conexion entre nodos inexistentes");
        }

        Conexion conexionNormalizada = Conexion.builder()
                .origen(origen)
                .destino(destino)
                .puertoOrigen(puertoOrigen)
                .puertoDestino(puertoDestino)
                .build();

        int indiceConexion = -1;
        for (int i = 0; i < conexiones.size(); i++) {
            Conexion actual = conexiones.get(i);
            if (origen.equals(actual.getOrigen()) && destino.equals(actual.getDestino())) {
                indiceConexion = i;
                break;
            }
        }

        boolean existe = indiceConexion >= 0;
        if (existe) {
            conexiones.set(indiceConexion, conexionNormalizada);
        } else {
            conexiones.add(conexionNormalizada);
        }

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
            request.getNodeId(),
                null,
                null,
                clonarConexion(conexionNormalizada),
                existe ? "Conexion actualizada" : "Conexion creada"
        );
    }

    private ResultadoAplicacion aplicarDeleteEdge(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request
    ) {
        Conexion conexion = clonarConexion(request.getConexion());
        if (conexion == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DELETE_EDGE requiere el objeto conexion");
        }

        String origen = normalizarTexto(conexion.getOrigen());
        String destino = normalizarTexto(conexion.getDestino());
        String puertoOrigen = normalizarPuerto(conexion.getPuertoOrigen());
        String puertoDestino = normalizarPuerto(conexion.getPuertoDestino());
        if (origen == null || destino == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DELETE_EDGE requiere origen y destino");
        }

        boolean removida = conexiones.removeIf(c -> origen.equals(c.getOrigen()) && destino.equals(c.getDestino()));

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
            request.getNodeId(),
                null,
                null,
            Conexion.builder()
                .origen(origen)
                .destino(destino)
                .puertoOrigen(puertoOrigen)
                .puertoDestino(puertoDestino)
                .build(),
                removida ? "Conexion eliminada" : "Conexion no existia"
        );
    }

    private ResultadoAplicacion aplicarReplaceFlow(ColaboracionEventoRequest request, LocalDateTime now) {
        List<Nodo> nuevosNodos = clonarNodos(request.getNodos());
        List<Conexion> nuevasConexiones = clonarConexiones(request.getConexiones());

        for (Nodo nodo : nuevosNodos) {
            if (nodo.getId() == null || nodo.getId().isBlank()) {
                nodo.setId(UUID.randomUUID().toString());
            }
            if (nodo.getVersion() == null || nodo.getVersion() < 0) {
                nodo.setVersion(0L);
            }
            nodo.setFechaActualizacion(now);
        }

        return new ResultadoAplicacion(
                toFlujoRequest(nuevosNodos, nuevasConexiones),
                null,
                null,
                null,
                null,
                "Flujo reemplazado"
        );
    }

    private void validarSolicitudEvento(ColaboracionEventoRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el evento colaborativo");
        }
        if (normalizarTexto(request.getEventId()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "eventId es obligatorio para idempotencia");
        }
        if (normalizarTexto(request.getActorUserId()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "actorUserId es obligatorio");
        }
        if (request.getTipo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "tipo de evento es obligatorio");
        }
    }

    private void validarSecuenciaEsperada(ColaboracionEventoRequest request, PoliticaNegocio politica) {
        if (request.getExpectedSequence() == null) {
            return;
        }

        long secuenciaActual = secuenciaActual(politica);
        if (request.getTipo() != TipoEventoColaboracion.MOVE_NODE
                && request.getTipo() != TipoEventoColaboracion.UPDATE_NODE
                && request.getExpectedSequence() < secuenciaActual) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Evento desactualizado: secuencia actual " + secuenciaActual + ", expected " + request.getExpectedSequence()
            );
        }
    }

    private void registrarEventoAplicado(
            String politicaId,
            String eventId,
            ColaboracionEventoRequest request,
            Long secuencia,
            LocalDateTime fechaAplicacion
    ) {
        eventoRepository.save(EventoColaboracionAplicado.builder()
                .politicaId(politicaId)
                .eventId(eventId)
                .actorUserId(request.getActorUserId())
                .tipo(request.getTipo())
                .secuencia(secuencia)
                .fechaAplicacion(fechaAplicacion)
                .build());
    }

    private void assertAdmin(String adminUserId) {
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el actorUserId del admin");
        }

        Usuario admin = usuarioRepository.findByIdAndActivo(adminUserId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));

        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede editar politicas en colaboracion");
        }
    }

    private ColaboracionEstadoResponse toEstadoResponse(PoliticaNegocio politica) {
        return ColaboracionEstadoResponse.builder()
                .politicaId(politica.getId())
                .estadoPolitica(politica.getEstado())
                .secuenciaActual(secuenciaActual(politica))
                .nodos(clonarNodos(politica.getNodos()))
                .conexiones(clonarConexiones(politica.getConexiones()))
                .fechaUltimaColaboracion(politica.getFechaUltimaColaboracion())
                .fechaActualizacion(politica.getFechaActualizacion())
                .build();
    }

    private UpdateFlujoRequest toFlujoRequest(List<Nodo> nodos, List<Conexion> conexiones) {
        UpdateFlujoRequest request = new UpdateFlujoRequest();
        request.setNodos(nodos);
        request.setConexiones(conexiones);
        return request;
    }

    private List<Nodo> clonarNodos(List<Nodo> source) {
        List<Nodo> nodos = new ArrayList<>();
        if (source == null) {
            return nodos;
        }

        for (Nodo nodo : source) {
            nodos.add(clonarNodo(nodo));
        }
        return nodos;
    }

    private List<Conexion> clonarConexiones(List<Conexion> source) {
        List<Conexion> conexiones = new ArrayList<>();
        if (source == null) {
            return conexiones;
        }

        for (Conexion conexion : source) {
            conexiones.add(clonarConexion(conexion));
        }
        return conexiones;
    }

    private Nodo clonarNodo(Nodo nodo) {
        if (nodo == null) {
            return null;
        }

        return Nodo.builder()
                .id(nodo.getId())
                .tipo(nodo.getTipo())
                .nombre(nodo.getNombre())
                .departamentoId(nodo.getDepartamentoId())
                .responsableTipo(nodo.getResponsableTipo())
                .responsableId(nodo.getResponsableId())
                .posX(nodo.getPosX())
                .posY(nodo.getPosY())
                .version(nodo.getVersion())
                .fechaActualizacion(nodo.getFechaActualizacion())
                .formulario(nodo.getFormulario() != null ? new ArrayList<>(nodo.getFormulario()) : null)
                .condiciones(nodo.getCondiciones() != null ? new ArrayList<>(nodo.getCondiciones()) : null)
                .build();
    }

    private Nodo mezclarNodoConLww(
            Nodo base,
            Nodo patch,
            String nodeId,
            long nuevaVersion,
            LocalDateTime now
    ) {
        return Nodo.builder()
                .id(nodeId)
                .tipo(patch.getTipo() != null ? patch.getTipo() : base.getTipo())
                .nombre(patch.getNombre() != null ? patch.getNombre() : base.getNombre())
                .departamentoId(patch.getDepartamentoId() != null ? patch.getDepartamentoId() : base.getDepartamentoId())
                .responsableTipo(patch.getResponsableTipo() != null ? patch.getResponsableTipo() : base.getResponsableTipo())
                .responsableId(patch.getResponsableId() != null ? patch.getResponsableId() : base.getResponsableId())
                .posX(patch.getPosX() != null ? patch.getPosX() : base.getPosX())
                .posY(patch.getPosY() != null ? patch.getPosY() : base.getPosY())
                .version(nuevaVersion)
                .fechaActualizacion(now)
                .formulario(patch.getFormulario() != null
                        ? new ArrayList<>(patch.getFormulario())
                        : (base.getFormulario() != null ? new ArrayList<>(base.getFormulario()) : null))
                .condiciones(patch.getCondiciones() != null
                        ? new ArrayList<>(patch.getCondiciones())
                        : (base.getCondiciones() != null ? new ArrayList<>(base.getCondiciones()) : null))
                .build();
    }

    private Conexion clonarConexion(Conexion conexion) {
        if (conexion == null) {
            return null;
        }

        return Conexion.builder()
                .origen(conexion.getOrigen())
                .destino(conexion.getDestino())
                .puertoOrigen(conexion.getPuertoOrigen())
                .puertoDestino(conexion.getPuertoDestino())
                .build();
    }

    private int indiceNodo(List<Nodo> nodos, String nodeId) {
        if (nodeId == null) {
            return -1;
        }

        for (int i = 0; i < nodos.size(); i++) {
            Nodo nodo = nodos.get(i);
            if (nodo != null && nodeId.equals(nodo.getId())) {
                return i;
            }
        }
        return -1;
    }

    private long secuenciaActual(PoliticaNegocio politica) {
        if (politica.getSecuenciaColaboracion() == null || politica.getSecuenciaColaboracion() < 0) {
            return 0L;
        }
        return politica.getSecuenciaColaboracion();
    }

    private long versionNodo(Nodo nodo) {
        if (nodo.getVersion() == null || nodo.getVersion() < 0) {
            return 0L;
        }
        return nodo.getVersion();
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizarPuerto(String value) {
        String normalized = normalizarTexto(value);
        if (normalized == null) {
            return null;
        }

        String upper = normalized.toUpperCase();
        if (!"LEFT".equals(upper)
                && !"RIGHT".equals(upper)
                && !"TOP".equals(upper)
                && !"BOTTOM".equals(upper)) {
            return null;
        }

        return upper;
    }

    private record ResultadoAplicacion(
            UpdateFlujoRequest flujoActualizado,
            String nodeId,
            Long nodeVersion,
            Nodo nodo,
            Conexion conexion,
            String detalle
    ) {
    }
}
