package com.leo.politicas_de_negocio.workflow.service;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.instancias.service.HistorialInstanciaService;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.CondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.GrupoCondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.ReglaCondicionDecision;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private static final List<EstadoTarea> ESTADOS_TAREA_ABIERTA = List.of(
            EstadoTarea.PENDIENTE,
            EstadoTarea.EN_PROCESO
    );

    private static final String RESPONSABLE_USUARIO_FINAL_ID = "__RESPONSABLE_USUARIO_FINAL__";
    private static final String RESPONSABLE_INICIADOR_TRAMITE_ID = "__RESPONSABLE_INICIADOR_TRAMITE__";

    private static final int MAX_PASOS_TRANSICION = 1000;

    private final InstanciaPoliticaRepository instanciaRepository;
    private final TareaActividadRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialInstanciaService historialService;

    public void iniciarInstancia(InstanciaPolitica instancia, PoliticaNegocio politica, String actorUserId) {
        Nodo inicio = buscarNodoInicio(politica);
        historialService.registrar(
                instancia.getId(),
                null,
                "INSTANCIA_INICIADA",
                actorUserId,
                "Workflow iniciado en nodo " + inicio.getId()
        );
        avanzarDesdeNodo(instancia, politica, inicio.getId(), actorUserId, instancia.getDatosContexto());
    }

    public void avanzarDesdeNodo(
            InstanciaPolitica instancia,
            PoliticaNegocio politica,
            String nodoOrigenId,
            String actorUserId,
            Map<String, Object> contexto
    ) {
        validarInstanciaEditable(instancia);

        Map<String, Nodo> indiceNodos = construirIndiceNodos(politica);
        if (!indiceNodos.containsKey(nodoOrigenId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo origen " + nodoOrigenId + " no existe en la politica");
        }

        ArrayDeque<PasoPendiente> cola = new ArrayDeque<>();
        encolarDestinosDesde(politica, nodoOrigenId, cola);

        int pasos = 0;
        while (!cola.isEmpty()) {
            if (++pasos > MAX_PASOS_TRANSICION) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Se detecto un flujo ciclico sin salida en la politica");
            }

            PasoPendiente paso = cola.poll();
            Nodo nodo = indiceNodos.get(paso.nodoId());
            if (nodo == null) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "El nodo destino " + paso.nodoId() + " no existe en la politica");
            }

            procesarNodo(instancia, politica, nodo, paso.origenId(), actorUserId, contexto, cola);
        }

        controlarJoinesPendientesSinTrabajo(instancia, actorUserId);

        instancia.setFechaActualizacion(LocalDateTime.now());
        instanciaRepository.save(instancia);
    }

    private void procesarNodo(
            InstanciaPolitica instancia,
            PoliticaNegocio politica,
            Nodo nodo,
            String nodoOrigenId,
            String actorUserId,
            Map<String, Object> contexto,
            ArrayDeque<PasoPendiente> cola
    ) {
        if (nodo.getTipo() == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo " + nodo.getId() + " no tiene tipo definido");
        }

        switch (nodo.getTipo()) {
            case INICIO -> encolarDestinosDesde(politica, nodo.getId(), cola);
            case ACTIVIDAD -> crearTareaActividad(instancia, politica, nodo, actorUserId);
            case DECISION -> procesarDecision(politica, nodo, contexto, cola, actorUserId, instancia.getId());
            case FORK -> encolarDestinosDesde(politica, nodo.getId(), cola);
            case JOIN -> procesarJoin(instancia, politica, nodo, nodoOrigenId, cola, actorUserId);
            case FIN -> finalizarInstanciaSiCorresponde(instancia, actorUserId, nodo.getId());
        }
    }

    private void procesarDecision(
            PoliticaNegocio politica,
            Nodo nodo,
            Map<String, Object> contexto,
            ArrayDeque<PasoPendiente> cola,
            String actorUserId,
            String instanciaId
    ) {
        List<String> salidas = destinosDesde(politica, nodo.getId());
        String destino = resolverDestinoDecision(nodo, salidas, contexto);
        cola.add(new PasoPendiente(destino, nodo.getId()));

        historialService.registrar(
                instanciaId,
                null,
                "DECISION_EVALUADA",
                actorUserId,
                "Nodo " + nodo.getId() + " decide hacia " + destino
        );
    }

    private void procesarJoin(
            InstanciaPolitica instancia,
            PoliticaNegocio politica,
            Nodo nodoJoin,
            String nodoOrigenId,
            ArrayDeque<PasoPendiente> cola,
            String actorUserId
    ) {
        int entradasEsperadas = origenesHacia(politica, nodoJoin.getId()).size();
        boolean joinListo = registrarLlegadaJoin(instancia, nodoJoin.getId(), nodoOrigenId, entradasEsperadas);

        if (!joinListo) {
            historialService.registrar(
                    instancia.getId(),
                    null,
                    "JOIN_EN_ESPERA",
                    actorUserId,
                    "Nodo JOIN " + nodoJoin.getId() + " espera ramas pendientes"
            );
            return;
        }

        consumirJoin(instancia, nodoJoin.getId());
        historialService.registrar(
                instancia.getId(),
                null,
                "JOIN_RESUELTO",
                actorUserId,
                "Nodo JOIN " + nodoJoin.getId() + " habilita siguiente transicion"
        );
        encolarDestinosDesde(politica, nodoJoin.getId(), cola);
    }

    private void crearTareaActividad(
            InstanciaPolitica instancia,
            PoliticaNegocio politica,
            Nodo nodo,
            String actorUserId
    ) {
        String responsableTipo = normalizarTexto(nodo.getResponsableTipo());
        String responsableIdConfigurado = normalizarTexto(nodo.getResponsableId());

        if (responsableTipo == null || responsableIdConfigurado == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo ACTIVIDAD " + nodo.getId() + " debe tener responsableTipo y responsableId");
        }

        String responsableTipoNormalizado = responsableTipo.toUpperCase();
        if (!"USUARIO".equals(responsableTipoNormalizado) && !"DEPARTAMENTO".equals(responsableTipoNormalizado)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "responsableTipo invalido en nodo " + nodo.getId() + ": " + responsableTipo);
        }

        String responsableId = responsableIdConfigurado;
        if ("USUARIO".equals(responsableTipoNormalizado)) {
            responsableId = resolverResponsableUsuarioId(instancia, responsableIdConfigurado);
        }

        List<TareaActividad> tareasAbiertas = tareaRepository.findByInstanciaIdAndNodoIdAndEstadoTareaIn(
                instancia.getId(),
                nodo.getId(),
                ESTADOS_TAREA_ABIERTA
        );
        if (!tareasAbiertas.isEmpty()) {
            return;
        }

        TareaActividad nueva = TareaActividad.builder()
                .instanciaId(instancia.getId())
                .politicaId(politica.getId())
                .nodoId(nodo.getId())
                .nombreNodo(nodo.getNombre())
                .responsableTipo(responsableTipoNormalizado)
                .responsableId(responsableId)
                .estadoTarea(EstadoTarea.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .formularioDefinicion(clonarFormulario(nodo.getFormulario()))
                .build();

        TareaActividad guardada = tareaRepository.save(nueva);
        historialService.registrar(
                instancia.getId(),
                guardada.getId(),
                "TAREA_CREADA",
                actorUserId,
                "Se creo tarea para nodo " + nodo.getId()
        );
    }

    private String resolverResponsableUsuarioId(
            InstanciaPolitica instancia,
            String responsableIdConfigurado
    ) {
        String normalized = normalizarTexto(responsableIdConfigurado);
        if (normalized == null) {
            return null;
        }

        if (RESPONSABLE_INICIADOR_TRAMITE_ID.equalsIgnoreCase(normalized)) {
            String iniciadorId = normalizarTexto(instancia.getCreadaPor());
            if (iniciadorId == null) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "No se pudo resolver el usuario que inicio el tramite"
                );
            }
            return iniciadorId;
        }

        if (RESPONSABLE_USUARIO_FINAL_ID.equalsIgnoreCase(normalized)) {
            String usuarioFinalId = extraerUsuarioFinalDesdeContexto(instancia.getDatosContexto());
            if (usuarioFinalId != null) {
                return usuarioFinalId;
            }

            String iniciadorId = normalizarTexto(instancia.getCreadaPor());
            if (iniciadorId != null) {
                return iniciadorId;
            }

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "No se pudo resolver el usuario final del tramite"
            );
        }

        return normalized;
    }

    private String extraerUsuarioFinalDesdeContexto(Map<String, Object> contexto) {
        if (contexto == null || contexto.isEmpty()) {
            return null;
        }

        List<String> candidateKeys = List.of(
                "usuarioFinalId",
                "usuario_final_id",
                "solicitanteId",
                "solicitante_id",
                "usuarioId",
                "usuario_id",
                "actorUserId",
                "actor_user_id",
                "creadaPor",
                "creadoPor"
        );

        for (String key : candidateKeys) {
            Object value = obtenerValorMapaCaseInsensitive(contexto, key);
            String resolved = normalizarTexto(valorComoTexto(value));
            if (resolved != null && usuarioRepository.existsById(resolved)) {
                return resolved;
            }
        }

        return null;
    }

    private void finalizarInstanciaSiCorresponde(InstanciaPolitica instancia, String actorUserId, String nodoFinId) {
        long tareasAbiertas = tareaRepository.countByInstanciaIdAndEstadoTareaIn(
                instancia.getId(),
                ESTADOS_TAREA_ABIERTA
        );

        if (tareasAbiertas > 0) {
            historialService.registrar(
                    instancia.getId(),
                    null,
                    "FIN_DIFERIDO",
                    actorUserId,
                    "Nodo FIN " + nodoFinId + " alcanzado, pero hay tareas abiertas"
            );
            return;
        }

        if (instancia.getEstadoInstancia() == EstadoInstancia.FINALIZADA) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        instancia.setEstadoInstancia(EstadoInstancia.FINALIZADA);
        instancia.setFechaActualizacion(now);
        instancia.setFechaFinalizacion(now);
        instancia.setFinalizadaPor(actorUserId);
        if (instancia.getTokensJoin() != null) {
            instancia.getTokensJoin().clear();
        }
        historialService.registrar(
                instancia.getId(),
                null,
                "INSTANCIA_FINALIZADA",
                actorUserId,
                "La instancia finalizo correctamente en nodo " + nodoFinId
        );
    }

    private boolean registrarLlegadaJoin(
            InstanciaPolitica instancia,
            String joinId,
            String origenId,
            int entradasEsperadas
    ) {
        if (entradasEsperadas <= 1) {
            return true;
        }

        Map<String, List<String>> tokensJoin = instancia.getTokensJoin();
        if (tokensJoin == null) {
            tokensJoin = new HashMap<>();
            instancia.setTokensJoin(tokensJoin);
        }

        List<String> ramasLlegadas = tokensJoin.computeIfAbsent(joinId, key -> new ArrayList<>());
        String origenNormalizado = normalizarTexto(origenId);

        if (origenNormalizado != null && !ramasLlegadas.contains(origenNormalizado)) {
            ramasLlegadas.add(origenNormalizado);
        }

        return ramasLlegadas.size() >= entradasEsperadas;
    }

    private void consumirJoin(InstanciaPolitica instancia, String joinId) {
        if (instancia.getTokensJoin() == null) {
            return;
        }
        instancia.getTokensJoin().remove(joinId);
    }

    private Nodo buscarNodoInicio(PoliticaNegocio politica) {
        if (politica.getNodos() == null || politica.getNodos().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La politica no tiene nodos para ejecutar workflow");
        }

        return politica.getNodos().stream()
                .filter(n -> n.getTipo() == TipoNodo.INICIO)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "La politica no tiene nodo INICIO"));
    }

    private Map<String, Nodo> construirIndiceNodos(PoliticaNegocio politica) {
        if (politica.getNodos() == null) {
            return Collections.emptyMap();
        }

        Map<String, Nodo> indice = new LinkedHashMap<>();
        for (Nodo nodo : politica.getNodos()) {
            String id = normalizarTexto(nodo.getId());
            if (id == null) {
                continue;
            }
            indice.put(id, nodo);
        }
        return indice;
    }

    private void encolarDestinosDesde(
            PoliticaNegocio politica,
            String nodoOrigenId,
            ArrayDeque<PasoPendiente> cola
    ) {
        List<String> destinos = destinosDesde(politica, nodoOrigenId);
        if (destinos.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo " + nodoOrigenId + " no tiene transiciones salientes");
        }

        for (String destino : destinos) {
            cola.add(new PasoPendiente(destino, nodoOrigenId));
        }
    }

    private List<String> destinosDesde(PoliticaNegocio politica, String nodoOrigenId) {
        if (politica.getConexiones() == null) {
            return List.of();
        }

        Set<String> destinos = new HashSet<>();
        for (Conexion conexion : politica.getConexiones()) {
            String origen = normalizarTexto(conexion.getOrigen());
            if (!nodoOrigenId.equals(origen)) {
                continue;
            }
            String destino = normalizarTexto(conexion.getDestino());
            if (destino != null) {
                destinos.add(destino);
            }
        }

        List<String> resultado = new ArrayList<>(destinos);
        Collections.sort(resultado);
        return resultado;
    }

    private List<String> origenesHacia(PoliticaNegocio politica, String nodoDestinoId) {
        if (politica.getConexiones() == null) {
            return List.of();
        }

        Set<String> origenes = new HashSet<>();
        for (Conexion conexion : politica.getConexiones()) {
            String destino = normalizarTexto(conexion.getDestino());
            if (!nodoDestinoId.equals(destino)) {
                continue;
            }
            String origen = normalizarTexto(conexion.getOrigen());
            if (origen != null) {
                origenes.add(origen);
            }
        }

        List<String> resultado = new ArrayList<>(origenes);
        Collections.sort(resultado);
        return resultado;
    }

    private String resolverDestinoDecision(Nodo nodoDecision, List<String> salidas, Map<String, Object> contexto) {
        if (salidas.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo DECISION " + nodoDecision.getId() + " no tiene conexiones salientes");
        }

        List<CondicionDecision> condiciones = nodoDecision.getCondiciones() != null
                ? nodoDecision.getCondiciones()
                : List.of();

        if (condiciones.isEmpty()) {
            if (salidas.size() == 1) {
                return salidas.get(0);
            }
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo DECISION " + nodoDecision.getId() + " requiere condiciones configuradas");
        }

        CondicionDecision condicion = buscarCondicionPorGrupo(condiciones, contexto);

        String resultado = extraerResultadoDecision(contexto);
        if (condicion == null) {
            condicion = buscarCondicion(condiciones, resultado);
        }
        if (condicion == null) {
            condicion = buscarCondicionDefault(condiciones);
        }

        if (condicion == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                "No existe salida para el resultado de decision '"
                    + (resultado != null ? resultado : "null")
                    + "' en nodo " + nodoDecision.getId());
        }

        String destino = normalizarTexto(condicion.getSiguiente());
        if (destino == null && salidas.size() == 1) {
            destino = salidas.get(0);
        }

        if (destino == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La condicion de decision no define nodo siguiente");
        }

        if (!salidas.contains(destino)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La condicion apunta al nodo " + destino + " pero no existe conexion desde " + nodoDecision.getId());
        }

        return destino;
    }

    private CondicionDecision buscarCondicionPorGrupo(
            List<CondicionDecision> condiciones,
            Map<String, Object> contexto
    ) {
        for (CondicionDecision condicion : condiciones) {
            if (condicion == null || condicion.getGrupo() == null) {
                continue;
            }

            if (evaluarGrupoCondicion(condicion.getGrupo(), contexto)) {
                return condicion;
            }
        }

        return null;
    }

    private boolean evaluarGrupoCondicion(
            GrupoCondicionDecision grupo,
            Map<String, Object> contexto
    ) {
        if (grupo == null) {
            return false;
        }

        boolean operadorOr = "OR".equalsIgnoreCase(normalizarTexto(grupo.getOperadorLogico()));
        List<Boolean> resultados = new ArrayList<>();

        if (grupo.getReglas() != null) {
            for (ReglaCondicionDecision regla : grupo.getReglas()) {
                resultados.add(evaluarReglaCondicion(regla, contexto));
            }
        }

        if (grupo.getGrupos() != null) {
            for (GrupoCondicionDecision subgrupo : grupo.getGrupos()) {
                resultados.add(evaluarGrupoCondicion(subgrupo, contexto));
            }
        }

        if (resultados.isEmpty()) {
            return false;
        }

        if (operadorOr) {
            for (Boolean resultado : resultados) {
                if (Boolean.TRUE.equals(resultado)) {
                    return true;
                }
            }
            return false;
        }

        for (Boolean resultado : resultados) {
            if (!Boolean.TRUE.equals(resultado)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluarReglaCondicion(
            ReglaCondicionDecision regla,
            Map<String, Object> contexto
    ) {
        if (regla == null) {
            return false;
        }

        String operador = normalizarTexto(regla.getOperador());
        if (operador == null) {
            return false;
        }

        String tipo = normalizarTexto(regla.getTipo());
        Object valorContexto = extraerValorCampoContexto(contexto, regla.getCampo());
        Object valorRegla = regla.getValor();

        return switch (operador.toUpperCase(Locale.ROOT)) {
            case "ESTA_VACIO" -> esValorVacio(valorContexto);
            case "NO_ESTA_VACIO" -> !esValorVacio(valorContexto);
            case "ES_VERDADERO" -> Boolean.TRUE.equals(valorComoBooleano(valorContexto));
            case "ES_FALSO" -> Boolean.FALSE.equals(valorComoBooleano(valorContexto));
            case "IGUAL" -> compararIgual(tipo, valorContexto, valorRegla);
            case "DISTINTO" -> !compararIgual(tipo, valorContexto, valorRegla);
            case "CONTIENE" -> {
                String izquierda = normalizarTexto(valorComoTexto(valorContexto));
                String derecha = normalizarTexto(valorComoTexto(valorRegla));
                yield izquierda != null
                        && derecha != null
                        && izquierda.toLowerCase(Locale.ROOT).contains(derecha.toLowerCase(Locale.ROOT));
            }
            case "NO_CONTIENE" -> {
                String izquierda = normalizarTexto(valorComoTexto(valorContexto));
                String derecha = normalizarTexto(valorComoTexto(valorRegla));
                yield izquierda != null
                        && derecha != null
                        && !izquierda.toLowerCase(Locale.ROOT).contains(derecha.toLowerCase(Locale.ROOT));
            }
            case "INICIA_CON" -> {
                String izquierda = normalizarTexto(valorComoTexto(valorContexto));
                String derecha = normalizarTexto(valorComoTexto(valorRegla));
                yield izquierda != null
                        && derecha != null
                        && izquierda.toLowerCase(Locale.ROOT).startsWith(derecha.toLowerCase(Locale.ROOT));
            }
            case "TERMINA_CON" -> {
                String izquierda = normalizarTexto(valorComoTexto(valorContexto));
                String derecha = normalizarTexto(valorComoTexto(valorRegla));
                yield izquierda != null
                        && derecha != null
                        && izquierda.toLowerCase(Locale.ROOT).endsWith(derecha.toLowerCase(Locale.ROOT));
            }
            case "MAYOR_QUE" -> {
                Double izquierda = valorComoNumero(valorContexto);
                Double derecha = valorComoNumero(valorRegla);
                yield izquierda != null && derecha != null && izquierda > derecha;
            }
            case "MAYOR_O_IGUAL" -> {
                Double izquierda = valorComoNumero(valorContexto);
                Double derecha = valorComoNumero(valorRegla);
                yield izquierda != null && derecha != null && izquierda >= derecha;
            }
            case "MENOR_QUE" -> {
                Double izquierda = valorComoNumero(valorContexto);
                Double derecha = valorComoNumero(valorRegla);
                yield izquierda != null && derecha != null && izquierda < derecha;
            }
            case "MENOR_O_IGUAL" -> {
                Double izquierda = valorComoNumero(valorContexto);
                Double derecha = valorComoNumero(valorRegla);
                yield izquierda != null && derecha != null && izquierda <= derecha;
            }
            case "ANTES_DE" -> {
                LocalDate izquierda = valorComoFecha(valorContexto);
                LocalDate derecha = valorComoFecha(valorRegla);
                yield izquierda != null && derecha != null && izquierda.isBefore(derecha);
            }
            case "DESPUES_DE" -> {
                LocalDate izquierda = valorComoFecha(valorContexto);
                LocalDate derecha = valorComoFecha(valorRegla);
                yield izquierda != null && derecha != null && izquierda.isAfter(derecha);
            }
            case "EN_FECHA" -> {
                LocalDate izquierda = valorComoFecha(valorContexto);
                LocalDate derecha = valorComoFecha(valorRegla);
                yield izquierda != null && derecha != null && izquierda.isEqual(derecha);
            }
            default -> false;
        };
    }

    private Object extraerValorCampoContexto(Map<String, Object> contexto, String campo) {
        if (contexto == null || contexto.isEmpty()) {
            return null;
        }

        String normalizedCampo = normalizarTexto(campo);
        if (normalizedCampo == null) {
            return null;
        }

        Object valorDirecto = obtenerValorMapaCaseInsensitive(contexto, normalizedCampo);
        if (valorDirecto != null) {
            return valorDirecto;
        }

        if (!normalizedCampo.contains(".")) {
            return null;
        }

        String[] segmentos = normalizedCampo.split("\\.");
        Object actual = contexto;

        for (String segmento : segmentos) {
            String clave = normalizarTexto(segmento);
            if (clave == null || !(actual instanceof Map<?, ?> mapaActual)) {
                return null;
            }

            actual = obtenerValorMapaCaseInsensitive(mapaActual, clave);
            if (actual == null) {
                return null;
            }
        }

        return actual;
    }

    private Object obtenerValorMapaCaseInsensitive(Map<?, ?> mapa, String claveBuscada) {
        if (mapa == null || mapa.isEmpty()) {
            return null;
        }

        String claveFlexibleBuscada = normalizarClaveFlexible(claveBuscada);

        for (Map.Entry<?, ?> entry : mapa.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }

            if (key.equalsIgnoreCase(claveBuscada)) {
                return entry.getValue();
            }

            if (claveFlexibleBuscada != null
                    && claveFlexibleBuscada.equals(normalizarClaveFlexible(key))) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String normalizarClaveFlexible(String value) {
        String normalized = normalizarTexto(value);
        if (normalized == null) {
            return null;
        }

        String withoutDiacritics = Normalizer
                .normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String collapsed = withoutDiacritics
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");

        return collapsed.isEmpty() ? null : collapsed;
    }

    private boolean compararIgual(String tipo, Object izquierdaRaw, Object derechaRaw) {
        String normalizedTipo = tipo != null ? tipo.toUpperCase(Locale.ROOT) : "";

        return switch (normalizedTipo) {
            case "NUMERO" -> {
                Double izquierda = valorComoNumero(izquierdaRaw);
                Double derecha = valorComoNumero(derechaRaw);
                yield izquierda != null && derecha != null && Double.compare(izquierda, derecha) == 0;
            }
            case "BOOLEANO" -> {
                Boolean izquierda = valorComoBooleano(izquierdaRaw);
                Boolean derecha = valorComoBooleano(derechaRaw);
                yield izquierda != null && derecha != null && izquierda.equals(derecha);
            }
            case "FECHA" -> {
                LocalDate izquierda = valorComoFecha(izquierdaRaw);
                LocalDate derecha = valorComoFecha(derechaRaw);
                yield izquierda != null && derecha != null && izquierda.isEqual(derecha);
            }
            default -> {
                String izquierda = normalizarTexto(valorComoTexto(izquierdaRaw));
                String derecha = normalizarTexto(valorComoTexto(derechaRaw));
                if (izquierda == null || derecha == null) {
                    yield izquierda == null && derecha == null;
                }
                yield izquierda.equalsIgnoreCase(derecha);
            }
        };
    }

    private boolean esValorVacio(Object valor) {
        if (valor == null) {
            return true;
        }

        if (valor instanceof String texto) {
            return texto.trim().isEmpty();
        }

        if (valor instanceof Map<?, ?> mapa) {
            return mapa.isEmpty();
        }

        if (valor instanceof List<?> lista) {
            return lista.isEmpty();
        }

        if (valor instanceof Set<?> set) {
            return set.isEmpty();
        }

        if (valor.getClass().isArray()) {
            return Array.getLength(valor) == 0;
        }

        return false;
    }

    private String valorComoTexto(Object valor) {
        if (valor == null) {
            return null;
        }

        if (valor instanceof String texto) {
            return texto.trim();
        }

        if (valor instanceof Map<?, ?> mapa) {
            Object nombre = obtenerValorMapaCaseInsensitive(mapa, "nombre");
            if (nombre != null) {
                return nombre.toString().trim();
            }

            Object filename = obtenerValorMapaCaseInsensitive(mapa, "fileName");
            if (filename != null) {
                return filename.toString().trim();
            }
        }

        return valor.toString().trim();
    }

    private Double valorComoNumero(Object valor) {
        if (valor == null) {
            return null;
        }

        if (valor instanceof Number numero) {
            return numero.doubleValue();
        }

        String texto = normalizarTexto(valorComoTexto(valor));
        if (texto == null) {
            return null;
        }

        try {
            return Double.parseDouble(texto);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean valorComoBooleano(Object valor) {
        if (valor == null) {
            return null;
        }

        if (valor instanceof Boolean bool) {
            return bool;
        }

        if (valor instanceof Number numero) {
            return numero.doubleValue() != 0d;
        }

        String texto = normalizarTexto(valorComoTexto(valor));
        if (texto == null) {
            return null;
        }

        String upper = texto.toUpperCase(Locale.ROOT);
        if ("TRUE".equals(upper) || "SI".equals(upper) || "YES".equals(upper) || "1".equals(upper)) {
            return true;
        }
        if ("FALSE".equals(upper) || "NO".equals(upper) || "0".equals(upper)) {
            return false;
        }

        return null;
    }

    private LocalDate valorComoFecha(Object valor) {
        if (valor == null) {
            return null;
        }

        if (valor instanceof LocalDate fecha) {
            return fecha;
        }

        if (valor instanceof LocalDateTime fechaHora) {
            return fechaHora.toLocalDate();
        }

        String texto = normalizarTexto(valorComoTexto(valor));
        if (texto == null) {
            return null;
        }

        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException ignored) {
            // Try next format.
        }

        try {
            return LocalDateTime.parse(texto).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // Try YYYY-MM-DD prefix.
        }

        if (texto.length() >= 10) {
            try {
                return LocalDate.parse(texto.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        return null;
    }

    private CondicionDecision buscarCondicion(List<CondicionDecision> condiciones, String resultado) {
        if (resultado == null) {
            return null;
        }

        for (CondicionDecision condicion : condiciones) {
            if (condicion == null) {
                continue;
            }
            String valor = normalizarTexto(condicion.getResultado());
            if (valor != null && valor.equalsIgnoreCase(resultado)) {
                return condicion;
            }
        }
        return null;
    }

    private CondicionDecision buscarCondicionDefault(List<CondicionDecision> condiciones) {
        for (CondicionDecision condicion : condiciones) {
            if (condicion == null) {
                continue;
            }
            String valor = normalizarTexto(condicion.getResultado());
            if (valor == null || "*".equals(valor) || "DEFAULT".equalsIgnoreCase(valor) || "ELSE".equalsIgnoreCase(valor)) {
                return condicion;
            }
        }
        return null;
    }

    private String extraerResultadoDecision(Map<String, Object> contexto) {
        if (contexto == null || contexto.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Object> entry : contexto.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if ("resultado".equalsIgnoreCase(entry.getKey())
                    || "decision".equalsIgnoreCase(entry.getKey())
                    || "resultadoDecision".equalsIgnoreCase(entry.getKey())) {
                Object valor = entry.getValue();
                return valor != null ? valor.toString().trim() : null;
            }
        }

        return null;
    }

    private List<CampoFormulario> clonarFormulario(List<CampoFormulario> formulario) {
        if (formulario == null || formulario.isEmpty()) {
            return List.of();
        }

        List<CampoFormulario> copia = new ArrayList<>();
        for (CampoFormulario campo : formulario) {
            if (campo == null) {
                continue;
            }
            copia.add(CampoFormulario.builder()
                    .campo(campo.getCampo())
                    .tipo(campo.getTipo())
                    .build());
        }
        return copia;
    }

    private void validarInstanciaEditable(InstanciaPolitica instancia) {
        if (instancia.getEstadoInstancia() == EstadoInstancia.FINALIZADA
                || instancia.getEstadoInstancia() == EstadoInstancia.CANCELADA) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La instancia no puede avanzar porque ya esta cerrada");
        }
    }

    private void controlarJoinesPendientesSinTrabajo(InstanciaPolitica instancia, String actorUserId) {
        if (instancia.getEstadoInstancia() == EstadoInstancia.FINALIZADA) {
            return;
        }

        if (instancia.getTokensJoin() == null || instancia.getTokensJoin().isEmpty()) {
            return;
        }

        long tareasAbiertas = tareaRepository.countByInstanciaIdAndEstadoTareaIn(
                instancia.getId(),
                ESTADOS_TAREA_ABIERTA
        );
        if (tareasAbiertas > 0) {
            return;
        }

        List<String> joinsPendientes = new ArrayList<>(instancia.getTokensJoin().keySet());
        Collections.sort(joinsPendientes);

        instancia.setEstadoInstancia(EstadoInstancia.PAUSADA);
        instancia.setFechaActualizacion(LocalDateTime.now());
        instanciaRepository.save(instancia);

        historialService.registrar(
                instancia.getId(),
                null,
                "JOIN_BLOQUEADO",
                actorUserId,
                "Instancia pausada por JOIN pendiente sin tareas abiertas: "
                        + String.join(", ", joinsPendientes)
        );

        throw new ApiException(HttpStatus.CONFLICT,
                "La instancia quedo bloqueada esperando ramas JOIN sin tareas abiertas. JOIN pendientes: "
                        + String.join(", ", joinsPendientes));
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record PasoPendiente(String nodoId, String origenId) {
    }
}
