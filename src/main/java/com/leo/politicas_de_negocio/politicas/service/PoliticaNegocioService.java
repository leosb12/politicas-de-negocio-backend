package com.leo.politicas_de_negocio.politicas.service;

import com.leo.politicas_de_negocio.colaboracion.repository.EventoColaboracionAplicadoRepository;
import com.leo.politicas_de_negocio.colaboracion.repository.SnapshotColaboracionPoliticaRepository;
import com.leo.politicas_de_negocio.colaboracion.service.PoliticaPresenciaService;
import com.leo.politicas_de_negocio.politicas.dto.CreatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.dto.TramiteDisponibleResponse;
import com.leo.politicas_de_negocio.politicas.dto.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.ResponsableTipo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.ConfiguracionDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosLecturaSeccion;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosSeccion;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PoliticaNegocioService {

    private static final String DEFAULT_LANE_ORIENTATION = "VERTICAL";
    private static final double DEFAULT_LANE_WIDTH = 320d;
    private static final double DEFAULT_LANE_HEIGHT = 220d;
    private static final TipoPolitica DEFAULT_TIPO_POLITICA = TipoPolitica.EXTERNA;
    private static final String DEFAULT_MONEDA_PAGO = "USD";
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
    private final InstanciaPoliticaRepository instanciaPoliticaRepository;
    private final DocumentoColaborativoMetadataService documentoColaborativoMetadataService;

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
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos de la politica");
        }

        String nombre = normalizeNullableText(request.getNombre());
        if (nombre == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre de la politica es obligatorio");
        }

        TipoPolitica tipoPolitica = parseTipoPolitica(request.getTipoPolitica());
        String departamentoInicioId = validarDepartamentoInicio(request.getDepartamentoInicioId(), tipoPolitica);
        PaymentConfig paymentConfig = normalizePaymentConfig(
                request.getRequierePago(),
                request.getMontoPago(),
                request.getMonedaPago(),
                request.getDescripcionPago()
        );

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nombre(nombre)
                .descripcion(normalizeNullableText(request.getDescripcion()))
                .estado(EstadoPolitica.BORRADOR)
                .tipoPolitica(tipoPolitica)
                .departamentoInicioId(departamentoInicioId)
                .requierePago(paymentConfig.requierePago())
                .montoPago(paymentConfig.montoPago())
                .monedaPago(paymentConfig.monedaPago())
                .descripcionPago(paymentConfig.descripcionPago())
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
        List<PoliticaNegocio> politicas = repository.findAll();
        politicas.forEach(this::normalizarConfiguracionesDocumento);
        return politicas;
    }

    public List<TramiteDisponibleResponse> obtenerTramitesDisponibles(String actorUserId) {
        Usuario actor = assertUsuarioActivo(actorUserId);

        return repository.findByEstado(EstadoPolitica.ACTIVA).stream()
                .filter(politica -> puedeIniciarPolitica(actor, politica))
                .sorted(Comparator.comparing(politica -> safeLower(politica.getNombre())))
                .map(this::toTramiteDisponibleResponse)
                .toList();
    }

    public PoliticaNegocio obtenerPorId(String adminUserId, String id) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Política no encontrada con ID: " + id));
        normalizarConfiguracionesDocumento(politica);
        return politica;
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

        PoliticaNegocio persistida = repository.save(politica);
        sincronizarDocumentosColaborativosExistentes(persistida);
        return persistida;
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

    private Usuario assertUsuarioActivo(String actorUserId) {
        String userId = normalizeNullableText(actorUserId);
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id");
        }

        return usuarioRepository.findByIdAndActivo(userId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
    }

    private TramiteDisponibleResponse toTramiteDisponibleResponse(PoliticaNegocio politica) {
        String departamentoInicioId = normalizeNullableText(politica.getDepartamentoInicioId());
        return TramiteDisponibleResponse.builder()
                .id(politica.getId())
                .nombre(politica.getNombre())
                .descripcion(politica.getDescripcion())
                .tipoPolitica((politica.getTipoPolitica() != null ? politica.getTipoPolitica() : DEFAULT_TIPO_POLITICA).name())
                .departamentoInicioId(departamentoInicioId)
                .departamentoInicioNombre(resolveDepartamentoNombre(departamentoInicioId))
                .requierePago(Boolean.TRUE.equals(politica.getRequierePago()))
                .montoPago(politica.getMontoPago())
                .monedaPago(resolveMonedaPago(politica))
                .descripcionPago(resolveDescripcionPago(politica))
                .build();
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT);
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
            normalizarConfiguracionesDocumento(nodo);
        }
    }

    private void normalizarConfiguracionesDocumento(Nodo nodo) {
        if (nodo == null || nodo.getFormulario() == null) {
            return;
        }

        for (CampoFormulario campo : nodo.getFormulario()) {
            if (campo == null || campo.getConfiguracionDocumento() == null) {
                continue;
            }

            ConfiguracionDocumento config = campo.getConfiguracionDocumento();
            config.setPermisosEdicion(normalizarPermisosSeccion(config.getPermisosEdicion()));
            config.setPermisosLectura(normalizarPermisosLectura(config.getPermisosLectura()));
            config.setPermisosDescarga(normalizarPermisosSeccion(config.getPermisosDescarga()));
            config.setPermisosComentarios(normalizarPermisosSeccion(config.getPermisosComentarios()));
            config.setPermisosReemplazo(normalizarPermisosSeccion(config.getPermisosReemplazo()));
            config.setPermisosEliminacion(normalizarPermisosSeccion(config.getPermisosEliminacion()));
            config.setPermisosCompartirInternamente(normalizarPermisosSeccion(config.getPermisosCompartirInternamente()));
        }
    }

    private void normalizarConfiguracionesDocumento(PoliticaNegocio politica) {
        if (politica == null || politica.getNodos() == null) {
            return;
        }
        politica.getNodos().forEach(this::normalizarConfiguracionesDocumento);
    }

    private void sincronizarDocumentosColaborativosExistentes(PoliticaNegocio politica) {
        if (politica == null || politica.getId() == null || documentoColaborativoMetadataService == null
                || instanciaPoliticaRepository == null) {
            return;
        }

        Map<String, ConfiguracionDocumento> configuracionesPorCampo = configuracionesDocumentoPorCampo(politica.getNodos());
        if (configuracionesPorCampo.isEmpty()) {
            return;
        }

        List<InstanciaPolitica> instancias = instanciaPoliticaRepository.findByPoliticaIdOrderByFechaCreacionDesc(politica.getId());
        if (instancias == null || instancias.isEmpty()) {
            return;
        }

        for (InstanciaPolitica instancia : instancias) {
            if (instancia == null || normalizeNullableText(instancia.getCreadaPor()) == null
                    || normalizeNullableText(instancia.getId()) == null) {
                continue;
            }

            List<DocumentoColaborativoMetadata> documentos = documentoColaborativoMetadataService.listarPorTramite(
                    instancia.getCreadaPor(),
                    instancia.getId()
            );
            if (documentos == null || documentos.isEmpty()) {
                continue;
            }
            for (DocumentoColaborativoMetadata documento : documentos) {
                ConfiguracionDocumento config = configuracionesPorCampo.get(normalizeNullableText(documento.getCampoFormularioId()));
                if (config != null) {
                    documentoColaborativoMetadataService.actualizarConfiguracionDesdeCampo(documento, config);
                }
            }
        }
    }

    private Map<String, ConfiguracionDocumento> configuracionesDocumentoPorCampo(List<Nodo> nodos) {
        Map<String, ConfiguracionDocumento> result = new HashMap<>();
        if (nodos == null) {
            return result;
        }

        for (Nodo nodo : nodos) {
            if (nodo == null || nodo.getFormulario() == null) {
                continue;
            }
            for (CampoFormulario campo : nodo.getFormulario()) {
                String campoId = campo != null ? normalizeNullableText(campo.getCampo()) : null;
                if (campoId != null
                        && campo.getTipo() == TipoCampo.DOCUMENTO_COLABORATIVO
                        && campo.getConfiguracionDocumento() != null) {
                    result.put(campoId, campo.getConfiguracionDocumento());
                }
            }
        }
        return result;
    }

    private PermisosSeccion normalizarPermisosSeccion(PermisosSeccion permisos) {
        if (permisos == null) {
            return PermisosSeccion.builder()
                    .departamentos(new ArrayList<>())
                    .roles(new ArrayList<>())
                    .usuarios(new ArrayList<>())
                    .build();
        }

        return PermisosSeccion.builder()
                .departamentos(normalizarListaPermisos(permisos.getDepartamentos()))
                .roles(normalizarListaPermisos(permisos.getRoles()))
                .usuarios(normalizarListaPermisos(permisos.getUsuarios()))
                .build();
    }

    private PermisosLecturaSeccion normalizarPermisosLectura(PermisosLecturaSeccion permisos) {
        if (permisos == null) {
            return PermisosLecturaSeccion.builder()
                    .departamentos(new ArrayList<>())
                    .roles(new ArrayList<>())
                    .usuarios(new ArrayList<>())
                    .incluirClienteIniciador(false)
                    .build();
        }

        return PermisosLecturaSeccion.builder()
                .departamentos(normalizarListaPermisos(permisos.getDepartamentos()))
                .roles(normalizarListaPermisos(permisos.getRoles()))
                .usuarios(normalizarListaPermisos(permisos.getUsuarios()))
                .incluirClienteIniciador(Boolean.TRUE.equals(permisos.getIncluirClienteIniciador()))
                .build();
    }

    private List<String> normalizarListaPermisos(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        return values.stream()
                .map(this::normalizeNullableText)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    public PoliticaNegocio actualizarNombreDescripcion(String adminUserId, String id, String nombre, String descripcion) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = obtenerPorId(adminUserId, id);
        String normalizedNombre = normalizeNullableText(nombre);
        if (normalizedNombre != null) {
            politica.setNombre(normalizedNombre);
        }
        if (descripcion != null) {
            politica.setDescripcion(normalizeNullableText(descripcion));
        }
        politica.setFechaActualizacion(LocalDateTime.now());
        return repository.save(politica);
    }

    public PoliticaNegocio actualizarMetadatos(
            String adminUserId,
            String id,
            String nombre,
            String descripcion,
            String tipoPoliticaRaw,
            String departamentoInicioIdRaw,
            Boolean requierePago,
            BigDecimal montoPago,
            String monedaPago,
            String descripcionPago
    ) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = obtenerPorId(adminUserId, id);

        String normalizedNombre = normalizeNullableText(nombre);
        if (normalizedNombre != null) {
            politica.setNombre(normalizedNombre);
        }

        if (descripcion != null) {
            politica.setDescripcion(normalizeNullableText(descripcion));
        }

        if (tipoPoliticaRaw != null) {
            TipoPolitica tipoPolitica = parseTipoPolitica(tipoPoliticaRaw);
            politica.setTipoPolitica(tipoPolitica);

            String departamentoInicioId = departamentoInicioIdRaw != null
                    ? departamentoInicioIdRaw
                    : politica.getDepartamentoInicioId();
            politica.setDepartamentoInicioId(validarDepartamentoInicio(departamentoInicioId, tipoPolitica));
        } else if (departamentoInicioIdRaw != null) {
            TipoPolitica tipoPoliticaActual = politica.getTipoPolitica() != null
                    ? politica.getTipoPolitica()
                    : DEFAULT_TIPO_POLITICA;
            politica.setDepartamentoInicioId(validarDepartamentoInicio(departamentoInicioIdRaw, tipoPoliticaActual));
        }

        boolean shouldUpdatePayment = requierePago != null
                || montoPago != null
                || monedaPago != null
                || descripcionPago != null;
        if (shouldUpdatePayment) {
            PaymentConfig paymentConfig = normalizePaymentConfig(
                    requierePago != null ? requierePago : politica.getRequierePago(),
                    montoPago != null ? montoPago : politica.getMontoPago(),
                    monedaPago != null ? monedaPago : politica.getMonedaPago(),
                    descripcionPago != null ? descripcionPago : politica.getDescripcionPago()
            );
            politica.setRequierePago(paymentConfig.requierePago());
            politica.setMontoPago(paymentConfig.montoPago());
            politica.setMonedaPago(paymentConfig.monedaPago());
            politica.setDescripcionPago(paymentConfig.descripcionPago());
        }

        politica.setFechaActualizacion(LocalDateTime.now());
        return repository.save(politica);
    }

    public boolean puedeIniciarPolitica(Usuario actor, PoliticaNegocio politica) {
        if (actor == null || politica == null) {
            return false;
        }

        TipoPolitica tipoPolitica = politica.getTipoPolitica() != null
                ? politica.getTipoPolitica()
                : DEFAULT_TIPO_POLITICA;

        return switch (tipoPolitica) {
            case EXTERNA -> esRol(actor, "USUARIO");
            case AMBAS -> true;
            case INTERNA -> {
                String departamentoInicioId = normalizeNullableText(politica.getDepartamentoInicioId());
                if (departamentoInicioId == null) {
                    yield esRol(actor, "ADMIN") || esRol(actor, "FUNCIONARIO");
                }

                String actorDepartamentoId = normalizeNullableText(actor.getDepartamentoId());
                yield departamentoInicioId.equals(actorDepartamentoId);
            }
        };
    }

    public void validarInicioPoliticaPorActor(Usuario actor, PoliticaNegocio politica) {
        if (puedeIniciarPolitica(actor, politica)) {
            return;
        }

        String detalle = switch (politica.getTipoPolitica() != null ? politica.getTipoPolitica() : DEFAULT_TIPO_POLITICA) {
            case INTERNA -> {
                String departamentoNombre = resolveDepartamentoNombre(politica.getDepartamentoInicioId());
                if (departamentoNombre != null) {
                    yield "Solo usuarios del departamento " + departamentoNombre + " pueden iniciar esta politica";
                }
                yield "Solo usuarios admin o funcionario pueden iniciar esta politica";
            }
            case EXTERNA -> "Solo usuarios con rol USUARIO pueden iniciar esta politica";
            case AMBAS -> "No tiene permisos para iniciar esta politica";
        };

        throw new ApiException(HttpStatus.FORBIDDEN, detalle);
    }

    private TipoPolitica parseTipoPolitica(String rawTipoPolitica) {
        String normalized = normalizeNullableText(rawTipoPolitica);
        if (normalized == null) {
            return DEFAULT_TIPO_POLITICA;
        }

        try {
            return TipoPolitica.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "tipoPolitica invalido: " + rawTipoPolitica + ". Valores permitidos: INTERNA, EXTERNA, AMBAS"
            );
        }
    }

    private String validarDepartamentoInicio(String rawDepartamentoInicioId, TipoPolitica tipoPolitica) {
        String departamentoInicioId = normalizeNullableText(rawDepartamentoInicioId);

        if (tipoPolitica != TipoPolitica.INTERNA) {
            return null;
        }

        if (departamentoInicioId == null) {
            return null;
        }

        if (!departamentoRepository.existsById(departamentoInicioId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El departamentoInicioId indicado no existe");
        }

        return departamentoInicioId;
    }

    private boolean esRol(Usuario actor, String rolEsperado) {
        return actor != null && actor.getRol() != null && rolEsperado.equalsIgnoreCase(actor.getRol());
    }

    private String resolveDepartamentoNombre(String departamentoId) {
        String normalizedId = normalizeNullableText(departamentoId);
        if (normalizedId == null) {
            return null;
        }

        return departamentoRepository.findById(normalizedId)
                .map(departamento -> normalizeNullableText(departamento.getNombre()))
                .orElse(null);
    }

    private PaymentConfig normalizePaymentConfig(
            Boolean requierePago,
            BigDecimal montoPago,
            String monedaPago,
            String descripcionPago
    ) {
        boolean requierePagoNormalizado = Boolean.TRUE.equals(requierePago);
        String descripcionPagoNormalizada = normalizeNullableText(descripcionPago);

        if (!requierePagoNormalizado) {
            return new PaymentConfig(false, null, DEFAULT_MONEDA_PAGO, descripcionPagoNormalizada);
        }

        if (montoPago == null || montoPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Si la politica requiere pago, montoPago debe ser mayor a 0");
        }

        return new PaymentConfig(
                true,
                montoPago.stripTrailingZeros(),
                normalizeNullableText(monedaPago) != null
                        ? normalizeNullableText(monedaPago).toUpperCase(Locale.ROOT)
                        : DEFAULT_MONEDA_PAGO,
                descripcionPagoNormalizada
        );
    }

    private String resolveMonedaPago(PoliticaNegocio politica) {
        String monedaPago = normalizeNullableText(politica.getMonedaPago());
        return monedaPago != null ? monedaPago : DEFAULT_MONEDA_PAGO;
    }

    private String resolveDescripcionPago(PoliticaNegocio politica) {
        String descripcionPago = normalizeNullableText(politica.getDescripcionPago());
        if (descripcionPago != null) {
            return descripcionPago;
        }
        return normalizeNullableText(politica.getNombre());
    }

    private record PaymentConfig(
            boolean requierePago,
            BigDecimal montoPago,
            String monedaPago,
            String descripcionPago
    ) {
    }
}
