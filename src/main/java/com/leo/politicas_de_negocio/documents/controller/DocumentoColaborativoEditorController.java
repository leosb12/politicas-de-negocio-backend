package com.leo.politicas_de_negocio.documents.controller;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoPermisoService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoS3Service;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
    private final UsuarioRepository usuarioRepository;
    private final InstanciaPoliticaRepository instanciaPoliticaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String sourceTokenSecret = UUID.randomUUID().toString();

    @Value("${onlyoffice.document-server-url}")
    private String documentServerUrl;

    @Value("${onlyoffice.callback-base-url}")
    private String callbackBaseUrl;

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
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId) {

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

        String fileType = resolverFileType(metadata.getTipoDocumento());
        String documentType = resolverDocumentType(metadata.getTipoDocumento());
        String documentKey = resolverDocumentKey(metadata);
        String titulo = resolverTitulo(metadata.getNombreDocumento(), fileType);
        String sourceUrl = construirSourceUrl(metadata);
        String callbackUrl = construirCallbackUrl(metadata.getDocumentoId());
        String serverUrl = limpiarUrlBase(documentServerUrl);

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
            "        comment:" + permisos.isPuedeComentar() +
            "      }" +
            "    }," +
            "    documentType:'" + documentType + "'," +
            "    editorConfig:{" +
            "      mode:'" + editorMode + "'," +
            "      callbackUrl:'" + callbackUrl + "'," +
            "      user:{id:'" + usuario.getId() + "',name:'" + resolverNombreUsuario(usuario).replace("'", "\\'") + "'}," +
            "      coEditing:{mode:'fast',change:true}," +
            "      customization:{autosave:true,forcesave:false,mobile:true,uiTheme:'theme-dark'}" +
            "    }" +
            "  };" +
            (jwtEnabled ? "  config.token='" + generarJwtOnlyOffice(buildConfigMap(fileType, documentKey, titulo, sourceUrl, permisos, documentType, editorMode, callbackUrl, usuario)) + "';" : "") +
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
            String editorMode, String callbackUrl, Usuario usuario) {
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
        documentMap.put("permissions", docPerms);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", editorMode);
        editorConfig.put("callbackUrl", callbackUrl);
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", usuario.getId());
        userMap.put("name", resolverNombreUsuario(usuario));
        editorConfig.put("user", userMap);

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
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId) {

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

        String fileType = resolverFileType(metadata.getTipoDocumento());
        String documentType = resolverDocumentType(metadata.getTipoDocumento());
        String documentKey = resolverDocumentKey(metadata);
        String titulo = resolverTitulo(metadata.getNombreDocumento(), fileType);
        String sourceUrl = construirSourceUrl(metadata);
        String callbackUrl = construirCallbackUrl(metadata.getDocumentoId());

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
        response.put("documentServerUrl", limpiarUrlBase(documentServerUrl));
        response.put("config", config);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentoId}/source")
    public ResponseEntity<byte[]> obtenerArchivoFuente(
            @PathVariable String documentoId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserIdHeader,
            @RequestParam(value = "userId", required = false) String userIdParam,
            @RequestParam(value = "accessToken", required = false) String accessToken) {

        log.info("GET DOCUMENTO COLABORATIVO SOURCE documentoId={}", documentoId);
        DocumentoColaborativoMetadata metadata = obtenerMetadataValida(documentoId);
        log.info("Documento source encontrado=true documentoId={}, s3Key={}", documentoId, metadata.getS3Key());

        if (sourcePublicAccessEnabled) {
            log.info("Acceso source autorizado por onlyoffice.source-public-access-enabled=true");
        } else if (accessToken != null && !accessToken.isBlank()) {
            if (!validarSourceToken(metadata, accessToken)) {
                log.warn("Source accessToken invalido para documentoId={}", documentoId);
                throw new ApiException(HttpStatus.FORBIDDEN, "Token interno de descarga invalido");
            }
            log.info("Acceso source autorizado por accessToken interno");
        } else {
            String actorUserId = resolverActorUserIdParaSource(userIdHeader, adminUserIdHeader, userIdParam);
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
            log.info("Acceso source autorizado por permisos de usuario: usuarioId={}", usuario.getId());
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
        try {
            byte[] contenidoActualizado = descargarDesdeOnlyOffice(downloadUrl);
            String fileType = resolverFileType(metadata.getTipoDocumento());
            s3Service.subirArchivo(metadata.getS3Key(), contenidoActualizado, resolverContentType(fileType));
            log.info("resultado upload S3=OK documentoId={}, s3Key={}, bytes={}", documentoId, metadata.getS3Key(), contenidoActualizado.length);
        } catch (Exception ex) {
            log.error("resultado upload S3=ERROR documentoId={}, s3Key={}", documentoId, metadata.getS3Key(), ex);
            return callbackError(HttpStatus.INTERNAL_SERVER_ERROR, "Error guardando archivo actualizado en S3");
        }

        try {
            metadata.setEstado("CREADO");
            metadata.setFechaUltimaModificacion(LocalDateTime.now().toString());
            metadata.setModificadoPor(resolverModificadoPor(userIdParam, body));
            metadata.setUltimoEventoOnlyOffice(String.valueOf(status));
            metadataService.guardarMetadata(metadata);
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

    private String construirSourceUrl(DocumentoColaborativoMetadata metadata) {
        String baseUrl = limpiarUrlBase(callbackBaseUrl)
                + "/api/documentos-colaborativos/"
                + metadata.getDocumentoId()
                + "/source";
        if (sourcePublicAccessEnabled) {
            return baseUrl;
        }
        return baseUrl + "?accessToken=" + encode(generarSourceToken(metadata));
    }

    private String construirCallbackUrl(String documentoId) {
        return limpiarUrlBase(callbackBaseUrl)
                + "/api/documentos-colaborativos/onlyoffice/callback/"
                + documentoId;
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
        String version = metadata.getFechaUltimaModificacion() != null
                ? metadata.getFechaUltimaModificacion()
                : metadata.getFechaCreacion();
        if (version == null || version.isBlank()) {
            return metadata.getDocumentoId();
        }
        return metadata.getDocumentoId() + "_" + hashCorto(version);
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
