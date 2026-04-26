package com.leo.politicas_de_negocio.guide.usuario_movil.servicio;

import com.leo.politicas_de_negocio.guide.cliente.ClienteGuiaIa;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.ContextoEtapaActualGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.ContextoGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.HistorialGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.RespuestaGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.ResumenProgresoGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.SolicitudGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.instancias.dto.SeguimientoInstanciaResponse;
import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.service.HistorialInstanciaService;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.politicas.dto.TramiteDisponibleResponse;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ServicioGuiaUsuarioMovil {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final UsuarioRepository usuarioRepository;
    private final InstanciaPoliticaService instanciaPoliticaService;
    private final HistorialInstanciaService historialInstanciaService;
    private final PoliticaNegocioService politicaNegocioService;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final TareaActividadRepository tareaActividadRepository;
    private final ClienteGuiaIa clienteGuiaIa;
    private final ServicioFallbackGuiaUsuarioMovil servicioFallback;
    private final ResolvedorIntencionGuiaUsuarioMovil resolvedorIntencion;

    public RespuestaGuiaUsuarioMovil guiarUsuarioMovil(String usuarioMovilId, SolicitudGuiaUsuarioMovil solicitud) {
        Usuario usuario = validarUsuarioMovil(usuarioMovilId);
        SolicitudGuiaUsuarioMovil solicitudIa = construirSolicitudIa(usuario, solicitud);
        RespuestaGuiaUsuarioMovil respuesta = clienteGuiaIa.guiarUsuarioMovil(solicitudIa);
        if (esRespuestaUtil(respuesta)) {
            return respuesta;
        }

        return servicioFallback.construir(
                solicitudIa,
                resolvedorIntencion.resolver(solicitudIa.getPregunta(), solicitudIa.getPantalla())
        );
    }

    private SolicitudGuiaUsuarioMovil construirSolicitudIa(Usuario usuario, SolicitudGuiaUsuarioMovil solicitud) {
        ContextoGuiaUsuarioMovil contextoEntrante = solicitud != null && solicitud.getContexto() != null
                ? solicitud.getContexto()
                : new ContextoGuiaUsuarioMovil();

        String tramiteId = normalizarTexto(contextoEntrante.getTramiteId());
        SeguimientoInstanciaResponse seguimiento = cargarSeguimiento(usuario.getId(), tramiteId);
        List<TareaActividad> tareasInstancia = cargarTareasInstancia(tramiteId);
        List<HistorialInstancia> historialInstancia = cargarHistorialInstancia(tramiteId);
        List<TareaActividad> tareasUsuarioAbiertas = filtrarTareasUsuarioAbiertas(usuario, tareasInstancia);

        String politicaId = resolverPoliticaId(contextoEntrante, seguimiento);
        PoliticaNegocio politica = cargarPolitica(politicaId);
        String pantalla = normalizarPantalla(solicitud != null ? solicitud.getPantalla() : null, tramiteId);
        List<TramiteDisponibleResponse> tramitesDisponibles = cargarTramitesDisponibles(usuario, pantalla);

        List<String> documentosFaltantes = construirDocumentosFaltantes(tareasUsuarioAbiertas);
        List<String> observaciones = construirObservaciones(tareasInstancia, historialInstancia);
        ContextoEtapaActualGuiaUsuarioMovil etapaActual = construirEtapaActual(seguimiento, politica, tareasInstancia);
        ResumenProgresoGuiaUsuarioMovil resumenProgreso = construirResumenProgreso(seguimiento);
        List<HistorialGuiaUsuarioMovil> historial = construirHistorial(tareasInstancia, historialInstancia);
        List<String> proximosPasos = construirProximosPasos(seguimiento);

        ContextoGuiaUsuarioMovil contexto = ContextoGuiaUsuarioMovil.builder()
                .tramiteId(tramiteId)
                .politicaId(politica != null ? politica.getId() : normalizarTexto(contextoEntrante.getPoliticaId()))
                .nombrePolitica(resolverNombrePolitica(contextoEntrante, seguimiento, politica))
                .estadoTramite(resolverEstadoTramite(seguimiento, tareasInstancia))
                .etapaActual(etapaActual)
                .resumenProgreso(resumenProgreso)
                .historial(historial)
                .documentosFaltantes(documentosFaltantes)
                .observaciones(observaciones)
                .proximosPasos(proximosPasos)
                .accionesDisponibles(construirAccionesDisponibles(
                        solicitud,
                        pantalla,
                        usuario.getId(),
                        tramiteId,
                        documentosFaltantes,
                        observaciones,
                        tramitesDisponibles
                ))
                .build();

        return SolicitudGuiaUsuarioMovil.builder()
                .usuarioId(usuario.getId())
                .nombreUsuario(normalizarTexto(usuario.getNombre()))
                .rol("MOBILE_USER")
                .pantalla(pantalla)
                .pregunta(normalizarPregunta(solicitud != null ? solicitud.getPregunta() : null))
                .contexto(contexto)
                .build();
    }

    private SeguimientoInstanciaResponse cargarSeguimiento(String actorUserId, String tramiteId) {
        if (normalizarTexto(tramiteId) == null) {
            return null;
        }
        return instanciaPoliticaService.obtenerSeguimientoPorId(actorUserId, tramiteId);
    }

    private List<TareaActividad> cargarTareasInstancia(String tramiteId) {
        if (normalizarTexto(tramiteId) == null) {
            return List.of();
        }
        List<TareaActividad> tareas = tareaActividadRepository.findByInstanciaIdOrderByFechaCreacionAsc(tramiteId);
        return tareas != null ? tareas : List.of();
    }

    private List<HistorialInstancia> cargarHistorialInstancia(String tramiteId) {
        if (normalizarTexto(tramiteId) == null) {
            return List.of();
        }
        List<HistorialInstancia> historial = historialInstanciaService.listarPorInstancia(tramiteId);
        return historial != null ? historial : List.of();
    }

    private PoliticaNegocio cargarPolitica(String politicaId) {
        String id = normalizarTexto(politicaId);
        if (id == null) {
            return null;
        }
        return politicaNegocioRepository.findById(id).orElse(null);
    }

    private String resolverPoliticaId(ContextoGuiaUsuarioMovil contextoEntrante, SeguimientoInstanciaResponse seguimiento) {
        if (seguimiento != null && normalizarTexto(seguimiento.getPoliticaId()) != null) {
            return seguimiento.getPoliticaId();
        }
        return normalizarTexto(contextoEntrante.getPoliticaId());
    }

    private String resolverNombrePolitica(
            ContextoGuiaUsuarioMovil contextoEntrante,
            SeguimientoInstanciaResponse seguimiento,
            PoliticaNegocio politica
    ) {
        if (seguimiento != null && normalizarTexto(seguimiento.getPoliticaNombre()) != null) {
            return seguimiento.getPoliticaNombre();
        }
        if (politica != null && normalizarTexto(politica.getNombre()) != null) {
            return politica.getNombre();
        }
        return normalizarTexto(contextoEntrante.getNombrePolitica());
    }

    private ContextoEtapaActualGuiaUsuarioMovil construirEtapaActual(
            SeguimientoInstanciaResponse seguimiento,
            PoliticaNegocio politica,
            List<TareaActividad> tareasInstancia
    ) {
        if (seguimiento != null && seguimiento.getDepartamentosActuales() != null
                && !seguimiento.getDepartamentosActuales().isEmpty()) {
            SeguimientoInstanciaResponse.DepartamentoActualResponse actual = seguimiento.getDepartamentosActuales().get(0);
            return ContextoEtapaActualGuiaUsuarioMovil.builder()
                    .identificador(actual.getNodoId())
                    .nombre(normalizarTexto(actual.getNodoNombre()))
                    .descripcion(construirDescripcionEtapa(actual.getNodoId(), politica, tareasInstancia))
                    .departamento(normalizarTexto(actual.getDepartamentoNombre()))
                    .responsable(primerValorNoVacio(actual.getAsignadoANombre(), actual.getResponsableNombre()))
                    .build();
        }

        if (seguimiento != null && seguimiento.getNodos() != null && seguimiento.getNodosActualesIds() != null) {
            for (String nodoActualId : seguimiento.getNodosActualesIds()) {
                SeguimientoInstanciaResponse.NodoSeguimientoResponse nodo = seguimiento.getNodos().stream()
                        .filter(Objects::nonNull)
                        .filter(item -> Objects.equals(normalizarTexto(item.getId()), normalizarTexto(nodoActualId)))
                        .findFirst()
                        .orElse(null);
                if (nodo == null) {
                    continue;
                }
                return ContextoEtapaActualGuiaUsuarioMovil.builder()
                        .identificador(nodo.getId())
                        .nombre(normalizarTexto(nodo.getNombre()))
                        .descripcion(construirDescripcionEtapa(nodo.getId(), politica, tareasInstancia))
                        .departamento(normalizarTexto(nodo.getDepartamentoNombre()))
                        .responsable(primerValorNoVacio(nodo.getAsignadoANombre(), nodo.getResponsableNombre()))
                        .build();
            }
        }
        return null;
    }

    private ResumenProgresoGuiaUsuarioMovil construirResumenProgreso(SeguimientoInstanciaResponse seguimiento) {
        if (seguimiento == null || seguimiento.getNodos() == null || seguimiento.getNodos().isEmpty()) {
            return null;
        }

        List<SeguimientoInstanciaResponse.NodoSeguimientoResponse> nodosEtapa = seguimiento.getNodos().stream()
                .filter(Objects::nonNull)
                .filter(nodo -> nodo.getTipo() == TipoNodo.ACTIVIDAD || nodo.getTipo() == TipoNodo.DECISION)
                .toList();

        int pasosCompletados = (int) nodosEtapa.stream()
                .filter(nodo -> "COMPLETADO".equalsIgnoreCase(nodo.getEstadoSeguimiento()))
                .count();
        int pasosPendientes = (int) nodosEtapa.stream()
                .filter(nodo -> !"COMPLETADO".equalsIgnoreCase(nodo.getEstadoSeguimiento()))
                .count();

        String pasoActual = seguimiento.getDepartamentosActuales() != null
                ? seguimiento.getDepartamentosActuales().stream()
                .map(SeguimientoInstanciaResponse.DepartamentoActualResponse::getNodoNombre)
                .map(this::normalizarTexto)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null)
                : null;

        int total = pasosCompletados + pasosPendientes;
        int porcentaje = total > 0 ? (int) Math.round((pasosCompletados * 100d) / total) : 0;

        return ResumenProgresoGuiaUsuarioMovil.builder()
                .pasosCompletados(pasosCompletados)
                .pasoActual(pasoActual)
                .pasosPendientes(pasosPendientes)
                .porcentajeAvance(porcentaje)
                .build();
    }

    private List<HistorialGuiaUsuarioMovil> construirHistorial(
            List<TareaActividad> tareasInstancia,
            List<HistorialInstancia> historialInstancia
    ) {
        List<HistorialGuiaUsuarioMovil> historial = new ArrayList<>();

        for (TareaActividad tarea : tareasInstancia) {
            if (tarea == null || tarea.getEstadoTarea() == null) {
                continue;
            }
            if (tarea.getEstadoTarea() != EstadoTarea.COMPLETADA
                    && tarea.getEstadoTarea() != EstadoTarea.RECHAZADA
                    && tarea.getEstadoTarea() != EstadoTarea.CANCELADA) {
                continue;
            }

            historial.add(HistorialGuiaUsuarioMovil.builder()
                    .etapa(normalizarTexto(tarea.getNombreNodo()))
                    .estado(mapearEstadoTareaGuia(tarea.getEstadoTarea()))
                    .fecha(formatearFecha(tarea.getFechaFin() != null ? tarea.getFechaFin() : tarea.getFechaInicio()))
                    .detalle(normalizarTexto(tarea.getObservaciones()))
                    .responsable(null)
                    .build());
        }

        if (historial.isEmpty()) {
            for (HistorialInstancia evento : historialInstancia) {
                if (evento == null || normalizarTexto(evento.getDetalle()) == null) {
                    continue;
                }
                historial.add(HistorialGuiaUsuarioMovil.builder()
                        .etapa(normalizarTexto(evento.getAccion()))
                        .estado("EVENTO")
                        .fecha(formatearFecha(evento.getFecha()))
                        .detalle(normalizarTexto(evento.getDetalle()))
                        .responsable(normalizarTexto(evento.getUsuario()))
                        .build());
                if (historial.size() >= 5) {
                    break;
                }
            }
            return historial;
        }

        historial.sort(Comparator.comparing(
                item -> normalizarTexto(item.getFecha()) != null ? item.getFecha() : "",
                Comparator.naturalOrder()
        ));
        return historial.stream().limit(5).toList();
    }

    private List<String> construirDocumentosFaltantes(List<TareaActividad> tareasUsuarioAbiertas) {
        LinkedHashSet<String> documentos = new LinkedHashSet<>();
        for (TareaActividad tarea : tareasUsuarioAbiertas) {
            List<CampoFormulario> campos = tarea.getFormularioDefinicion() != null ? tarea.getFormularioDefinicion() : List.of();
            Map<String, Object> respuestas = tarea.getFormularioRespuesta() != null ? tarea.getFormularioRespuesta() : Map.of();

            for (CampoFormulario campo : campos) {
                if (campo == null || !esCampoDocumento(campo)) {
                    continue;
                }
                String clave = normalizarTexto(campo.getCampo());
                if (clave == null || !esValorVacio(respuestas.get(clave))) {
                    continue;
                }
                documentos.add(clave);
            }
        }
        return documentos.stream().limit(5).toList();
    }

    private List<String> construirObservaciones(
            List<TareaActividad> tareasInstancia,
            List<HistorialInstancia> historialInstancia
    ) {
        LinkedHashSet<String> observaciones = new LinkedHashSet<>();

        for (int i = tareasInstancia.size() - 1; i >= 0; i--) {
            TareaActividad tarea = tareasInstancia.get(i);
            String observacion = normalizarTexto(tarea != null ? tarea.getObservaciones() : null);
            if (observacion != null) {
                observaciones.add(observacion);
            }
            if (tarea != null && tarea.getEstadoTarea() == EstadoTarea.RECHAZADA && normalizarTexto(tarea.getNombreNodo()) != null) {
                observaciones.add("La etapa " + tarea.getNombreNodo() + " fue rechazada.");
            }
            if (observaciones.size() >= 5) {
                break;
            }
        }

        if (observaciones.isEmpty()) {
            for (int i = historialInstancia.size() - 1; i >= 0; i--) {
                HistorialInstancia evento = historialInstancia.get(i);
                String detalle = normalizarTexto(evento != null ? evento.getDetalle() : null);
                if (detalle == null) {
                    continue;
                }
                String detalleNormalizado = normalizarSinAcentos(detalle);
                if (detalleNormalizado.contains("observ") || detalleNormalizado.contains("rechaz")) {
                    observaciones.add(detalle);
                }
                if (observaciones.size() >= 5) {
                    break;
                }
            }
        }

        return observaciones.stream().limit(5).toList();
    }

    private List<String> construirProximosPasos(SeguimientoInstanciaResponse seguimiento) {
        if (seguimiento == null || seguimiento.getConexiones() == null || seguimiento.getNodos() == null) {
            return List.of();
        }

        Map<String, String> nodosPorId = new LinkedHashMap<>();
        seguimiento.getNodos().stream()
                .filter(Objects::nonNull)
                .forEach(nodo -> nodosPorId.put(normalizarTexto(nodo.getId()), nombreNodoGuia(nodo.getNombre(), nodo.getTipo())));

        LinkedHashSet<String> proximosPasos = new LinkedHashSet<>();
        for (String nodoActualId : seguimiento.getNodosActualesIds() != null ? seguimiento.getNodosActualesIds() : List.<String>of()) {
            String nodoNormalizado = normalizarTexto(nodoActualId);
            seguimiento.getConexiones().stream()
                    .filter(Objects::nonNull)
                    .filter(conexion -> Objects.equals(normalizarTexto(conexion.getOrigen()), nodoNormalizado))
                    .map(SeguimientoInstanciaResponse.ConexionSeguimientoResponse::getDestino)
                    .map(this::normalizarTexto)
                    .filter(Objects::nonNull)
                    .map(nodosPorId::get)
                    .filter(Objects::nonNull)
                    .forEach(proximosPasos::add);
        }

        return proximosPasos.stream().limit(4).toList();
    }

    private List<String> construirAccionesDisponibles(
            SolicitudGuiaUsuarioMovil solicitud,
            String pantalla,
            String actorUserId,
            String tramiteId,
            List<String> documentosFaltantes,
            List<String> observaciones,
            List<TramiteDisponibleResponse> tramitesDisponibles
    ) {
        LinkedHashSet<String> acciones = new LinkedHashSet<>();
        if (solicitud != null && solicitud.getContexto() != null && solicitud.getContexto().getAccionesDisponibles() != null) {
            solicitud.getContexto().getAccionesDisponibles().stream()
                    .map(this::normalizarCodigo)
                    .filter(valor -> !valor.isBlank())
                    .forEach(acciones::add);
        }

        if ("INICIO_USUARIO".equals(pantalla) || "FORMULARIO_SOLICITUD".equals(pantalla)) {
            if (!tramitesDisponibles.isEmpty()) {
                acciones.add("INICIAR_TRAMITE");
            }
        }

        if ("LISTA_TRAMITES".equals(pantalla)) {
            acciones.add("CONSULTAR_ESTADO");
            acciones.add("VER_HISTORIAL");
            acciones.add("VER_DETALLE_TRAMITE");
        }

        if (("DETALLE_TRAMITE".equals(pantalla) || "ESTADO_TRAMITE".equals(pantalla))
                && normalizarTexto(tramiteId) != null) {
            acciones.add("CONSULTAR_ESTADO");
            acciones.add("VER_HISTORIAL");
            acciones.add("VER_DETALLE_TRAMITE");
            if (!observaciones.isEmpty()) {
                acciones.add("VER_OBSERVACIONES");
            }
            if (!documentosFaltantes.isEmpty()) {
                acciones.add("SUBIR_DOCUMENTO");
            }
        }

        if ("NOTIFICACIONES".equals(pantalla)) {
            acciones.add("CONSULTAR_ESTADO");
        }

        return acciones.stream().limit(8).toList();
    }

    private List<TramiteDisponibleResponse> cargarTramitesDisponibles(Usuario usuario, String pantalla) {
        if (!"INICIO_USUARIO".equals(pantalla) && !"FORMULARIO_SOLICITUD".equals(pantalla)) {
            return List.of();
        }
        return politicaNegocioService.obtenerTramitesDisponibles(usuario.getId());
    }

    private List<TareaActividad> filtrarTareasUsuarioAbiertas(Usuario usuario, List<TareaActividad> tareasInstancia) {
        return tareasInstancia.stream()
                .filter(Objects::nonNull)
                .filter(tarea -> tarea.getEstadoTarea() == EstadoTarea.PENDIENTE || tarea.getEstadoTarea() == EstadoTarea.EN_PROCESO)
                .filter(tarea -> actorPuedeResolverTarea(usuario, tarea))
                .toList();
    }

    private boolean actorPuedeResolverTarea(Usuario usuario, TareaActividad tarea) {
        if (usuario == null || tarea == null) {
            return false;
        }
        if (Objects.equals(normalizarTexto(tarea.getAsignadoA()), normalizarTexto(usuario.getId()))) {
            return true;
        }
        return "USUARIO".equalsIgnoreCase(normalizarTexto(tarea.getResponsableTipo()))
                && Objects.equals(normalizarTexto(tarea.getResponsableId()), normalizarTexto(usuario.getId()));
    }

    private String resolverEstadoTramite(SeguimientoInstanciaResponse seguimiento, List<TareaActividad> tareasInstancia) {
        boolean tieneRechazo = tareasInstancia.stream()
                .filter(Objects::nonNull)
                .anyMatch(tarea -> tarea.getEstadoTarea() == EstadoTarea.RECHAZADA);
        if (tieneRechazo) {
            return "RECHAZADO";
        }

        if (seguimiento == null || seguimiento.getEstadoInstancia() == null) {
            return null;
        }

        return switch (seguimiento.getEstadoInstancia()) {
            case EN_CURSO -> "EN_PROCESO";
            case PAUSADA -> "DETENIDO";
            case FINALIZADA -> "FINALIZADO";
            case CANCELADA -> "CANCELADO";
        };
    }

    private String construirDescripcionEtapa(
            String nodoId,
            PoliticaNegocio politica,
            List<TareaActividad> tareasInstancia
    ) {
        TareaActividad tareaAbierta = tareasInstancia.stream()
                .filter(Objects::nonNull)
                .filter(tarea -> Objects.equals(normalizarTexto(tarea.getNodoId()), normalizarTexto(nodoId)))
                .filter(tarea -> tarea.getEstadoTarea() == EstadoTarea.PENDIENTE || tarea.getEstadoTarea() == EstadoTarea.EN_PROCESO)
                .findFirst()
                .orElse(null);

        if (tareaAbierta != null && tareaAbierta.getFormularioDefinicion() != null && !tareaAbierta.getFormularioDefinicion().isEmpty()) {
            return "En esta etapa se revisa informacion o documentos antes de permitir que el tramite continue.";
        }

        if (politica != null && politica.getNodos() != null) {
            TipoNodo tipoNodo = politica.getNodos().stream()
                    .filter(Objects::nonNull)
                    .filter(nodo -> Objects.equals(normalizarTexto(nodo.getId()), normalizarTexto(nodoId)))
                    .map(com.leo.politicas_de_negocio.politicas.model.politica.Nodo::getTipo)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (tipoNodo == TipoNodo.DECISION) {
                return "En esta etapa se evalua el resultado para decidir el siguiente camino del tramite.";
            }
        }

        return "El area responsable esta trabajando en esta etapa del tramite.";
    }

    private String normalizarPantalla(String pantalla, String tramiteId) {
        String normalizada = normalizarCodigo(pantalla);
        if (List.of(
                "INICIO_USUARIO",
                "LISTA_TRAMITES",
                "DETALLE_TRAMITE",
                "ESTADO_TRAMITE",
                "FORMULARIO_SOLICITUD",
                "PERFIL_USUARIO",
                "NOTIFICACIONES"
        ).contains(normalizada)) {
            return normalizada;
        }
        if (normalizarTexto(tramiteId) != null) {
            return "DETALLE_TRAMITE";
        }
        return "INICIO_USUARIO";
    }

    private String normalizarPregunta(String pregunta) {
        String preguntaNormalizada = normalizarTexto(pregunta);
        if (preguntaNormalizada == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar la pregunta del bot guia");
        }
        return preguntaNormalizada;
    }

    private boolean esRespuestaUtil(RespuestaGuiaUsuarioMovil respuesta) {
        return respuesta != null && normalizarTexto(respuesta.getRespuesta()) != null;
    }

    private Usuario validarUsuarioMovil(String usuarioMovilId) {
        String userId = normalizarTexto(usuarioMovilId);
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-User-Id");
        }

        Usuario usuario = usuarioRepository.findByIdAndActivo(userId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
        if (!"USUARIO".equalsIgnoreCase(usuario.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El bot guia de usuario movil solo esta disponible para el rol USUARIO");
        }
        return usuario;
    }

    private boolean esCampoDocumento(CampoFormulario campo) {
        if (campo == null) {
            return false;
        }
        if (campo.getTipo() == TipoCampo.ARCHIVO) {
            return true;
        }
        String nombreCampo = normalizarSinAcentos(campo.getCampo());
        return nombreCampo.contains("document")
                || nombreCampo.contains("archivo")
                || nombreCampo.contains("adjunto")
                || nombreCampo.contains("evidencia")
                || nombreCampo.contains("soporte");
    }

    private boolean esValorVacio(Object valor) {
        if (valor == null) {
            return true;
        }
        if (valor instanceof String texto) {
            return normalizarTexto(texto) == null;
        }
        if (valor instanceof List<?> lista) {
            return lista.isEmpty();
        }
        if (valor instanceof Map<?, ?> mapa) {
            return mapa.isEmpty();
        }
        return false;
    }

    private String mapearEstadoTareaGuia(EstadoTarea estadoTarea) {
        if (estadoTarea == null) {
            return null;
        }
        return switch (estadoTarea) {
            case COMPLETADA -> "COMPLETADO";
            case RECHAZADA -> "RECHAZADO";
            case CANCELADA -> "CANCELADO";
            case PENDIENTE -> "PENDIENTE";
            case EN_PROCESO -> "EN_PROCESO";
        };
    }

    private String nombreNodoGuia(String nombreNodo, TipoNodo tipoNodo) {
        if (normalizarTexto(nombreNodo) != null) {
            return nombreNodo;
        }
        if (tipoNodo == TipoNodo.FIN) {
            return "Finalizacion del tramite";
        }
        return "Siguiente etapa";
    }

    private String primerValorNoVacio(String... valores) {
        for (String valor : valores) {
            if (normalizarTexto(valor) != null) {
                return valor;
            }
        }
        return null;
    }

    private String formatearFecha(LocalDateTime fecha) {
        return fecha != null ? FORMATO_FECHA.format(fecha) : null;
    }

    private String normalizarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarSinAcentos(String valor) {
        if (valor == null) {
            return "";
        }
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalizado.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
