package com.leo.politicas_de_negocio.service;

import com.leo.politicas_de_negocio.dto.politica.CreatePoliticaRequest;
import com.leo.politicas_de_negocio.dto.politica.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.exception.ApiException;
import com.leo.politicas_de_negocio.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.model.Usuario;
import com.leo.politicas_de_negocio.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.model.enums.ResponsableTipo;
import com.leo.politicas_de_negocio.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.model.politica.Nodo;
import com.leo.politicas_de_negocio.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PoliticaNegocioService {

    private final PoliticaNegocioRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;

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
                .nodos(new ArrayList<>())
                .conexiones(new ArrayList<>())
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
        politica.setFechaActualizacion(LocalDateTime.now());

        return repository.save(politica);
    }

    public PoliticaNegocio cambiarEstado(String adminUserId, String id, EstadoPolitica nuevoEstado) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = obtenerPorId(adminUserId, id);

        if (nuevoEstado == EstadoPolitica.ACTIVA) {
            validarPoliticaParaActivacion(politica);
        }

        politica.setEstado(nuevoEstado);
        politica.setFechaActualizacion(LocalDateTime.now());
        return repository.save(politica);
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
                if (!usuarioRepository.existsById(responsableId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El responsable USUARIO del nodo " + safeNodeName(nodo, index) + " no existe");
                }
            }
        }
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
}
