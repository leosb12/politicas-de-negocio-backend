package com.leo.politicas_de_negocio.colaboracion.service;

import com.leo.politicas_de_negocio.politicas.dto.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.colaboracion.dto.ColaboracionEstadoResponse;
import com.leo.politicas_de_negocio.colaboracion.dto.ColaboracionEventoRequest;
import com.leo.politicas_de_negocio.colaboracion.dto.ColaboracionEventoResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.colaboracion.model.EventoColaboracionAplicado;
import com.leo.politicas_de_negocio.colaboracion.model.SnapshotColaboracionPolitica;
import com.leo.politicas_de_negocio.colaboracion.model.TipoEventoColaboracion;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.ConfiguracionDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosAdicionalesDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosLecturaSeccion;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosSeccion;
import com.leo.politicas_de_negocio.colaboracion.repository.EventoColaboracionAplicadoRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import com.leo.politicas_de_negocio.colaboracion.repository.SnapshotColaboracionPoliticaRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
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

    private static final String LANE_ORIENTATION_VERTICAL = "VERTICAL";
    private static final String LANE_ORIENTATION_HORIZONTAL = "HORIZONTAL";
    private static final double DEFAULT_LANE_WIDTH = 320d;
    private static final double DEFAULT_LANE_HEIGHT = 220d;
    private static final double MIN_LANE_WIDTH = 220d;
    private static final double MAX_LANE_WIDTH = 960d;
    private static final double MIN_LANE_HEIGHT = 140d;
    private static final double MAX_LANE_HEIGHT = 680d;

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

                aplicarResultadoCanvasConfig(persistidaFlujo, resultado);

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
                    .laneOrientation(persistidaFlujo.getLaneOrientation())
                    .laneWidth(persistidaFlujo.getLaneWidth())
                    .laneHeight(persistidaFlujo.getLaneHeight())
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
            case UPDATE_CANVAS_CONFIG -> aplicarUpdateCanvasConfig(nodos, conexiones, request, politica);
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
            "Nodo creado",
            null,
            null,
            null
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
            "Nodo actualizado",
            null,
            null,
            null
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
                "Nodo movido",
                null,
                null,
                null
        );
    }

    private ResultadoAplicacion aplicarUpdateCanvasConfig(
            List<Nodo> nodos,
            List<Conexion> conexiones,
            ColaboracionEventoRequest request,
            PoliticaNegocio politica
    ) {
        String laneOrientation = normalizarLaneOrientation(request.getLaneOrientation());
        Double laneWidth = normalizarLaneWidth(request.getLaneWidth());
        Double laneHeight = normalizarLaneHeight(request.getLaneHeight());

        String currentOrientation = normalizarLaneOrientation(politica.getLaneOrientation());
        Double currentWidth = normalizarLaneWidth(politica.getLaneWidth());
        Double currentHeight = normalizarLaneHeight(politica.getLaneHeight());

        String resolvedOrientation =
                laneOrientation != null ? laneOrientation : ensureLaneOrientationDefault(currentOrientation);
        Double resolvedWidth = laneWidth != null ? laneWidth : ensureLaneWidthDefault(currentWidth);
        Double resolvedHeight = laneHeight != null ? laneHeight : ensureLaneHeightDefault(currentHeight);

        return new ResultadoAplicacion(
                toFlujoRequest(nodos, conexiones),
                null,
                null,
                null,
                null,
                "Configuracion del canvas actualizada",
                resolvedOrientation,
                resolvedWidth,
                resolvedHeight
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
                "Nodo eliminado",
                null,
                null,
                null
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
            existe ? "Conexion actualizada" : "Conexion creada",
            null,
            null,
            null
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
                removida ? "Conexion eliminada" : "Conexion no existia",
                null,
                null,
                null
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
            "Flujo reemplazado",
            null,
            null,
            null
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

        if (request.getTipo() == TipoEventoColaboracion.UPDATE_CANVAS_CONFIG
                && request.getLaneOrientation() == null
                && request.getLaneWidth() == null
                && request.getLaneHeight() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UPDATE_CANVAS_CONFIG requiere laneOrientation, laneWidth o laneHeight"
            );
        }
    }

    private void validarSecuenciaEsperada(ColaboracionEventoRequest request, PoliticaNegocio politica) {
        if (request.getExpectedSequence() == null) {
            return;
        }

        long secuenciaActual = secuenciaActual(politica);
        if (request.getTipo() != TipoEventoColaboracion.MOVE_NODE
                && request.getTipo() != TipoEventoColaboracion.UPDATE_NODE
            && request.getTipo() != TipoEventoColaboracion.UPDATE_CANVAS_CONFIG
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
        String laneOrientation = ensureLaneOrientationDefault(
            normalizarLaneOrientation(politica.getLaneOrientation())
        );
        Double laneWidth = ensureLaneWidthDefault(normalizarLaneWidth(politica.getLaneWidth()));
        Double laneHeight = ensureLaneHeightDefault(normalizarLaneHeight(politica.getLaneHeight()));

        return ColaboracionEstadoResponse.builder()
                .politicaId(politica.getId())
                .estadoPolitica(politica.getEstado())
                .secuenciaActual(secuenciaActual(politica))
            .laneOrientation(laneOrientation)
            .laneWidth(laneWidth)
            .laneHeight(laneHeight)
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
                .formulario(clonarFormulario(nodo.getFormulario()))
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
                        ? clonarFormulario(patch.getFormulario())
                        : clonarFormulario(base.getFormulario()))
                .condiciones(patch.getCondiciones() != null
                        ? new ArrayList<>(patch.getCondiciones())
                        : (base.getCondiciones() != null ? new ArrayList<>(base.getCondiciones()) : null))
                .build();
    }

    private List<CampoFormulario> clonarFormulario(List<CampoFormulario> formulario) {
        if (formulario == null) {
            return null;
        }

        List<CampoFormulario> copia = new ArrayList<>();
        for (CampoFormulario campo : formulario) {
            if (campo == null) {
                continue;
            }

            copia.add(CampoFormulario.builder()
                    .campo(campo.getCampo())
                    .tipo(campo.getTipoRaw())
                    .etiqueta(campo.getEtiqueta())
                    .requerido(campo.getRequerido())
                    .placeholder(campo.getPlaceholder())
                    .ayuda(campo.getAyuda())
                    .orden(campo.getOrden())
                    .opciones(campo.getOpciones() != null ? new ArrayList<>(campo.getOpciones()) : null)
                    .validaciones(campo.getValidaciones())
                    .configuracionDocumento(clonarConfiguracionDocumento(campo.getConfiguracionDocumento()))
                    .build());
        }
        return copia;
    }

    private ConfiguracionDocumento clonarConfiguracionDocumento(ConfiguracionDocumento config) {
        if (config == null) {
            return null;
        }

        return ConfiguracionDocumento.builder()
                .tipoDocumento(config.getTipoDocumento())
                .modoColaboracion(config.getModoColaboracion())
                .permisosEdicion(clonarPermisosSeccion(config.getPermisosEdicion()))
                .permisosLectura(clonarPermisosLectura(config.getPermisosLectura()))
                .permisosDescarga(clonarPermisosSeccion(config.getPermisosDescarga()))
                .permisosImpresion(clonarPermisosSeccion(config.getPermisosImpresion()))
                .permisosComentarios(clonarPermisosSeccion(config.getPermisosComentarios()))
                .permisosReemplazo(clonarPermisosSeccion(config.getPermisosReemplazo()))
                .permisosEliminacion(clonarPermisosSeccion(config.getPermisosEliminacion()))
                .permisosCompartirInternamente(clonarPermisosSeccion(config.getPermisosCompartirInternamente()))
                .permisosAdicionales(clonarPermisosAdicionales(config.getPermisosAdicionales()))
                .auditarCambios(config.getAuditarCambios())
                .controlVersionesHabilitado(config.getControlVersionesHabilitado())
                .documentoPlantilla(config.getDocumentoPlantilla() != null ? ConfiguracionDocumento.DocumentoPlantilla.builder()
                        .nombreOriginal(config.getDocumentoPlantilla().getNombreOriginal())
                        .extension(config.getDocumentoPlantilla().getExtension())
                        .mimeType(config.getDocumentoPlantilla().getMimeType())
                        .url(config.getDocumentoPlantilla().getUrl())
                        .storageKey(config.getDocumentoPlantilla().getStorageKey())
                        .fechaSubida(config.getDocumentoPlantilla().getFechaSubida())
                        .build() : null)
                .build();
    }

    private PermisosSeccion clonarPermisosSeccion(PermisosSeccion permisos) {
        if (permisos == null) {
            return PermisosSeccion.builder()
                    .departamentos(new ArrayList<>())
                    .roles(new ArrayList<>())
                    .usuarios(new ArrayList<>())
                    .build();
        }

        return PermisosSeccion.builder()
                .departamentos(permisos.getDepartamentos() != null ? new ArrayList<>(permisos.getDepartamentos()) : new ArrayList<>())
                .roles(permisos.getRoles() != null ? new ArrayList<>(permisos.getRoles()) : new ArrayList<>())
                .usuarios(permisos.getUsuarios() != null ? new ArrayList<>(permisos.getUsuarios()) : new ArrayList<>())
                .build();
    }

    private PermisosLecturaSeccion clonarPermisosLectura(PermisosLecturaSeccion permisos) {
        if (permisos == null) {
            return PermisosLecturaSeccion.builder()
                    .departamentos(new ArrayList<>())
                    .roles(new ArrayList<>())
                    .usuarios(new ArrayList<>())
                    .incluirClienteIniciador(false)
                    .build();
        }

        return PermisosLecturaSeccion.builder()
                .departamentos(permisos.getDepartamentos() != null ? new ArrayList<>(permisos.getDepartamentos()) : new ArrayList<>())
                .roles(permisos.getRoles() != null ? new ArrayList<>(permisos.getRoles()) : new ArrayList<>())
                .usuarios(permisos.getUsuarios() != null ? new ArrayList<>(permisos.getUsuarios()) : new ArrayList<>())
                .incluirClienteIniciador(Boolean.TRUE.equals(permisos.getIncluirClienteIniciador()))
                .build();
    }

    private PermisosAdicionalesDocumento clonarPermisosAdicionales(PermisosAdicionalesDocumento permisos) {
        if (permisos == null) {
            return null;
        }

        return PermisosAdicionalesDocumento.builder()
                .puedeDescargar(permisos.getPuedeDescargar())
                .puedeComentar(permisos.getPuedeComentar())
                .puedeReemplazar(permisos.getPuedeReemplazar())
                .puedeEliminar(permisos.getPuedeEliminar())
                .puedeCompartirInternamente(permisos.getPuedeCompartirInternamente())
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

    private void aplicarResultadoCanvasConfig(PoliticaNegocio politica, ResultadoAplicacion resultado) {
        String laneOrientation = ensureLaneOrientationDefault(
                resultado.laneOrientation() != null
                        ? resultado.laneOrientation()
                        : normalizarLaneOrientation(politica.getLaneOrientation())
        );
        Double laneWidth = ensureLaneWidthDefault(
                resultado.laneWidth() != null
                        ? resultado.laneWidth()
                        : normalizarLaneWidth(politica.getLaneWidth())
        );
        Double laneHeight = ensureLaneHeightDefault(
                resultado.laneHeight() != null
                        ? resultado.laneHeight()
                        : normalizarLaneHeight(politica.getLaneHeight())
        );

        politica.setLaneOrientation(laneOrientation);
        politica.setLaneWidth(laneWidth);
        politica.setLaneHeight(laneHeight);
    }

    private String normalizarLaneOrientation(String value) {
        String normalized = normalizarTexto(value);
        if (normalized == null) {
            return null;
        }

        String upper = normalized.toUpperCase();
        if (!LANE_ORIENTATION_VERTICAL.equals(upper) && !LANE_ORIENTATION_HORIZONTAL.equals(upper)) {
            return null;
        }

        return upper;
    }

    private Double normalizarLaneWidth(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }

        return Math.max(MIN_LANE_WIDTH, Math.min(MAX_LANE_WIDTH, value));
    }

    private Double normalizarLaneHeight(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }

        return Math.max(MIN_LANE_HEIGHT, Math.min(MAX_LANE_HEIGHT, value));
    }

    private String ensureLaneOrientationDefault(String value) {
        return value != null ? value : LANE_ORIENTATION_VERTICAL;
    }

    private Double ensureLaneWidthDefault(Double value) {
        return value != null ? value : DEFAULT_LANE_WIDTH;
    }

    private Double ensureLaneHeightDefault(Double value) {
        return value != null ? value : DEFAULT_LANE_HEIGHT;
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
            String detalle,
            String laneOrientation,
            Double laneWidth,
            Double laneHeight
    ) {
    }
}
