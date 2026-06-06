package com.leo.politicas_de_negocio.instancias.service;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.archivos.model.ArchivoAdjunto;
import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import com.leo.politicas_de_negocio.archivos.repository.ArchivoAdjuntoRepository;
import com.leo.politicas_de_negocio.instancias.dto.CrearInstanciaRequest;
import com.leo.politicas_de_negocio.instancias.dto.FlujoInstanciaResponse;
import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.dto.MisTramiteCardResponse;
import com.leo.politicas_de_negocio.instancias.dto.PagedResponse;
import com.leo.politicas_de_negocio.instancias.dto.SeguimientoInstanciaResponse;
import com.leo.politicas_de_negocio.documents.model.DocumentoMetadata;
import com.leo.politicas_de_negocio.documents.service.DocumentoMetadataService;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaCardProjection;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNombreProjection;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.tareas.repository.TareaResumenProjection;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.workflow.service.WorkflowEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InstanciaPoliticaService {

    private static final List<EstadoTarea> ESTADOS_TAREA_ABIERTA = List.of(
            EstadoTarea.PENDIENTE,
            EstadoTarea.EN_PROCESO
    );

    private final InstanciaPoliticaRepository instanciaRepository;
    private final PoliticaNegocioRepository politicaRepository;
    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialInstanciaService historialService;
    private final WorkflowEngineService workflowEngineService;
    private final TareaActividadRepository tareaRepository;
    private final PoliticaNegocioService politicaNegocioService;
    private final com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService documentoColaborativoMetadataService;
    private final ArchivoAdjuntoRepository archivoRepository;
    private final DocumentoMetadataService documentoMetadataService;

    public InstanciaPolitica crearInstanciaDirecta(String actorUserId, CrearInstanciaRequest request) {
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

        politicaNegocioService.validarInicioPoliticaPorActor(actor, politica);
        Map<String, Object> respuestasRequisitosIniciales = validarRespuestasRequisitosInicialesParaPolitica(
                politica,
                request.getRespuestasRequisitosIniciales()
        );

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
                .requisitosInicialesDefinicion(politicaNegocioService.clonarCampos(politica.getRequisitosIniciales()))
                .respuestasRequisitosIniciales(respuestasRequisitosIniciales)
                .tokensJoin(new HashMap<>())
                .build();

        instancia = instanciaRepository.save(instancia);
        vincularArchivosDeRequisitosIniciales(instancia, actor, respuestasRequisitosIniciales);

        historialService.registrar(
                instancia.getId(),
                null,
                "INSTANCIA_CREADA",
                actor.getId(),
                "Instancia creada usando politica " + politica.getId()
        );

        try {
            documentoColaborativoMetadataService.crearDocumentosColaborativosIniciales(instancia, politica);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(InstanciaPoliticaService.class)
                    .error("Error al inicializar documentos colaborativos del tramite", e);
        }

        workflowEngineService.iniciarInstancia(instancia, politica, actor.getId());

        return instanciaRepository.findById(instancia.getId()).orElse(instancia);
    }

    public InstanciaPolitica obtenerPorId(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        InstanciaPolitica instancia = buscarInstancia(instanciaId);
        validarAccesoLectura(actor, instancia);
        return instancia;
    }

    public InstanciaPolitica obtenerInstanciaParaDocumentoColaborativo(String tramiteId, String actorUserId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        String instanciaId = normalizarTexto(tramiteId);
        if (instanciaId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id de instancia del tramite");
        }

        InstanciaPolitica instancia = instanciaRepository.findById(instanciaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No se encontró la instancia del trámite: " + instanciaId));

        if (tieneAccesoDocumentoColaborativo(actor, instancia)) {
            return instancia;
        }

        throw new ApiException(HttpStatus.FORBIDDEN,
                "El usuario no tiene permiso para consultar documentos colaborativos");
    }

    public void asegurarDocumentosColaborativosIniciales(InstanciaPolitica instancia) {
        if (instancia == null) {
            return;
        }

        String politicaId = normalizarTexto(instancia.getPoliticaId());
        if (politicaId == null) {
            return;
        }

        PoliticaNegocio politica = politicaRepository.findById(politicaId).orElse(null);
        if (politica == null) {
            return;
        }

        try {
            documentoColaborativoMetadataService.crearDocumentosColaborativosIniciales(instancia, politica);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(InstanciaPoliticaService.class)
                    .error("Error al asegurar documentos colaborativos del tramite {}", instancia.getId(), e);
        }
    }

    public InstanciaDetalleResponse obtenerDetallePorId(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        InstanciaPolitica instancia = buscarInstancia(instanciaId);
        validarAccesoLectura(actor, instancia);

        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId()).orElse(null);
        return construirDetalleInstancia(instancia, politica);
    }

    public SeguimientoInstanciaResponse obtenerSeguimientoPorId(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        InstanciaPolitica instancia = buscarInstancia(instanciaId);
        validarAccesoLectura(actor, instancia);

        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Politica no encontrada para la instancia " + instancia.getId()));

        List<TareaActividad> tareas = tareaRepository.findByInstanciaIdOrderByFechaCreacionAsc(instancia.getId());
        if (tareas == null) {
            tareas = List.of();
        }
        Map<String, List<TareaActividad>> tareasPorNodo = agruparTareasPorNodo(tareas);
        Map<String, Nodo> nodosPorId = construirIndiceNodos(politica.getNodos());
        Map<String, Usuario> usuariosCache = new HashMap<>();
        Map<String, Departamento> departamentosCache = new HashMap<>();

        List<SeguimientoInstanciaResponse.NodoSeguimientoResponse> nodos = construirNodosSeguimiento(
                politica.getNodos(),
                tareasPorNodo,
                instancia,
                usuariosCache,
                departamentosCache
        );
        List<SeguimientoInstanciaResponse.TareaSeguimientoResponse> tareasResponse = construirTareasSeguimiento(
                tareas,
                usuariosCache,
                departamentosCache
        );
        List<SeguimientoInstanciaResponse.DepartamentoActualResponse> departamentosActuales = construirDepartamentosActuales(
                tareas,
                nodosPorId,
                usuariosCache,
                departamentosCache
        );
        List<String> nodosActualesIds = departamentosActuales.stream()
                .map(SeguimientoInstanciaResponse.DepartamentoActualResponse::getNodoId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        return SeguimientoInstanciaResponse.builder()
                .instanciaId(instancia.getId())
                .politicaId(instancia.getPoliticaId())
                .politicaNombre(politica.getNombre())
                .politicaDescripcion(politica.getDescripcion())
                .politicaEstado(politica.getEstado())
                .politicaVersion(instancia.getPoliticaVersion())
                .codigoTramite(instancia.getCodigoTramite())
                .estadoInstancia(instancia.getEstadoInstancia())
                .fechaCreacion(instancia.getFechaCreacion())
                .fechaActualizacion(instancia.getFechaActualizacion())
                .fechaFinalizacion(instancia.getFechaFinalizacion())
                .creadaPor(instancia.getCreadaPor())
                .creadaPorNombre(resolverNombreUsuario(usuariosCache, instancia.getCreadaPor()))
                .finalizadaPor(instancia.getFinalizadaPor())
                .finalizadaPorNombre(resolverNombreUsuario(usuariosCache, instancia.getFinalizadaPor()))
                .totalTareas((long) tareas.size())
                .tareasAbiertas(contarTareasPorEstados(tareas, ESTADOS_TAREA_ABIERTA))
                .tareasCompletadas(contarTareasPorEstado(tareas, EstadoTarea.COMPLETADA))
                .tareasCanceladas(contarTareasPorEstado(tareas, EstadoTarea.CANCELADA))
                .tareasRechazadas(contarTareasPorEstado(tareas, EstadoTarea.RECHAZADA))
                .tokensJoin(instancia.getTokensJoin())
                .laneOrientation(politica.getLaneOrientation())
                .laneWidth(politica.getLaneWidth())
                .laneHeight(politica.getLaneHeight())
                .requisitosIniciales(construirRequisitosInicialesSeguimiento(instancia))
                .nodos(nodos)
                .conexiones(construirConexionesSeguimiento(politica.getConexiones()))
                .tareas(tareasResponse)
                .departamentosActuales(departamentosActuales)
                .nodosActualesIds(nodosActualesIds)
                .build();
    }

    public FlujoInstanciaResponse obtenerFlujoPorId(String actorUserId, String instanciaId) {
        SeguimientoInstanciaResponse seguimiento = obtenerSeguimientoPorId(actorUserId, instanciaId);
        return construirFlujoInstancia(seguimiento);
    }

    public PagedResponse<MisTramiteCardResponse> listarMisTramitesCards(String actorUserId, int page, int size) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(Sort.Direction.DESC, "fechaCreacion")
        );

        Page<InstanciaCardProjection> cardsPage = instanciaRepository.findCardsByCreadaPor(actor.getId(), pageable);
        Map<String, String> nombresPolitica = cargarNombresPolitica(cardsPage.getContent());
        Map<String, PoliticaNegocio> politicas = cargarPoliticas(cardsPage.getContent());
        Map<String, List<TareaResumenProjection>> tareasPorInstancia = cargarTareasResumenPorInstancia(cardsPage.getContent());

        List<MisTramiteCardResponse> content = cardsPage.getContent().stream()
                .map(card -> MisTramiteCardResponse.builder()
                        .id(card.getId())
                        .codigoTramite(card.getCodigoTramite())
                        .nombre(nombresPolitica.get(card.getPoliticaId()))
                        .estadoInstancia(card.getEstadoInstancia())
                        .porcentaje(calcularPorcentajeCard(
                                card,
                                politicas.get(normalizarTexto(card.getPoliticaId())),
                                tareasPorInstancia.getOrDefault(card.getId(), List.of())
                        ))
                        .fechaCreacion(card.getFechaCreacion())
                        .build())
                .toList();

        return new PagedResponse<>(
                content,
                cardsPage.getNumber(),
                cardsPage.getSize(),
                cardsPage.getTotalElements(),
                cardsPage.getTotalPages(),
                cardsPage.isLast()
        );
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

    public Map<String, Object> validarRespuestasRequisitosInicialesParaPolitica(
            PoliticaNegocio politica,
            Map<String, Object> respuestas
    ) {
        List<CampoFormulario> requisitos = politica != null ? politica.getRequisitosIniciales() : null;
        if (requisitos == null || requisitos.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> respuestasNormalizadas = copiarMapa(respuestas);
        List<String> faltantes = new ArrayList<>();

        for (CampoFormulario requisito : requisitos) {
            if (requisito == null || requisito.getTipo() == TipoCampo.LABEL) {
                continue;
            }

            String campo = normalizarTexto(requisito.getCampo());
            if (campo == null) {
                continue;
            }

            if (Boolean.TRUE.equals(requisito.getRequerido())
                    && !tieneValorRequisito(respuestasNormalizadas.get(campo))) {
                faltantes.add(resolveEtiquetaRequisito(requisito));
            }
        }

        if (!faltantes.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Faltan requisitos iniciales obligatorios: " + String.join(", ", faltantes)
            );
        }

        return respuestasNormalizadas;
    }

    private void vincularArchivosDeRequisitosIniciales(
            InstanciaPolitica instancia,
            Usuario actor,
            Map<String, Object> respuestas
    ) {
        if (instancia == null || actor == null || respuestas == null || respuestas.isEmpty()) {
            return;
        }

        List<String> archivoIds = new ArrayList<>();
        recolectarArchivoIdsRequisito(respuestas, archivoIds);
        for (String archivoId : archivoIds) {
            archivoRepository.findByIdAndEstado(archivoId, EstadoArchivo.ACTIVO)
                    .ifPresent(archivo -> vincularArchivoInicial(instancia, actor, archivo));
        }
    }

    private void vincularArchivoInicial(
            InstanciaPolitica instancia,
            Usuario actor,
            ArchivoAdjunto archivo
    ) {
        if (archivo == null) {
            return;
        }

        String subidoPor = normalizarTexto(archivo.getSubidoPor());
        String usuarioId = normalizarTexto(archivo.getUsuarioId());
        if (!actor.getId().equals(subidoPor) && !actor.getId().equals(usuarioId)) {
            return;
        }

        archivo.setInstanciaId(instancia.getId());
        archivo.setTramiteId(instancia.getId());
        if (normalizarTexto(archivo.getUsuarioId()) == null) {
            archivo.setUsuarioId(actor.getId());
        }
        if (normalizarTexto(archivo.getClienteId()) == null) {
            archivo.setClienteId(actor.getId());
        }
        if (normalizarTexto(archivo.getPoliticaId()) == null) {
            archivo.setPoliticaId(instancia.getPoliticaId());
        }
        archivoRepository.save(archivo);
    }

    private void recolectarArchivoIdsRequisito(Object value, List<String> archivoIds) {
        if (value instanceof Map<?, ?> map) {
            agregarArchivoId(map.get("archivoId"), archivoIds);
            agregarArchivoId(map.get("id"), archivoIds);
            for (Object child : map.values()) {
                recolectarArchivoIdsRequisito(child, archivoIds);
            }
            return;
        }

        if (value instanceof Collection<?> collection) {
            for (Object child : collection) {
                recolectarArchivoIdsRequisito(child, archivoIds);
            }
        }
    }

    private void agregarArchivoId(Object rawValue, List<String> archivoIds) {
        String archivoId = normalizarTexto(rawValue != null ? rawValue.toString() : null);
        if (archivoId != null && !archivoIds.contains(archivoId)) {
            archivoIds.add(archivoId);
        }
    }

    private boolean tieneValorRequisito(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return normalizarTexto(text) != null;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(this::tieneValorRequisito);
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty() && map.values().stream().anyMatch(this::tieneValorRequisito);
        }
        return true;
    }

    private String resolveEtiquetaRequisito(CampoFormulario requisito) {
        String etiqueta = normalizarTexto(requisito.getEtiqueta());
        if (etiqueta != null) {
            return etiqueta;
        }
        String campo = normalizarTexto(requisito.getCampo());
        return campo != null ? campo : "requisito";
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

        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId()).orElse(null);
        if (actorParticipaEnPolitica(actor, politica)) {
            return;
        }

        throw new ApiException(HttpStatus.FORBIDDEN,
                "No tiene permisos para consultar esta instancia");
    }

    private boolean tieneAccesoDocumentoColaborativo(Usuario actor, InstanciaPolitica instancia) {
        if (actor == null || instancia == null) {
            return false;
        }

        String rol = normalizarTexto(actor.getRol());
        if ("ADMIN".equalsIgnoreCase(rol)
                || "ADMINISTRADOR".equalsIgnoreCase(rol)
                || "JEFE_PROCESO".equalsIgnoreCase(rol)) {
            return true;
        }

        if (actor.getId().equals(normalizarTexto(instancia.getCreadaPor()))) {
            return true;
        }

        return actorParticipaEnInstancia(actor, instancia.getId());
    }

    private boolean actorParticipaEnPolitica(Usuario actor, PoliticaNegocio politica) {
        if (actor == null || politica == null || politica.getNodos() == null) {
            return false;
        }

        String actorId = normalizarTexto(actor.getId());
        String departamentoId = normalizarTexto(actor.getDepartamentoId());
        for (Nodo nodo : politica.getNodos()) {
            if (nodo == null) {
                continue;
            }

            if (actorId != null
                    && "USUARIO".equalsIgnoreCase(normalizarTexto(nodo.getResponsableTipo()))
                    && actorId.equals(normalizarTexto(nodo.getResponsableId()))) {
                return true;
            }

            if (departamentoId != null
                    && (departamentoId.equals(normalizarTexto(nodo.getDepartamentoId()))
                    || ("DEPARTAMENTO".equalsIgnoreCase(normalizarTexto(nodo.getResponsableTipo()))
                    && departamentoId.equals(normalizarTexto(nodo.getResponsableId()))))) {
                return true;
            }
        }
        return false;
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

    private List<SeguimientoInstanciaResponse.NodoSeguimientoResponse> construirNodosSeguimiento(
            List<Nodo> nodos,
            Map<String, List<TareaActividad>> tareasPorNodo,
            InstanciaPolitica instancia,
            Map<String, Usuario> usuariosCache,
            Map<String, Departamento> departamentosCache
    ) {
        if (nodos == null || nodos.isEmpty()) {
            return List.of();
        }

        List<SeguimientoInstanciaResponse.NodoSeguimientoResponse> response = new ArrayList<>();
        for (Nodo nodo : nodos) {
            if (nodo == null) {
                continue;
            }
            String nodoId = normalizarTexto(nodo.getId());
            List<TareaActividad> tareasNodo = nodoId != null
                    ? tareasPorNodo.getOrDefault(nodoId, List.of())
                    : List.of();
            TareaActividad tareaAbierta = buscarTareaAbierta(tareasNodo);

            response.add(SeguimientoInstanciaResponse.NodoSeguimientoResponse.builder()
                    .id(nodo.getId())
                    .tipo(nodo.getTipo())
                    .nombre(nodo.getNombre())
                    .departamentoId(nodo.getDepartamentoId())
                    .departamentoNombre(resolverNombreDepartamento(departamentosCache, nodo.getDepartamentoId()))
                    .responsableTipo(nodo.getResponsableTipo())
                    .responsableId(nodo.getResponsableId())
                    .responsableNombre(resolverNombreResponsable(
                            usuariosCache,
                            departamentosCache,
                            nodo.getResponsableTipo(),
                            nodo.getResponsableId()
                    ))
                    .posX(nodo.getPosX())
                    .posY(nodo.getPosY())
                    .estadoSeguimiento(resolverEstadoNodoSeguimiento(nodo, tareasNodo, tareaAbierta, instancia))
                    .tareaActualId(tareaAbierta != null ? tareaAbierta.getId() : null)
                    .estadoTareaActual(tareaAbierta != null ? tareaAbierta.getEstadoTarea() : null)
                    .asignadoA(tareaAbierta != null ? tareaAbierta.getAsignadoA() : null)
                    .asignadoANombre(tareaAbierta != null
                            ? resolverNombreUsuario(usuariosCache, tareaAbierta.getAsignadoA())
                            : null)
                    .fechaInicio(tareaAbierta != null ? tareaAbierta.getFechaInicio() : null)
                    .fechaFin(tareaAbierta != null ? tareaAbierta.getFechaFin() : null)
                    .build());
        }
        return response;
    }

    private List<SeguimientoInstanciaResponse.ConexionSeguimientoResponse> construirConexionesSeguimiento(
            List<Conexion> conexiones
    ) {
        if (conexiones == null || conexiones.isEmpty()) {
            return List.of();
        }

        return conexiones.stream()
                .filter(conexion -> conexion != null)
                .map(conexion -> SeguimientoInstanciaResponse.ConexionSeguimientoResponse.builder()
                        .origen(conexion.getOrigen())
                        .destino(conexion.getDestino())
                        .puertoOrigen(conexion.getPuertoOrigen())
                        .puertoDestino(conexion.getPuertoDestino())
                        .build())
                .toList();
    }

    private List<SeguimientoInstanciaResponse.TareaSeguimientoResponse> construirTareasSeguimiento(
            List<TareaActividad> tareas,
            Map<String, Usuario> usuariosCache,
            Map<String, Departamento> departamentosCache
    ) {
        if (tareas == null || tareas.isEmpty()) {
            return List.of();
        }

        return tareas.stream()
                .filter(tarea -> tarea != null)
                .map(tarea -> SeguimientoInstanciaResponse.TareaSeguimientoResponse.builder()
                        .id(tarea.getId())
                        .nodoId(tarea.getNodoId())
                        .nombreNodo(tarea.getNombreNodo())
                        .responsableTipo(tarea.getResponsableTipo())
                        .responsableId(tarea.getResponsableId())
                        .responsableNombre(resolverNombreResponsable(
                                usuariosCache,
                                departamentosCache,
                                tarea.getResponsableTipo(),
                                tarea.getResponsableId()
                        ))
                        .estadoTarea(tarea.getEstadoTarea())
                        .fechaCreacion(tarea.getFechaCreacion())
                        .fechaInicio(tarea.getFechaInicio())
                        .fechaFin(tarea.getFechaFin())
                        .asignadoA(tarea.getAsignadoA())
                        .asignadoANombre(resolverNombreUsuario(usuariosCache, tarea.getAsignadoA()))
                        .build())
                .toList();
    }

    private List<SeguimientoInstanciaResponse.DepartamentoActualResponse> construirDepartamentosActuales(
            List<TareaActividad> tareas,
            Map<String, Nodo> nodosPorId,
            Map<String, Usuario> usuariosCache,
            Map<String, Departamento> departamentosCache
    ) {
        if (tareas == null || tareas.isEmpty()) {
            return List.of();
        }

        List<SeguimientoInstanciaResponse.DepartamentoActualResponse> response = new ArrayList<>();
        for (TareaActividad tarea : tareas) {
            if (!esTareaAbierta(tarea)) {
                continue;
            }

            Nodo nodo = nodosPorId.get(normalizarTexto(tarea.getNodoId()));
            String departamentoActualId = resolverDepartamentoActualId(tarea, nodo, usuariosCache);

            response.add(SeguimientoInstanciaResponse.DepartamentoActualResponse.builder()
                    .departamentoId(departamentoActualId)
                    .departamentoNombre(resolverNombreDepartamento(departamentosCache, departamentoActualId))
                    .nodoId(tarea.getNodoId())
                    .nodoNombre(tarea.getNombreNodo())
                    .tareaId(tarea.getId())
                    .estadoTarea(tarea.getEstadoTarea())
                    .responsableTipo(tarea.getResponsableTipo())
                    .responsableId(tarea.getResponsableId())
                    .responsableNombre(resolverNombreResponsable(
                            usuariosCache,
                            departamentosCache,
                            tarea.getResponsableTipo(),
                            tarea.getResponsableId()
                    ))
                    .asignadoA(tarea.getAsignadoA())
                    .asignadoANombre(resolverNombreUsuario(usuariosCache, tarea.getAsignadoA()))
                    .build());
        }
        return response;
    }

    private Map<String, List<TareaActividad>> agruparTareasPorNodo(List<TareaActividad> tareas) {
        Map<String, List<TareaActividad>> tareasPorNodo = new LinkedHashMap<>();
        if (tareas == null || tareas.isEmpty()) {
            return tareasPorNodo;
        }

        for (TareaActividad tarea : tareas) {
            if (tarea == null) {
                continue;
            }
            String nodoId = normalizarTexto(tarea.getNodoId());
            if (nodoId == null) {
                continue;
            }
            tareasPorNodo.computeIfAbsent(nodoId, key -> new ArrayList<>()).add(tarea);
        }
        return tareasPorNodo;
    }

    private Map<String, Nodo> construirIndiceNodos(List<Nodo> nodos) {
        Map<String, Nodo> indice = new LinkedHashMap<>();
        if (nodos == null || nodos.isEmpty()) {
            return indice;
        }

        for (Nodo nodo : nodos) {
            if (nodo == null) {
                continue;
            }
            String nodoId = normalizarTexto(nodo.getId());
            if (nodoId != null) {
                indice.put(nodoId, nodo);
            }
        }
        return indice;
    }

    private TareaActividad buscarTareaAbierta(List<TareaActividad> tareas) {
        if (tareas == null || tareas.isEmpty()) {
            return null;
        }

        for (int i = tareas.size() - 1; i >= 0; i--) {
            TareaActividad tarea = tareas.get(i);
            if (esTareaAbierta(tarea)) {
                return tarea;
            }
        }
        return null;
    }

    private TareaActividad buscarUltimaTarea(List<TareaActividad> tareas) {
        if (tareas == null || tareas.isEmpty()) {
            return null;
        }
        return tareas.get(tareas.size() - 1);
    }

    private boolean esTareaAbierta(TareaActividad tarea) {
        return tarea != null && ESTADOS_TAREA_ABIERTA.contains(tarea.getEstadoTarea());
    }

    private String resolverEstadoNodoSeguimiento(
            Nodo nodo,
            List<TareaActividad> tareasNodo,
            TareaActividad tareaAbierta,
            InstanciaPolitica instancia
    ) {
        if (tareaAbierta != null) {
            return "ACTUAL";
        }

        TareaActividad ultimaTarea = buscarUltimaTarea(tareasNodo);
        if (ultimaTarea != null && ultimaTarea.getEstadoTarea() != null) {
            return switch (ultimaTarea.getEstadoTarea()) {
                case COMPLETADA -> "COMPLETADO";
                case CANCELADA -> "CANCELADO";
                case RECHAZADA -> "RECHAZADO";
                case PENDIENTE, EN_PROCESO -> "ACTUAL";
            };
        }

        if (nodo.getTipo() == TipoNodo.INICIO && instancia.getFechaCreacion() != null) {
            return "COMPLETADO";
        }

        if (nodo.getTipo() == TipoNodo.FIN && instancia.getEstadoInstancia() == EstadoInstancia.FINALIZADA) {
            return "COMPLETADO";
        }

        if (instancia.getTokensJoin() != null && instancia.getTokensJoin().containsKey(nodo.getId())) {
            return "EN_ESPERA";
        }

        return "PENDIENTE";
    }

    private String resolverDepartamentoActualId(
            TareaActividad tarea,
            Nodo nodo,
            Map<String, Usuario> usuariosCache
    ) {
        String responsableTipo = normalizarTexto(tarea.getResponsableTipo());
        if ("DEPARTAMENTO".equalsIgnoreCase(responsableTipo)) {
            return normalizarTexto(tarea.getResponsableId());
        }

        Usuario asignado = resolverUsuario(usuariosCache, tarea.getAsignadoA());
        if (normalizarTexto(asignado != null ? asignado.getDepartamentoId() : null) != null) {
            return asignado.getDepartamentoId();
        }

        if ("USUARIO".equalsIgnoreCase(responsableTipo)) {
            Usuario responsable = resolverUsuario(usuariosCache, tarea.getResponsableId());
            if (normalizarTexto(responsable != null ? responsable.getDepartamentoId() : null) != null) {
                return responsable.getDepartamentoId();
            }
        }

        return nodo != null ? normalizarTexto(nodo.getDepartamentoId()) : null;
    }

    private long contarTareasPorEstado(List<TareaActividad> tareas, EstadoTarea estado) {
        if (tareas == null || tareas.isEmpty()) {
            return 0L;
        }

        return tareas.stream()
                .filter(tarea -> tarea != null && tarea.getEstadoTarea() == estado)
                .count();
    }

    private long contarTareasPorEstados(List<TareaActividad> tareas, List<EstadoTarea> estados) {
        if (tareas == null || tareas.isEmpty() || estados == null || estados.isEmpty()) {
            return 0L;
        }

        return tareas.stream()
                .filter(tarea -> tarea != null && estados.contains(tarea.getEstadoTarea()))
                .count();
    }

    private String resolverNombreResponsable(
            Map<String, Usuario> usuariosCache,
            Map<String, Departamento> departamentosCache,
            String responsableTipo,
            String responsableId
    ) {
        String tipo = normalizarTexto(responsableTipo);
        if ("USUARIO".equalsIgnoreCase(tipo)) {
            return resolverNombreUsuario(usuariosCache, responsableId);
        }
        if ("DEPARTAMENTO".equalsIgnoreCase(tipo)) {
            return resolverNombreDepartamento(departamentosCache, responsableId);
        }
        return null;
    }

    private String resolverNombreUsuario(Map<String, Usuario> usuariosCache, String userId) {
        Usuario usuario = resolverUsuario(usuariosCache, userId);
        return usuario != null ? normalizarTexto(usuario.getNombre()) : null;
    }

    private Usuario resolverUsuario(Map<String, Usuario> usuariosCache, String userId) {
        String id = normalizarTexto(userId);
        if (id == null) {
            return null;
        }

        if (!usuariosCache.containsKey(id)) {
            usuariosCache.put(id, usuarioRepository.findById(id).orElse(null));
        }
        return usuariosCache.get(id);
    }

    private String resolverNombreDepartamento(Map<String, Departamento> departamentosCache, String departamentoId) {
        Departamento departamento = resolverDepartamento(departamentosCache, departamentoId);
        return departamento != null ? normalizarTexto(departamento.getNombre()) : null;
    }

    private SeguimientoInstanciaResponse.RequisitosInicialesSeguimientoResponse construirRequisitosInicialesSeguimiento(
            InstanciaPolitica instancia
    ) {
        List<DocumentoMetadata> documentos = List.of();
        String clienteId = normalizarTexto(instancia.getCreadaPor());
        String tramiteId = normalizarTexto(instancia.getId());
        if (clienteId != null && tramiteId != null) {
            documentos = documentoMetadataService.listarRequisitosInicialesPorTramite(clienteId, tramiteId);
            if (documentos == null) {
                documentos = List.of();
            }
        }

        return SeguimientoInstanciaResponse.RequisitosInicialesSeguimientoResponse.builder()
                .titulo("Requisitos iniciales")
                .definicion(instancia.getRequisitosInicialesDefinicion() != null
                        ? instancia.getRequisitosInicialesDefinicion()
                        : List.of())
                .respuestas(instancia.getRespuestasRequisitosIniciales() != null
                        ? instancia.getRespuestasRequisitosIniciales()
                        : Map.of())
                .documentos(documentos)
                .build();
    }

    private Departamento resolverDepartamento(Map<String, Departamento> departamentosCache, String departamentoId) {
        String id = normalizarTexto(departamentoId);
        if (id == null) {
            return null;
        }

        if (!departamentosCache.containsKey(id)) {
            departamentosCache.put(id, departamentoRepository.findById(id).orElse(null));
        }
        return departamentosCache.get(id);
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
                .requisitosInicialesDefinicion(instancia.getRequisitosInicialesDefinicion())
                .respuestasRequisitosIniciales(instancia.getRespuestasRequisitosIniciales())
                .tokensJoin(instancia.getTokensJoin())
                .totalTareas(totalTareas)
                .tareasAbiertas(tareasAbiertas)
                .tareasCompletadas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.COMPLETADA))
                .tareasCanceladas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.CANCELADA))
                .tareasRechazadas(tareaRepository.countByInstanciaIdAndEstadoTarea(instancia.getId(), EstadoTarea.RECHAZADA))
                .build();
    }

    private Map<String, String> cargarNombresPolitica(Collection<InstanciaCardProjection> cards) {
        List<String> politicaIds = cards.stream()
                .map(InstanciaCardProjection::getPoliticaId)
                .map(this::normalizarTexto)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (politicaIds.isEmpty()) {
            return Map.of();
        }

        Map<String, String> nombres = new HashMap<>();
        for (PoliticaNombreProjection politica : politicaRepository.findNombreByIdIn(politicaIds)) {
            if (politica == null) {
                continue;
            }
            String id = normalizarTexto(politica.getId());
            if (id != null) {
                nombres.put(id, normalizarTexto(politica.getNombre()));
            }
        }
        return nombres;
    }

    private Map<String, PoliticaNegocio> cargarPoliticas(Collection<InstanciaCardProjection> cards) {
        List<String> politicaIds = cards.stream()
                .map(InstanciaCardProjection::getPoliticaId)
                .map(this::normalizarTexto)
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (politicaIds.isEmpty()) {
            return Map.of();
        }

        Map<String, PoliticaNegocio> politicas = new HashMap<>();
        for (PoliticaNegocio politica : politicaRepository.findAllById(politicaIds)) {
            if (politica == null) {
                continue;
            }
            String id = normalizarTexto(politica.getId());
            if (id != null) {
                politicas.put(id, politica);
            }
        }
        return politicas;
    }

    private Map<String, List<TareaResumenProjection>> cargarTareasResumenPorInstancia(Collection<InstanciaCardProjection> cards) {
        List<String> instanciaIds = cards.stream()
                .map(InstanciaCardProjection::getId)
                .map(this::normalizarTexto)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (instanciaIds.isEmpty()) {
            return Map.of();
        }

        Map<String, List<TareaResumenProjection>> tareasPorInstancia = new HashMap<>();
        for (TareaResumenProjection tarea : tareaRepository.findResumenByInstanciaIdIn(instanciaIds)) {
            if (tarea == null) {
                continue;
            }
            String instanciaId = normalizarTexto(tarea.getInstanciaId());
            if (instanciaId == null) {
                continue;
            }
            tareasPorInstancia.computeIfAbsent(instanciaId, key -> new ArrayList<>()).add(tarea);
        }

        tareasPorInstancia.values().forEach(tareas -> tareas.sort(
                Comparator.comparing(
                        TareaResumenProjection::getFechaCreacion,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
        ));
        return tareasPorInstancia;
    }

    private int calcularPorcentajeCard(
            InstanciaCardProjection card,
            PoliticaNegocio politica,
            List<TareaResumenProjection> tareas
    ) {
        if (politica == null || politica.getNodos() == null || politica.getNodos().isEmpty()) {
            return 0;
        }

        Map<String, List<TareaResumenProjection>> tareasPorNodo = agruparResumenTareasPorNodo(tareas);
        int total = 0;
        int completados = 0;

        for (Nodo nodo : politica.getNodos()) {
            if (nodo == null) {
                continue;
            }
            total++;
            String nodoId = normalizarTexto(nodo.getId());
            List<TareaResumenProjection> tareasNodo = nodoId != null
                    ? tareasPorNodo.getOrDefault(nodoId, List.of())
                    : List.of();
            if ("COMPLETADO".equals(resolverEstadoNodoCard(nodo, tareasNodo, card))) {
                completados++;
            }
        }

        return total > 0 ? (int) Math.round((completados * 100d) / total) : 0;
    }

    private String resolverEstadoNodoCard(
            Nodo nodo,
            List<TareaResumenProjection> tareasNodo,
            InstanciaCardProjection card
    ) {
        TareaResumenProjection tareaAbierta = buscarTareaResumenAbierta(tareasNodo);
        if (tareaAbierta != null) {
            return "ACTUAL";
        }

        TareaResumenProjection ultimaTarea = buscarUltimaTareaResumen(tareasNodo);
        if (ultimaTarea != null && ultimaTarea.getEstadoTarea() != null) {
            return switch (ultimaTarea.getEstadoTarea()) {
                case COMPLETADA -> "COMPLETADO";
                case CANCELADA -> "CANCELADO";
                case RECHAZADA -> "RECHAZADO";
                case PENDIENTE, EN_PROCESO -> "ACTUAL";
            };
        }

        if (nodo.getTipo() == TipoNodo.INICIO && card.getFechaCreacion() != null) {
            return "COMPLETADO";
        }

        if (nodo.getTipo() == TipoNodo.FIN && card.getEstadoInstancia() == EstadoInstancia.FINALIZADA) {
            return "COMPLETADO";
        }

        return "PENDIENTE";
    }

    private Map<String, List<TareaResumenProjection>> agruparResumenTareasPorNodo(
            List<TareaResumenProjection> tareas
    ) {
        Map<String, List<TareaResumenProjection>> tareasPorNodo = new HashMap<>();
        if (tareas == null || tareas.isEmpty()) {
            return tareasPorNodo;
        }

        for (TareaResumenProjection tarea : tareas) {
            if (tarea == null) {
                continue;
            }
            String nodoId = normalizarTexto(tarea.getNodoId());
            if (nodoId == null) {
                continue;
            }
            tareasPorNodo.computeIfAbsent(nodoId, key -> new ArrayList<>()).add(tarea);
        }
        return tareasPorNodo;
    }

    private TareaResumenProjection buscarTareaResumenAbierta(List<TareaResumenProjection> tareas) {
        if (tareas == null || tareas.isEmpty()) {
            return null;
        }

        for (int i = tareas.size() - 1; i >= 0; i--) {
            TareaResumenProjection tarea = tareas.get(i);
            if (tarea != null && ESTADOS_TAREA_ABIERTA.contains(tarea.getEstadoTarea())) {
                return tarea;
            }
        }
        return null;
    }

    private TareaResumenProjection buscarUltimaTareaResumen(List<TareaResumenProjection> tareas) {
        if (tareas == null || tareas.isEmpty()) {
            return null;
        }
        return tareas.get(tareas.size() - 1);
    }

    private FlujoInstanciaResponse construirFlujoInstancia(SeguimientoInstanciaResponse seguimiento) {
        return FlujoInstanciaResponse.builder()
                .instanciaId(seguimiento.getInstanciaId())
                .politicaId(seguimiento.getPoliticaId())
                .politicaNombre(seguimiento.getPoliticaNombre())
                .codigoTramite(seguimiento.getCodigoTramite())
                .estadoInstancia(seguimiento.getEstadoInstancia() != null
                        ? seguimiento.getEstadoInstancia().name()
                        : null)
                .laneOrientation(seguimiento.getLaneOrientation())
                .laneWidth(seguimiento.getLaneWidth())
                .laneHeight(seguimiento.getLaneHeight())
                .nodos(seguimiento.getNodos() == null ? List.of() : seguimiento.getNodos().stream()
                        .map(nodo -> FlujoInstanciaResponse.NodoFlujoResponse.builder()
                                .id(nodo.getId())
                                .tipo(nodo.getTipo() != null ? nodo.getTipo().name() : null)
                                .nombre(nodo.getNombre())
                                .departamentoId(nodo.getDepartamentoId())
                                .departamentoNombre(nodo.getDepartamentoNombre())
                                .responsableTipo(nodo.getResponsableTipo())
                                .responsableId(nodo.getResponsableId())
                                .responsableNombre(nodo.getResponsableNombre())
                                .posX(nodo.getPosX())
                                .posY(nodo.getPosY())
                                .estadoSeguimiento(nodo.getEstadoSeguimiento())
                                .tareaActualId(nodo.getTareaActualId())
                                .estadoTareaActual(nodo.getEstadoTareaActual() != null
                                        ? nodo.getEstadoTareaActual().name()
                                        : null)
                                .asignadoA(nodo.getAsignadoA())
                                .asignadoANombre(nodo.getAsignadoANombre())
                                .build())
                        .toList())
                .conexiones(seguimiento.getConexiones() == null ? List.of() : seguimiento.getConexiones().stream()
                        .map(conexion -> FlujoInstanciaResponse.ConexionFlujoResponse.builder()
                                .origen(conexion.getOrigen())
                                .destino(conexion.getDestino())
                                .puertoOrigen(conexion.getPuertoOrigen())
                                .puertoDestino(conexion.getPuertoDestino())
                                .build())
                        .toList())
                .tareas(seguimiento.getTareas() == null ? List.of() : seguimiento.getTareas().stream()
                        .map(tarea -> FlujoInstanciaResponse.TareaFlujoResponse.builder()
                                .id(tarea.getId())
                                .nodoId(tarea.getNodoId())
                                .nombre(tarea.getNombreNodo())
                                .responsableTipo(tarea.getResponsableTipo())
                                .responsableId(tarea.getResponsableId())
                                .responsableNombre(tarea.getResponsableNombre())
                                .estado(tarea.getEstadoTarea() != null ? tarea.getEstadoTarea().name() : null)
                                .asignadoA(tarea.getAsignadoA())
                                .asignadoANombre(tarea.getAsignadoANombre())
                                .build())
                        .toList())
                .departamentosActuales(seguimiento.getDepartamentosActuales() == null
                        ? List.of()
                        : seguimiento.getDepartamentosActuales().stream()
                        .map(item -> FlujoInstanciaResponse.DepartamentoActualFlujoResponse.builder()
                                .departamentoId(item.getDepartamentoId())
                                .departamentoNombre(item.getDepartamentoNombre())
                                .nodoId(item.getNodoId())
                                .nodoNombre(item.getNodoNombre())
                                .tareaId(item.getTareaId())
                                .estadoTarea(item.getEstadoTarea() != null ? item.getEstadoTarea().name() : null)
                                .responsableTipo(item.getResponsableTipo())
                                .responsableNombre(item.getResponsableNombre())
                                .asignadoANombre(item.getAsignadoANombre())
                                .build())
                        .toList())
                .nodosActualesIds(seguimiento.getNodosActualesIds() == null ? List.of() : seguimiento.getNodosActualesIds())
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
