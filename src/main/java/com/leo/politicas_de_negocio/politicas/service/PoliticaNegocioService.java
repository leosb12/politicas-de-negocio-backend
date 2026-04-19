package com.leo.politicas_de_negocio.politicas.service;

import com.leo.politicas_de_negocio.colaboracion.repository.EventoColaboracionAplicadoRepository;
import com.leo.politicas_de_negocio.colaboracion.repository.SnapshotColaboracionPoliticaRepository;
import com.leo.politicas_de_negocio.colaboracion.service.PoliticaPresenciaService;
import com.leo.politicas_de_negocio.politicas.dto.CreatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.dto.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.ResponsableTipo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PoliticaNegocioService {

    private static final String DEFAULT_LANE_ORIENTATION = "VERTICAL";
    private static final double DEFAULT_LANE_WIDTH = 320d;
    private static final double DEFAULT_LANE_HEIGHT = 220d;
    private static final String RESPONSABLE_USUARIO_FINAL_ID = "__RESPONSABLE_USUARIO_FINAL__";
    private static final String RESPONSABLE_INICIADOR_TRAMITE_ID = "__RESPONSABLE_INICIADOR_TRAMITE__";

    private static final Set<EstadoPolitica> ESTADOS_ELIMINABLES =
        Set.of(EstadoPolitica.BORRADOR, EstadoPolitica.DESHABILITADA);

    private static final Set<String> COLECCIONES_EXCLUIDAS_REFERENCIA_POLITICA = Set.of(
        "politicas_negocio",
        "politicas_eventos_colaboracion",
        "politicas_snapshots_colaboracion"
    );

    private final PoliticaNegocioRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final EventoColaboracionAplicadoRepository eventoColaboracionRepository;
    private final SnapshotColaboracionPoliticaRepository snapshotColaboracionRepository;
    private final PoliticaPresenciaService presenciaService;
    private final MongoTemplate mongoTemplate;

    private Usuario assertAdmin(String adminUserId) {
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }
        Usuario admin = usuarioRepository.findById(adminUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));
        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede realizar esta acción");
        }
        return admin;
    }

    public PoliticaNegocio crearPolitica(String adminUserId, CreatePoliticaRequest request) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .estado(EstadoPolitica.BORRADOR)
            .fueActivada(false)
                .nodos(new ArrayList<>())
                .conexiones(new ArrayList<>())
                .laneOrientation(DEFAULT_LANE_ORIENTATION)
                .laneWidth(DEFAULT_LANE_WIDTH)
                .laneHeight(DEFAULT_LANE_HEIGHT)
                .secuenciaColaboracion(0L)
                .fechaUltimaColaboracion(LocalDateTime.now())
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();
        return repository.save(politica);
    }

    public List<PoliticaNegocio> obtenerTodas(String adminUserId) {
        assertAdmin(adminUserId);
        return repository.findAll();
    }

    public PoliticaNegocio obtenerPorId(String adminUserId, String id) {
        assertAdmin(adminUserId);
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Política no encontrada con ID: " + id));
    }

    public PoliticaNegocio guardarFlujo(String adminUserId, String id, UpdateFlujoRequest request) {
        assertAdmin(adminUserId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el flujo de la politica");
        }

        PoliticaNegocio politica = obtenerPorId(adminUserId, id);
        
        // No se puede modificar el flujo si ya está activa y no queremos romper instancias vivas.
        // Podría permitirse si hacemos versionado, pero por ahora simplificamos
        if (politica.getEstado() == EstadoPolitica.ACTIVA) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se puede modificar el flujo de una política activa");
        }

        List<Nodo> nodos = request.getNodos() != null ? request.getNodos() : new ArrayList<>();
        validarResponsablesYNodos(nodos, false);
        inicializarMetadatosColaborativosNodos(nodos);

        politica.setNodos(nodos);
        politica.setConexiones(request.getConexiones() != null ? request.getConexiones() : new ArrayList<>());
        if (normalizeNullableText(politica.getLaneOrientation()) == null) {
            politica.setLaneOrientation(DEFAULT_LANE_ORIENTATION);
        }
        if (politica.getLaneWidth() == null || politica.getLaneWidth() <= 0) {
            politica.setLaneWidth(DEFAULT_LANE_WIDTH);
        }
        if (politica.getLaneHeight() == null || politica.getLaneHeight() <= 0) {
            politica.setLaneHeight(DEFAULT_LANE_HEIGHT);
        }
        politica.setFechaActualizacion(LocalDateTime.now());

        return repository.save(politica);
    }

    public PoliticaNegocio cambiarEstado(String adminUserId, String id, EstadoPolitica nuevoEstado) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = obtenerPorId(adminUserId, id);

        if (nuevoEstado == EstadoPolitica.ACTIVA) {
            validarPoliticaParaActivacion(politica);
        }

        if (Boolean.TRUE.equals(politica.getFueActivada())
                || politica.getEstado() == EstadoPolitica.ACTIVA
                || politica.getEstado() == EstadoPolitica.PAUSADA
                || nuevoEstado == EstadoPolitica.ACTIVA) {
            politica.setFueActivada(true);
        }

        politica.setEstado(nuevoEstado);
        politica.setFechaActualizacion(LocalDateTime.now());
        return repository.save(politica);
    }

    public void eliminarPolitica(String adminUserId, String id) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = obtenerPorId(adminUserId, id);
        validarPoliticaEliminable(politica);
        repository.delete(politica);
    }

    private void validarPoliticaEliminable(PoliticaNegocio politica) {
        if (!ESTADOS_ELIMINABLES.contains(politica.getEstado())) {
            throw bloqueoEliminacion(
                    "Solo se puede eliminar una politica en estado BORRADOR o DESHABILITADA"
            );
        }

        if (presenciaService.tieneActividadActiva(politica.getId())) {
            throw bloqueoEliminacion(
                    "La politica no puede eliminarse porque esta siendo utilizada actualmente"
            );
        }

        if (Boolean.TRUE.equals(politica.getFueActivada())) {
            throw bloqueoEliminacion(
                    "La politica ya fue utilizada y no puede eliminarse"
            );
        }

        Long secuenciaColaboracion = politica.getSecuenciaColaboracion();
        if (secuenciaColaboracion != null && secuenciaColaboracion > 0) {
            throw bloqueoEliminacion(
                    "La politica no puede eliminarse porque tiene ejecuciones colaborativas registradas"
            );
        }

        if (eventoColaboracionRepository.existsByPoliticaId(politica.getId())
                || snapshotColaboracionRepository.existsByPoliticaId(politica.getId())) {
            throw bloqueoEliminacion(
                    "La politica no puede eliminarse porque tiene historial asociado"
            );
        }

        List<String> coleccionesReferenciando = buscarColeccionesConReferencia(politica.getId());
        if (!coleccionesReferenciando.isEmpty()) {
            throw bloqueoEliminacion(
                    "La politica no puede eliminarse porque tiene instancias o referencias en "
                            + String.join(", ", coleccionesReferenciando)
            );
        }
    }

    private ApiException bloqueoEliminacion(String detalle) {
        return new ApiException(
                HttpStatus.CONFLICT,
                detalle + ". Si la politica ya fue usada, debe desactivarse en lugar de eliminarse"
        );
    }

    private List<String> buscarColeccionesConReferencia(String politicaId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("politicaId").is(politicaId),
                Criteria.where("idPolitica").is(politicaId),
                Criteria.where("politicaNegocioId").is(politicaId)
        ));

        return mongoTemplate.getCollectionNames().stream()
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .filter(nombre -> !nombre.startsWith("system."))
                .filter(nombre -> !COLECCIONES_EXCLUIDAS_REFERENCIA_POLITICA.contains(nombre))
                .filter(nombre -> mongoTemplate.exists(query, nombre))
                .sorted()
                .toList();
    }

    private void validarPoliticaParaActivacion(PoliticaNegocio politica) {
        if (politica.getNodos() == null || politica.getNodos().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La política debe tener al menos un nodo para ser activada");
        }

        validarResponsablesYNodos(politica.getNodos(), true);
        
        boolean tieneInicio = politica.getNodos().stream().anyMatch(n -> n.getTipo() == TipoNodo.INICIO);
        boolean tieneFin = politica.getNodos().stream().anyMatch(n -> n.getTipo() == TipoNodo.FIN);

        if (!tieneInicio || !tieneFin) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La política debe tener al menos un nodo de INICIO y un nodo de FIN para ser activada");
        }
    }

    private void validarResponsablesYNodos(List<Nodo> nodos, boolean exigirResponsableEnActividad) {
        for (int i = 0; i < nodos.size(); i++) {
            Nodo nodo = nodos.get(i);
            if (nodo == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El nodo en posicion " + i + " no puede ser nulo");
            }

            if (nodo.getTipo() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El nodo en posicion " + i + " debe tener tipo");
            }

            String carrilDepartamentoId = normalizeNullableText(nodo.getDepartamentoId());
            nodo.setDepartamentoId(carrilDepartamentoId);
            if (carrilDepartamentoId != null && !departamentoRepository.existsById(carrilDepartamentoId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "El carril visual (departamentoId) del nodo " + safeNodeName(nodo, i) + " no existe");
            }

            String responsableId = normalizeNullableText(nodo.getResponsableId());
            nodo.setResponsableId(responsableId);
            ResponsableTipo responsableTipo = parseResponsableTipo(nodo);

            if (nodo.getTipo() != TipoNodo.ACTIVIDAD && (responsableTipo != null || responsableId != null)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Solo los nodos ACTIVIDAD pueden definir responsable real");
            }

            if (nodo.getTipo() == TipoNodo.ACTIVIDAD) {
                if (responsableTipo == null && responsableId != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El nodo " + safeNodeName(nodo, i) + " tiene responsableId pero no responsableTipo");
                }

                if (responsableTipo != null && responsableId == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El nodo " + safeNodeName(nodo, i) + " debe indicar responsableId");
                }

                if (exigirResponsableEnActividad && (responsableTipo == null || responsableId == null)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El nodo ACTIVIDAD " + safeNodeName(nodo, i)
                                    + " debe tener responsableTipo y responsableId para activar la politica");
                }

                if (responsableTipo != null && responsableId != null) {
                    validarExistenciaResponsable(responsableTipo, responsableId, nodo, i);
                }
            }
        }
    }

    private void validarExistenciaResponsable(
            ResponsableTipo responsableTipo,
            String responsableId,
            Nodo nodo,
            int index
    ) {
        switch (responsableTipo) {
            case DEPARTAMENTO -> {
                if (!departamentoRepository.existsById(responsableId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El responsable DEPARTAMENTO del nodo " + safeNodeName(nodo, index) + " no existe");
                }
            }
            case USUARIO -> {
                if (isResponsableUsuarioDinamico(responsableId)) {
                    return;
                }

                if (!usuarioRepository.existsById(responsableId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El responsable USUARIO del nodo " + safeNodeName(nodo, index) + " no existe");
                }
            }
        }
    }

    private boolean isResponsableUsuarioDinamico(String responsableId) {
        return RESPONSABLE_USUARIO_FINAL_ID.equals(responsableId)
                || RESPONSABLE_INICIADOR_TRAMITE_ID.equals(responsableId);
    }

    private ResponsableTipo parseResponsableTipo(Nodo nodo) {
        String rawTipo = normalizeNullableText(nodo.getResponsableTipo());
        nodo.setResponsableTipo(rawTipo);
        if (rawTipo == null) {
            return null;
        }

        String normalizedTipo = rawTipo.toUpperCase(Locale.ROOT);
        nodo.setResponsableTipo(normalizedTipo);

        try {
            return ResponsableTipo.valueOf(normalizedTipo);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "responsableTipo invalido: " + rawTipo + ". Valores permitidos: DEPARTAMENTO, USUARIO");
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String safeNodeName(Nodo nodo, int index) {
        String nombre = normalizeNullableText(nodo.getNombre());
        if (nombre != null) {
            return nombre;
        }
        String id = normalizeNullableText(nodo.getId());
        if (id != null) {
            return id;
        }
        return "#" + index;
    }

    private void inicializarMetadatosColaborativosNodos(List<Nodo> nodos) {
        LocalDateTime now = LocalDateTime.now();
        for (Nodo nodo : nodos) {
            if (nodo.getVersion() == null || nodo.getVersion() < 0) {
                nodo.setVersion(0L);
            }
            if (nodo.getFechaActualizacion() == null) {
                nodo.setFechaActualizacion(now);
            }
        }
    }

    public PoliticaNegocio actualizarNombreDescripcion(String adminUserId, String id, String nombre, String descripcion) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = obtenerPorId(adminUserId, id);
        if (nombre != null && !nombre.isBlank()) {
            politica.setNombre(nombre.trim());
        }
        if (descripcion != null) {
            politica.setDescripcion(descripcion.trim());
        }
        politica.setFechaActualizacion(LocalDateTime.now());
        return repository.save(politica);
    }
}
