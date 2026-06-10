package com.leo.politicas_de_negocio.documents.controller;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.model.DocumentoVersion;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventRequest;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentAuditService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoPermisoService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoS3Service;
import com.leo.politicas_de_negocio.documents.service.DocumentoVersionService;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documentos-colaborativos")
@RequiredArgsConstructor
public class DocumentoColaborativoEditorController {

    private static final Logger log = LoggerFactory.getLogger(DocumentoColaborativoEditorController.class);

    private final DocumentoColaborativoMetadataService metadataService;
    private final DocumentoColaborativoPermisoService permisoService;
    private final DocumentoColaborativoS3Service s3Service;
    private final DocumentoVersionService versionService;
    private final DocumentAuditService auditService;
    private final UsuarioRepository usuarioRepository;
    private final InstanciaPoliticaRepository instanciaPoliticaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String sourceTokenSecret = UUID.randomUUID().toString();

    @Value("${onlyoffice.document-server-url}")
    private String documentServerUrl;

    @Value("${onlyoffice.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${onlyoffice.public-url:${onlyoffice.document-server-url}}")
    private String documentServerPublicUrl;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Value("${onlyoffice.source-public-access-enabled:true}")
    private boolean sourcePublicAccessEnabled;

    @Value("${onlyoffice.jwt-enabled:false}")
    private boolean jwtEnabled;

    @Value("${onlyoffice.jwt-secret:}")
    private String jwtSecret;

    @GetMapping("/{documentoId}/mobile-viewer")
    public ResponseEntity<String> obtenerMobileViewer(
            @PathVariable String documentoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest) {

        String actorUserId = resolverActorUserId(userId, adminUserId);
        Usuario usuario = usuarioRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        InstanciaPolitica instancia = obtenerInstancia(metadata);

        DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                metadata, usuario, usuario.getRol(), usuario.getDepartamentoId(), instancia);
        if (!permisos.isPuedeLeer()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para abrir este documento colaborativo");
        }
        registrarEventoDocumento(metadata, instancia, usuario, null, DocumentAuditAction.VISUALIZAR,
                DocumentAuditResult.PERMITIDO, userAgent, servletRequest, "Documento abierto desde visor movil OnlyOffice");

        String fileType = resolverFileType(metadata.getTipoDocumento());
        String documentType = resolverDocumentType(metadata.getTipoDocumento());
        String documentKey = resolverDocumentKey(metadata);
        String titulo = resolverTitulo(metadata.getNombreDocumento(), fileType);
        String sourceUrl = construirSourceUrl(metadata, usuario.getId());
        String callbackUrl = construirCallbackUrl(metadata.getDocumentoId(), usuario.getId());
        String auditEventUrl = construirAuditEventUrl(metadata.getDocumentoId());
        String serverUrl = limpiarUrlBase(documentServerPublicUrl);

        boolean puedeEditar = permisos.isPuedeEditar();
        String editorMode = puedeEditar ? "edit" : "view";

