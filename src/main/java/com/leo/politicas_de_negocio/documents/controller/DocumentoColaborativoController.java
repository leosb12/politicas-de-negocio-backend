package com.leo.politicas_de_negocio.documents.controller;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoPermisoService;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentoColaborativoController {

    private static final Logger log = LoggerFactory.getLogger(DocumentoColaborativoController.class);

    private final InstanciaPoliticaService instanciaPoliticaService;
    private final DocumentoColaborativoMetadataService metadataService;
    private final DocumentoColaborativoPermisoService permisoService;
    private final TareaActividadRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/tramites/{tramiteId}/documentos-colaborativos")
    public ResponseEntity<List<DocumentoColaborativoMetadata>> listarDocumentosColaborativos(
            @PathVariable String tramiteId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId) {

        log.info("GET DOCUMENTOS COLABORATIVOS");
        log.info("tramiteId recibido={}", tramiteId);
        log.info("userId recibido={}, adminUserId recibido={}", userId, adminUserId);

        try {
            String actorUserId = resolverActorUserId(userId, adminUserId);
            log.info("actorUserId resuelto={}", actorUserId);

            InstanciaPolitica instancia = instanciaPoliticaService
                    .obtenerInstanciaParaDocumentoColaborativo(tramiteId, actorUserId);
            log.info("resultado de buscar instancia=encontrada");

            Usuario actor = buscarUsuarioActivo(actorUserId);
            return listarDocumentosDeInstancia(instancia, tramiteId, actor);
        } catch (ApiException e) {
            log.error("GET DOCUMENTOS COLABORATIVOS fallo controlado: status={}, message={}",
                    e.getStatus(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("GET DOCUMENTOS COLABORATIVOS error completo", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error consultando documentos colaborativos");
        }
    }

    @GetMapping("/tareas/{tareaId}/documentos-colaborativos")
    public ResponseEntity<List<DocumentoColaborativoMetadata>> listarDocumentosColaborativosPorTarea(
            @PathVariable String tareaId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId) {

        log.info("GET DOCUMENTOS COLABORATIVOS POR TAREA");
        log.info("tareaId recibido={}", tareaId);
        log.info("userId recibido={}, adminUserId recibido={}", userId, adminUserId);

        try {
            String actorUserId = resolverActorUserId(userId, adminUserId);
            log.info("actorUserId resuelto={}", actorUserId);

            String id = normalizar(tareaId);
            if (id == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id de tarea");
            }

            TareaActividad tarea = tareaRepository.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "No se encontró la tarea: " + id));
            log.info("tarea encontrada: tareaId={}, instanciaId={}", tarea.getId(), tarea.getInstanciaId());

            InstanciaPolitica instancia = instanciaPoliticaService
                    .obtenerInstanciaParaDocumentoColaborativo(tarea.getInstanciaId(), actorUserId);
            log.info("resultado de buscar instancia=encontrada");

            Usuario actor = buscarUsuarioActivo(actorUserId);
            return listarDocumentosDeInstancia(instancia, tarea.getInstanciaId(), actor);
        } catch (ApiException e) {
            log.error("GET DOCUMENTOS COLABORATIVOS POR TAREA fallo controlado: status={}, message={}",
                    e.getStatus(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("GET DOCUMENTOS COLABORATIVOS POR TAREA error completo", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error consultando documentos colaborativos");
        }
    }

    private ResponseEntity<List<DocumentoColaborativoMetadata>> listarDocumentosDeInstancia(
            InstanciaPolitica instancia,
            String idRecibido,
            Usuario actor
    ) {
        if (instancia == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró la instancia del trámite");
        }

        log.info("instancia.id={}", instancia.getId());
        log.info("instancia.creadaPor={}", instancia.getCreadaPor());

        String clienteId = instancia.getCreadaPor();
        if (clienteId == null || clienteId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo resolver clienteId de la instancia");
        }
        log.info("clienteId usado={}", clienteId);

        String tramiteId = instancia.getId();
        log.info("tramiteId usado para DynamoDB={}, id recibido={}", tramiteId, idRecibido);

        List<DocumentoColaborativoMetadata> documentos = metadataService.listarPorTramite(clienteId, tramiteId);
        if (documentos.isEmpty()) {
            log.info("No hay metadata colaborativa para tramite={}. Intentando inicializacion diferida.", tramiteId);
            instanciaPoliticaService.asegurarDocumentosColaborativosIniciales(instancia);
            documentos = metadataService.listarPorTramite(clienteId, tramiteId);
        }
        log.info("cantidad de documentos encontrados en DynamoDB={}", documentos.size());

        List<TareaActividad> tareas = tareaRepository.findByInstanciaIdOrderByFechaCreacionAsc(tramiteId);

        List<DocumentoColaborativoMetadata> visibles = documentos.stream()
                .map(documento -> prepararDocumentoVisible(documento, actor, instancia, tareas))
                .filter(Objects::nonNull)
                .toList();
        log.info("cantidad de documentos colaborativos visibles para actor={} => {}",
                actor != null ? actor.getId() : null,
                visibles.size());

        return ResponseEntity.ok(visibles);
    }

    private DocumentoColaborativoMetadata prepararDocumentoVisible(
            DocumentoColaborativoMetadata documento,
            Usuario actor,
            InstanciaPolitica instancia,
            List<TareaActividad> tareas
    ) {
        DocumentoColaborativoPermisosDto permisos = permisoService.evaluarPermisos(
                documento,
                actor,
                actor != null ? actor.getRol() : null,
                actor != null ? actor.getDepartamentoId() : null,
                instancia
        );
        if (!permisos.isPuedeLeer()) {
            return null;
        }
        documento.setPermisosUsuario(permisos);

        if (documento.getCampoFormularioId() != null && tareas != null) {
            for (TareaActividad tarea : tareas) {
                if (tarea.getFormularioDefinicion() == null) {
                    continue;
                }
                boolean found = tarea.getFormularioDefinicion().stream()
                        .anyMatch(campo -> documento.getCampoFormularioId().equals(campo.getCampo()));
                if (found) {
                    documento.setNodoId(tarea.getNodoId());
                    documento.setTareaId(tarea.getId());
                    break;
                }
            }
        }

        if (documento.getTareaId() == null && documento.getNodoId() != null && tareas != null) {
            TareaActividad ultimaTarea = null;
            for (TareaActividad t : tareas) {
                if (documento.getNodoId().equals(t.getNodoId())) {
                    ultimaTarea = t;
                }
            }
            if (ultimaTarea != null) {
                documento.setTareaId(ultimaTarea.getId());
            }
        }

        return documento;
    }

    private Usuario buscarUsuarioActivo(String actorUserId) {
        return usuarioRepository.findByIdAndActivo(actorUserId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado o inactivo"));
    }

    private String resolverActorUserId(String userId, String adminUserId) {
        String normalizadoUser = normalizar(userId);
        if (normalizadoUser != null) {
            return normalizadoUser;
        }
        String normalizadoAdmin = normalizar(adminUserId);
        if (normalizadoAdmin != null) {
            return normalizadoAdmin;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id en los headers");
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
