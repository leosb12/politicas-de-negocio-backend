package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.dto.S3UploadResult;
import com.leo.politicas_de_negocio.documents.model.DocumentoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentalService.class);
    private static final String ORIGEN_CARGA_DEFAULT = "WEB";

    private final InstanciaPoliticaRepository instanciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioRepository politicaRepository;
    private final DocumentoS3Service s3Service;
    private final DocumentoMetadataService metadataService;
    private final DocumentRepositoryService repositoryService;

    public DocumentoMetadata subirDocumento(String actorUserId, String tramiteId, MultipartFile file, String origenCarga) {
        return subirDocumento(actorUserId, tramiteId, file, origenCarga, null);
    }

    public DocumentoMetadata subirDocumento(
            String actorUserId,
            String tramiteId,
            MultipartFile file,
            String origenCarga,
            String campoFormularioId
    ) {
        log.info("Iniciando flujo documental: actorUserId={}, tramiteId={}", actorUserId, tramiteId);

        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe proporcionar un archivo válido para subir");
        }

        // 1. Obtener datos del tramite
        InstanciaPolitica tramite = instanciaRepository.findById(tramiteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El trámite (instancia de política) con ID: " + tramiteId + " no existe"));

        // 2. Obtener el cliente asociado al tramite (creadaPor)
        String clienteId = tramite.getCreadaPor();
        if (clienteId == null || clienteId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El trámite no tiene un cliente asociado");
        }

        Usuario cliente = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cliente asociado al trámite (" + clienteId + ") no fue encontrado"));

        String clienteNombre = cliente.getNombre();

        // 3. Obtener el usuario autenticado que sube el archivo
        Usuario actor = usuarioRepository.findById(actorUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario autenticado con ID: " + actorUserId + " no existe"));

        String subidoPorUsuarioId = actor.getId();
        String subidoPorNombre = actor.getNombre();
        String subidoPorRol = actor.getRol();

        // 4. Obtener nombre del tramite / politica
        String tramiteNombre = "Trámite de política";
        String tramiteCodigo = tramite.getCodigoTramite();
        PoliticaNegocio politica = null;
        if (tramite.getPoliticaId() != null) {
            politica = politicaRepository.findById(tramite.getPoliticaId()).orElse(null);
            if (politica != null) {
                tramiteNombre = politica.getNombre();
            }
        }
        String campoNormalizado = normalizarTexto(campoFormularioId);
        boolean esRequisitoInicial = esCampoArchivoRequisitoInicial(politica, campoNormalizado);

        // 5. Obtener o crear RepositoryId para el cliente
        String repositoryId = repositoryService.obtenerORegistrarRepositoryId(clienteId, tramite.getId(), tramite.getPoliticaId());

        // 6. Generar archivoId unico
        String archivoId = "doc_" + UUID.randomUUID().toString();

        // 7. Subir a S3
        S3UploadResult s3Result = s3Service.subirArchivo(clienteId, tramite.getId(), archivoId, file);

        // 8. Crear y guardar metadata en DynamoDB
        DocumentoMetadata metadata = new DocumentoMetadata();
        metadata.setPk("CLIENTE#" + clienteId);
        metadata.setSk("TRAMITE#" + tramite.getId() + "#ARCHIVO#" + archivoId);
        metadata.setRepositoryId(repositoryId);
        metadata.setClienteId(clienteId);
        metadata.setClienteNombre(clienteNombre);
        metadata.setTramiteId(tramite.getId());
        metadata.setTramiteNombre(tramiteNombre);
        metadata.setTramiteCodigo(tramiteCodigo);
        metadata.setArchivoId(archivoId);
        metadata.setCampoFormularioId(campoNormalizado);
        metadata.setCategoriaDocumento(esRequisitoInicial
                ? DocumentoMetadataService.CATEGORIA_REQUISITO_INICIAL
                : "TRAMITE");
        metadata.setNombreArchivoOriginal(file.getOriginalFilename());
        metadata.setNombreArchivoSanitizado(s3Result.getNombreArchivoSanitizado());
        metadata.setTipoArchivo(file.getContentType());
        metadata.setExtension(extraerExtension(file.getOriginalFilename()));
        metadata.setTamanoBytes(file.getSize());
        metadata.setS3Bucket(s3Result.getBucket());
        metadata.setS3Key(s3Result.getS3Key());
        metadata.setS3Uri(s3Result.getS3Uri());
        metadata.setS3Url(s3Result.getS3Url());
        metadata.setSubidoPorUsuarioId(subidoPorUsuarioId);
        metadata.setSubidoPorNombre(subidoPorNombre);
        metadata.setSubidoPorRol(subidoPorRol);
        metadata.setFechaSubida(Instant.now().toString());
        metadata.setEstadoDocumento("ACTIVO");
        metadata.setVersion(1);
        metadata.setOrigenCarga(normalizarOrigenCarga(origenCarga));
        metadata.setChecksum(s3Result.getETag());

        try {
            metadataService.guardarMetadata(metadata);
            log.info("Flujo documental finalizado con éxito: archivoId={}", archivoId);
            return metadata;
        } catch (Exception e) {
            log.error("Fallo al guardar metadata en DynamoDB. Realizando rollback del archivo en S3.", e);
            s3Service.eliminarArchivo(s3Result.getS3Key());
            throw e;
        }
    }

    private String extraerExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean esCampoArchivoRequisitoInicial(PoliticaNegocio politica, String campoFormularioId) {
        if (politica == null || campoFormularioId == null || politica.getRequisitosIniciales() == null) {
            return false;
        }

        for (CampoFormulario campo : politica.getRequisitosIniciales()) {
            if (campo == null || !campoFormularioId.equals(normalizarTexto(campo.getCampo()))) {
                continue;
            }
            return campo.getTipo() == TipoCampo.ARCHIVO;
        }
        return false;
    }

    private String normalizarOrigenCarga(String origenCarga) {
        String normalized = normalizarTexto(origenCarga);
        return normalized != null ? normalized.toUpperCase() : ORIGEN_CARGA_DEFAULT;
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