        String html = "<!DOCTYPE html>" +
            "<html lang=\"es\">" +
            "<head>" +
            "<meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no\">" +
            "<title>" + titulo + "</title>" +
            "<style>" +
            "*{margin:0;padding:0;box-sizing:border-box}" +
            "html,body{width:100%;height:100%;overflow:hidden;background:#1e1e2e}" +
            "#placeholder{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;color:#cdd6f4;font-family:sans-serif;font-size:14px;gap:12px}" +
            ".spinner{width:40px;height:40px;border:3px solid #313244;border-top-color:#89b4fa;border-radius:50%;animation:spin 0.8s linear infinite}" +
            "@keyframes spin{to{transform:rotate(360deg)}}" +
            "#editor{width:100%;height:100%}" +
            "</style>" +
            "</head>" +
            "<body>" +
            "<div id=\"placeholder\"><div class=\"spinner\"></div><span>Cargando documento...</span></div>" +
            "<div id=\"editor\"></div>" +
            "<script src=\"" + serverUrl + "/web-apps/apps/api/documents/api.js\"></script>" +
            "<script>" +
            "var docEditor;" +
            "var auditEditPending=false;" +
            "function auditOnlyOffice(action,detail){" +
            "  try{fetch('" + auditEventUrl + "',{" +
            "    method:'POST'," +
            "    headers:{'Content-Type':'application/json','X-User-Id':'" + usuario.getId() + "'}," +
            "    body:JSON.stringify({accion:action,detalle:detail})" +
            "  }).catch(function(){});}catch(e){}" +
            "}" +
            "function initEditor(){" +
            "  document.getElementById('placeholder').style.display='none';" +
            "  var config={" +
            "    document:{" +
            "      fileType:'" + fileType + "'," +
            "      key:'" + documentKey + "'," +
            "      title:'" + titulo.replace("'", "\\'") + "'," +
            "      url:'" + sourceUrl + "'," +
            "      permissions:{" +
            "        edit:" + puedeEditar + "," +
            "        download:" + permisos.isPuedeDescargar() + "," +
            "        print:" + permisos.isPuedeImprimir() + "," +
            "        copy:" + permisos.isPuedeDescargar() + "," +
            "        comment:" + permisos.isPuedeComentar() + "," +
            "        review:" + Boolean.TRUE.equals(metadata.getAuditarCambios()) +
            "      }" +
            "    }," +
            "    documentType:'" + documentType + "'," +
            "    editorConfig:{" +
            "      mode:'" + editorMode + "'," +
            "      callbackUrl:'" + callbackUrl + "'," +
            "      user:{id:'" + usuario.getId() + "',name:'" + resolverNombreUsuario(usuario).replace("'", "\\'") + "'}," +
            "      coEditing:{mode:'fast',change:true}," +
            "      customization:{autosave:true,forcesave:true,mobile:true,uiTheme:'theme-dark'" +
            (Boolean.TRUE.equals(metadata.getAuditarCambios())
                    ? ",review:{trackChanges:true,reviewDisplay:'markup',showReviewChanges:false},trackChanges:true"
                    : "") +
            "}" +
            "    }," +
            "    events:{" +
            "      onDocumentStateChange:function(e){if(e&&e.data&&!auditEditPending){auditEditPending=true;auditOnlyOffice('EDITAR','Documento modificado en OnlyOffice');setTimeout(function(){auditEditPending=false;},3000);}}," +
            "      onDownloadAs:function(){auditOnlyOffice('DESCARGAR','Documento descargado desde OnlyOffice');}," +
            "      onRequestPrint:function(){auditOnlyOffice('IMPRIMIR','Documento enviado a impresion desde OnlyOffice');}" +
            "    }" +
            "  };" +
            (jwtEnabled ? "  config.token='" + generarJwtOnlyOffice(buildConfigMap(fileType, documentKey, titulo, sourceUrl, permisos, documentType, editorMode, callbackUrl, usuario, Boolean.TRUE.equals(metadata.getAuditarCambios()))) + "';" : "") +
            "  docEditor=new DocsAPI.DocEditor('editor',config);" +
            "}" +
            "if(typeof DocsAPI!=='undefined'){initEditor();}else{" +
            "  var s=document.querySelector('script[src*=\"api.js\"]');" +
            "  if(s){s.onload=initEditor;s.onerror=function(){document.getElementById('placeholder').innerHTML='<span style=color:#f38ba8>No se pudo conectar con el servidor de documentos.</span>';};}" +
            "}" +
            "</script>" +
            "</body>" +
            "</html>";

        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .body(html);
    }

    private Map<String, Object> buildConfigMap(
            String fileType, String documentKey, String titulo, String sourceUrl,
            DocumentoColaborativoPermisosDto permisos, String documentType,
            String editorMode, String callbackUrl, Usuario usuario, boolean auditarCambios) {
        Map<String, Object> documentMap = new LinkedHashMap<>();
        documentMap.put("fileType", fileType);
        documentMap.put("key", documentKey);
        documentMap.put("title", titulo);
        documentMap.put("url", sourceUrl);
        Map<String, Object> docPerms = new LinkedHashMap<>();
        docPerms.put("edit", permisos.isPuedeEditar());
        docPerms.put("download", permisos.isPuedeDescargar());
        docPerms.put("print", permisos.isPuedeImprimir());
        docPerms.put("copy", permisos.isPuedeDescargar());
        docPerms.put("comment", permisos.isPuedeComentar());
        docPerms.put("review", auditarCambios);
        documentMap.put("permissions", docPerms);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", editorMode);
        editorConfig.put("callbackUrl", callbackUrl);
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", usuario.getId());
        userMap.put("name", resolverNombreUsuario(usuario));
        editorConfig.put("user", userMap);
        if (auditarCambios) {
            Map<String, Object> customization = new LinkedHashMap<>();
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("trackChanges", true);
            review.put("reviewDisplay", "markup");
            review.put("showReviewChanges", false);
            customization.put("review", review);
            customization.put("trackChanges", true);
            editorConfig.put("customization", customization);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("document", documentMap);
        config.put("documentType", documentType);
        config.put("editorConfig", editorConfig);
        return config;
    }

    @PostMapping("/{documentoId}/editor-config")
    public ResponseEntity<Map<String, Object>> obtenerEditorConfig(
            @PathVariable String documentoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest) {

        String actorUserId = resolverActorUserId(userId, adminUserId);
        Usuario usuario = usuarioRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        InstanciaPolitica instancia = obtenerInstancia(metadata);

        DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                metadata,
                usuario,
                usuario.getRol(),
                usuario.getDepartamentoId(),
                instancia
        );
        if (!permisos.isPuedeLeer()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para abrir este documento colaborativo");
        }
        registrarEventoDocumento(metadata, instancia, usuario, null, DocumentAuditAction.VISUALIZAR,
                DocumentAuditResult.PERMITIDO, userAgent, servletRequest, "Documento abierto desde editor OnlyOffice");

        String fileType = resolverFileType(metadata.getTipoDocumento());
        String documentType = resolverDocumentType(metadata.getTipoDocumento());
        String documentKey = resolverDocumentKey(metadata);
        String titulo = resolverTitulo(metadata.getNombreDocumento(), fileType);
        String sourceUrl = construirSourceUrl(metadata, usuario.getId());
        String callbackUrl = construirCallbackUrl(metadata.getDocumentoId(), usuario.getId());

        log.info("GENERANDO ONLYOFFICE CONFIG");
        log.info("documentoId={}", documentoId);
        log.info("usuarioId={}", usuario.getId());
        log.info("rol={}", usuario.getRol());
        log.info("departamento={}", usuario.getDepartamentoId());
        log.info("estado documento={}", metadata.getEstado());
        log.info("s3Key={}", metadata.getS3Key());
        log.info("permisos calculados={}", permisos);
        log.info("documentType={}", documentType);
        log.info("fileType={}", fileType);
        log.info("document.key={}", documentKey);
        log.info("callbackUrl={}", callbackUrl);
        log.info("sourceUrl={}", sourceUrl);
        log.info("sourcePublicAccessEnabled={}", sourcePublicAccessEnabled);
        log.info("onlyoffice.jwt-enabled={}", jwtEnabled);

        Map<String, Object> documentMap = new LinkedHashMap<>();
        documentMap.put("fileType", fileType);
        documentMap.put("key", documentKey);
        documentMap.put("title", titulo);
        documentMap.put("url", sourceUrl);

        Map<String, Object> documentPermissions = new LinkedHashMap<>();
        documentPermissions.put("edit", permisos.isPuedeEditar());
        documentPermissions.put("download", permisos.isPuedeDescargar());
        documentPermissions.put("print", permisos.isPuedeImprimir());
        documentPermissions.put("copy", permisos.isPuedeDescargar());
        documentPermissions.put("comment", permisos.isPuedeComentar());
        documentPermissions.put("review", Boolean.TRUE.equals(metadata.getAuditarCambios()));
        documentMap.put("permissions", documentPermissions);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", permisos.isPuedeEditar() ? "edit" : "view");
        editorConfig.put("callbackUrl", callbackUrl);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", usuario.getId());
        userMap.put("name", resolverNombreUsuario(usuario));
        editorConfig.put("user", userMap);

        Map<String, Object> coEditing = new LinkedHashMap<>();
        coEditing.put("mode", "fast");
        coEditing.put("change", true);
        editorConfig.put("coEditing", coEditing);

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("autosave", true);
        customization.put("forcesave", true);
        if (Boolean.TRUE.equals(metadata.getAuditarCambios())) {
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("trackChanges", true);
            review.put("reviewDisplay", "markup");
            review.put("showReviewChanges", false);
            customization.put("review", review);
            customization.put("trackChanges", true);
        }
        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("document", documentMap);
        config.put("documentType", documentType);
        config.put("editorConfig", editorConfig);
        if (jwtEnabled) {
            config.put("token", generarJwtOnlyOffice(config));
            log.info("OnlyOffice token incluido=true");
        } else {
            log.info("OnlyOffice token incluido=false");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documentServerUrl", limpiarUrlBase(documentServerPublicUrl));
        response.put("config", config);
        response.put("auditEventUrl", construirAuditEventUrl(metadata.getDocumentoId()));
        response.put("audit", construirAuditInstructions(metadata.getDocumentoId(), usuario.getId()));
        response.put("controlVersionesHabilitado", Boolean.TRUE.equals(metadata.getControlVersionesHabilitado()));
        response.put("versionActual", metadata.getVersionActual() != null ? metadata.getVersionActual() : 0);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{documentoId}/audit-event")
    public ResponseEntity<Map<String, Object>> registrarEventoOnlyOffice(
            @PathVariable String documentoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestBody Map<String, Object> body,
            HttpServletRequest servletRequest) {

        String actorUserId = resolverActorUserId(userId, adminUserId);
        Usuario usuario = usuarioRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        InstanciaPolitica instancia = obtenerInstancia(metadata);
        DocumentAuditAction accion = resolverAuditAction(body != null ? body.get("accion") : null);
        String detalle = body != null && body.get("detalle") instanceof String value ? value : detallePorDefecto(accion);

        DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                metadata, usuario, usuario.getRol(), usuario.getDepartamentoId(), instancia);
        if (!permisoSatisfecho(permisos, accion)) {
            registrarEventoDocumento(metadata, instancia, usuario, null, accion, DocumentAuditResult.DENEGADO,
                    userAgent, servletRequest, "Accion documental denegada desde OnlyOffice: " + accion);
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para " + accion.name() + " este documento colaborativo");
        }

        registrarEventoDocumento(metadata, instancia, usuario, null, accion, DocumentAuditResult.PERMITIDO,
                userAgent, servletRequest, detalle);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/{documentoId}/download")
    public ResponseEntity<byte[]> descargarDocumentoAuditado(
            @PathVariable String documentoId,
            @RequestParam(value = "format", required = false, defaultValue = "original") String format,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest) {

        String actorUserId = resolverActorUserId(userId, adminUserId);
        Usuario usuario = usuarioRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        InstanciaPolitica instancia = obtenerInstancia(metadata);

        DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                metadata, usuario, usuario.getRol(), usuario.getDepartamentoId(), instancia);
        if (!permisos.isPuedeDescargar()) {
            registrarEventoDocumento(metadata, instancia, usuario, null, DocumentAuditAction.DESCARGAR,
                    DocumentAuditResult.DENEGADO, userAgent, servletRequest, "Descarga documental denegada");
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para descargar este documento colaborativo");
        }

        String normalizedFormat = normalizarFormatoDescarga(format);
        String originalFileType = resolverFileType(metadata.getTipoDocumento());
        byte[] content;
        String outputFileType;
        if ("pdf".equals(normalizedFormat)) {
            content = convertirDocumento(metadata, originalFileType, "pdf");
            outputFileType = "pdf";
        } else {
            try {
                content = s3Service.descargarArchivo(metadata.getS3Key());
            } catch (Exception ex) {
                log.error("Error leyendo archivo colaborativo para descarga: documentoId={}, s3Key={}", documentoId, metadata.getS3Key(), ex);
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error leyendo archivo para descarga");
            }
            outputFileType = originalFileType;
        }

        registrarEventoDocumento(metadata, instancia, usuario, null, DocumentAuditAction.DESCARGAR,
                DocumentAuditResult.PERMITIDO, userAgent, servletRequest,
                "Documento descargado en formato " + outputFileType.toUpperCase(Locale.ROOT));

        String filename = resolverTitulo(metadata.getNombreDocumento(), outputFileType).replace("\"", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .contentType(MediaType.parseMediaType(resolverContentTypeDescarga(outputFileType)))
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/{documentoId}/versiones")
    public ResponseEntity<List<DocumentoVersion>> listarVersiones(
            @PathVariable String documentoId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId) {

        PermisoContext context = validarPermisoDocumento(documentoId, userId, adminUserId);
        if (!context.permisos().isPuedeLeer()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para consultar versiones de este documento");
        }
        if (!Boolean.TRUE.equals(context.metadata().getControlVersionesHabilitado())) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(versionService.listarVersiones(context.metadata()));
    }

    @GetMapping("/{documentoId}/versiones/{numeroVersion}/download")
    public ResponseEntity<byte[]> descargarVersion(
            @PathVariable String documentoId,
            @PathVariable Integer numeroVersion,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest) {

        PermisoContext context = validarPermisoDocumento(documentoId, userId, adminUserId);
        if (!context.permisos().isPuedeDescargar()) {
            registrarEventoDocumento(context.metadata(), context.instancia(), context.usuario(), null, DocumentAuditAction.DESCARGAR,
                    DocumentAuditResult.DENEGADO, userAgent, servletRequest, "Descarga de version documental denegada");
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para descargar versiones de este documento");
        }

        DocumentoVersion version = versionService.buscarVersion(context.metadata(), numeroVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version del documento no encontrada"));
        byte[] content = s3Service.descargarArchivo(version.getS3KeyVersion());
        String fileType = resolverFileTypeDesdeS3Key(version.getS3KeyVersion(), resolverFileType(context.metadata().getTipoDocumento()));
        String filename = (version.getNombreArchivo() == null || version.getNombreArchivo().isBlank())
                ? resolverTitulo(context.metadata().getNombreDocumento(), fileType)
                : version.getNombreArchivo();

        registrarEventoDocumento(context.metadata(), context.instancia(), context.usuario(), null, DocumentAuditAction.DESCARGAR,
                DocumentAuditResult.PERMITIDO, userAgent, servletRequest,
                "Version " + numeroVersion + " descargada");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replace("\"", "") + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .contentType(MediaType.parseMediaType(resolverContentTypeDescarga(fileType)))
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping("/{documentoId}/versiones/{numeroVersion}/restaurar")
    public ResponseEntity<DocumentoVersion> restaurarVersion(
            @PathVariable String documentoId,
            @PathVariable Integer numeroVersion,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId) {

        PermisoContext context = validarPermisoDocumento(documentoId, userId, adminUserId);
        if (!context.permisos().isPuedeEditar() && !context.permisos().isPuedeReemplazar()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso para restaurar versiones de este documento");
        }
        if (!Boolean.TRUE.equals(context.metadata().getControlVersionesHabilitado())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El control de versiones no esta habilitado para este documento");
        }

        DocumentoVersion version = versionService.buscarVersion(context.metadata(), numeroVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Version del documento no encontrada"));
        byte[] content = s3Service.descargarArchivo(version.getS3KeyVersion());
        String fileType = resolverFileTypeDesdeS3Key(version.getS3KeyVersion(), resolverFileType(context.metadata().getTipoDocumento()));
        s3Service.subirArchivo(context.metadata().getS3Key(), content, resolverContentTypeDescarga(fileType));

        DocumentoVersion nuevaVersion = versionService.crearVersion(
                context.metadata(),
                content,
                fileType,
                context.usuario(),
                context.usuario().getId(),
                "RESTAURACION",
                "RESTAURACION"
        );
        context.metadata().setEstado("CREADO");
        context.metadata().setFechaUltimaModificacion(LocalDateTime.now().toString());
        context.metadata().setModificadoPor(context.usuario().getId());
        context.metadata().setUltimoEventoOnlyOffice("RESTAURACION");
        context.metadata().setVersionActual(nuevaVersion.getNumeroVersion());
        metadataService.guardarMetadata(context.metadata());
        return ResponseEntity.ok(nuevaVersion);
    }

    @GetMapping("/{documentoId}/source")
    public ResponseEntity<byte[]> obtenerArchivoFuente(
            @PathVariable String documentoId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserIdHeader,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestParam(value = "userId", required = false) String userIdParam,
            @RequestParam(value = "accessToken", required = false) String accessToken,
            HttpServletRequest servletRequest) {

        log.info("GET DOCUMENTO COLABORATIVO SOURCE documentoId={}", documentoId);
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        log.info("Documento source encontrado=true documentoId={}, s3Key={}", documentoId, metadata.getS3Key());

        String actorUserId = primerValor(userIdHeader, adminUserIdHeader, userIdParam);
        if (actorUserId != null) {
            Usuario usuario = usuarioRepository.findById(actorUserId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
            InstanciaPolitica instancia = obtenerInstancia(metadata);

            DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                    metadata,
                    usuario,
                    usuario.getRol(),
                    usuario.getDepartamentoId(),
                    instancia
            );
            if (!permisos.isPuedeLeer()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "El usuario no tiene permiso de lectura para este documento colaborativo");
            }
            registrarEventoDocumento(metadata, instancia, usuario, null, DocumentAuditAction.VISUALIZAR,
                    DocumentAuditResult.PERMITIDO, userAgent, servletRequest, "Archivo fuente cargado por OnlyOffice");
            log.info("Acceso source autorizado y auditado por usuario: usuarioId={}", usuario.getId());
        } else if (sourcePublicAccessEnabled) {
            log.info("Acceso source autorizado por onlyoffice.source-public-access-enabled=true sin usuario auditable");
        } else if (accessToken != null && !accessToken.isBlank()) {
            if (!validarSourceToken(metadata, accessToken)) {
                log.warn("Source accessToken invalido para documentoId={}", documentoId);
                throw new ApiException(HttpStatus.FORBIDDEN, "Token interno de descarga invalido");
            }
            log.info("Acceso source autorizado por accessToken interno");
        } else {
            resolverActorUserIdParaSource(userIdHeader, adminUserIdHeader, userIdParam);
        }

        byte[] content;
        try {
            content = s3Service.descargarArchivo(metadata.getS3Key());
        } catch (Exception ex) {
            log.error("Error leyendo archivo colaborativo desde S3: documentoId={}, s3Key={}", documentoId, metadata.getS3Key(), ex);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error leyendo archivo desde S3");
        }

        String fileType = resolverFileType(metadata.getTipoDocumento());
        String contentType = resolverContentType(fileType);
        String filename = resolverTitulo(metadata.getNombreDocumento(), fileType).replace("\"", "");
        log.info("Source response documentoId={}, contentType={}, bytes={}", documentoId, contentType, content.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header("Accept-Ranges", "bytes")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping("/onlyoffice/callback/{documentoId}")
    public ResponseEntity<Map<String, Object>> onlyOfficeCallback(
            @PathVariable String documentoId,
            @RequestParam(value = "userId", required = false) String userIdParam,
            @RequestBody Map<String, Object> body) {

        Integer status = resolverStatus(body.get("status"));
        String downloadUrl = body.get("url") instanceof String value ? value : null;

        log.info("ONLYOFFICE CALLBACK RECIBIDO");
        log.info("documentoId={}", documentoId);
        log.info("status={}", status);
        log.info("url recibida={}", downloadUrl);

        if (status == null) {
            return callbackError(HttpStatus.BAD_REQUEST, "Callback OnlyOffice sin status");
        }
        if (status != 2 && status != 6) {
            return ResponseEntity.ok(Map.of("error", 0));
        }
        if (downloadUrl == null || downloadUrl.isBlank()) {
            return callbackError(HttpStatus.BAD_REQUEST, "Callback OnlyOffice sin URL de descarga");
        }

        DocumentoColaborativoMetadata metadata = metadataService.buscarPorDocumentoId(documentoId);
        if (metadata == null) {
            return callbackError(HttpStatus.NOT_FOUND, "Documento colaborativo no encontrado");
        }
        if (metadata.getS3Key() == null || metadata.getS3Key().isBlank()) {
            return callbackError(HttpStatus.BAD_REQUEST, "El documento colaborativo no tiene s3Key");
        }
        if (!tipoSoportado(metadata.getTipoDocumento())) {
            return callbackError(HttpStatus.BAD_REQUEST, "Tipo de documento colaborativo no soportado");
        }

        // TODO: Validar JWT de OnlyOffice cuando onlyoffice.jwt-secret se active en ambientes productivos.
        log.info("guardando en s3Key={}", metadata.getS3Key());
        byte[] contenidoActualizado;
        String fileType = resolverFileType(metadata.getTipoDocumento());
        try {
            contenidoActualizado = descargarDesdeOnlyOffice(downloadUrl);
            s3Service.subirArchivo(metadata.getS3Key(), contenidoActualizado, resolverContentType(fileType));
            log.info("resultado upload S3=OK documentoId={}, s3Key={}, bytes={}", documentoId, metadata.getS3Key(), contenidoActualizado.length);
        } catch (Exception ex) {
            log.error("resultado upload S3=ERROR documentoId={}, s3Key={}", documentoId, metadata.getS3Key(), ex);
            return callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "Error guardando archivo actualizado en S3");
        }

        try {
            metadata.setEstado("CREADO");
            metadata.setFechaUltimaModificacion(LocalDateTime.now().toString());
            String modificadoPor = resolverModificadoPor(userIdParam, body);
            metadata.setModificadoPor(modificadoPor);
            metadata.setUltimoEventoOnlyOffice(String.valueOf(status));
            if (Boolean.TRUE.equals(metadata.getControlVersionesHabilitado())) {
                Usuario usuarioVersion = modificadoPor != null ? usuarioRepository.findById(modificadoPor).orElse(null) : null;
                DocumentoVersion version = versionService.crearVersion(
                        metadata,
                        contenidoActualizado,
                        fileType,
                        usuarioVersion,
                        modificadoPor,
                        "ONLYOFFICE_CALLBACK",
                        "GUARDADO"
                );
                metadata.setVersionActual(version.getNumeroVersion());
                log.info("version creada documentoId={}, version={}, s3Key={}",
                        documentoId, version.getNumeroVersion(), version.getS3KeyVersion());
            } else {
                metadata.setVersionActual((metadata.getVersionActual() != null ? metadata.getVersionActual() : 0) + 1);
            }
            metadataService.guardarMetadata(metadata);
            registrarEdicionDesdeCallback(metadata, modificadoPor, body);
            log.info("resultado update DynamoDB=OK documentoId={}", documentoId);
        } catch (Exception ex) {
            log.error("resultado update DynamoDB=ERROR documentoId={}", documentoId, ex);
            return callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "Error actualizando metadata en DynamoDB");
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }

    private DocumentoColaborativoMetadata obtenerMetadataValida(String documentoId) {
        DocumentoColaborativoMetadata metadata = metadataService.buscarPorDocumentoId(documentoId);
        if (metadata == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Documento colaborativo no encontrado");
        }
        if (!"CREADO".equalsIgnoreCase(metadata.getEstado())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El documento colaborativo no esta en estado CREADO");
        }
        if (metadata.getS3Key() == null || metadata.getS3Key().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El documento colaborativo no tiene archivo S3 asociado");
        }
        if (!tipoSoportado(metadata.getTipoDocumento())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tipo de documento colaborativo no soportado: " + metadata.getTipoDocumento());
        }
        return metadata;
    }

    private InstanciaPolitica obtenerInstancia(DocumentoColaborativoMetadata metadata) {
        if (metadata.getTramiteId() == null || metadata.getTramiteId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo resolver tramiteId de la metadata del documento colaborativo");
        }
        return instanciaPoliticaRepository.findById(metadata.getTramiteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No se encontro la instancia del tramite"));
    }

    private PermisoContext validarPermisoDocumento(String documentoId, String userId, String adminUserId) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        Usuario usuario = usuarioRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        InstanciaPolitica instancia = obtenerInstancia(metadata);
        DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                metadata, usuario, usuario.getRol(), usuario.getDepartamentoId(), instancia);
        return new PermisoContext(metadata, instancia, usuario, permisos);
    }

    private record PermisoContext(
            DocumentoColaborativoMetadata metadata,
            InstanciaPolitica instancia,
            Usuario usuario,
            DocumentoColaborativoPermisosDto permisos
    ) {
    }

    private String construirSourceUrl(DocumentoColaborativoMetadata metadata, String userId) {
        String baseUrl = resolverCallbackBaseUrl()
                + "/api/documentos-colaborativos/"
                + metadata.getDocumentoId()
                + "/source";
        String userQueryParam = userId != null && !userId.isBlank()
                ? "userId=" + encode(userId.trim())
                : null;
        if (sourcePublicAccessEnabled || userQueryParam == null) {
            return userQueryParam == null ? baseUrl : baseUrl + "?" + userQueryParam;
        }
        return baseUrl + "?" + userQueryParam + "&accessToken=" + encode(generarSourceToken(metadata));
    }

    private String construirCallbackUrl(String documentoId, String userId) {
        String url = resolverCallbackBaseUrl()
                + "/api/documentos-colaborativos/onlyoffice/callback/"
                + documentoId;
        if (userId == null || userId.isBlank()) {
            return url;
        }
        return url + "?userId=" + encode(userId.trim());
    }

    private String construirAuditEventUrl(String documentoId) {
        return limpiarUrlBase(publicBaseUrl)
                + "/api/documentos-colaborativos/"
                + documentoId
                + "/audit-event";
    }

    private String construirSourceUrlInterna(DocumentoColaborativoMetadata metadata) {
        return resolverCallbackBaseUrl()
                + "/api/documentos-colaborativos/"
                + metadata.getDocumentoId()
                + "/source?accessToken="
                + encode(generarSourceToken(metadata));
    }

    private Map<String, Object> construirAuditInstructions(String documentoId, String userId) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("enabled", true);
        audit.put("url", construirAuditEventUrl(documentoId));
        audit.put("userId", userId);
        audit.put("headerName", "X-User-Id");
        audit.put("editAction", DocumentAuditAction.EDITAR.name());
        audit.put("downloadAction", DocumentAuditAction.DESCARGAR.name());
        audit.put("printAction", DocumentAuditAction.IMPRIMIR.name());
        audit.put("editEvent", "onDocumentStateChange");
        audit.put("downloadEvent", "onDownloadAs");
        audit.put("printEvent", "onRequestPrint");
        return audit;
    }

    private String generarJwtOnlyOffice(Map<String, Object> config) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Configuracion invalida: onlyoffice.jwt-enabled=true requiere onlyoffice.jwt-secret");
        }
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            String encodedHeader = base64Url(objectMapper.writeValueAsBytes(header));
            String encodedPayload = base64Url(objectMapper.writeValueAsBytes(config));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = base64Url(hmacSha256(signingInput, jwtSecret));
            return signingInput + "." + signature;
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo serializar la configuracion OnlyOffice para JWT");
        }
    }

    private String generarSourceToken(DocumentoColaborativoMetadata metadata) {
        String payload = sourceTokenPayload(metadata);
        return base64Url(hmacSha256(payload, resolverSourceTokenSecret()));
    }

    private boolean validarSourceToken(DocumentoColaborativoMetadata metadata, String accessToken) {
        String expected = generarSourceToken(metadata);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                accessToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String sourceTokenPayload(DocumentoColaborativoMetadata metadata) {
        return metadata.getDocumentoId() + "|" + metadata.getS3Key();
    }

    private String resolverSourceTokenSecret() {
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            return jwtSecret;
        }
        return sourceTokenSecret;
    }

    private byte[] hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo firmar la configuracion OnlyOffice");
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String resolverActorUserId(String userId, String adminUserId) {
        String actorUserId = primerValor(userId, adminUserId);
        if (actorUserId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id en los headers");
        }
        return actorUserId;
    }

    private String resolverActorUserIdParaSource(String userIdHeader, String adminUserIdHeader, String userIdParam) {
        String actorUserId = primerValor(userIdHeader, adminUserIdHeader, userIdParam);
        if (actorUserId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Falta identificacion de usuario para descargar el documento fuente");
        }
        return actorUserId;
    }

    private String primerValor(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String resolverNombreUsuario(Usuario usuario) {
        if (usuario.getNombre() != null && !usuario.getNombre().isBlank()) {
            return usuario.getNombre();
        }
        if (usuario.getCorreo() != null && !usuario.getCorreo().isBlank()) {
            return usuario.getCorreo();
        }
        return usuario.getId();
    }

    private String resolverTitulo(String nombreDocumento, String fileType) {
        String title = nombreDocumento == null || nombreDocumento.isBlank() ? "documento-colaborativo" : nombreDocumento.trim();
        String suffix = "." + fileType.toLowerCase(Locale.ROOT);
        return title.toLowerCase(Locale.ROOT).endsWith(suffix) ? title : title + suffix;
    }

    private String resolverDocumentKey(DocumentoColaborativoMetadata metadata) {
        String fileType = resolverFileType(metadata.getTipoDocumento());
        String updatedAt = (metadata.getFechaUltimaModificacion() != null && !metadata.getFechaUltimaModificacion().isBlank())
                ? metadata.getFechaUltimaModificacion()
                : ((metadata.getFechaCreacion() != null && !metadata.getFechaCreacion().isBlank()) ? metadata.getFechaCreacion() : "0");
        Integer version = metadata.getVersionActual() != null ? metadata.getVersionActual() : 0;
        
        String stamp = updatedAt + "_" + version;
        return metadata.getDocumentoId() + "_" + fileType + "_" + hashCorto(stamp);
    }

    private String hashCorto(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            return value.replaceAll("[^a-zA-Z0-9]", "");
        }
    }

    private boolean tipoSoportado(String tipo) {
        return "WORD".equalsIgnoreCase(tipo) || "EXCEL".equalsIgnoreCase(tipo) || "POWERPOINT".equalsIgnoreCase(tipo);
    }

    private String resolverDocumentType(String tipo) {
        if ("EXCEL".equalsIgnoreCase(tipo)) {
            return "cell";
        }
        if ("POWERPOINT".equalsIgnoreCase(tipo)) {
            return "slide";
        }
        return "word";
    }

    private String resolverFileType(String tipo) {
        if ("EXCEL".equalsIgnoreCase(tipo)) {
            return "xlsx";
        }
        if ("POWERPOINT".equalsIgnoreCase(tipo)) {
            return "pptx";
        }
        return "docx";
    }

    private String resolverContentType(String fileType) {
        return switch (fileType) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
    }

    private String resolverContentTypeDescarga(String fileType) {
        if ("pdf".equalsIgnoreCase(fileType)) {
            return "application/pdf";
        }
        return resolverContentType(fileType.toLowerCase(Locale.ROOT));
    }

    private String resolverFileTypeDesdeS3Key(String s3Key, String fallback) {
        if (s3Key == null) {
            return fallback;
        }
        int dot = s3Key.lastIndexOf('.');
        if (dot < 0 || dot == s3Key.length() - 1) {
            return fallback;
        }
        return s3Key.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizarFormatoDescarga(String format) {
        if (format == null || format.isBlank()) {
            return "original";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if ("pdf".equals(normalized)) {
            return "pdf";
        }
        return "original";
    }

    private Integer resolverStatus(Object rawStatus) {
        if (rawStatus instanceof Number number) {
            return number.intValue();
        }
        if (rawStatus instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolverModificadoPor(String userIdParam, Map<String, Object> body) {
        String userId = primerValor(userIdParam);
        if (userId != null) {
            return userId;
        }
        Object usersRaw = body.get("users");
        if (usersRaw instanceof List<?> users && !users.isEmpty()) {
            Object first = users.get(0);
            if (first instanceof String value && !value.isBlank()) {
                return value;
            }
            if (first instanceof Map<?, ?> map) {
                Object id = map.get("id");
                if (id instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private DocumentAuditAction resolverAuditAction(Object rawAction) {
        if (rawAction == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar accion de auditoria");
        }
        String value = rawAction.toString().trim();
        if (value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar accion de auditoria");
        }
        try {
            return DocumentAuditAction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Accion de auditoria no soportada: " + value);
        }
    }

    private boolean permisoSatisfecho(DocumentoColaborativoPermisosDto permisos, DocumentAuditAction accion) {
        if (permisos == null || accion == null) {
            return false;
        }
        return switch (accion) {
            case VISUALIZAR -> permisos.isPuedeLeer();
            case EDITAR -> permisos.isPuedeEditar();
            case DESCARGAR -> permisos.isPuedeDescargar();
            case IMPRIMIR -> permisos.isPuedeImprimir();
            case SUBIR, REEMPLAZAR -> permisos.isPuedeReemplazar();
            case ELIMINAR -> permisos.isPuedeEliminar();
            case CAMBIAR_PERMISOS, INICIAR_COLABORACION, SALIR_COLABORACION -> false;
        };
    }

    private String detallePorDefecto(DocumentAuditAction accion) {
        return switch (accion) {
            case EDITAR -> "Documento modificado en OnlyOffice";
            case DESCARGAR -> "Documento descargado desde OnlyOffice";
            case IMPRIMIR -> "Documento enviado a impresion desde OnlyOffice";
            case VISUALIZAR -> "Documento visualizado en OnlyOffice";
            default -> "Evento documental registrado desde OnlyOffice";
        };
    }

    private void registrarEdicionDesdeCallback(
            DocumentoColaborativoMetadata metadata,
            String modificadoPor,
            Map<String, Object> body
    ) {
        String editorUserId = primerValor(modificadoPor, resolverUsuarioDesdeAcciones(body));
        Usuario usuario = null;
        if (editorUserId != null) {
            usuario = usuarioRepository.findById(editorUserId).orElse(null);
        }
        InstanciaPolitica instancia = obtenerInstancia(metadata);
        registrarEventoDocumento(metadata, instancia, usuario, editorUserId, DocumentAuditAction.EDITAR,
                DocumentAuditResult.PERMITIDO, null, null, "Documento guardado por callback de OnlyOffice");
    }

    private String resolverUsuarioDesdeAcciones(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object actionsRaw = body.get("actions");
        if (actionsRaw instanceof List<?> actions) {
            for (Object actionRaw : actions) {
                if (actionRaw instanceof Map<?, ?> action) {
                    Object userId = action.get("userid");
                    if (userId != null && !userId.toString().isBlank()) {
                        return userId.toString().trim();
                    }
                }
            }
        }
        return null;
    }

    private byte[] descargarDesdeOnlyOffice(String url) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            byte[] content = restTemplate.getForObject(url, byte[].class);
            if (content == null || content.length == 0) {
                throw new IllegalStateException("OnlyOffice devolvio un archivo vacio");
            }
            return content;
        } catch (Exception firstError) {
            String fallbackUrl = construirOnlyOfficeDownloadFallbackUrl(url);
            if (fallbackUrl == null || fallbackUrl.equals(url)) {
                throw firstError;
            }
            log.warn("No se pudo descargar desde URL OnlyOffice original. Reintentando con fallback: original={}, fallback={}", url, fallbackUrl, firstError);
            byte[] content = restTemplate.getForObject(fallbackUrl, byte[].class);
            if (content == null || content.length == 0) {
                throw new IllegalStateException("OnlyOffice devolvio un archivo vacio usando fallback");
            }
            return content;
        }
    }

    private byte[] convertirDocumento(DocumentoColaborativoMetadata metadata, String inputType, String outputType) {
        String key = hashCorto(metadata.getDocumentoId() + "|" + metadata.getS3Key() + "|" + outputType + "|" + LocalDateTime.now());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("async", false);
        body.put("filetype", inputType);
        body.put("key", key);
        body.put("outputtype", outputType);
        body.put("title", resolverTitulo(metadata.getNombreDocumento(), outputType));
        body.put("url", construirSourceUrlInterna(metadata));
        if (jwtEnabled) {
            body.put("token", generarJwtOnlyOffice(body));
        }

        String converterUrl = limpiarUrlBase(documentServerUrl) + "/converter?shardkey=" + encode(key);
        RestTemplate restTemplate = new RestTemplate();
        Map<?, ?> response;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            response = restTemplate.postForObject(converterUrl, new HttpEntity<>(body, headers), Map.class);
        } catch (Exception ex) {
            String fallbackUrl = limpiarUrlBase(documentServerUrl) + "/ConvertService.ashx";
            log.warn("No se pudo convertir usando /converter. Reintentando con ConvertService.ashx: {}", converterUrl, ex);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            response = restTemplate.postForObject(fallbackUrl, new HttpEntity<>(body, headers), Map.class);
        }

        if (response == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OnlyOffice no devolvio respuesta de conversion");
        }
        Object error = response.get("error");
        if (error instanceof Number number && number.intValue() != 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OnlyOffice no pudo convertir el documento. Codigo: " + number.intValue());
        }
        Object fileUrl = response.get("fileUrl");
        if (!(fileUrl instanceof String url) || url.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "OnlyOffice no devolvio URL del archivo convertido");
        }
        return descargarDesdeOnlyOffice(url);
    }

    private String construirOnlyOfficeDownloadFallbackUrl(String originalUrl) {
        try {
            URI original = URI.create(originalUrl);
            URI base = URI.create(limpiarUrlBase(documentServerUrl));
            return new URI(
                    base.getScheme(),
                    base.getAuthority(),
                    original.getPath(),
                    original.getQuery(),
                    original.getFragment()
            ).toString();
        } catch (Exception ex) {
            log.warn("No se pudo construir fallback de descarga OnlyOffice para url={}", originalUrl, ex);
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> callbackError(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", 1);
        response.put("message", message);
        log.error("ONLYOFFICE CALLBACK ERROR status={}, message={}", status, message);
        return ResponseEntity.ok(response);
    }

    private void registrarEventoDocumento(
            DocumentoColaborativoMetadata metadata,
            InstanciaPolitica instancia,
            Usuario usuario,
            String usuarioIdFallback,
            DocumentAuditAction accion,
            DocumentAuditResult resultado,
            String userAgent,
            HttpServletRequest servletRequest,
            String detalle
    ) {
        DocumentAuditEventRequest request = new DocumentAuditEventRequest();
        request.setDocumentoId(metadata.getDocumentoId());
        request.setCampoId(metadata.getCampoFormularioId());
        request.setTramiteId(metadata.getTramiteId());
        request.setClienteId(metadata.getClienteId());
        request.setPoliticaId(instancia.getPoliticaId());
        request.setNodoId(metadata.getNodoId());
        request.setAccion(accion);
        request.setUsuarioId(usuario != null ? usuario.getId() : usuarioIdFallback);
        request.setUsuarioNombre(usuario != null ? resolverNombreUsuario(usuario) : null);
        request.setRol(usuario != null ? usuario.getRol() : null);
        request.setDepartamentoId(usuario != null ? usuario.getDepartamentoId() : null);
        request.setIp(resolverIp(servletRequest));
        request.setUserAgent(userAgent);
        request.setDetalle(detalle);
        request.setResultado(resultado);
        auditService.registrarEventoAuditoria(request);
    }

    private String resolverIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = primerValor(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded;
        }
        return primerValor(request.getRemoteAddr());
    }

    private String resolverCallbackBaseUrl() {
        String url = limpiarUrlBase(callbackBaseUrl);
        if (url.endsWith("/api")) {
            url = url.substring(0, url.length() - 4);
        }
        return url;
    }

    private String limpiarUrlBase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
