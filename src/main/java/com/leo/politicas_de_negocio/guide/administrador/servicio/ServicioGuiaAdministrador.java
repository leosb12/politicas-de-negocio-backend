package com.leo.politicas_de_negocio.guide.administrador.servicio;

import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.guide.administrador.dto.CampoFormularioGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.ContextoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.NodoSeleccionadoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.ProblemaDetectadoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.RespuestaGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.ResumenPoliticaGuia;
import com.leo.politicas_de_negocio.guide.administrador.dto.SolicitudGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.cliente.ClienteGuiaIa;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioGuiaAdministrador {

    private static final String RESPONSABLE_USUARIO_FINAL_ID = "__RESPONSABLE_USUARIO_FINAL__";
    private static final String RESPONSABLE_INICIADOR_TRAMITE_ID = "__RESPONSABLE_INICIADOR_TRAMITE__";

    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ClienteGuiaIa clienteGuiaIa;
    private final ServicioFallbackGuiaAdministrador servicioFallback;
    private final ResolvedorIntencionGuiaAdministrador resolvedorIntencion;

    public RespuestaGuiaAdministrador guiarAdministrador(String adminUserId, SolicitudGuiaAdministrador solicitud) {
        Usuario administrador = validarAdministrador(adminUserId);
        SolicitudGuiaAdministrador solicitudIa = construirSolicitudIa(administrador, solicitud);
        RespuestaGuiaAdministrador respuesta = clienteGuiaIa.guiarAdministrador(solicitudIa);
        if (esRespuestaUtil(respuesta)) {
            return respuesta;
        }
        return servicioFallback.construir(
                solicitudIa,
                resolvedorIntencion.resolver(solicitudIa.getPregunta(), solicitudIa.getPantalla())
        );
    }

    private SolicitudGuiaAdministrador construirSolicitudIa(Usuario administrador, SolicitudGuiaAdministrador solicitud) {
        ContextoGuiaAdministrador contextoEntrante = solicitud != null && solicitud.getContexto() != null
                ? solicitud.getContexto()
                : new ContextoGuiaAdministrador();

        PoliticaNegocio politica = cargarPolitica(contextoEntrante.getPoliticaId());
        ResumenPoliticaGuia resumenPolitica = politica != null ? construirResumenPolitica(politica) : null;
        List<ProblemaDetectadoGuiaAdministrador> problemasDetectados = politica != null
                ? construirProblemasDetectados(politica, resumenPolitica)
                : new ArrayList<>();
        NodoSeleccionadoGuiaAdministrador nodoSeleccionado = politica != null
                ? construirNodoSeleccionado(politica, contextoEntrante.getNodoSeleccionadoId(), problemasDetectados)
                : null;

        ContextoGuiaAdministrador contexto = ContextoGuiaAdministrador.builder()
                .politicaId(politica != null ? politica.getId() : normalizar(contextoEntrante.getPoliticaId()))
                .nombrePolitica(politica != null ? normalizar(politica.getNombre()) : null)
                .estadoPolitica(politica != null && politica.getEstado() != null ? politica.getEstado().name() : null)
                .nodoSeleccionadoId(normalizar(contextoEntrante.getNodoSeleccionadoId()))
                .nodoSeleccionado(nodoSeleccionado)
                .resumenPolitica(resumenPolitica)
                .problemasDetectados(problemasDetectados)
                .accionesDisponibles(construirAccionesDisponibles(solicitud, politica, nodoSeleccionado, problemasDetectados))
                .departamentosPolitica(construirDepartamentosPolitica(politica))
                .build();

        return SolicitudGuiaAdministrador.builder()
                .usuarioId(administrador.getId())
                .nombreUsuario(normalizar(administrador.getNombre()))
                .rol("ADMIN")
                .pantalla(normalizarPantalla(solicitud != null ? solicitud.getPantalla() : null))
                .pregunta(normalizarPregunta(solicitud != null ? solicitud.getPregunta() : null))
                .contexto(contexto)
                .build();
    }

    private PoliticaNegocio cargarPolitica(String politicaId) {
        String politicaIdNormalizada = normalizar(politicaId);
        if (politicaIdNormalizada == null) {
            return null;
        }
        return politicaNegocioRepository.findById(politicaIdNormalizada).orElse(null);
    }

    private ResumenPoliticaGuia construirResumenPolitica(PoliticaNegocio politica) {
        List<Nodo> nodos = politica.getNodos() != null ? politica.getNodos() : List.of();
        List<Conexion> conexiones = politica.getConexiones() != null ? politica.getConexiones() : List.of();
        Set<String> idsNodos = nodos.stream()
                .map(Nodo::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int conexionesInvalidas = 0;
        for (Conexion conexion : conexiones) {
            if (conexion == null || normalizar(conexion.getOrigen()) == null || normalizar(conexion.getDestino()) == null) {
                conexionesInvalidas++;
                continue;
            }
            if (!idsNodos.contains(conexion.getOrigen()) || !idsNodos.contains(conexion.getDestino())) {
                conexionesInvalidas++;
            }
        }

        int nodosHuerfanos = 0;
        for (Nodo nodo : nodos) {
            if (nodo == null || normalizar(nodo.getId()) == null) {
                continue;
            }
            boolean tieneEntradas = conexiones.stream().anyMatch(conexion -> nodo.getId().equals(conexion.getDestino()));
            boolean tieneSalidas = conexiones.stream().anyMatch(conexion -> nodo.getId().equals(conexion.getOrigen()));
            if (nodo.getTipo() == TipoNodo.INICIO && !tieneSalidas) {
                nodosHuerfanos++;
            } else if (nodo.getTipo() == TipoNodo.FIN && !tieneEntradas) {
                nodosHuerfanos++;
            } else if (nodo.getTipo() != TipoNodo.INICIO && nodo.getTipo() != TipoNodo.FIN && (!tieneEntradas || !tieneSalidas)) {
                nodosHuerfanos++;
            }
        }

        int decisionesSinRutas = (int) nodos.stream()
                .filter(nodo -> nodo != null && nodo.getTipo() == TipoNodo.DECISION)
                .filter(nodo -> conexiones.stream().filter(conexion -> nodo.getId().equals(conexion.getOrigen())).count() < 2)
                .count();

        int nodosParalelosIncompletos = (int) nodos.stream()
                .filter(nodo -> nodo != null && (nodo.getTipo() == TipoNodo.FORK || nodo.getTipo() == TipoNodo.JOIN))
                .filter(nodo -> esNodoParaleloIncompleto(nodo, conexiones))
                .count();

        return ResumenPoliticaGuia.builder()
                .tieneNodoInicio(nodos.stream().anyMatch(nodo -> nodo != null && nodo.getTipo() == TipoNodo.INICIO))
                .tieneNodoFinal(nodos.stream().anyMatch(nodo -> nodo != null && nodo.getTipo() == TipoNodo.FIN))
                .totalActividades((int) nodos.stream().filter(nodo -> nodo != null && nodo.getTipo() == TipoNodo.ACTIVIDAD).count())
                .totalDecisiones((int) nodos.stream().filter(nodo -> nodo != null && nodo.getTipo() == TipoNodo.DECISION).count())
                .actividadesSinResponsable((int) nodos.stream()
                        .filter(nodo -> nodo != null && nodo.getTipo() == TipoNodo.ACTIVIDAD)
                        .filter(nodo -> normalizar(nodo.getResponsableTipo()) == null || normalizar(nodo.getResponsableId()) == null)
                        .count())
                .actividadesSinFormulario((int) nodos.stream()
                        .filter(nodo -> nodo != null && nodo.getTipo() == TipoNodo.ACTIVIDAD)
                        .filter(nodo -> nodo.getFormulario() == null || nodo.getFormulario().isEmpty())
                        .count())
                .conexionesInvalidas(conexionesInvalidas)
                .decisionesSinRutas(decisionesSinRutas)
                .nodosParalelosIncompletos(nodosParalelosIncompletos)
                .nodosHuerfanos(nodosHuerfanos)
                .build();
    }

    private boolean esNodoParaleloIncompleto(Nodo nodo, List<Conexion> conexiones) {
        long salidas = conexiones.stream().filter(conexion -> nodo.getId().equals(conexion.getOrigen())).count();
        long entradas = conexiones.stream().filter(conexion -> nodo.getId().equals(conexion.getDestino())).count();
        if (nodo.getTipo() == TipoNodo.FORK) {
            return salidas < 2;
        }
        if (nodo.getTipo() == TipoNodo.JOIN) {
            return entradas < 2 || salidas < 1;
        }
        return false;
    }

    private List<ProblemaDetectadoGuiaAdministrador> construirProblemasDetectados(
            PoliticaNegocio politica,
            ResumenPoliticaGuia resumen
    ) {
        List<ProblemaDetectadoGuiaAdministrador> problemas = new ArrayList<>();
        if (resumen == null) {
            return problemas;
        }
        if (!resumen.isTieneNodoInicio()) {
            problemas.add(problema("MISSING_START_NODE", "La politica no tiene nodo de inicio."));
        }
        if (!resumen.isTieneNodoFinal()) {
            problemas.add(problema("MISSING_END_NODE", "La politica no tiene nodo final."));
        }
        if (resumen.getActividadesSinResponsable() > 0) {
            problemas.add(problema(
                    "ACTIVITIES_WITHOUT_RESPONSIBLE",
                    "Hay " + resumen.getActividadesSinResponsable() + " actividad(es) sin responsable asignado."
            ));
        }
        if (resumen.getActividadesSinFormulario() > 0) {
            problemas.add(problema(
                    "ACTIVITIES_WITHOUT_FORM",
                    "Hay " + resumen.getActividadesSinFormulario() + " actividad(es) sin formulario configurado."
            ));
        }
        if (resumen.getConexionesInvalidas() > 0) {
            problemas.add(problema(
                    "INVALID_CONNECTIONS",
                    "Hay " + resumen.getConexionesInvalidas() + " conexion(es) invalidas o incompletas."
            ));
        }
        if (resumen.getDecisionesSinRutas() > 0) {
            problemas.add(problema(
                    "DECISIONS_WITHOUT_ROUTES",
                    "Hay " + resumen.getDecisionesSinRutas() + " decision(es) sin caminos completos."
            ));
        }
        if (resumen.getNodosParalelosIncompletos() > 0) {
            problemas.add(problema(
                    "PARALLEL_FLOW_INCOMPLETE",
                    "Hay " + resumen.getNodosParalelosIncompletos() + " nodo(s) de paralelismo incompletos."
            ));
        }
        if (resumen.getNodosHuerfanos() > 0) {
            problemas.add(problema(
                    "ORPHAN_NODES",
                    "Hay " + resumen.getNodosHuerfanos() + " nodo(s) desconectados del flujo principal."
            ));
        }
        if (politica.getNodos() == null || politica.getNodos().isEmpty()) {
            problemas.add(problema("EMPTY_POLICY", "La politica todavia no tiene nodos."));
        }
        return problemas;
    }

    private NodoSeleccionadoGuiaAdministrador construirNodoSeleccionado(
            PoliticaNegocio politica,
            String nodoSeleccionadoId,
            List<ProblemaDetectadoGuiaAdministrador> problemasDetectados
    ) {
        String nodoIdNormalizado = normalizar(nodoSeleccionadoId);
        if (nodoIdNormalizado == null) {
            return null;
        }

        List<Nodo> nodos = politica.getNodos() != null ? politica.getNodos() : List.of();
        Map<String, Nodo> nodosPorId = nodos.stream()
                .filter(Objects::nonNull)
                .filter(nodo -> normalizar(nodo.getId()) != null)
                .collect(Collectors.toMap(Nodo::getId, Function.identity(), (izq, der) -> izq));

        Nodo nodoSeleccionado = nodosPorId.get(nodoIdNormalizado);
        if (nodoSeleccionado == null) {
            problemasDetectados.add(problema(
                    "SELECTED_NODE_NOT_FOUND",
                    "El nodo seleccionado ya no existe o no pertenece a la politica actual."
            ));
            return null;
        }

        List<Conexion> conexiones = politica.getConexiones() != null ? politica.getConexiones() : List.of();
        List<String> nodosEntrantes = conexiones.stream()
                .filter(conexion -> nodoIdNormalizado.equals(conexion.getDestino()))
                .map(Conexion::getOrigen)
                .map(nodosPorId::get)
                .filter(Objects::nonNull)
                .map(Nodo::getNombre)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<String> nodosSalientes = conexiones.stream()
                .filter(conexion -> nodoIdNormalizado.equals(conexion.getOrigen()))
                .map(Conexion::getDestino)
                .map(nodosPorId::get)
                .filter(Objects::nonNull)
                .map(Nodo::getNombre)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<CampoFormularioGuiaAdministrador> camposFormulario = (nodoSeleccionado.getFormulario() != null
                ? nodoSeleccionado.getFormulario()
                : List.<CampoFormulario>of()).stream()
                .filter(Objects::nonNull)
                .map(this::mapearCampoGuia)
                .toList();

        return NodoSeleccionadoGuiaAdministrador.builder()
                .id(nodoSeleccionado.getId())
                .tipo(mapearTipoNodo(nodoSeleccionado.getTipo()))
                .nombre(normalizar(nodoSeleccionado.getNombre()))
                .departamento(resolverNombreDepartamento(nodoSeleccionado.getDepartamentoId()))
                .responsable(resolverNombreResponsable(nodoSeleccionado.getResponsableTipo(), nodoSeleccionado.getResponsableId()))
                .tipoResponsable(normalizarCodigo(nodoSeleccionado.getResponsableTipo()))
                .camposFormulario(camposFormulario)
                .nodosEntrantes(nodosEntrantes)
                .nodosSalientes(nodosSalientes)
                .build();
    }

    private List<String> construirAccionesDisponibles(
            SolicitudGuiaAdministrador solicitud,
            PoliticaNegocio politica,
            NodoSeleccionadoGuiaAdministrador nodoSeleccionado,
            List<ProblemaDetectadoGuiaAdministrador> problemasDetectados
    ) {
        LinkedHashSet<String> acciones = new LinkedHashSet<>();
        if (solicitud != null && solicitud.getContexto() != null && solicitud.getContexto().getAccionesDisponibles() != null) {
            solicitud.getContexto().getAccionesDisponibles().stream()
                    .map(this::normalizarCodigo)
                    .filter(valor -> !valor.isBlank())
                    .forEach(acciones::add);
        }

        String pantalla = normalizarPantalla(solicitud != null ? solicitud.getPantalla() : null);
        if ("POLICY_DESIGNER".equals(pantalla)) {
            acciones.add("ADD_ACTIVITY");
            acciones.add("ADD_DECISION");
            acciones.add("CONNECT_NODES");
            acciones.add("SAVE_POLICY");
            if (politica == null || politica.getEstado() == null || !"ACTIVA".equals(politica.getEstado().name())) {
                acciones.add("ACTIVATE_POLICY");
                acciones.add("PAUSE_DESIGN");
            } else {
                acciones.add("PAUSE_POLICY");
                acciones.add("DEACTIVATE_POLICY");
            }
            if (nodoSeleccionado != null && "ACTIVITY".equals(nodoSeleccionado.getTipo())) {
                acciones.add("ASSIGN_RESPONSIBLE");
                acciones.add("ADD_FORM_FIELD");
            }
            if (nodoSeleccionado != null && "DECISION".equals(nodoSeleccionado.getTipo())) {
                acciones.add("CONFIGURE_DECISION");
            }
        } else if ("POLICY_LIST".equals(pantalla)) {
            acciones.add("CREATE_POLICY");
            acciones.add("EDIT_POLICY");
            acciones.add("ACTIVATE_POLICY");
            acciones.add("DEACTIVATE_POLICY");
        }

        Set<String> tiposProblema = problemasDetectados.stream()
                .map(ProblemaDetectadoGuiaAdministrador::getTipo)
                .collect(Collectors.toSet());
        if (tiposProblema.contains("MISSING_START_NODE")) {
            acciones.add("ADD_START_NODE");
        }
        if (tiposProblema.contains("MISSING_END_NODE")) {
            acciones.add("ADD_END_NODE");
        }
        if (tiposProblema.contains("ACTIVITIES_WITHOUT_RESPONSIBLE")) {
            acciones.add("ASSIGN_RESPONSIBLE");
        }
        if (tiposProblema.contains("ACTIVITIES_WITHOUT_FORM")) {
            acciones.add("ADD_FORM_FIELD");
        }
        if (tiposProblema.contains("INVALID_CONNECTIONS") || tiposProblema.contains("DECISIONS_WITHOUT_ROUTES")) {
            acciones.add("CONNECT_NODES");
        }

        return acciones.stream().limit(12).toList();
    }

    private List<String> construirDepartamentosPolitica(PoliticaNegocio politica) {
        if (politica == null || politica.getNodos() == null) {
            return List.of();
        }

        return politica.getNodos().stream()
                .map(Nodo::getDepartamentoId)
                .map(this::resolverNombreDepartamento)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private CampoFormularioGuiaAdministrador mapearCampoGuia(CampoFormulario campo) {
        return CampoFormularioGuiaAdministrador.builder()
                .etiqueta(normalizar(campo.getCampo()))
                .tipo(mapearTipoCampo(campo.getTipo()))
                .obligatorio(false)
                .build();
    }

    private String resolverNombreDepartamento(String departamentoId) {
        String departamentoNormalizado = normalizar(departamentoId);
        if (departamentoNormalizado == null) {
            return null;
        }
        return departamentoRepository.findById(departamentoNormalizado)
                .map(departamento -> normalizar(departamento.getNombre()))
                .orElse(departamentoNormalizado);
    }

    private String resolverNombreResponsable(String responsableTipo, String responsableId) {
        String tipoResponsableNormalizado = normalizarCodigo(responsableTipo);
        String responsableIdNormalizado = normalizar(responsableId);
        if (responsableIdNormalizado == null) {
            return null;
        }

        if ("DEPARTAMENTO".equals(tipoResponsableNormalizado)) {
            return resolverNombreDepartamento(responsableIdNormalizado);
        }

        if ("USUARIO".equals(tipoResponsableNormalizado)) {
            if (RESPONSABLE_USUARIO_FINAL_ID.equals(responsableIdNormalizado)) {
                return "Usuario final";
            }
            if (RESPONSABLE_INICIADOR_TRAMITE_ID.equals(responsableIdNormalizado)) {
                return "Iniciador del tramite";
            }
            return usuarioRepository.findById(responsableIdNormalizado)
                    .map(usuario -> normalizar(usuario.getNombre()))
                    .orElse(responsableIdNormalizado);
        }

        return responsableIdNormalizado;
    }

    private String mapearTipoNodo(TipoNodo tipoNodo) {
        if (tipoNodo == null) {
            return null;
        }
        return switch (tipoNodo) {
            case INICIO -> "START";
            case ACTIVIDAD -> "ACTIVITY";
            case DECISION -> "DECISION";
            case FORK -> "FORK";
            case JOIN -> "JOIN";
            case FIN -> "END";
        };
    }

    private String mapearTipoCampo(TipoCampo tipoCampo) {
        if (tipoCampo == null) {
            return "TEXT";
        }
        return switch (tipoCampo) {
            case TEXTO -> "TEXT";
            case NUMERO -> "NUMBER";
            case BOOLEANO -> "BOOLEAN";
            case ARCHIVO -> "FILE";
            case FECHA -> "DATE";
            case CHECKBOX -> "CHECKBOX";
            case SELECCION -> "SELECTION";
            case GRID -> "GRID";
            case LABEL -> "LABEL";
        };
    }

    private String normalizarPregunta(String pregunta) {
        String preguntaNormalizada = normalizar(pregunta);
        if (preguntaNormalizada == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La pregunta del bot guia es obligatoria");
        }
        return preguntaNormalizada;
    }

    private String normalizarPantalla(String pantalla) {
        String pantallaNormalizada = normalizarCodigo(pantalla);
        return pantallaNormalizada.isBlank() ? "GENERAL_ADMIN" : pantallaNormalizada;
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String valorNormalizado = valor.trim();
        return valorNormalizado.isEmpty() ? null : valorNormalizado;
    }

    private ProblemaDetectadoGuiaAdministrador problema(String tipo, String mensaje) {
        return ProblemaDetectadoGuiaAdministrador.builder()
                .tipo(tipo)
                .mensaje(mensaje)
                .build();
    }

    private boolean esRespuestaUtil(RespuestaGuiaAdministrador respuesta) {
        return respuesta != null
                && respuesta.isDisponible()
                && normalizar(respuesta.getRespuesta()) != null;
    }

    private Usuario validarAdministrador(String adminUserId) {
        String adminUserIdNormalizado = normalizar(adminUserId);
        if (adminUserIdNormalizado == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }

        Usuario administrador = usuarioRepository.findById(adminUserIdNormalizado)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));

        if (administrador.getRol() == null || !"ADMIN".equalsIgnoreCase(administrador.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede usar el bot guia administrativo");
        }

        return administrador;
    }
}
