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
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private static final List<EstadoTarea> ESTADOS_TAREA_ABIERTA = List.of(
            EstadoTarea.PENDIENTE,
            EstadoTarea.EN_PROCESO
    );

    private static final int MAX_PASOS_TRANSICION = 1000;

    private final InstanciaPoliticaRepository instanciaRepository;
    private final TareaActividadRepository tareaRepository;
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
        String responsableId = normalizarTexto(nodo.getResponsableId());

        if (responsableTipo == null || responsableId == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El nodo ACTIVIDAD " + nodo.getId() + " debe tener responsableTipo y responsableId");
        }

        String responsableTipoNormalizado = responsableTipo.toUpperCase();
        if (!"USUARIO".equals(responsableTipoNormalizado) && !"DEPARTAMENTO".equals(responsableTipoNormalizado)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "responsableTipo invalido en nodo " + nodo.getId() + ": " + responsableTipo);
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

        instancia.setEstadoInstancia(EstadoInstancia.FINALIZADA);
        instancia.setFechaActualizacion(LocalDateTime.now());
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

        String resultado = extraerResultadoDecision(contexto);
        CondicionDecision condicion = buscarCondicion(condiciones, resultado);
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
