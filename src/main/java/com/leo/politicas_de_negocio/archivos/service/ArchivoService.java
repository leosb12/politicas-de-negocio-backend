package com.leo.politicas_de_negocio.archivos.service;

import com.leo.politicas_de_negocio.archivos.dto.ArchivoDescargaResponse;
import com.leo.politicas_de_negocio.archivos.dto.ArchivoMetadataResponse;
import com.leo.politicas_de_negocio.archivos.dto.EditarArchivoRequest;
import com.leo.politicas_de_negocio.archivos.dto.ReemplazarArchivoRequest;
import com.leo.politicas_de_negocio.archivos.dto.SubirArchivoRequest;
import com.leo.politicas_de_negocio.archivos.model.ArchivoAdjunto;
import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import com.leo.politicas_de_negocio.archivos.repository.ArchivoAdjuntoRepository;
import com.leo.politicas_de_negocio.archivos.storage.ArchivoStorageService;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoContenido;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStorageRequest;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStoredObject;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionConfig;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentFileType;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentPermissionAction;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentAuditService;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentPermissionService;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchivoService {

    private static final String CONTENT_TYPE_DEFAULT = "application/octet-stream";

    private final ArchivoAdjuntoRepository archivoRepository;
    private final ArchivoStorageService storageService;
    private final UsuarioRepository usuarioRepository;
    private final DocumentPermissionService documentPermissionService;
    private final DocumentAuditService documentAuditService;

    public ArchivoMetadataResponse subirArchivo(String actorUserId, SubirArchivoRequest request) {
        return subirArchivo(actorUserId, request, null, null);
    }

    public ArchivoMetadataResponse subirArchivo(String actorUserId, SubirArchivoRequest request, String ip, String userAgent) {
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
        String campoId = normalizarTexto(request.getCampoId());
        String tramiteId = normalizarTexto(request.getTramiteId());
        String clienteId = normalizarTexto(request.getClienteId());
        String politicaId = normalizarTexto(request.getPoliticaId());
        String nodoId = normalizarTexto(request.getNodoId());
        String descripcion = normalizarTexto(request.getDescripcion());

        DocumentPermissionConfig config = validarAccionDocumental(
                actor,
                campoId,
                clienteId,
                tramiteId,
                DocumentPermissionAction.SUBIR,
                DocumentAuditAction.SUBIR,
                null,
                ip,
                userAgent,
                "Validacion previa a subida documental"
        );
        validarRestriccionesArchivo(config, archivo.getSize(), extension);

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
                .campoId(campoId)
                .tramiteId(tramiteId)
                .clienteId(clienteId)
                .politicaId(politicaId)
                .nodoId(nodoId)
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

        registrarAuditoriaSiAplica(
                config,
                DocumentAuditAction.SUBIR,
                DocumentAuditResult.PERMITIDO,
                metadata,
                actor,
                ip,
                userAgent,
                "Documento subido correctamente"
        );

        return toResponse(metadata);
    }

    public ArchivoMetadataResponse obtenerMetadatos(String actorUserId, String archivoId) {
        return obtenerMetadatos(actorUserId, archivoId, null, null);
    }

    public ArchivoMetadataResponse obtenerMetadatos(String actorUserId, String archivoId, String ip, String userAgent) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);
        validarAccionDocumental(
                actor,
                archivo.getCampoId(),
                archivo.getClienteId(),
                archivo.getTramiteId(),
                DocumentPermissionAction.LEER,
                DocumentAuditAction.VISUALIZAR,
                archivo,
                ip,
                userAgent,
                "Visualizacion de metadata documental"
        );
        return toResponse(archivo);
    }

    public ArchivoDescargaResponse descargar(String actorUserId, String archivoId) {
        return descargar(actorUserId, archivoId, null, null);
    }

    public ArchivoDescargaResponse descargar(String actorUserId, String archivoId, String ip, String userAgent) {
        return leerContenido(actorUserId, archivoId, DocumentPermissionAction.DESCARGAR, DocumentAuditAction.DESCARGAR,
                ip, userAgent, "Descarga documental");
    }

    public ArchivoDescargaResponse visualizar(String actorUserId, String archivoId, String ip, String userAgent) {
        return leerContenido(actorUserId, archivoId, DocumentPermissionAction.LEER, DocumentAuditAction.VISUALIZAR,
                ip, userAgent, "Visualizacion documental");
    }

    private ArchivoDescargaResponse leerContenido(
            String actorUserId,
            String archivoId,
            DocumentPermissionAction permissionAction,
            DocumentAuditAction auditAction,
            String ip,
            String userAgent,
            String detalle
    ) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);
        validarAccionDocumental(
                actor,
                archivo.getCampoId(),
                archivo.getClienteId(),
                archivo.getTramiteId(),
                permissionAction,
                auditAction,
                archivo,
                ip,
                userAgent,
                detalle
        );

        ArchivoContenido contenido = storageService.descargar(archivo.getRutaOKey());
        String contentType = normalizarContentType(contenido.getContentType());

        return ArchivoDescargaResponse.builder()
                .contenido(contenido.getContenido())
                .nombreOriginal(archivo.getNombreOriginal())
                .contentType(contentType)
                .build();
    }

    public ArchivoMetadataResponse editar(String actorUserId, String archivoId, EditarArchivoRequest request, String ip, String userAgent) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);
        validarAccionDocumental(
                actor,
                archivo.getCampoId(),
                archivo.getClienteId(),
                archivo.getTramiteId(),
                DocumentPermissionAction.EDITAR,
                DocumentAuditAction.EDITAR,
                archivo,
                ip,
                userAgent,
                "Edicion de metadata documental"
        );

        String nombreOriginal = request != null ? normalizarTexto(request.getNombreOriginal()) : null;
        if (nombreOriginal != null) {
            archivo.setNombreOriginal(sanitizarNombreOriginal(nombreOriginal));
            archivo.setExtension(extraerExtension(archivo.getNombreOriginal()));
        }
        archivo.setDescripcion(request != null ? normalizarTexto(request.getDescripcion()) : null);

        ArchivoAdjunto saved = archivoRepository.save(archivo);
        return toResponse(saved);
    }

    public ArchivoMetadataResponse reemplazar(String actorUserId, String archivoId, ReemplazarArchivoRequest request, String ip, String userAgent) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);
        DocumentPermissionConfig config = validarAccionDocumental(
                actor,
                archivo.getCampoId(),
                archivo.getClienteId(),
                archivo.getTramiteId(),
                DocumentPermissionAction.REEMPLAZAR,
                DocumentAuditAction.REEMPLAZAR,
                archivo,
                ip,
                userAgent,
                "Reemplazo documental"
        );

        if (request == null || request.getArchivo() == null || request.getArchivo().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el archivo de reemplazo en el campo 'archivo'");
        }

        MultipartFile nuevoArchivo = request.getArchivo();
        String nombreOriginal = sanitizarNombreOriginal(nuevoArchivo.getOriginalFilename());
        String extension = extraerExtension(nombreOriginal);
        validarRestriccionesArchivo(config, nuevoArchivo.getSize(), extension);

        byte[] contenido;
        try {
            contenido = nuevoArchivo.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el contenido del archivo de reemplazo");
        }

        String oldRutaOKey = archivo.getRutaOKey();
        ArchivoStoredObject stored = storageService.almacenar(ArchivoStorageRequest.builder()
                .nombreGuardado(generarNombreUnico(extension))
                .contentType(normalizarContentType(nuevoArchivo.getContentType()))
                .contenido(contenido)
                .subdirectorio(construirSubdirectorio(archivo.getInstanciaId(), archivo.getActividadId(), archivo.getTareaId(), archivo.getUsuarioId()))
                .build());

        archivo.setNombreOriginal(nombreOriginal);
        archivo.setNombreGuardado(stored.getNombreGuardado());
        archivo.setRutaOKey(stored.getRutaOKey());
        archivo.setStorageType(stored.getStorageType());
        archivo.setContentType(normalizarContentType(nuevoArchivo.getContentType()));
        archivo.setExtension(extension);
        archivo.setTamanoBytes(nuevoArchivo.getSize());
        archivo.setFechaSubida(LocalDateTime.now());
        archivo.setSubidoPor(actor.getId());
        archivo.setUrlAcceso(stored.getUrlAcceso());
        archivo.setBucket(stored.getBucket());

        try {
            ArchivoAdjunto saved = archivoRepository.save(archivo);
            if (oldRutaOKey != null && !oldRutaOKey.equals(stored.getRutaOKey())) {
                rollbackStorage(oldRutaOKey);
            }
            return toResponse(saved);
        } catch (RuntimeException ex) {
            rollbackStorage(stored.getRutaOKey());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo reemplazar la metadata del archivo");
        }
    }

    public void eliminar(String actorUserId, String archivoId) {
        eliminar(actorUserId, archivoId, null, null);
    }

    public void eliminar(String actorUserId, String archivoId, String ip, String userAgent) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        ArchivoAdjunto archivo = buscarActivoPorId(archivoId);
        validarAccionDocumental(
                actor,
                archivo.getCampoId(),
                archivo.getClienteId(),
                archivo.getTramiteId(),
                DocumentPermissionAction.ELIMINAR,
                DocumentAuditAction.ELIMINAR,
                archivo,
                ip,
                userAgent,
                "Eliminacion documental"
        );

        storageService.eliminar(archivo.getRutaOKey());

        archivo.setEstado(EstadoArchivo.ELIMINADO);
        archivoRepository.save(archivo);
    }

    public List<ArchivoMetadataResponse> listarPorInstancia(String actorUserId, String instanciaId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        String id = normalizarTexto(instanciaId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar instanciaId");
        }

        List<ArchivoAdjunto> archivos = archivoRepository.findByInstanciaIdAndEstadoOrderByFechaSubidaDesc(id, EstadoArchivo.ACTIVO);
        Map<String, String> usuariosPorId = nombresUsuariosPorId(archivos.stream()
                .map(ArchivoAdjunto::getSubidoPor)
                .toList());

        return archivos.stream()
                .map(archivo -> toResponseConPermisos(actor, archivo, usuariosPorId))
                .filter(response -> Boolean.TRUE.equals(response.getPuedeVer()))
                .toList();
    }

    public List<ArchivoMetadataResponse> listarPorActividad(String actorUserId, String actividadId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        String id = normalizarTexto(actividadId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar actividadId");
        }

        List<ArchivoAdjunto> archivos = archivoRepository.findByActividadIdAndEstadoOrderByFechaSubidaDesc(id, EstadoArchivo.ACTIVO);
        Map<String, String> usuariosPorId = nombresUsuariosPorId(archivos.stream()
                .map(ArchivoAdjunto::getSubidoPor)
                .toList());

        return archivos.stream()
                .map(archivo -> toResponseConPermisos(actor, archivo, usuariosPorId))
                .filter(response -> Boolean.TRUE.equals(response.getPuedeVer()))
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
                .campoId(archivo.getCampoId())
                .tramiteId(archivo.getTramiteId())
                .clienteId(archivo.getClienteId())
                .politicaId(archivo.getPoliticaId())
                .nodoId(archivo.getNodoId())
                .estado(archivo.getEstado())
                .descripcion(archivo.getDescripcion())
                .urlAcceso(archivo.getUrlAcceso())
                .bucket(archivo.getBucket())
                .build();
    }

    private ArchivoMetadataResponse toResponseConPermisos(
            Usuario actor,
            ArchivoAdjunto archivo,
            Map<String, String> usuariosPorId
    ) {
        ArchivoMetadataResponse response = toResponse(archivo);
        response.setSubidoPorNombre(usuariosPorId.getOrDefault(archivo.getSubidoPor(), archivo.getSubidoPor()));
        response.setPuedeVer(tienePermisoDocumental(actor, archivo, DocumentPermissionAction.LEER));
        response.setPuedeDescargar(tienePermisoDocumental(actor, archivo, DocumentPermissionAction.DESCARGAR));
        response.setPuedeEditar(tienePermisoDocumental(actor, archivo, DocumentPermissionAction.EDITAR));
        response.setPuedeReemplazar(tienePermisoDocumental(actor, archivo, DocumentPermissionAction.REEMPLAZAR));
        response.setPuedeEliminar(tienePermisoDocumental(actor, archivo, DocumentPermissionAction.ELIMINAR));
        return response;
    }

    private Map<String, String> nombresUsuariosPorId(Collection<String> ids) {
        Set<String> normalized = new HashSet<>();
        for (String id : ids) {
            String value = normalizarTexto(id);
            if (value != null) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        usuarioRepository.findAllById(normalized).forEach(usuario ->
                result.put(usuario.getId(), usuario.getNombre()));
        return result;
    }

    private boolean tienePermisoDocumental(
            Usuario actor,
            ArchivoAdjunto archivo,
            DocumentPermissionAction action
    ) {
        String campoId = normalizarTexto(archivo.getCampoId());
        if (campoId == null) {
            return false;
        }

        Optional<DocumentPermissionConfig> config = documentPermissionService.buscarConfiguracionActivaPorCampoOpcional(campoId);
        if (config.isEmpty()) {
            return false;
        }

        DocumentPermissionValidationRequest request = new DocumentPermissionValidationRequest();
        request.setUsuarioId(actor.getId());
        request.setRol(actor.getRol());
        request.setDepartamentoId(actor.getDepartamentoId());
        request.setClienteId(resolverClienteId(actor, archivo.getClienteId()));
        request.setTramiteId(archivo.getTramiteId());
        request.setCampoId(campoId);
        request.setAccion(action);

        DocumentPermissionValidationResponse validation = documentPermissionService.validarPermiso(request);
        return Boolean.TRUE.equals(validation.getPermitido());
    }

    private DocumentPermissionConfig validarAccionDocumental(
            Usuario actor,
            String campoId,
            String clienteId,
            String tramiteId,
            DocumentPermissionAction permissionAction,
            DocumentAuditAction auditAction,
            ArchivoAdjunto archivo,
            String ip,
            String userAgent,
            String detalle
    ) {
        String normalizedCampoId = normalizarTexto(campoId);
        if (normalizedCampoId == null) {
            return null;
        }

        DocumentPermissionConfig config = documentPermissionService.buscarConfiguracionActivaPorCampoOpcional(normalizedCampoId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "No existe configuracion activa de permisos documentales para este campo"));

        DocumentPermissionValidationRequest request = new DocumentPermissionValidationRequest();
        request.setUsuarioId(actor.getId());
        request.setRol(actor.getRol());
        request.setDepartamentoId(actor.getDepartamentoId());
        request.setClienteId(resolverClienteId(actor, clienteId));
        request.setTramiteId(tramiteId);
        request.setCampoId(normalizedCampoId);
        request.setAccion(permissionAction);

        DocumentPermissionValidationResponse validation = documentPermissionService.validarPermiso(request);
        if (!Boolean.TRUE.equals(validation.getPermitido())) {
            registrarAuditoriaSiAplica(config, auditAction, DocumentAuditResult.DENEGADO,
                    archivo, actor, ip, userAgent, validation.getMotivo());
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "No tiene permiso documental para " + permissionAction.name() + ": " + validation.getMotivo());
        }

        if (auditAction != DocumentAuditAction.SUBIR || archivo != null) {
            registrarAuditoriaSiAplica(config, auditAction, DocumentAuditResult.PERMITIDO,
                    archivo, actor, ip, userAgent, detalle);
        }
        return config;
    }

    private void validarRestriccionesArchivo(DocumentPermissionConfig config, long tamanoBytes, String extension) {
        if (config == null) {
            return;
        }

        Integer maxMb = config.getTamanoMaximoMb();
        if (maxMb != null && maxMb > 0) {
            long maxBytes = maxMb * 1024L * 1024L;
            if (tamanoBytes > maxBytes) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "El archivo excede el tamano maximo permitido para este campo documental (" + maxMb + " MB)");
            }
        }

        List<DocumentFileType> permitidos = config.getTiposArchivoPermitidos();
        if (permitidos == null || permitidos.isEmpty()) {
            return;
        }

        Set<DocumentFileType> tiposDetectados = detectarTiposArchivo(extension);
        boolean permitido = tiposDetectados.stream().anyMatch(permitidos::contains)
                || (tiposDetectados.isEmpty() && permitidos.contains(DocumentFileType.OTRO));
        if (!permitido) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El tipo de archivo no esta permitido para este campo documental");
        }
    }

    private Set<DocumentFileType> detectarTiposArchivo(String extension) {
        String ext = normalizarTexto(extension);
        if (ext == null) {
            return Set.of();
        }
        ext = ext.toLowerCase(Locale.ROOT);
        Set<DocumentFileType> tipos = new HashSet<>();
        switch (ext) {
            case "pdf" -> tipos.add(DocumentFileType.PDF);
            case "doc", "docx" -> tipos.add(DocumentFileType.WORD);
            case "xls", "xlsx", "csv" -> tipos.add(DocumentFileType.EXCEL);
            case "ppt", "pptx" -> tipos.add(DocumentFileType.POWERPOINT);
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> tipos.add(DocumentFileType.IMAGEN);
            case "mp4", "mov", "avi", "mkv", "webm", "mpeg" -> tipos.add(DocumentFileType.VIDEO);
            default -> tipos.add(DocumentFileType.OTRO);
        }
        return tipos;
    }

    private void registrarAuditoriaSiAplica(
            DocumentPermissionConfig config,
            DocumentAuditAction action,
            DocumentAuditResult result,
            ArchivoAdjunto archivo,
            Usuario actor,
            String ip,
            String userAgent,
            String detalle
    ) {
        if (!documentPermissionService.auditoriaHabilitada(config, action)) {
            return;
        }

        DocumentAuditEventRequest request = new DocumentAuditEventRequest();
        request.setDocumentoId(archivo != null ? archivo.getId() : null);
        request.setCampoId(archivo != null ? archivo.getCampoId() : config.getCampoId());
        request.setTramiteId(archivo != null ? archivo.getTramiteId() : scopeTramite(config));
        request.setClienteId(archivo != null ? archivo.getClienteId() : scopeCliente(config));
        request.setPoliticaId(archivo != null ? archivo.getPoliticaId() : config.getPoliticaId());
        request.setNodoId(archivo != null ? archivo.getNodoId() : config.getNodoId());
        request.setAccion(action);
        request.setUsuarioId(debeGuardarUsuario(config) ? actor.getId() : null);
        request.setUsuarioNombre(debeGuardarUsuario(config) ? actor.getNombre() : null);
        request.setRol(debeGuardarUsuario(config) ? actor.getRol() : null);
        request.setDepartamentoId(actor.getDepartamentoId());
        request.setIp(debeGuardarIp(config) ? ip : null);
        request.setUserAgent(debeGuardarUserAgent(config) ? userAgent : null);
        request.setDetalle(detalle);
        request.setResultado(result);

        documentAuditService.registrarEventoAuditoria(request);
    }

    private boolean debeGuardarIp(DocumentPermissionConfig config) {
        return config != null && config.getAuditoria() != null
                && Boolean.TRUE.equals(config.getAuditoria().getGuardarIpDispositivo());
    }

    private boolean debeGuardarUserAgent(DocumentPermissionConfig config) {
        return config != null && config.getAuditoria() != null
                && Boolean.TRUE.equals(config.getAuditoria().getGuardarUserAgent());
    }

    private boolean debeGuardarUsuario(DocumentPermissionConfig config) {
        return config != null && config.getAuditoria() != null
                && Boolean.TRUE.equals(config.getAuditoria().getGuardarUsuarioActor());
    }

    private String scopeCliente(DocumentPermissionConfig config) {
        return config != null && config.getAlcance() != null ? config.getAlcance().getClienteId() : null;
    }

    private String scopeTramite(DocumentPermissionConfig config) {
        return config != null && config.getAlcance() != null ? config.getAlcance().getTramiteId() : null;
    }

    private String resolverClienteId(Usuario actor, String clienteId) {
        String normalized = normalizarTexto(clienteId);
        if (normalized != null) {
            return normalized;
        }
        if (actor != null && esRolCliente(actor.getRol())) {
            return actor.getId();
        }
        return null;
    }

    private boolean esRolCliente(String rol) {
        String normalized = normalizarTexto(rol);
        if (normalized == null) {
            return false;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return "CLIENTE".equals(normalized) || "USUARIO".equals(normalized);
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
