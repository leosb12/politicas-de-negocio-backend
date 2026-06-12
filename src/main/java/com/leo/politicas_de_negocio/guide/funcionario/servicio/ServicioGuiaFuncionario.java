package com.leo.politicas_de_negocio.guide.funcionario.servicio;

import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.guide.cliente.ClienteGuiaIa;
import com.leo.politicas_de_negocio.guide.funcionario.dto.CampoFormularioGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.ContextoGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.FormularioGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.ItemColaTareaGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.NodoActualGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.PasoPosibleGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.RespuestaGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.ResumenDashboardGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.ResumenHistorialGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.SolicitudGuiaFuncionario;
import com.leo.politicas_de_negocio.instancias.dto.SeguimientoInstanciaResponse;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.CondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.dto.TareaDetalleResponse;
import com.leo.politicas_de_negocio.tareas.dto.TareaMiaResponse;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.service.TareaActividadService;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioGuiaFuncionario {

    private final UsuarioRepository usuarioRepository;
    private final TareaActividadService tareaActividadService;
    private final InstanciaPoliticaService instanciaPoliticaService;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ClienteGuiaIa clienteGuiaIa;
    private final ServicioFallbackGuiaFuncionario servicioFallback;
    private final ResolvedorIntencionGuiaFuncionario resolvedorIntencion;

    public RespuestaGuiaFuncionario guiarFuncionario(String funcionarioUserId, SolicitudGuiaFuncionario solicitud) {
        Usuario funcionario = validarFuncionario(funcionarioUserId);
        SolicitudGuiaFuncionario solicitudIa = construirSolicitudIa(funcionario, solicitud);
        RespuestaGuiaFuncionario respuesta = clienteGuiaIa.guiarFuncionario(solicitudIa);
        if (esRespuestaUtil(respuesta)) {
            return respuesta;
        }
        return servicioFallback.construir(
                solicitudIa,
                resolvedorIntencion.resolver(solicitudIa.getPregunta(), solicitudIa.getPantalla()));
    }

    private SolicitudGuiaFuncionario construirSolicitudIa(Usuario funcionario, SolicitudGuiaFuncionario solicitud) {
        ContextoGuiaFuncionario contextoEntrante = solicitud != null && solicitud.getContexto() != null
                ? solicitud.getContexto()
                : new ContextoGuiaFuncionario();

        List<TareaMiaResponse> colaTareas = tareaActividadService.listarMisTareasResumen(funcionario.getId());
        TareaDetalleResponse detalleTarea = cargarDetalleTarea(funcionario.getId(), contextoEntrante.getTareaId());
        TareaMiaResponse resumenTareaActual = buscarResumenTareaActual(colaTareas, contextoEntrante.getTareaId(),
                detalleTarea);

        String instanciaId = resolverInstanciaId(contextoEntrante.getInstanciaId(), detalleTarea, resumenTareaActual);
        SeguimientoInstanciaResponse seguimiento = cargarSeguimiento(funcionario.getId(), instanciaId);
        PoliticaNegocio politica = cargarPolitica(resolverPoliticaId(detalleTarea, resumenTareaActual, seguimiento));

        FormularioGuiaFuncionario formulario = construirFormulario(detalleTarea);

        ContextoGuiaFuncionario contexto = ContextoGuiaFuncionario.builder()
                .tareaId(detalleTarea != null ? detalleTarea.getId() : normalizar(contextoEntrante.getTareaId()))
                .instanciaId(instanciaId)
                .politicaId(politica != null ? politica.getId()
                        : resolverPoliticaId(detalleTarea, resumenTareaActual, seguimiento))
                .nombrePolitica(resolverNombrePolitica(detalleTarea, resumenTareaActual, seguimiento, politica))
                .nodoActual(construirNodoActual(detalleTarea, seguimiento, politica))
                .estadoTarea(resolverEstadoTarea(detalleTarea, resumenTareaActual))
                .prioridad(resolverPrioridad(resumenTareaActual, detalleTarea))
                .formulario(formulario)
                .resumenHistorial(construirResumenHistorial(detalleTarea, seguimiento))
                .pasosPosibles(construirPasosPosibles(detalleTarea, seguimiento, politica))
                .resumenDashboard(construirResumenDashboard(colaTareas))
                .colaTareas(construirColaTareas(colaTareas))
                .accionesDisponibles(
                        construirAccionesDisponibles(solicitud, detalleTarea, resumenTareaActual, formulario))
                .build();

        return SolicitudGuiaFuncionario.builder()
                .usuarioId(funcionario.getId())
                .nombreUsuario(normalizar(funcionario.getNombre()))
                .rol("EMPLOYEE")
                .pantalla(normalizarPantalla(
                        solicitud != null ? solicitud.getPantalla() : null,
                        contextoEntrante.getTareaId(),
                        instanciaId))
                .pregunta(normalizarPregunta(solicitud != null ? solicitud.getPregunta() : null))
                .contexto(contexto)
                .build();
    }

    private TareaDetalleResponse cargarDetalleTarea(String funcionarioId, String tareaId) {
        String tareaIdNormalizada = normalizar(tareaId);
        if (tareaIdNormalizada == null) {
            return null;
        }
        return tareaActividadService.obtenerDetalleTarea(funcionarioId, tareaIdNormalizada);
    }

    private SeguimientoInstanciaResponse cargarSeguimiento(String funcionarioId, String instanciaId) {
        String instanciaIdNormalizada = normalizar(instanciaId);
        if (instanciaIdNormalizada == null) {
            return null;
        }
        return instanciaPoliticaService.obtenerSeguimientoPorId(funcionarioId, instanciaIdNormalizada);
    }

    private PoliticaNegocio cargarPolitica(String politicaId) {
        String politicaIdNormalizada = normalizar(politicaId);
        if (politicaIdNormalizada == null) {
            return null;
        }
        return politicaNegocioRepository.findById(politicaIdNormalizada).orElse(null);
    }

    private TareaMiaResponse buscarResumenTareaActual(
            List<TareaMiaResponse> colaTareas,
            String tareaSolicitadaId,
            TareaDetalleResponse detalleTarea) {
        String objetivoId = normalizar(tareaSolicitadaId);
        if (objetivoId == null && detalleTarea != null) {
            objetivoId = normalizar(detalleTarea.getId());
        }
        if (objetivoId == null) {
            return null;
        }
        final String objetivoFinalId = objetivoId;

        return colaTareas.stream()
                .filter(Objects::nonNull)
                .filter(item -> objetivoFinalId.equals(normalizar(item.getId())))
                .findFirst()
                .orElse(null);
    }

    private String resolverInstanciaId(
            String instanciaSolicitadaId,
            TareaDetalleResponse detalleTarea,
            TareaMiaResponse resumenTareaActual) {
        String desdeTarea = detalleTarea != null && detalleTarea.getInstancia() != null
                ? normalizar(detalleTarea.getInstancia().getId())
                : null;
        if (desdeTarea != null) {
            return desdeTarea;
        }
        if (resumenTareaActual != null && normalizar(resumenTareaActual.getInstanciaId()) != null) {
            return resumenTareaActual.getInstanciaId();
        }
        return normalizar(instanciaSolicitadaId);
    }

    private String resolverPoliticaId(
            TareaDetalleResponse detalleTarea,
            TareaMiaResponse resumenTareaActual,
            SeguimientoInstanciaResponse seguimiento) {
        if (detalleTarea != null && detalleTarea.getPolitica() != null
                && normalizar(detalleTarea.getPolitica().getId()) != null) {
            return detalleTarea.getPolitica().getId();
        }
        if (resumenTareaActual != null && normalizar(resumenTareaActual.getPoliticaId()) != null) {
            return resumenTareaActual.getPoliticaId();
        }
        if (seguimiento != null && normalizar(seguimiento.getPoliticaId()) != null) {
            return seguimiento.getPoliticaId();
        }
        return null;
    }

    private String resolverNombrePolitica(
            TareaDetalleResponse detalleTarea,
            TareaMiaResponse resumenTareaActual,
            SeguimientoInstanciaResponse seguimiento,
            PoliticaNegocio politica) {
        if (detalleTarea != null && detalleTarea.getPolitica() != null
                && normalizar(detalleTarea.getPolitica().getNombre()) != null) {
            return detalleTarea.getPolitica().getNombre();
        }
        if (resumenTareaActual != null && normalizar(resumenTareaActual.getPoliticaNombre()) != null) {
            return resumenTareaActual.getPoliticaNombre();
        }
        if (seguimiento != null && normalizar(seguimiento.getPoliticaNombre()) != null) {
            return seguimiento.getPoliticaNombre();
        }
        return politica != null ? normalizar(politica.getNombre()) : null;
    }

    private NodoActualGuiaFuncionario construirNodoActual(
            TareaDetalleResponse detalleTarea,
            SeguimientoInstanciaResponse seguimiento,
            PoliticaNegocio politica) {
        Nodo nodo = buscarNodoActualPolitica(detalleTarea, seguimiento, politica);
        if (nodo != null) {
            return NodoActualGuiaFuncionario.builder()
                    .id(nodo.getId())
                    .tipo(mapearTipoNodo(nodo.getTipo()))
                    .nombre(normalizar(nodo.getNombre()))
                    .descripcion(construirDescripcionNodo(nodo))
                    .departamento(resolverDepartamentoNodo(nodo))
                    .tiempoEstimado(null)
                    .build();
        }

        if (detalleTarea != null && detalleTarea.getActividad() != null) {
            return NodoActualGuiaFuncionario.builder()
                    .id(normalizar(detalleTarea.getActividad().getNodoId()))
                    .tipo("ACTIVITY")
                    .nombre(normalizar(detalleTarea.getActividad().getNombreActividad()))
                    .descripcion("Debes ejecutar esta actividad y registrar el resultado correctamente.")
                    .departamento(resolverDepartamentoResponsable(
                            detalleTarea.getActividad().getResponsableTipo(),
                            detalleTarea.getActividad().getResponsableId()))
                    .tiempoEstimado(null)
                    .build();
        }

        return null;
    }

    private Nodo buscarNodoActualPolitica(
            TareaDetalleResponse detalleTarea,
            SeguimientoInstanciaResponse seguimiento,
            PoliticaNegocio politica) {
        if (politica == null || politica.getNodos() == null || politica.getNodos().isEmpty()) {
            return null;
        }
        Map<String, Nodo> nodosPorId = politica.getNodos().stream()
                .filter(Objects::nonNull)
                .filter(nodo -> normalizar(nodo.getId()) != null)
                .collect(Collectors.toMap(Nodo::getId, Function.identity(), (izq, der) -> izq));

        String nodoId = detalleTarea != null && detalleTarea.getActividad() != null
                ? normalizar(detalleTarea.getActividad().getNodoId())
                : null;
        if (nodoId == null && seguimiento != null && seguimiento.getNodosActualesIds() != null
                && !seguimiento.getNodosActualesIds().isEmpty()) {
            nodoId = normalizar(seguimiento.getNodosActualesIds().get(0));
        }
        return nodoId != null ? nodosPorId.get(nodoId) : null;
    }

    private FormularioGuiaFuncionario construirFormulario(TareaDetalleResponse detalleTarea) {
        if (detalleTarea == null || detalleTarea.getActividad() == null) {
            return null;
        }

        List<CampoFormulario> definicion = detalleTarea.getActividad().getFormularioDefinicion() != null
                ? detalleTarea.getActividad().getFormularioDefinicion()
                : List.of();
        if (definicion.isEmpty()) {
            return null;
        }

        Map<String, Object> respuestas = detalleTarea.getFormularioRespuesta() != null
                ? detalleTarea.getFormularioRespuesta()
                : Map.of();

        List<CampoFormularioGuiaFuncionario> campos = new ArrayList<>();
        List<String> camposObligatoriosFaltantes = new ArrayList<>();
        for (CampoFormulario campo : definicion) {
            if (campo == null || normalizar(campo.getCampo()) == null) {
                continue;
            }
            Object valor = resolverValorCampo(respuestas, campo.getCampo());
            campos.add(CampoFormularioGuiaFuncionario.builder()
                    .nombre(normalizar(campo.getCampo()))
                    .etiqueta(normalizar(campo.getCampo()))
                    .tipo(mapearTipoCampo(campo.getTipo()))
                    .obligatorio(true)
                    .valor(valor)
                    .build());
            if (esValorVacio(valor)) {
                camposObligatoriosFaltantes.add(normalizar(campo.getCampo()));
            }
        }

        return FormularioGuiaFuncionario.builder()
                .campos(campos)
                .camposObligatoriosFaltantes(camposObligatoriosFaltantes)
                .build();
    }

    private Object resolverValorCampo(Map<String, Object> respuestas, String nombreCampo) {
        if (respuestas == null || respuestas.isEmpty() || normalizar(nombreCampo) == null) {
            return null;
        }
        if (respuestas.containsKey(nombreCampo)) {
            return respuestas.get(nombreCampo);
        }
        return respuestas.entrySet().stream()
                .filter(entry -> normalizar(entry.getKey()) != null)
                .filter(entry -> normalizar(entry.getKey()).equalsIgnoreCase(normalizar(nombreCampo)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResumenHistorialGuiaFuncionario construirResumenHistorial(
            TareaDetalleResponse detalleTarea,
            SeguimientoInstanciaResponse seguimiento) {
        if (detalleTarea == null && seguimiento == null) {
            return null;
        }

        List<SeguimientoInstanciaResponse.TareaSeguimientoResponse> tareas = seguimiento != null
                && seguimiento.getTareas() != null
                        ? seguimiento.getTareas()
                        : List.of();

        int pasosCompletados = (int) tareas.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getEstadoTarea() == EstadoTarea.COMPLETADA)
                .count();
        int pasosPendientes = (int) tareas.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getEstadoTarea() == EstadoTarea.PENDIENTE
                        || item.getEstadoTarea() == EstadoTarea.EN_PROCESO)
                .count();
        String pasoActual = detalleTarea != null && detalleTarea.getActividad() != null
                ? normalizar(detalleTarea.getActividad().getNombreActividad())
                : resolverPasoActualDesdeSeguimiento(seguimiento);

        String ultimoCompletadoPor = tareas.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getEstadoTarea() == EstadoTarea.COMPLETADA)
                .max(Comparator.comparing(
                        item -> item.getFechaFin() != null ? item.getFechaFin() : item.getFechaInicio(),
                        Comparator.nullsLast(LocalDateTime::compareTo)))
                .map(item -> normalizar(item.getAsignadoANombre()) != null ? item.getAsignadoANombre()
                        : item.getResponsableNombre())
                .orElse(null);

        return ResumenHistorialGuiaFuncionario.builder()
                .pasosCompletados(pasosCompletados)
                .pasoActual(pasoActual)
                .pasosPendientes(pasosPendientes)
                .ultimoCompletadoPor(ultimoCompletadoPor)
                .build();
    }

    private String resolverPasoActualDesdeSeguimiento(SeguimientoInstanciaResponse seguimiento) {
        if (seguimiento == null || seguimiento.getDepartamentosActuales() == null
                || seguimiento.getDepartamentosActuales().isEmpty()) {
            return null;
        }
        return seguimiento.getDepartamentosActuales().stream()
                .filter(Objects::nonNull)
                .map(SeguimientoInstanciaResponse.DepartamentoActualResponse::getNodoNombre)
                .filter(valor -> normalizar(valor) != null)
                .findFirst()
                .orElse(null);
    }

    private List<PasoPosibleGuiaFuncionario> construirPasosPosibles(
            TareaDetalleResponse detalleTarea,
            SeguimientoInstanciaResponse seguimiento,
            PoliticaNegocio politica) {
        Nodo nodoActual = buscarNodoActualPolitica(detalleTarea, seguimiento, politica);
        if (nodoActual == null || politica == null) {
            return List.of();
        }

        Map<String, Nodo> nodosPorId = (politica.getNodos() != null ? politica.getNodos() : List.<Nodo>of()).stream()
                .filter(Objects::nonNull)
                .filter(nodo -> normalizar(nodo.getId()) != null)
                .collect(Collectors.toMap(Nodo::getId, Function.identity(), (izq, der) -> izq));

        List<PasoPosibleGuiaFuncionario> pasos = new ArrayList<>();
        if (nodoActual.getTipo() == TipoNodo.DECISION) {
            agregarPasosDecision(pasos, nodoActual.getCondiciones(), nodosPorId);
        } else {
            List<Nodo> nodosSiguientesDirectos = (politica.getConexiones() != null ? politica.getConexiones()
                    : List.<Conexion>of()).stream()
                    .filter(Objects::nonNull)
                    .filter(conexion -> Objects.equals(conexion.getOrigen(), nodoActual.getId()))
                    .map(Conexion::getDestino)
                    .map(nodosPorId::get)
                    .filter(Objects::nonNull)
                    .toList();

            for (Nodo siguienteNodo : nodosSiguientesDirectos) {
                if (siguienteNodo.getTipo() == TipoNodo.DECISION
                        && siguienteNodo.getCondiciones() != null
                        && !siguienteNodo.getCondiciones().isEmpty()) {
                    agregarPasosDecision(pasos, siguienteNodo.getCondiciones(), nodosPorId);
                    continue;
                }
                pasos.add(PasoPosibleGuiaFuncionario.builder()
                        .condicion("Al completar la tarea actual")
                        .siguienteNodo(normalizar(siguienteNodo.getNombre()))
                        .siguienteDepartamento(resolverDepartamentoNodo(siguienteNodo))
                        .build());
            }
        }

        LinkedHashSet<String> llavesUnicas = new LinkedHashSet<>();
        List<PasoPosibleGuiaFuncionario> pasosUnicos = new ArrayList<>();
        for (PasoPosibleGuiaFuncionario paso : pasos) {
            String llave = String.join("|",
                    String.valueOf(normalizar(paso.getCondicion())),
                    String.valueOf(normalizar(paso.getSiguienteNodo())),
                    String.valueOf(normalizar(paso.getSiguienteDepartamento())));
            if (!llavesUnicas.add(llave)) {
                continue;
            }
            pasosUnicos.add(paso);
            if (pasosUnicos.size() >= 4) {
                break;
            }
        }
        return pasosUnicos;
    }

    private void agregarPasosDecision(
            List<PasoPosibleGuiaFuncionario> pasos,
            List<CondicionDecision> condiciones,
            Map<String, Nodo> nodosPorId) {
        if (condiciones == null || condiciones.isEmpty()) {
            return;
        }

        for (CondicionDecision condicion : condiciones) {
            if (condicion == null) {
                continue;
            }
            Nodo siguienteNodo = nodosPorId.get(condicion.getSiguiente());
            pasos.add(PasoPosibleGuiaFuncionario.builder()
                    .condicion(construirEtiquetaCondicion(condicion.getResultado()))
                    .siguienteNodo(siguienteNodo != null ? normalizar(siguienteNodo.getNombre())
                            : normalizar(condicion.getSiguiente()))
                    .siguienteDepartamento(siguienteNodo != null ? resolverDepartamentoNodo(siguienteNodo) : null)
                    .build());
        }
    }

    private String construirEtiquetaCondicion(String resultado) {
        String resultadoNormalizado = normalizar(resultado);
        if (resultadoNormalizado == null) {
            return "Segun el resultado registrado";
        }
        if ("SI".equalsIgnoreCase(resultadoNormalizado) || "NO".equalsIgnoreCase(resultadoNormalizado)) {
            return "Si marcas " + resultadoNormalizado;
        }
        return "Si el resultado es " + resultadoNormalizado;
    }

    private ResumenDashboardGuiaFuncionario construirResumenDashboard(List<TareaMiaResponse> colaTareas) {
        if (colaTareas == null || colaTareas.isEmpty()) {
            return ResumenDashboardGuiaFuncionario.builder().build();
        }

        int tareasPendientes = 0;
        int tareasEnProceso = 0;
        int tareasCompletadas = 0;
        int tareasAtrasadas = 0;
        for (TareaMiaResponse tarea : colaTareas) {
            String estadoBruto = tarea.getEstadoTarea() != null ? tarea.getEstadoTarea().name() : null;
            if ("PENDIENTE".equals(normalizarCodigo(estadoBruto))) {
                tareasPendientes++;
            } else if ("EN_PROCESO".equals(normalizarCodigo(estadoBruto))) {
                tareasEnProceso++;
            } else if ("COMPLETADA".equals(normalizarCodigo(estadoBruto))) {
                tareasCompletadas++;
            }
            if (estaAtrasada(estadoBruto, tarea.getFechaCreacion())) {
                tareasAtrasadas++;
            }
        }

        return ResumenDashboardGuiaFuncionario.builder()
                .tareasPendientes(tareasPendientes)
                .tareasEnProceso(tareasEnProceso)
                .tareasCompletadas(tareasCompletadas)
                .tareasAtrasadas(tareasAtrasadas)
                .build();
    }

    private List<ItemColaTareaGuiaFuncionario> construirColaTareas(List<TareaMiaResponse> colaTareas) {
        if (colaTareas == null || colaTareas.isEmpty()) {
            return List.of();
        }

        return colaTareas.stream()
                .filter(Objects::nonNull)
                .map(tarea -> {
                    ItemColaTareaGuiaFuncionario itemCola = ItemColaTareaGuiaFuncionario.builder()
                            .idTarea(tarea.getId())
                            .nombreTarea(normalizar(tarea.getNombreActividad()))
                            .estadoTarea(resolverEstadoTarea(null, tarea))
                            .prioridad(mapearPrioridad(tarea.getPrioridad()))
                            .horasAntiguedad(calcularHorasAntiguedad(tarea.getFechaCreacion()))
                            .atrasada(estaAtrasada(
                                    tarea.getEstadoTarea() != null ? tarea.getEstadoTarea().name() : null,
                                    tarea.getFechaCreacion()))
                            .nombrePolitica(normalizar(tarea.getPoliticaNombre()))
                            .build();
                    return itemCola;
                })
                .toList();
    }

    private List<String> construirAccionesDisponibles(
            SolicitudGuiaFuncionario solicitud,
            TareaDetalleResponse detalleTarea,
            TareaMiaResponse resumenTareaActual,
            FormularioGuiaFuncionario formulario) {
        LinkedHashSet<String> acciones = new LinkedHashSet<>();
        if (solicitud != null && solicitud.getContexto() != null
                && solicitud.getContexto().getAccionesDisponibles() != null) {
            solicitud.getContexto().getAccionesDisponibles().stream()
                    .map(this::normalizarCodigo)
                    .filter(valor -> !valor.isBlank())
                    .forEach(acciones::add);
        }

        String pantalla = normalizarPantalla(
                solicitud != null ? solicitud.getPantalla() : null,
                solicitud != null && solicitud.getContexto() != null ? solicitud.getContexto().getTareaId() : null,
                solicitud != null && solicitud.getContexto() != null ? solicitud.getContexto().getInstanciaId() : null);
        String estadoTarea = resolverEstadoTarea(detalleTarea, resumenTareaActual);

        acciones.add("ASK_HELP");
        if ("EMPLOYEE_DASHBOARD".equals(pantalla)) {
            if (resumenTareaActual != null || (solicitud != null && solicitud.getContexto() != null
                    && normalizar(solicitud.getContexto().getTareaId()) != null)) {
                acciones.add("START_TASK");
            }
            return acciones.stream().limit(6).toList();
        }

        if ("PENDING".equals(estadoTarea)) {
            acciones.add("START_TASK");
        }
        if (formulario != null && formulario.getCampos() != null && !formulario.getCampos().isEmpty()) {
            acciones.add("SAVE_FORM");
            acciones.add("COMPLETE_TASK");
            acciones.add("FILL_FORM_WITH_AI");
        } else if ("IN_PROGRESS".equals(estadoTarea) || "PENDING".equals(estadoTarea)
                || "OVERDUE".equals(estadoTarea)) {
            acciones.add("COMPLETE_TASK");
        }

        return acciones.stream().limit(6).toList();
    }

    private String resolverEstadoTarea(TareaDetalleResponse detalleTarea, TareaMiaResponse resumenTareaActual) {
        String estado = detalleTarea != null && detalleTarea.getEstadoTarea() != null
                ? detalleTarea.getEstadoTarea().name()
                : resumenTareaActual != null && resumenTareaActual.getEstadoTarea() != null
                        ? resumenTareaActual.getEstadoTarea().name()
                        : null;

        LocalDateTime fechaCreacion = detalleTarea != null
                ? detalleTarea.getFechaCreacion()
                : resumenTareaActual != null ? resumenTareaActual.getFechaCreacion() : null;

        if (estaAtrasada(estado, fechaCreacion)) {
            return "OVERDUE";
        }

        String estadoNormalizado = normalizarCodigo(estado);
        return switch (estadoNormalizado) {
            case "PENDIENTE", "ABIERTA", "ASIGNADA" -> "PENDING";
            case "EN_PROCESO", "TOMADA" -> "IN_PROGRESS";
            case "COMPLETADA", "FINALIZADA" -> "COMPLETED";
            default -> "PENDING";
        };
    }

    private String resolverPrioridad(TareaMiaResponse resumenTareaActual, TareaDetalleResponse detalleTarea) {
        if (resumenTareaActual != null) {
            return mapearPrioridad(resumenTareaActual.getPrioridad());
        }
        if (detalleTarea != null && estaAtrasada(
                detalleTarea.getEstadoTarea() != null ? detalleTarea.getEstadoTarea().name() : null,
                detalleTarea.getFechaCreacion())) {
            return "HIGH";
        }
        return "LOW";
    }

    private boolean estaAtrasada(String estadoBruto, LocalDateTime fechaCreacion) {
        String estado = normalizarCodigo(estadoBruto);
        if (!"PENDIENTE".equals(estado)
                && !"EN_PROCESO".equals(estado)
                && !"PENDING".equals(estado)
                && !"IN_PROGRESS".equals(estado)) {
            return false;
        }
        Integer horasAntiguedad = calcularHorasAntiguedad(fechaCreacion);
        return horasAntiguedad != null && horasAntiguedad >= 48;
    }

    private Integer calcularHorasAntiguedad(LocalDateTime fechaCreacion) {
        if (fechaCreacion == null) {
            return null;
        }
        return (int) Duration.between(fechaCreacion, LocalDateTime.now()).toHours();
    }

    private String mapearPrioridad(String prioridad) {
        String prioridadNormalizada = normalizarCodigo(prioridad);
        return switch (prioridadNormalizada) {
            case "ALTA", "HIGH" -> "HIGH";
            case "MEDIA", "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private String resolverDepartamentoNodo(Nodo nodo) {
        if (nodo == null) {
            return null;
        }
        if (normalizar(nodo.getDepartamentoId()) != null) {
            return resolverNombreDepartamento(nodo.getDepartamentoId());
        }
        return resolverDepartamentoResponsable(nodo.getResponsableTipo(), nodo.getResponsableId());
    }

    private String resolverDepartamentoResponsable(String tipoResponsable, String responsableId) {
        if ("DEPARTAMENTO".equals(normalizarCodigo(tipoResponsable))) {
            return resolverNombreDepartamento(responsableId);
        }
        return null;
    }

    private String resolverNombreDepartamento(String departamentoId) {
        String departamentoIdNormalizado = normalizar(departamentoId);
        if (departamentoIdNormalizado == null) {
            return null;
        }
        return departamentoRepository.findById(departamentoIdNormalizado)
                .map(departamento -> normalizar(departamento.getNombre()))
                .orElse(departamentoIdNormalizado);
    }

    private String construirDescripcionNodo(Nodo nodo) {
        if (nodo.getTipo() == TipoNodo.DECISION) {
            return "Debes registrar el resultado correcto para que el flujo tome el camino correspondiente.";
        }
        if (nodo.getFormulario() != null && !nodo.getFormulario().isEmpty()) {
            return "Debes completar el formulario de la actividad y validar la informacion antes de finalizar.";
        }
        return "Debes ejecutar esta actividad operativa y registrar el resultado correctamente.";
    }

    private String mapearTipoNodo(TipoNodo tipoNodo) {
        if (tipoNodo == null) {
            return "ACTIVITY";
        }
        return switch (tipoNodo) {
            case ACTIVIDAD -> "ACTIVITY";
            case DECISION -> "DECISION";
            case INICIO -> "START";
            case FIN -> "END";
            case FORK -> "FORK";
            case JOIN -> "JOIN";
        };
    }

    private String mapearTipoCampo(TipoCampo tipoCampo) {
        if (tipoCampo == null) {
            return "TEXT";
        }
        return switch (tipoCampo) {
            case BOOLEANO -> "BOOLEAN";
            case ARCHIVO -> "FILE";
            case FECHA -> "DATE";
            case NUMERO -> "NUMBER";
            case TEXTO -> "TEXTAREA";
            case TEXTAREA -> "TEXTAREA";
            case CHECKBOX -> "CHECKBOX";
            case SELECCION -> "SELECTION";
            case GRID -> "GRID";
            case LABEL -> "LABEL";
            case DOCUMENTO_COLABORATIVO -> "DOCUMENTO_COLABORATIVO";
        };
    }

    private boolean esValorVacio(Object valor) {
        if (valor == null) {
            return true;
        }
        if (valor instanceof String texto) {
            return normalizar(texto) == null;
        }
        if (valor instanceof Map<?, ?> mapa) {
            return mapa.isEmpty();
        }
        if (valor instanceof List<?> lista) {
            return lista.isEmpty();
        }
        return false;
    }

    private Usuario validarFuncionario(String funcionarioUserId) {
        String funcionarioIdNormalizado = normalizar(funcionarioUserId);
        if (funcionarioIdNormalizado == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-User-Id");
        }

        Usuario funcionario = usuarioRepository.findByIdAndActivo(funcionarioIdNormalizado, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
        if (!"FUNCIONARIO".equalsIgnoreCase(funcionario.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "El bot guia de funcionario solo esta disponible para el rol FUNCIONARIO");
        }
        return funcionario;
    }

    private boolean esRespuestaUtil(RespuestaGuiaFuncionario respuesta) {
        return respuesta != null && normalizar(respuesta.getRespuesta()) != null;
    }

    private String normalizarPantalla(String pantalla, String tareaId, String instanciaId) {
        String pantallaNormalizada = normalizarCodigo(pantalla);
        if (List.of("EMPLOYEE_DASHBOARD", "TASK_DETAIL", "TASK_FORM", "TASK_HISTORY").contains(pantallaNormalizada)) {
            return pantallaNormalizada;
        }
        if (normalizar(tareaId) != null) {
            return "TASK_DETAIL";
        }
        if (normalizar(instanciaId) != null) {
            return "TASK_HISTORY";
        }
        return "EMPLOYEE_DASHBOARD";
    }

    private String normalizarPregunta(String pregunta) {
        String preguntaNormalizada = normalizar(pregunta);
        if (preguntaNormalizada == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar la pregunta del bot guia");
        }
        return preguntaNormalizada;
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String valorNormalizado = valor.trim();
        return valorNormalizado.isEmpty() ? null : valorNormalizado;
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
