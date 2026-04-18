package com.leo.politicas_de_negocio.colaboracion.service;

import com.leo.politicas_de_negocio.colaboracion.dto.NodoBloqueoResponse;
import com.leo.politicas_de_negocio.colaboracion.dto.NodoEdicionRequest;
import com.leo.politicas_de_negocio.colaboracion.dto.PresenciaJoinRequest;
import com.leo.politicas_de_negocio.colaboracion.dto.PresenciaPoliticaResponse;
import com.leo.politicas_de_negocio.colaboracion.dto.PresenciaUsuarioResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class PoliticaPresenciaService {

    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioRepository politicaNegocioRepository;

    private final ConcurrentMap<String, ReentrantLock> locksPorPolitica = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, SesionActiva>> sesionesPorPolitica = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, EditorActivo>>> editoresPorPolitica =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, VinculoSesion> indiceSesion = new ConcurrentHashMap<>();

    public PresenciaPoliticaResponse registrarSesion(
            String politicaId,
            String sessionId,
            PresenciaJoinRequest request
    ) {
        validarPolitica(politicaId);
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo identificar la sesion WebSocket");
        }
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar actorUserId para registrar presencia");
        }

        Usuario admin = assertAdmin(request.getActorUserId());
        String nombre = nombreVisible(request.getActorNombre(), admin.getNombre());

        desvincularSesionAnteriorSiAplica(sessionId, politicaId);

        ReentrantLock lock = lockPolitica(politicaId);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            ConcurrentMap<String, SesionActiva> sesiones =
                    sesionesPorPolitica.computeIfAbsent(politicaId, key -> new ConcurrentHashMap<>());

            SesionActiva sesion = SesionActiva.builder()
                    .sessionId(sessionId)
                    .userId(admin.getId())
                    .nombre(nombre)
                    .conectadoDesde(now)
                    .ultimaActividad(now)
                    .nodosEditando(new HashSet<>())
                    .build();

            sesiones.put(sessionId, sesion);
            indiceSesion.put(sessionId, new VinculoSesion(politicaId, admin.getId()));

            return construirPresencia(politicaId, sesiones.values(), now);
        } finally {
            lock.unlock();
        }
    }

    public ResultadoDesconexion desregistrarSesion(String politicaId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return liberarSesion(politicaId, sessionId);
    }

    public ResultadoDesconexion desconectarSesion(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        VinculoSesion vinculo = indiceSesion.get(sessionId);
        if (vinculo == null) {
            return null;
        }

        return liberarSesion(vinculo.politicaId(), sessionId);
    }

    public PresenciaPoliticaResponse obtenerPresenciaActual(String adminUserId, String politicaId) {
        assertAdmin(adminUserId);
        validarPolitica(politicaId);

        ReentrantLock lock = lockPolitica(politicaId);
        lock.lock();
        try {
            Collection<SesionActiva> sesiones = sesionesPorPolitica
                    .getOrDefault(politicaId, new ConcurrentHashMap<>())
                    .values();
            return construirPresencia(politicaId, sesiones, LocalDateTime.now());
        } finally {
            lock.unlock();
        }
    }

    public NodoBloqueoResponse actualizarEdicionNodo(
            String politicaId,
            String sessionId,
            NodoEdicionRequest request
    ) {
        validarPolitica(politicaId);
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo identificar la sesion WebSocket");
        }
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos de edicion de nodo");
        }

        String userId = normalizarTexto(request.getActorUserId());
        String nodeId = normalizarTexto(request.getNodeId());
        if (userId == null || nodeId == null || request.getEditing() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "actorUserId, nodeId y editing son obligatorios");
        }

        Usuario admin = assertAdmin(userId);
        String nombre = nombreVisible(request.getActorNombre(), admin.getNombre());

        ReentrantLock lock = lockPolitica(politicaId);
        lock.lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            SesionActiva sesion = asegurarSesionActiva(politicaId, sessionId, userId, nombre, now);

            ConcurrentMap<String, ConcurrentMap<String, EditorActivo>> editoresPorNodo =
                    editoresPorPolitica.computeIfAbsent(politicaId, key -> new ConcurrentHashMap<>());
            ConcurrentMap<String, EditorActivo> editoresNodo =
                    editoresPorNodo.computeIfAbsent(nodeId, key -> new ConcurrentHashMap<>());

            if (Boolean.TRUE.equals(request.getEditing())) {
                EditorActivo existente = editoresNodo.get(userId);
                LocalDateTime desde = existente != null ? existente.desde() : now;
                editoresNodo.put(userId, new EditorActivo(userId, nombre, desde, now));
                sesion.getNodosEditando().add(nodeId);
            } else {
                editoresNodo.remove(userId);
                sesion.getNodosEditando().remove(nodeId);
            }

            limpiarEstructurasVacias(politicaId, nodeId, editoresNodo);
            sesion.setUltimaActividad(now);

            return construirBloqueoNodo(politicaId, nodeId, editoresPorNodo.get(nodeId), now);
        } finally {
            lock.unlock();
        }
    }

    public List<NodoBloqueoResponse> obtenerBloqueosActivos(String adminUserId, String politicaId) {
        assertAdmin(adminUserId);
        validarPolitica(politicaId);

        ReentrantLock lock = lockPolitica(politicaId);
        lock.lock();
        try {
            ConcurrentMap<String, ConcurrentMap<String, EditorActivo>> editoresPorNodo =
                    editoresPorPolitica.getOrDefault(politicaId, new ConcurrentHashMap<>());
            LocalDateTime now = LocalDateTime.now();

            List<NodoBloqueoResponse> bloqueos = new ArrayList<>();
            for (Map.Entry<String, ConcurrentMap<String, EditorActivo>> entry : editoresPorNodo.entrySet()) {
                bloqueos.add(construirBloqueoNodo(politicaId, entry.getKey(), entry.getValue(), now));
            }
            bloqueos.sort(Comparator.comparing(NodoBloqueoResponse::getNodeId, Comparator.nullsLast(String::compareTo)));
            return bloqueos;
        } finally {
            lock.unlock();
        }
    }

    public boolean tieneActividadActiva(String politicaId) {
        if (politicaId == null || politicaId.isBlank()) {
            return false;
        }

        ConcurrentMap<String, SesionActiva> sesiones = sesionesPorPolitica.get(politicaId);
        if (sesiones != null && !sesiones.isEmpty()) {
            return true;
        }

        ConcurrentMap<String, ConcurrentMap<String, EditorActivo>> editoresPorNodo = editoresPorPolitica.get(politicaId);
        if (editoresPorNodo == null || editoresPorNodo.isEmpty()) {
            return false;
        }

        for (ConcurrentMap<String, EditorActivo> editoresNodo : editoresPorNodo.values()) {
            if (editoresNodo != null && !editoresNodo.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private ResultadoDesconexion liberarSesion(String politicaId, String sessionId) {
        if (politicaId == null || politicaId.isBlank()) {
            return null;
        }

        ReentrantLock lock = lockPolitica(politicaId);
        lock.lock();
        try {
            ConcurrentMap<String, SesionActiva> sesiones = sesionesPorPolitica.get(politicaId);
            if (sesiones == null) {
                indiceSesion.remove(sessionId);
                return null;
            }

            SesionActiva sesion = sesiones.remove(sessionId);
            indiceSesion.remove(sessionId);
            if (sesion == null) {
                return null;
            }

            LocalDateTime now = LocalDateTime.now();
            ConcurrentMap<String, ConcurrentMap<String, EditorActivo>> editoresPorNodo =
                    editoresPorPolitica.getOrDefault(politicaId, new ConcurrentHashMap<>());

            List<NodoBloqueoResponse> bloqueosActualizados = new ArrayList<>();
            for (String nodeId : sesion.getNodosEditando()) {
                ConcurrentMap<String, EditorActivo> editoresNodo = editoresPorNodo.get(nodeId);
                if (editoresNodo == null) {
                    continue;
                }
                editoresNodo.remove(sesion.getUserId());
                limpiarEstructurasVacias(politicaId, nodeId, editoresNodo);
                bloqueosActualizados.add(construirBloqueoNodo(politicaId, nodeId, editoresPorNodo.get(nodeId), now));
            }

            if (sesiones.isEmpty()) {
                sesionesPorPolitica.remove(politicaId);
            }

            PresenciaPoliticaResponse presencia = construirPresencia(politicaId, sesiones.values(), now);
            return new ResultadoDesconexion(politicaId, presencia, bloqueosActualizados);
        } finally {
            lock.unlock();
        }
    }

    private SesionActiva asegurarSesionActiva(
            String politicaId,
            String sessionId,
            String userId,
            String nombre,
            LocalDateTime now
    ) {
        ConcurrentMap<String, SesionActiva> sesiones =
                sesionesPorPolitica.computeIfAbsent(politicaId, key -> new ConcurrentHashMap<>());

        SesionActiva sesion = sesiones.get(sessionId);
        if (sesion == null) {
            sesion = SesionActiva.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .nombre(nombre)
                    .conectadoDesde(now)
                    .ultimaActividad(now)
                    .nodosEditando(new HashSet<>())
                    .build();
            sesiones.put(sessionId, sesion);
            indiceSesion.put(sessionId, new VinculoSesion(politicaId, userId));
            return sesion;
        }

        if (!Objects.equals(sesion.getUserId(), userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La sesion no pertenece al usuario indicado");
        }

        if (nombre != null) {
            sesion.setNombre(nombre);
        }
        sesion.setUltimaActividad(now);
        return sesion;
    }

    private void desvincularSesionAnteriorSiAplica(String sessionId, String politicaIdActual) {
        VinculoSesion previo = indiceSesion.get(sessionId);
        if (previo == null || Objects.equals(previo.politicaId(), politicaIdActual)) {
            return;
        }
        liberarSesion(previo.politicaId(), sessionId);
    }

    private void limpiarEstructurasVacias(
            String politicaId,
            String nodeId,
            ConcurrentMap<String, EditorActivo> editoresNodo
    ) {
        if (editoresNodo != null && editoresNodo.isEmpty()) {
            ConcurrentMap<String, ConcurrentMap<String, EditorActivo>> editoresPorNodo = editoresPorPolitica.get(politicaId);
            if (editoresPorNodo != null) {
                editoresPorNodo.remove(nodeId);
                if (editoresPorNodo.isEmpty()) {
                    editoresPorPolitica.remove(politicaId);
                }
            }
        }
    }

    private PresenciaPoliticaResponse construirPresencia(
            String politicaId,
            Collection<SesionActiva> sesiones,
            LocalDateTime timestamp
    ) {
        Map<String, AcumuladorUsuario> porUsuario = new HashMap<>();
        for (SesionActiva sesion : sesiones) {
            AcumuladorUsuario acumulado = porUsuario.computeIfAbsent(
                    sesion.getUserId(),
                    key -> new AcumuladorUsuario(sesion.getUserId(), sesion.getNombre())
            );
            acumulado.sesiones += 1;
            if (acumulado.ultimaActividad == null || sesion.getUltimaActividad().isAfter(acumulado.ultimaActividad)) {
                acumulado.ultimaActividad = sesion.getUltimaActividad();
            }
            if (acumulado.nombre == null || acumulado.nombre.isBlank()) {
                acumulado.nombre = sesion.getNombre();
            }
        }

        List<PresenciaUsuarioResponse> usuarios = porUsuario.values().stream()
                .map(acc -> PresenciaUsuarioResponse.builder()
                        .userId(acc.userId)
                        .nombre(acc.nombre)
                        .sesionesActivas(acc.sesiones)
                        .ultimaActividad(acc.ultimaActividad)
                        .build())
                .sorted(Comparator.comparing(PresenciaUsuarioResponse::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        return PresenciaPoliticaResponse.builder()
                .politicaId(politicaId)
                .totalUsuariosConectados(usuarios.size())
                .usuarios(usuarios)
                .timestamp(timestamp)
                .build();
    }

    private NodoBloqueoResponse construirBloqueoNodo(
            String politicaId,
            String nodeId,
            ConcurrentMap<String, EditorActivo> editoresNodo,
            LocalDateTime timestamp
    ) {
        List<PresenciaUsuarioResponse> editores = new ArrayList<>();
        if (editoresNodo != null) {
            for (EditorActivo editor : editoresNodo.values()) {
                editores.add(PresenciaUsuarioResponse.builder()
                        .userId(editor.userId())
                        .nombre(editor.nombre())
                        .sesionesActivas(1)
                        .ultimaActividad(editor.ultimaActividad())
                        .build());
            }
        }

        editores.sort(Comparator.comparing(PresenciaUsuarioResponse::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)));
        boolean colision = editores.size() > 1;

        String aviso;
        if (editores.isEmpty()) {
            aviso = "Nadie esta editando este nodo";
        } else if (editores.size() == 1) {
            aviso = editores.get(0).getNombre() + " esta editando";
        } else {
            aviso = "Edicion concurrente detectada: " + editores.size() + " usuarios editando (aplica last-write-wins)";
        }

        return NodoBloqueoResponse.builder()
                .politicaId(politicaId)
                .nodeId(nodeId)
                .editores(editores)
                .advertenciaColision(colision)
                .aviso(aviso)
                .timestamp(timestamp)
                .build();
    }

    private Usuario assertAdmin(String adminUserId) {
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar actorUserId del admin");
        }

        Usuario admin = usuarioRepository.findByIdAndActivo(adminUserId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));

        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede editar politicas en colaboracion");
        }
        return admin;
    }

    private void validarPolitica(String politicaId) {
        if (politicaId == null || politicaId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "politicaId es obligatorio");
        }

        if (!politicaNegocioRepository.existsById(politicaId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + politicaId);
        }
    }

    private ReentrantLock lockPolitica(String politicaId) {
        return locksPorPolitica.computeIfAbsent(politicaId, key -> new ReentrantLock());
    }

    private String nombreVisible(String preferido, String fallback) {
        String normalizadoPreferido = normalizarTexto(preferido);
        if (normalizadoPreferido != null) {
            return normalizadoPreferido;
        }

        String normalizadoFallback = normalizarTexto(fallback);
        if (normalizadoFallback != null) {
            return normalizadoFallback;
        }

        return "Usuario";
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class AcumuladorUsuario {
        private final String userId;
        private String nombre;
        private int sesiones;
        private LocalDateTime ultimaActividad;

        private AcumuladorUsuario(String userId, String nombre) {
            this.userId = userId;
            this.nombre = nombre;
        }
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.Setter
    private static class SesionActiva {
        private String sessionId;
        private String userId;
        private String nombre;
        private LocalDateTime conectadoDesde;
        private LocalDateTime ultimaActividad;
        private Set<String> nodosEditando;
    }

    private record VinculoSesion(String politicaId, String userId) {
    }

    private record EditorActivo(String userId, String nombre, LocalDateTime desde, LocalDateTime ultimaActividad) {
    }

    public record ResultadoDesconexion(
            String politicaId,
            PresenciaPoliticaResponse presencia,
            List<NodoBloqueoResponse> bloqueosActualizados
    ) {
    }
}
