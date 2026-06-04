package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.ConfiguracionDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosLecturaSeccion;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosSeccion;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentoColaborativoMetadataService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoColaborativoMetadataService.class);

    private final DynamoDbTable<DocumentoColaborativoMetadata> metadataTable;
    private final DocumentoColaborativoS3Service s3Service;

    public DocumentoColaborativoMetadataService(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.document-repositories-table}") String tableName,
            DocumentoColaborativoS3Service s3Service) {
        this.metadataTable = enhancedClient.table(
                tableName,
                TableSchema.fromBean(DocumentoColaborativoMetadata.class));
        this.s3Service = s3Service;
    }

    public void guardarMetadata(DocumentoColaborativoMetadata metadata) {
        log.info("Guardando metadata de documento colaborativo en DynamoDB: PK={}, SK={}", metadata.getPk(), metadata.getSk());
        try {
            normalizarPermisos(metadata);
            metadataTable.putItem(metadata);
            log.info("Metadata de documento colaborativo guardada exitosamente en DynamoDB");
        } catch (Exception e) {
            log.error("Error al guardar metadata de documento colaborativo en DynamoDB para PK=" + metadata.getPk() + ", SK=" + metadata.getSk(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo persistir la metadata del documento colaborativo en DynamoDB: " + e.getMessage());
        }
    }

    public List<DocumentoColaborativoMetadata> listarPorTramite(String clienteId, String tramiteId) {
        log.info("Listando documentos colaborativos en DynamoDB: clienteId={}, tramiteId={}", clienteId, tramiteId);
        List<DocumentoColaborativoMetadata> list = new ArrayList<>();
        try {
            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue("CLIENTE#" + clienteId)
                            .sortValue("TRAMITE#" + tramiteId + "#DOC_COLAB#")
                            .build()
            );

            SdkIterable<Page<DocumentoColaborativoMetadata>> pages = metadataTable.query(r -> r.queryConditional(queryConditional));
            if (pages == null) {
                log.warn("DynamoDB devolvió null al listar documentos colaborativos: clienteId={}, tramiteId={}", clienteId, tramiteId);
                return list;
            }
            for (Page<DocumentoColaborativoMetadata> page : pages) {
                list.addAll(page.items());
            }
            list.forEach(this::normalizarPermisos);
            log.info("Se encontraron {} documentos colaborativos para clienteId={}, tramiteId={}", list.size(), clienteId, tramiteId);
        } catch (Exception e) {
            log.error("Error consultando DynamoDB al listar documentos colaborativos: clienteId={}, tramiteId={}", clienteId, tramiteId, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error consultando DynamoDB");
        }
        return list;
    }

    public DocumentoColaborativoMetadata buscarDocumento(String clienteId, String tramiteId, String documentoId) {
        log.info("Buscando documento colaborativo en DynamoDB: clienteId={}, tramiteId={}, documentoId={}", clienteId, tramiteId, documentoId);
        try {
            Key key = Key.builder()
                    .partitionValue("CLIENTE#" + clienteId)
                    .sortValue("TRAMITE#" + tramiteId + "#DOC_COLAB#" + documentoId)
                    .build();
            DocumentoColaborativoMetadata doc = metadataTable.getItem(r -> r.key(key));
            if (doc != null) {
                normalizarPermisos(doc);
                log.info("Documento colaborativo encontrado en DynamoDB: {}", documentoId);
            } else {
                log.warn("Documento colaborativo no encontrado en DynamoDB: {}", documentoId);
            }
            return doc;
        } catch (Exception e) {
            log.error("Error al obtener documento colaborativo de DynamoDB", e);
            return null;
        }
    }

    public boolean existeDocumentoPorCampo(String clienteId, String tramiteId, String campoFormularioId) {
        if (clienteId == null || tramiteId == null || campoFormularioId == null) {
            return false;
        }
        List<DocumentoColaborativoMetadata> documentos = listarPorTramite(clienteId, tramiteId);
        for (DocumentoColaborativoMetadata doc : documentos) {
            if (campoFormularioId.equalsIgnoreCase(doc.getCampoFormularioId())) {
                return true;
            }
        }
        return false;
    }

    public void actualizarConfiguracionDesdeCampo(
            DocumentoColaborativoMetadata metadata,
            ConfiguracionDocumento config
    ) {
        if (metadata == null || config == null) {
            return;
        }

        metadata.setTipoDocumento(config.getTipoDocumento() != null ? config.getTipoDocumento() : metadata.getTipoDocumento());

        DocumentoColaborativoMetadata.ConfiguracionOrigen configOrigen = metadata.getConfiguracionOrigen();
        if (configOrigen == null) {
            configOrigen = new DocumentoColaborativoMetadata.ConfiguracionOrigen();
        }
        configOrigen.setModoColaboracion(config.getModoColaboracion());
        metadata.setConfiguracionOrigen(configOrigen);

        metadata.setPermisosEdicion(toPermisosEdicion(config.getPermisosEdicion()));
        metadata.setPermisosLectura(toPermisosLectura(config.getPermisosLectura()));
        metadata.setPermisosDescarga(toPermisosAccion(config.getPermisosDescarga()));
        metadata.setPermisosImpresion(toPermisosAccion(config.getPermisosImpresion()));
        metadata.setPermisosComentarios(toPermisosAccion(config.getPermisosComentarios()));
        metadata.setPermisosReemplazo(toPermisosAccion(config.getPermisosReemplazo()));
        metadata.setPermisosEliminacion(toPermisosAccion(config.getPermisosEliminacion()));
        metadata.setPermisosCompartirInternamente(toPermisosAccion(config.getPermisosCompartirInternamente()));

        DocumentoColaborativoMetadata.PermisosAdicionales permAdic = new DocumentoColaborativoMetadata.PermisosAdicionales();
        if (config.getPermisosAdicionales() != null) {
            permAdic.setPuedeDescargar(config.getPermisosAdicionales().getPuedeDescargar());
            permAdic.setPuedeImprimir(config.getPermisosAdicionales().getPuedeImprimir());
            permAdic.setPuedeComentar(config.getPermisosAdicionales().getPuedeComentar());
            permAdic.setPuedeReemplazar(config.getPermisosAdicionales().getPuedeReemplazar());
            permAdic.setPuedeEliminar(config.getPermisosAdicionales().getPuedeEliminar());
            permAdic.setPuedeCompartirInternamente(config.getPermisosAdicionales().getPuedeCompartirInternamente());
        }
        metadata.setPermisosAdicionales(permAdic);
        guardarMetadata(metadata);
    }

    public void crearDocumentosColaborativosIniciales(InstanciaPolitica instancia, PoliticaNegocio politica) {
        System.out.println("=================================================================================");
        System.out.println("[HOOK DOCUMENTO_COLABORATIVO EJECUTADO] - INICIO DE VERIFICACIÓN");
        System.out.println("=================================================================================");
        
        if (instancia == null || politica == null || politica.getNodos() == null) {
            System.out.println("[HOOK DOCUMENTO_COLABORATIVO EJECUTADO] Omitido: Instancia, política o nodos es null.");
            System.out.println("instancia: " + (instancia == null ? "null" : "ok"));
            System.out.println("politica: " + (politica == null ? "null" : "ok"));
            System.out.println("nodos: " + (politica == null || politica.getNodos() == null ? "null" : "ok"));
            System.out.println("=================================================================================");
            return;
        }

        String clienteId = instancia.getCreadaPor();
        String tramiteId = instancia.getId();

        System.out.println("[HOOK DOCUMENTO_COLABORATIVO EJECUTADO] Parámetros iniciales:");
        System.out.println("  - Cliente ID (Creador del trámite): " + clienteId);
        System.out.println("  - Trámite ID (Instancia ID): " + tramiteId);
        System.out.println("  - Política ID: " + politica.getId());
        System.out.println("  - Total Nodos en la política: " + politica.getNodos().size());

        log.info("Iniciando búsqueda de campos DOCUMENTO_COLABORATIVO para tramiteId={}, clienteId={}", tramiteId, clienteId);

        int nodosRevisados = 0;
        int camposRevisados = 0;
        int detectados = 0;
        int creados = 0;

        for (Nodo nodo : politica.getNodos()) {
            if (nodo == null) {
                continue;
            }
            nodosRevisados++;
            System.out.println("  Revisando Nodo #" + nodosRevisados + ": ID=" + nodo.getId() + ", Nombre=" + nodo.getNombre() + ", Tipo=" + nodo.getTipo());
            
            if (nodo.getFormulario() == null) {
                System.out.println("    Formulario de este nodo es nulo o vacío.");
                continue;
            }

            System.out.println("    Campos en el formulario del nodo: " + nodo.getFormulario().size());
            for (CampoFormulario campo : nodo.getFormulario()) {
                if (campo == null) {
                    continue;
                }
                camposRevisados++;
                
                String rawType = campo.getTipoRaw();
                TipoCampo resolvedEnum = campo.getTipo();
                
                System.out.println("      Campo Formulario #" + camposRevisados + ":");
                System.out.println("        - Identificador/Campo: " + campo.getCampo());
                System.out.println("        - Etiqueta: " + campo.getEtiqueta());
                System.out.println("        - Tipo en DB (raw): " + rawType);
                System.out.println("        - Tipo resuelto (enum): " + (resolvedEnum != null ? resolvedEnum.name() : "null"));

                if (resolvedEnum != TipoCampo.DOCUMENTO_COLABORATIVO) {
                    continue;
                }

                detectados++;
                System.out.println("        >> [DETECTADO] Campo DOCUMENTO_COLABORATIVO encontrado! campo=" + campo.getCampo());

                if (existeDocumentoPorCampo(clienteId, tramiteId, campo.getCampo())) {
                    System.out.println("        >> [EXISTE] El documento colaborativo para este campo ya existe. Omitiendo creación.");
                    log.info("El documento colaborativo para el campo {} ya existe en el tramite {}. Omitiendo creacion.", campo.getCampo(), tramiteId);
                    continue;
                }

                try {
                    DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
                    String documentoId = "doc_" + UUID.randomUUID().toString();

                    metadata.setPk("CLIENTE#" + clienteId);
                    metadata.setSk("TRAMITE#" + tramiteId + "#DOC_COLAB#" + documentoId);
                    metadata.setDocumentoId(documentoId);
                    metadata.setClienteId(clienteId);
                    metadata.setTramiteId(tramiteId);
                    metadata.setCampoFormularioId(campo.getCampo());
                    metadata.setNombreDocumento(campo.getEtiqueta() != null ? campo.getEtiqueta() : campo.getCampo());
                    metadata.setDescripcion(campo.getAyuda() != null ? campo.getAyuda() : "Documento colaborativo");

                    ConfiguracionDocumento config = campo.getConfiguracionDocumento();
                    String tipoDocumento = resolverTipoDocumento(config, campo);
                    metadata.setTipoDocumento(tipoDocumento);
                    metadata.setCreadoPor("sistema");
                    metadata.setFechaCreacion(LocalDateTime.now().toString());

                    System.out.println("        >> Generando metadatos para DynamoDB: ID=" + documentoId + ", TipoDoc=" + tipoDocumento);
                    log.info("Detectado campo DOCUMENTO_COLABORATIVO: campo={}, documentoId={}, tipoDocumento={}", campo.getCampo(), documentoId, tipoDocumento);

                    // Check if supported
                    boolean isSupported = esTipoDocumentoSoportado(tipoDocumento);

                    if (!isSupported) {
                        System.out.println("        >> [ERROR] Tipo de documento no soportado: " + tipoDocumento);
                        log.warn("El tipo de documento {} no es soportado como documento colaborativo (soportados: WORD, EXCEL, POWERPOINT).", tipoDocumento);
                        metadata.setEstado("TIPO_NO_SOPORTADO");
                        metadata.setS3Key(null);
                        metadata.setFechaUltimaModificacion(LocalDateTime.now().toString());
                    } else {
                        try {
                            System.out.println("        >> Subiendo archivo vacío a S3...");
                            String s3Key = s3Service.subirDocumentoColaborativoVacio(clienteId, tramiteId, documentoId, tipoDocumento);
                            metadata.setEstado("CREADO");
                            metadata.setS3Key(s3Key);
                            metadata.setFechaUltimaModificacion(LocalDateTime.now().toString());
                            System.out.println("        >> [S3 EXITOSO] Archivo vacío creado con s3Key=" + s3Key);
                            log.info("Archivo colaborativo creado en S3: documentoId={}, s3Key={}", documentoId, s3Key);
                        } catch (Exception s3Ex) {
                            System.out.println("        >> [S3 FALLO] Excepción al subir a S3: " + s3Ex.getMessage());
                            s3Ex.printStackTrace();
                            log.error("Error al crear o subir archivo vacío a S3 para el documento colaborativo: " + documentoId, s3Ex);
                            metadata.setEstado("ERROR_CREACION_S3");
                            metadata.setS3Key(null);
                            metadata.setFechaUltimaModificacion(LocalDateTime.now().toString());
                        }
                    }

                    // Map configuracionOrigen
                    DocumentoColaborativoMetadata.ConfiguracionOrigen configOrigen = new DocumentoColaborativoMetadata.ConfiguracionOrigen();
                    configOrigen.setModoColaboracion(config != null && config.getModoColaboracion() != null ? config.getModoColaboracion() : "DEPARTAMENTO");
                    configOrigen.setPermitirDocumentoBlanco(true);
                    configOrigen.setPermitirPlantilla(false);
                    configOrigen.setPermitirSubidaBase(false);
                    metadata.setConfiguracionOrigen(configOrigen);

                    metadata.setPermisosEdicion(toPermisosEdicion(config != null ? config.getPermisosEdicion() : null));
                    metadata.setPermisosLectura(toPermisosLectura(config != null ? config.getPermisosLectura() : null));

                    metadata.setPermisosDescarga(toPermisosAccion(config != null ? config.getPermisosDescarga() : null));
                    metadata.setPermisosImpresion(toPermisosAccion(config != null ? config.getPermisosImpresion() : null));
                    metadata.setPermisosComentarios(toPermisosAccion(config != null ? config.getPermisosComentarios() : null));
                    metadata.setPermisosReemplazo(toPermisosAccion(config != null ? config.getPermisosReemplazo() : null));
                    metadata.setPermisosEliminacion(toPermisosAccion(config != null ? config.getPermisosEliminacion() : null));
                    metadata.setPermisosCompartirInternamente(toPermisosAccion(config != null ? config.getPermisosCompartirInternamente() : null));

                    // Map permisosAdicionales. Null debe evaluarse como false en permisos efectivos.
                    DocumentoColaborativoMetadata.PermisosAdicionales permAdic = new DocumentoColaborativoMetadata.PermisosAdicionales();
                    if (config != null && config.getPermisosAdicionales() != null) {
                        permAdic.setPuedeDescargar(config.getPermisosAdicionales().getPuedeDescargar());
                        permAdic.setPuedeImprimir(config.getPermisosAdicionales().getPuedeImprimir());
                        permAdic.setPuedeComentar(config.getPermisosAdicionales().getPuedeComentar());
                        permAdic.setPuedeReemplazar(config.getPermisosAdicionales().getPuedeReemplazar());
                        permAdic.setPuedeEliminar(config.getPermisosAdicionales().getPuedeEliminar());
                        permAdic.setPuedeCompartirInternamente(config.getPermisosAdicionales().getPuedeCompartirInternamente());
                    }
                    metadata.setPermisosAdicionales(permAdic);

                    System.out.println("        >> Guardando metadatos en DynamoDB...");
                    guardarMetadata(metadata);
                    System.out.println("        >> [DYNAMODB EXITOSO] Registro guardado con PK=" + metadata.getPk() + " y SK=" + metadata.getSk());
                    creados++;
                } catch (Exception e) {
                    System.out.println("        >> [DYNAMODB FALLO] Excepción al guardar en DynamoDB: " + e.getMessage());
                    e.printStackTrace();
                    log.error("Error al crear metadatos de documento colaborativo para campo " + campo.getCampo() + " en DynamoDB", e);
                }
            }
        }

        System.out.println("=================================================================================");
        System.out.println("[HOOK DOCUMENTO_COLABORATIVO EJECUTADO] - RESUMEN DE PROCESO:");
        System.out.println("  - Nodos revisados: " + nodosRevisados);
        System.out.println("  - Campos revisados en total: " + camposRevisados);
        System.out.println("  - Campos DOCUMENTO_COLABORATIVO detectados: " + detectados);
        System.out.println("  - Registros creados exitosamente: " + creados);
        System.out.println("=================================================================================");

        log.info("Proceso de creación de documentos colaborativos finalizado para tramiteId={}: detectados={}, creados={}", tramiteId, detectados, creados);
    }

    public DocumentoColaborativoMetadata buscarPorDocumentoId(String documentoId) {
        log.info("Escaneando DynamoDB para encontrar documentoId={}", documentoId);
        try {
            DocumentoColaborativoMetadata metadata = metadataTable.scan().items().stream()
                    .filter(doc -> documentoId.equals(doc.getDocumentoId()))
                    .findFirst()
                    .orElse(null);
            normalizarPermisos(metadata);
            return metadata;
        } catch (Exception e) {
            log.error("Error al escanear documento colaborativo por documentoId={}", documentoId, e);
            return null;
        }
    }

    private String resolverTipoDocumento(ConfiguracionDocumento config, CampoFormulario campo) {
        String tipoConfigurado = normalizarTexto(config != null ? config.getTipoDocumento() : null);
        if (tipoConfigurado != null) {
            String normalizado = normalizarTipoDocumento(tipoConfigurado);
            return normalizado != null ? normalizado : tipoConfigurado.toUpperCase(Locale.ROOT);
        }

        String tipoDesdeCampo = normalizarTipoDocumento(campo != null ? campo.getTipoRaw() : null);
        return tipoDesdeCampo != null ? tipoDesdeCampo : "WORD";
    }

    private boolean esTipoDocumentoSoportado(String tipoDocumento) {
        return normalizarTipoDocumento(tipoDocumento) != null;
    }

    private String normalizarTipoDocumento(String value) {
        String normalized = normalizarTexto(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_");

        return switch (normalized) {
            case "WORD", "DOC", "DOCX", "DOCUMENTO_WORD" -> "WORD";
            case "EXCEL", "XLS", "XLSX", "DOCUMENTO_EXCEL" -> "EXCEL";
            case "POWERPOINT", "PPT", "PPTX", "DOCUMENTO_POWERPOINT" -> "POWERPOINT";
            default -> null;
        };
    }

    private DocumentoColaborativoMetadata.PermisosAccion toPermisosAccion(PermisosSeccion source) {
        DocumentoColaborativoMetadata.PermisosAccion permisos = new DocumentoColaborativoMetadata.PermisosAccion();
        permisos.setDepartamentos(copiarListaPermisos(source != null ? source.getDepartamentos() : null));
        permisos.setRoles(copiarListaPermisos(source != null ? source.getRoles() : null));
        permisos.setUsuarios(copiarListaPermisos(source != null ? source.getUsuarios() : null));
        return permisos;
    }

    private DocumentoColaborativoMetadata.PermisosEdicion toPermisosEdicion(PermisosSeccion source) {
        DocumentoColaborativoMetadata.PermisosEdicion permisos = new DocumentoColaborativoMetadata.PermisosEdicion();
        permisos.setDepartamentos(copiarListaPermisos(source != null ? source.getDepartamentos() : null));
        permisos.setRoles(copiarListaPermisos(source != null ? source.getRoles() : null));
        permisos.setUsuarios(copiarListaPermisos(source != null ? source.getUsuarios() : null));
        return permisos;
    }

    private DocumentoColaborativoMetadata.PermisosLectura toPermisosLectura(PermisosLecturaSeccion source) {
        DocumentoColaborativoMetadata.PermisosLectura permisos = new DocumentoColaborativoMetadata.PermisosLectura();
        permisos.setDepartamentos(copiarListaPermisos(source != null ? source.getDepartamentos() : null));
        permisos.setRoles(copiarListaPermisos(source != null ? source.getRoles() : null));
        permisos.setUsuarios(copiarListaPermisos(source != null ? source.getUsuarios() : null));
        if (source != null) {
            permisos.setIncluirClienteIniciador(source.getIncluirClienteIniciador());
        }
        return permisos;
    }

    private void normalizarPermisos(DocumentoColaborativoMetadata metadata) {
        if (metadata == null) {
            return;
        }

        DocumentoColaborativoMetadata.PermisosEdicion permisosEdicion = metadata.getPermisosEdicion();
        if (permisosEdicion == null) {
            permisosEdicion = new DocumentoColaborativoMetadata.PermisosEdicion();
        }
        permisosEdicion.setDepartamentos(copiarListaPermisos(permisosEdicion.getDepartamentos()));
        permisosEdicion.setRoles(copiarListaPermisos(permisosEdicion.getRoles()));
        permisosEdicion.setUsuarios(copiarListaPermisos(permisosEdicion.getUsuarios()));
        metadata.setPermisosEdicion(permisosEdicion);

        DocumentoColaborativoMetadata.PermisosLectura permisosLectura = metadata.getPermisosLectura();
        if (permisosLectura == null) {
            permisosLectura = new DocumentoColaborativoMetadata.PermisosLectura();
        }
        permisosLectura.setDepartamentos(copiarListaPermisos(permisosLectura.getDepartamentos()));
        permisosLectura.setRoles(copiarListaPermisos(permisosLectura.getRoles()));
        permisosLectura.setUsuarios(copiarListaPermisos(permisosLectura.getUsuarios()));
        permisosLectura.setIncluirClienteIniciador(Boolean.TRUE.equals(permisosLectura.getIncluirClienteIniciador()));
        metadata.setPermisosLectura(permisosLectura);

        metadata.setPermisosDescarga(normalizarPermisosAccion(metadata.getPermisosDescarga()));
        metadata.setPermisosImpresion(normalizarPermisosAccion(metadata.getPermisosImpresion()));
        metadata.setPermisosComentarios(normalizarPermisosAccion(metadata.getPermisosComentarios()));
        metadata.setPermisosReemplazo(normalizarPermisosAccion(metadata.getPermisosReemplazo()));
        metadata.setPermisosEliminacion(normalizarPermisosAccion(metadata.getPermisosEliminacion()));
        metadata.setPermisosCompartirInternamente(normalizarPermisosAccion(metadata.getPermisosCompartirInternamente()));
    }

    private DocumentoColaborativoMetadata.PermisosAccion normalizarPermisosAccion(
            DocumentoColaborativoMetadata.PermisosAccion permisos
    ) {
        if (permisos == null) {
            permisos = new DocumentoColaborativoMetadata.PermisosAccion();
        }
        permisos.setDepartamentos(copiarListaPermisos(permisos.getDepartamentos()));
        permisos.setRoles(copiarListaPermisos(permisos.getRoles()));
        permisos.setUsuarios(copiarListaPermisos(permisos.getUsuarios()));
        return permisos;
    }

    private List<String> copiarListaPermisos(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.trim().isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizarTexto(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
