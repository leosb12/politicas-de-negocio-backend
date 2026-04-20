package com.leo.politicas_de_negocio.archivos.service;

import com.leo.politicas_de_negocio.archivos.dto.ArchivoDescargaResponse;
import com.leo.politicas_de_negocio.archivos.dto.ArchivoMetadataResponse;
import com.leo.politicas_de_negocio.archivos.dto.SubirArchivoRequest;
import com.leo.politicas_de_negocio.archivos.model.ArchivoAdjunto;
import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import com.leo.politicas_de_negocio.archivos.repository.ArchivoAdjuntoRepository;
import com.leo.politicas_de_negocio.archivos.storage.ArchivoStorageService;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoContenido;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStorageRequest;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStoredObject;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchivoService {

    private static final String CONTENT_TYPE_DEFAULT = "application/octet-stream";

    private final ArchivoAdjuntoRepository archivoRepository;
    private final ArchivoStorageService storageService;
    private final UsuarioRepository usuarioRepository;

    public ArchivoMetadataResponse subirArchivo(String actorUserId, SubirArchivoRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);

        if (request == null || request.getArchivo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el archivo en el campo 'archivo'");
        }

        MultipartFile archivo = request.getArchivo();
        if (archivo.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo no puede estar vacio");
        }

        String nombreOriginal = sanitizarNombreOriginal(archivo.getOriginalFilename());
        String extension = extraerExtension(nombreOriginal);
        String nombreGuardado = generarNombreUnico(extension);
        String contentType = normalizarContentType(archivo.getContentType());

        String instanciaId = normalizarTexto(request.getInstanciaId());
        String actividadId = normalizarTexto(request.getActividadId());
        String tareaId = normalizarTexto(request.getTareaId());
        String usuarioId = normalizarTexto(request.getUsuarioId());
        String descripcion = normalizarTexto(request.getDescripcion());

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el contenido del archivo");
        }

        ArchivoStoredObject stored = storageService.almacenar(ArchivoStorageRequest.builder()
                .nombreGuardado(nombreGuardado)
                .contentType(contentType)
                .contenido(contenido)
                .subdirectorio(construirSubdirectorio(instanciaId, actividadId, tareaId, usuarioId))
                .build());

        ArchivoAdjunto metadata = ArchivoAdjunto.builder()
                .nombreOriginal(nombreOriginal)
                .nombreGuardado(stored.getNombreGuardado())
                .rutaOKey(stored.getRutaOKey())
                .storageType(stored.getStorageType())
                .contentType(contentType)
                .extension(extension)
                .tamanoBytes(archivo.getSize())
                .fechaSubida(LocalDateTime.now())
                .subidoPor(actor.getId())
                .instanciaId(instanciaId)
                .actividadId(actividadId)
                .tareaId(tareaId)
                .usuarioId(usuarioId)
                .estado(EstadoArchivo.ACTIVO)
                .descripcion(descripcion)
                .urlAcceso(stored.getUrlAcceso())
                .bucket(stored.getBucket())
                .build();

        try {
            metadata = archivoRepository.save(metadata);
        } catch (RuntimeException ex) {
            rollbackStorage(stored.getRutaOKey());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar la metadata del archivo");
        }

        return toResponse(metadata);
    }

    public ArchivoMetadataResponse obtenerMetadatos(String actorUserId, String archivoId) {
        assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);
        return toResponse(archivo);
    }

    public ArchivoDescargaResponse descargar(String actorUserId, String archivoId) {
        assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);

        ArchivoContenido contenido = storageService.descargar(archivo.getRutaOKey());
        String contentType = normalizarContentType(contenido.getContentType());

        return ArchivoDescargaResponse.builder()
                .contenido(contenido.getContenido())
                .nombreOriginal(archivo.getNombreOriginal())
                .contentType(contentType)
                .build();
    }

    public void eliminar(String actorUserId, String archivoId) {
        assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);

        storageService.eliminar(archivo.getRutaOKey());

        archivo.setEstado(EstadoArchivo.ELIMINADO);
        archivoRepository.save(archivo);
    }

    public List<ArchivoMetadataResponse> listarPorInstancia(String actorUserId, String instanciaId) {
        assertUsuarioActivo(actorUserId);
        String id = normalizarTexto(instanciaId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar instanciaId");
        }

        return archivoRepository.findByInstanciaIdAndEstadoOrderByFechaSubidaDesc(id, EstadoArchivo.ACTIVO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ArchivoMetadataResponse> listarPorActividad(String actorUserId, String actividadId) {
        assertUsuarioActivo(actorUserId);
        String id = normalizarTexto(actividadId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar actividadId");
        }

        return archivoRepository.findByActividadIdAndEstadoOrderByFechaSubidaDesc(id, EstadoArchivo.ACTIVO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ArchivoAdjunto buscarActivoPorId(String archivoId) {
        String id = normalizarTexto(archivoId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id del archivo");
        }

        return archivoRepository.findByIdAndEstado(id, EstadoArchivo.ACTIVO)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Archivo no encontrado con ID: " + id));
    }

    private Usuario assertUsuarioActivo(String userId) {
        String actorId = normalizarTexto(userId);
        if (actorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-User-Id");
        }

        return usuarioRepository.findByIdAndActivo(actorId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
    }

    private ArchivoMetadataResponse toResponse(ArchivoAdjunto archivo) {
        return ArchivoMetadataResponse.builder()
                .id(archivo.getId())
                .nombreOriginal(archivo.getNombreOriginal())
                .nombreGuardado(archivo.getNombreGuardado())
                .rutaOKey(archivo.getRutaOKey())
                .storageType(archivo.getStorageType())
                .contentType(archivo.getContentType())
                .extension(archivo.getExtension())
                .tamanoBytes(archivo.getTamanoBytes())
                .fechaSubida(archivo.getFechaSubida())
                .subidoPor(archivo.getSubidoPor())
                .instanciaId(archivo.getInstanciaId())
                .actividadId(archivo.getActividadId())
                .tareaId(archivo.getTareaId())
                .usuarioId(archivo.getUsuarioId())
                .estado(archivo.getEstado())
                .descripcion(archivo.getDescripcion())
                .urlAcceso(archivo.getUrlAcceso())
                .bucket(archivo.getBucket())
                .build();
    }

    private void rollbackStorage(String rutaOKey) {
        try {
            storageService.eliminar(rutaOKey);
        } catch (RuntimeException ignored) {
            // Evita ocultar el error principal de persistencia.
        }
    }

    private String construirSubdirectorio(String instanciaId, String actividadId, String tareaId, String usuarioId) {
        List<String> segmentos = new ArrayList<>();

        agregarAsociacion(segmentos, "instancias", instanciaId);
        agregarAsociacion(segmentos, "actividades", actividadId);
        agregarAsociacion(segmentos, "tareas", tareaId);
        agregarAsociacion(segmentos, "usuarios", usuarioId);

        if (segmentos.isEmpty()) {
            return "general";
        }

        return String.join("/", segmentos);
    }

    private void agregarAsociacion(List<String> segmentos, String tipo, String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }

        String safeId = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!StringUtils.hasText(safeId)) {
            return;
        }

        segmentos.add(tipo);
        segmentos.add(safeId);
    }

    private String generarNombreUnico(String extension) {
        String base = UUID.randomUUID().toString().replace("-", "");
        if (StringUtils.hasText(extension)) {
            return base + "." + extension.toLowerCase(Locale.ROOT);
        }
        return base;
    }

    private String extraerExtension(String nombreArchivo) {
        String nombre = normalizarTexto(nombreArchivo);
        if (nombre == null) {
            return null;
        }

        int idx = nombre.lastIndexOf('.');
        if (idx <= 0 || idx == nombre.length() - 1) {
            return null;
        }

        return nombre.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizarNombreOriginal(String originalFilename) {
        String candidate = normalizarTexto(originalFilename);
        if (candidate == null) {
            return "archivo";
        }

        candidate = candidate.replace("\\", "/");
        int lastSlash = candidate.lastIndexOf('/');
        if (lastSlash >= 0) {
            candidate = candidate.substring(lastSlash + 1);
        }

        candidate = candidate.replaceAll("[\\r\\n\\t]", "_");
        candidate = candidate.replaceAll("[^a-zA-Z0-9._-]", "_");
        candidate = candidate.replaceAll("\\.{2,}", ".");

        if (!StringUtils.hasText(candidate)) {
            return "archivo";
        }

        if (candidate.length() > 180) {
            candidate = candidate.substring(0, 180);
        }

        if (candidate.startsWith(".")) {
            candidate = "archivo" + candidate;
        }

        return candidate;
    }

    private String normalizarContentType(String contentType) {
        String normalized = normalizarTexto(contentType);
        if (normalized == null) {
            return CONTENT_TYPE_DEFAULT;
        }

        try {
            MediaType.parseMediaType(normalized);
            return normalized;
        } catch (InvalidMediaTypeException ex) {
            return CONTENT_TYPE_DEFAULT;
        }
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
