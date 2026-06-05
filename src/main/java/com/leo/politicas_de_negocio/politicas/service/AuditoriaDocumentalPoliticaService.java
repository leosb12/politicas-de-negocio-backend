package com.leo.politicas_de_negocio.politicas.service;

import com.leo.politicas_de_negocio.archivos.model.ArchivoAdjunto;
import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import com.leo.politicas_de_negocio.archivos.repository.ArchivoAdjuntoRepository;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.model.DocumentoVersion;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.service.DocumentoVersionService;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.dto.AuditoriaDocumentalPoliticaResponse;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditoriaDocumentalPoliticaService {

    private final PoliticaNegocioRepository politicaRepository;
    private final InstanciaPoliticaRepository instanciaRepository;
    private final TareaActividadRepository tareaRepository;
    private final ArchivoAdjuntoRepository archivoRepository;
    private final DocumentoColaborativoMetadataService documentoColaborativoMetadataService;
    private final DocumentoVersionService documentoVersionService;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaDocumentalPoliticaResponse obtenerAuditoriaDocumental(String adminUserId, String politicaId) {
        assertAdmin(adminUserId);
        String idPolitica = normalizar(politicaId);
        if (idPolitica == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id de la politica");
        }
        if (!politicaRepository.existsById(idPolitica)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + idPolitica);
        }

        List<TareaActividad> tareas = tareaRepository.findByPoliticaIdOrderByFechaCreacionDesc(idPolitica);
        Map<String, TareaActividad> tareasPorId = tareas.stream()
                .filter(tarea -> normalizar(tarea.getId()) != null)
                .collect(Collectors.toMap(TareaActividad::getId, tarea -> tarea, (a, b) -> a));

        Map<String, List<AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse>> documentosPorTarea = new HashMap<>();
        agregarArchivosAdjuntos(idPolitica, tareasPorId, documentosPorTarea);
        agregarDocumentosColaborativos(idPolitica, tareas, documentosPorTarea);

        Set<String> usuariosIds = new HashSet<>();
        tareas.forEach(tarea -> {
            if (normalizar(tarea.getAsignadoA()) != null) {
                usuariosIds.add(tarea.getAsignadoA());
            }
        });
        documentosPorTarea.values().stream()
                .flatMap(List::stream)
                .map(AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse::getSubidoOCreadoPor)
                .map(this::normalizar)
                .filter(Objects::nonNull)
                .forEach(usuariosIds::add);
        Map<String, String> usuariosPorId = nombresUsuariosPorId(usuariosIds);

        List<AuditoriaDocumentalPoliticaResponse.TareaDocumentoResponse> tareasResponse = tareas.stream()
                .map(tarea -> toTareaResponse(tarea, documentosPorTarea.getOrDefault(tarea.getId(), List.of()), usuariosPorId))
                .filter(tarea -> tarea.getTotalDocumentos() > 0)
                .sorted(Comparator.comparing(
                        AuditoriaDocumentalPoliticaResponse.TareaDocumentoResponse::getFechaCreacion,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        int totalDocumentos = tareasResponse.stream()
                .mapToInt(AuditoriaDocumentalPoliticaResponse.TareaDocumentoResponse::getTotalDocumentos)
                .sum();

        return AuditoriaDocumentalPoliticaResponse.builder()
                .politicaId(idPolitica)
                .totalTareas(tareasResponse.size())
                .totalDocumentos(totalDocumentos)
                .tareas(tareasResponse)
                .build();
    }

    public List<DocumentoVersion> listarVersionesDocumento(String adminUserId, String politicaId, String documentoId) {
        assertAdmin(adminUserId);
        String idPolitica = normalizar(politicaId);
        String idDocumento = normalizar(documentoId);
        if (idPolitica == null || idDocumento == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar politica y documento");
        }
        if (!politicaRepository.existsById(idPolitica)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + idPolitica);
        }

        DocumentoColaborativoMetadata metadata = documentoColaborativoMetadataService.buscarPorDocumentoId(idDocumento);
        if (metadata == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Documento colaborativo no encontrado");
        }

        InstanciaPolitica instancia = instanciaRepository.findById(metadata.getTramiteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No se encontro la instancia del tramite"));
        if (!idPolitica.equals(instancia.getPoliticaId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El documento no pertenece a esta politica");
        }

        return documentoVersionService.listarVersiones(metadata);
    }

    private void agregarArchivosAdjuntos(
            String politicaId,
            Map<String, TareaActividad> tareasPorId,
            Map<String, List<AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse>> documentosPorTarea
    ) {
        archivoRepository.findByPoliticaIdAndEstadoOrderByFechaSubidaDesc(politicaId, EstadoArchivo.ACTIVO)
                .stream()
                .filter(archivo -> normalizar(archivo.getTareaId()) != null)
                .filter(archivo -> tareasPorId.containsKey(archivo.getTareaId()))
                .forEach(archivo -> documentosPorTarea
                        .computeIfAbsent(archivo.getTareaId(), ignored -> new ArrayList<>())
                        .add(toArchivoResponse(archivo)));
    }

    private void agregarDocumentosColaborativos(
            String politicaId,
            List<TareaActividad> tareas,
            Map<String, List<AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse>> documentosPorTarea
    ) {
        List<InstanciaPolitica> instancias = instanciaRepository.findByPoliticaIdOrderByFechaCreacionDesc(politicaId);
        Map<String, List<TareaActividad>> tareasPorInstancia = tareas.stream()
                .filter(tarea -> normalizar(tarea.getInstanciaId()) != null)
                .collect(Collectors.groupingBy(TareaActividad::getInstanciaId));

        for (InstanciaPolitica instancia : instancias) {
            String clienteId = normalizar(instancia.getCreadaPor());
            String tramiteId = normalizar(instancia.getId());
            if (clienteId == null || tramiteId == null) {
                continue;
            }

            List<DocumentoColaborativoMetadata> documentos;
            try {
                documentos = documentoColaborativoMetadataService.listarPorTramite(clienteId, tramiteId);
            } catch (Exception ignored) {
                continue;
            }

            for (DocumentoColaborativoMetadata documento : documentos) {
                TareaActividad tarea = resolverTareaDocumentoColaborativo(documento, tareasPorInstancia.getOrDefault(tramiteId, List.of()));
                if (tarea == null || normalizar(tarea.getId()) == null) {
                    continue;
                }
                documentosPorTarea
                        .computeIfAbsent(tarea.getId(), ignored -> new ArrayList<>())
                        .add(toDocumentoColaborativoResponse(documento));
            }
        }
    }

    private TareaActividad resolverTareaDocumentoColaborativo(
            DocumentoColaborativoMetadata documento,
            List<TareaActividad> tareas
    ) {
        String tareaId = normalizar(documento.getTareaId());
        if (tareaId != null) {
            for (TareaActividad tarea : tareas) {
                if (tareaId.equals(tarea.getId())) {
                    return tarea;
                }
            }
        }

        String nodoId = normalizar(documento.getNodoId());
        if (nodoId != null) {
            for (TareaActividad tarea : tareas) {
                if (nodoId.equals(tarea.getNodoId())) {
                    return tarea;
                }
            }
        }

        String campoId = normalizar(documento.getCampoFormularioId());
        if (campoId == null) {
            return null;
        }

        for (TareaActividad tarea : tareas) {
            if (tarea.getFormularioDefinicion() == null) {
                continue;
            }
            for (CampoFormulario campo : tarea.getFormularioDefinicion()) {
                if (campoId.equals(normalizar(campo.getCampo()))
                        && campo.getTipo() == TipoCampo.DOCUMENTO_COLABORATIVO) {
                    return tarea;
                }
            }
        }

        return null;
    }

    private AuditoriaDocumentalPoliticaResponse.TareaDocumentoResponse toTareaResponse(
            TareaActividad tarea,
            List<AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse> documentos,
            Map<String, String> usuariosPorId
    ) {
        List<AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse> documentosConNombres = documentos.stream()
                .map(documento -> withNombreActor(documento, usuariosPorId))
                .sorted(Comparator.comparing(
                        AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse::getFecha,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        return AuditoriaDocumentalPoliticaResponse.TareaDocumentoResponse.builder()
                .tareaId(tarea.getId())
                .instanciaId(tarea.getInstanciaId())
                .nodoId(tarea.getNodoId())
                .nombreNodo(tarea.getNombreNodo())
                .estadoTarea(tarea.getEstadoTarea())
                .fechaCreacion(tarea.getFechaCreacion())
                .fechaInicio(tarea.getFechaInicio())
                .fechaFin(tarea.getFechaFin())
                .asignadoA(tarea.getAsignadoA())
                .asignadoANombre(usuariosPorId.getOrDefault(tarea.getAsignadoA(), tarea.getAsignadoA()))
                .totalDocumentos(documentosConNombres.size())
                .documentos(documentosConNombres)
                .build();
    }

    private AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse toArchivoResponse(ArchivoAdjunto archivo) {
        return AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse.builder()
                .id(archivo.getId())
                .tipoOrigen("ARCHIVO")
                .nombre(archivo.getNombreOriginal())
                .campoId(archivo.getCampoId())
                .contentType(archivo.getContentType())
                .extension(archivo.getExtension())
                .tamanoBytes(archivo.getTamanoBytes())
                .estado(archivo.getEstado() != null ? archivo.getEstado().name() : null)
                .subidoOCreadoPor(archivo.getSubidoPor())
                .fecha(archivo.getFechaSubida())
                .build();
    }

    private AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse toDocumentoColaborativoResponse(
            DocumentoColaborativoMetadata documento
    ) {
        return AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse.builder()
                .id(documento.getDocumentoId())
                .tipoOrigen("DOCUMENTO_COLABORATIVO")
                .nombre(documento.getNombreDocumento())
                .campoId(documento.getCampoFormularioId())
                .contentType(documento.getTipoDocumento())
                .extension(documento.getTipoDocumento())
                .estado(documento.getEstado())
                .subidoOCreadoPor(normalizar(documento.getModificadoPor()) != null ? documento.getModificadoPor() : documento.getCreadoPor())
                .fecha(parseLocalDateTime(
                        normalizar(documento.getFechaUltimaModificacion()) != null
                                ? documento.getFechaUltimaModificacion()
                                : documento.getFechaCreacion()
                ))
                .build();
    }

    private AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse withNombreActor(
            AuditoriaDocumentalPoliticaResponse.DocumentoAuditoriaResponse documento,
            Map<String, String> usuariosPorId
    ) {
        documento.setSubidoOCreadoPorNombre(usuariosPorId.getOrDefault(
                documento.getSubidoOCreadoPor(),
                documento.getSubidoOCreadoPor()
        ));
        return documento;
    }

    private Map<String, String> nombresUsuariosPorId(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        return usuarioRepository.findAllById(ids).stream()
                .filter(usuario -> normalizar(usuario.getId()) != null)
                .collect(Collectors.toMap(
                        Usuario::getId,
                        usuario -> normalizar(usuario.getNombre()) != null ? usuario.getNombre() : usuario.getId(),
                        (a, b) -> a
                ));
    }

    private Usuario assertAdmin(String adminUserId) {
        String id = normalizar(adminUserId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }
        Usuario admin = usuarioRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));
        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede realizar esta accion");
        }
        return admin;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        String normalized = normalizar(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
