package com.leo.politicas_de_negocio.documents.permissions.service;

import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionConfigRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionConfigResponse;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentAuditConfig;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionConfig;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionRule;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionScope;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionSet;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentCategory;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentConfidentialityLevel;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentFileType;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentPermissionAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import com.leo.politicas_de_negocio.documents.permissions.repository.DocumentPermissionConfigRepository;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class DocumentPermissionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPermissionService.class);
    public static final String CLIENTE_INICIADOR_SUJETO_ID = "__CLIENTE_INICIADOR_TRAMITE__";

    private final DocumentPermissionConfigRepository repository;

    public DocumentPermissionConfigResponse crearConfiguracionPermisos(
            String actorUserId,
            DocumentPermissionConfigRequest request
    ) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar la configuracion de permisos documentales");
        }

        String campoId = normalizar(request.getCampoId());
        if (campoId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar campoId para el campo documental");
        }
        if (repository.existsByCampoIdAndActivoTrue(campoId)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Ya existe una configuracion activa de permisos documentales para el campo " + campoId);
        }

        LocalDateTime now = LocalDateTime.now();
        String actor = resolverActor(actorUserId, request.getCreadoPor());

        DocumentPermissionConfig config = construirConfigBase(request);
        config.setCampoId(campoId);
        config.setTipoCampo(TipoCampo.ARCHIVO);
        config.setActivo(request.getActivo() == null || Boolean.TRUE.equals(request.getActivo()));
        config.setCreadoPor(actor);
        config.setActualizadoPor(actor);
        config.setFechaCreacion(now);
        config.setFechaActualizacion(now);

        DocumentPermissionConfig saved = repository.save(config);
        log.info("Configuracion documental creada: id={}, campoId={}, formularioId={}, actor={}",
                saved.getId(), saved.getCampoId(), saved.getFormularioId(), actor);
        return toResponse(saved);
    }

    public DocumentPermissionConfigResponse actualizarConfiguracionPermisos(
            String actorUserId,
            String id,
            DocumentPermissionConfigRequest request
    ) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar la configuracion de permisos documentales");
        }

        String configId = normalizar(id);
        if (configId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el id de la configuracion");
        }

        DocumentPermissionConfig actual = repository.findById(configId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Configuracion de permisos documentales no encontrada"));

        String actor = resolverActor(actorUserId, request.getActualizadoPor());
        DocumentPermissionConfig updated = construirConfigBase(request);
        updated.setId(actual.getId());
        updated.setCampoId(normalizar(request.getCampoId()) != null ? normalizar(request.getCampoId()) : actual.getCampoId());
        updated.setTipoCampo(TipoCampo.ARCHIVO);
        updated.setCreadoPor(actual.getCreadoPor());
        updated.setFechaCreacion(actual.getFechaCreacion());
        updated.setActualizadoPor(actor);
        updated.setFechaActualizacion(LocalDateTime.now());
        updated.setActivo(request.getActivo() == null ? actual.getActivo() : Boolean.TRUE.equals(request.getActivo()));

        DocumentPermissionConfig saved = repository.save(updated);
        log.info("Configuracion documental actualizada: id={}, campoId={}, actor={}",
                saved.getId(), saved.getCampoId(), actor);
        return toResponse(saved);
    }

    public DocumentPermissionConfigResponse obtenerConfiguracionPorCampo(String campoId) {
        return toResponse(buscarConfigActivaPorCampo(campoId));
    }

    public Optional<DocumentPermissionConfig> buscarConfiguracionActivaPorCampoOpcional(String campoId) {
        String id = normalizar(campoId);
        if (id == null) {
            return Optional.empty();
        }
        return repository.findByCampoIdAndActivoTrue(id);
    }

    public List<DocumentPermissionConfigResponse> obtenerConfiguracionPorFormulario(String formularioId) {
        String id = normalizar(formularioId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar formularioId");
        }
        return repository.findByFormularioIdAndActivoTrueOrderByFechaCreacionDesc(id).stream()
                .map(this::toResponse)
                .toList();
    }

    public DocumentPermissionValidationResponse validarPermiso(DocumentPermissionValidationRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos para validar el permiso documental");
        }
        if (request.getAccion() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar la accion documental a validar");
        }

        DocumentPermissionConfig config = buscarConfigActivaPorCampo(request.getCampoId());
        List<DocumentPermissionRule> reglas = reglasVigentes(config);

        Optional<DocumentPermissionRule> rule = buscarPrimeraRegla(reglas,
                r -> r.getTipoSujeto() == DocumentSubjectType.USUARIO
                        && iguales(r.getSujetoId(), request.getUsuarioId()));
        if (rule.isEmpty()) {
            rule = buscarPrimeraRegla(reglas,
                    r -> r.getTipoSujeto() == DocumentSubjectType.DEPARTAMENTO
                            && iguales(r.getSujetoId(), request.getDepartamentoId()));
        }
        if (rule.isEmpty()) {
            rule = buscarPrimeraRegla(reglas,
                    r -> r.getTipoSujeto() == DocumentSubjectType.ROL
                            && rolesEquivalentes(r.getSujetoId(), request.getRol()));
        }
        if (rule.isEmpty()) {
            rule = buscarPrimeraRegla(reglas,
                    r -> r.getTipoSujeto() == DocumentSubjectType.CLIENTE
                            && clienteAplica(r.getSujetoId(), request.getClienteId(), request.getRol()));
        }
        if (rule.isEmpty()) {
            rule = buscarPrimeraRegla(reglas,
                    r -> r.getTipoSujeto() == DocumentSubjectType.TRAMITE
                            && iguales(r.getSujetoId(), request.getTramiteId()));
        }

        if (rule.isEmpty()) {
            log.warn("Permiso documental denegado sin regla explicita: campoId={}, usuarioId={}, rol={}, accion={}",
                    config.getCampoId(), request.getUsuarioId(), request.getRol(), request.getAccion());
            return DocumentPermissionValidationResponse.builder()
                    .permitido(false)
                    .motivo("No existe una regla explicita para este sujeto documental")
                    .configId(config.getId())
                    .campoId(config.getCampoId())
                    .accion(request.getAccion())
                    .build();
        }

        DocumentPermissionRule applied = rule.get();
        boolean allowed = permisoHabilitado(applied.getPermisos(), request.getAccion());
        log.info("Permiso documental validado: campoId={}, accion={}, permitido={}, tipoSujeto={}, sujetoId={}",
                config.getCampoId(), request.getAccion(), allowed, applied.getTipoSujeto(), applied.getSujetoId());

        return DocumentPermissionValidationResponse.builder()
                .permitido(allowed)
                .motivo(allowed ? "Permiso concedido por regla documental" : "La regla documental encontro al sujeto pero no habilita la accion")
                .configId(config.getId())
                .campoId(config.getCampoId())
                .accion(request.getAccion())
                .reglaAplicadaTipo(applied.getTipoSujeto() != null ? applied.getTipoSujeto().name() : null)
                .reglaAplicadaSujetoId(applied.getSujetoId())
                .reglaAplicadaSujetoNombre(applied.getSujetoNombre())
                .build();
    }

    public boolean auditoriaHabilitada(DocumentPermissionConfig config, DocumentAuditAction accion) {
        if (config == null || accion == null || config.getAuditoria() == null) {
            return false;
        }

        DocumentAuditConfig audit = config.getAuditoria();
        return switch (accion) {
            case VISUALIZAR -> Boolean.TRUE.equals(audit.getAuditarVisualizacion());
            case DESCARGAR -> Boolean.TRUE.equals(audit.getAuditarDescarga());
            case IMPRIMIR -> audit.getAuditarImpresion() == null || Boolean.TRUE.equals(audit.getAuditarImpresion());
            case SUBIR -> Boolean.TRUE.equals(audit.getAuditarSubida());
            case EDITAR, REEMPLAZAR -> Boolean.TRUE.equals(audit.getAuditarEdicion());
            case ELIMINAR -> Boolean.TRUE.equals(audit.getAuditarEliminacion());
            case CAMBIAR_PERMISOS -> Boolean.TRUE.equals(audit.getAuditarCambioPermisos());
            case INICIAR_COLABORACION, SALIR_COLABORACION -> true;
        };
    }

    public DocumentPermissionConfigResponse toResponse(DocumentPermissionConfig config) {
        return DocumentPermissionConfigResponse.builder()
                .id(config.getId())
                .politicaId(config.getPoliticaId())
                .nodoId(config.getNodoId())
                .formularioId(config.getFormularioId())
                .campoId(config.getCampoId())
                .campoNombre(config.getCampoNombre())
                .tipoCampo(config.getTipoCampo())
                .descripcion(config.getDescripcion())
                .obligatorio(config.getObligatorio())
                .permiteMultiplesArchivos(config.getPermiteMultiplesArchivos())
                .tiposArchivoPermitidos(config.getTiposArchivoPermitidos())
                .tamanoMaximoMb(config.getTamanoMaximoMb())
                .categoriaDocumental(config.getCategoriaDocumental())
                .nivelConfidencialidad(config.getNivelConfidencialidad())
                .alcance(config.getAlcance())
                .reglasPermiso(config.getReglasPermiso())
                .auditoria(config.getAuditoria())
                .activo(config.getActivo())
                .creadoPor(config.getCreadoPor())
                .fechaCreacion(config.getFechaCreacion())
                .actualizadoPor(config.getActualizadoPor())
                .fechaActualizacion(config.getFechaActualizacion())
                .build();
    }

    private DocumentPermissionConfig construirConfigBase(DocumentPermissionConfigRequest request) {
        return DocumentPermissionConfig.builder()
                .politicaId(normalizar(request.getPoliticaId()))
                .nodoId(normalizar(request.getNodoId()))
                .formularioId(normalizar(request.getFormularioId()))
                .campoNombre(validarCampoNombre(request.getCampoNombre()))
                .descripcion(normalizar(request.getDescripcion()))
                .obligatorio(Boolean.TRUE.equals(request.getObligatorio()))
                .permiteMultiplesArchivos(Boolean.TRUE.equals(request.getPermiteMultiplesArchivos()))
                .tiposArchivoPermitidos(normalizarTiposArchivo(request.getTiposArchivoPermitidos()))
                .tamanoMaximoMb(validarTamanoMaximo(request.getTamanoMaximoMb()))
                .categoriaDocumental(request.getCategoriaDocumental() != null ? request.getCategoriaDocumental() : DocumentCategory.RESPALDO)
                .nivelConfidencialidad(request.getNivelConfidencialidad() != null ? request.getNivelConfidencialidad() : DocumentConfidentialityLevel.INTERNO)
                .alcance(normalizarAlcance(request.getAlcance()))
                .reglasPermiso(normalizarReglas(request.getReglasPermiso()))
                .auditoria(normalizarAuditoria(request.getAuditoria()))
                .build();
    }

    private String validarCampoNombre(String campoNombre) {
        String nombre = normalizar(campoNombre);
        if (nombre == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre visible del campo documental es obligatorio");
        }
        return nombre;
    }

    private Integer validarTamanoMaximo(Integer tamanoMaximoMb) {
        int value = tamanoMaximoMb != null ? tamanoMaximoMb : 25;
        if (value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El tamano maximo permitido debe ser mayor a 0 MB");
        }
        return value;
    }

    private List<DocumentFileType> normalizarTiposArchivo(List<DocumentFileType> tipos) {
        if (tipos == null || tipos.isEmpty()) {
            return List.of(DocumentFileType.PDF, DocumentFileType.WORD, DocumentFileType.EXCEL,
                    DocumentFileType.IMAGEN, DocumentFileType.VIDEO);
        }
        return tipos.stream().distinct().toList();
    }

    private DocumentPermissionScope normalizarAlcance(DocumentPermissionScope scope) {
        if (scope == null) {
            return DocumentPermissionScope.builder().build();
        }
        return DocumentPermissionScope.builder()
                .clienteId(normalizar(scope.getClienteId()))
                .tramiteId(normalizar(scope.getTramiteId()))
                .departamentoId(normalizar(scope.getDepartamentoId()))
                .build();
    }

    private List<DocumentPermissionRule> normalizarReglas(List<DocumentPermissionRule> reglas) {
        List<DocumentPermissionRule> source = reglas == null || reglas.isEmpty()
                ? reglasPermisoPorDefecto()
                : reglas;
        List<DocumentPermissionRule> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (DocumentPermissionRule rule : source) {
            if (rule == null) {
                continue;
            }
            if (rule.getTipoSujeto() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cada regla debe indicar tipoSujeto");
            }
            String sujetoId = normalizar(rule.getSujetoId());
            if (sujetoId == null && rule.getTipoSujeto() == DocumentSubjectType.CLIENTE) {
                sujetoId = CLIENTE_INICIADOR_SUJETO_ID;
            }
            if (sujetoId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cada regla debe indicar sujetoId");
            }
            String sujetoNombre = normalizar(rule.getSujetoNombre());
            if (sujetoNombre == null && rule.getTipoSujeto() == DocumentSubjectType.CLIENTE
                    && CLIENTE_INICIADOR_SUJETO_ID.equals(sujetoId)) {
                sujetoNombre = "Cliente que inicio el tramite";
            }
            result.add(DocumentPermissionRule.builder()
                    .tipoSujeto(rule.getTipoSujeto())
                    .sujetoId(sujetoId)
                    .sujetoNombre(sujetoNombre != null ? sujetoNombre : sujetoId)
                    .permisos(normalizarPermisos(rule.getPermisos()))
                    .aplicaDesde(rule.getAplicaDesde() != null ? rule.getAplicaDesde() : now)
                    .aplicaHasta(rule.getAplicaHasta())
                    .activo(rule.getActivo() == null || Boolean.TRUE.equals(rule.getActivo()))
                    .build());
        }

        if (result.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe existir al menos una regla de permiso documental");
        }
        return result;
    }

    private DocumentPermissionSet normalizarPermisos(DocumentPermissionSet permisos) {
        if (permisos == null) {
            return DocumentPermissionSet.builder().build();
        }
        return DocumentPermissionSet.builder()
                .leer(Boolean.TRUE.equals(permisos.getLeer()))
                .subir(Boolean.TRUE.equals(permisos.getSubir()))
                .descargar(Boolean.TRUE.equals(permisos.getDescargar()))
                .editar(Boolean.TRUE.equals(permisos.getEditar()))
                .reemplazar(Boolean.TRUE.equals(permisos.getReemplazar()))
                .eliminar(Boolean.TRUE.equals(permisos.getEliminar()))
                .administrarPermisos(Boolean.TRUE.equals(permisos.getAdministrarPermisos()))
                .colaborar(Boolean.TRUE.equals(permisos.getColaborar()))
                .build();
    }

    private DocumentAuditConfig normalizarAuditoria(DocumentAuditConfig audit) {
        if (audit == null) {
            return auditoriaPorDefecto();
        }
        return DocumentAuditConfig.builder()
                .auditarVisualizacion(audit.getAuditarVisualizacion() == null || Boolean.TRUE.equals(audit.getAuditarVisualizacion()))
                .auditarDescarga(audit.getAuditarDescarga() == null || Boolean.TRUE.equals(audit.getAuditarDescarga()))
                .auditarImpresion(audit.getAuditarImpresion() == null || Boolean.TRUE.equals(audit.getAuditarImpresion()))
                .auditarSubida(audit.getAuditarSubida() == null || Boolean.TRUE.equals(audit.getAuditarSubida()))
                .auditarEdicion(audit.getAuditarEdicion() == null || Boolean.TRUE.equals(audit.getAuditarEdicion()))
                .auditarEliminacion(audit.getAuditarEliminacion() == null || Boolean.TRUE.equals(audit.getAuditarEliminacion()))
                .auditarCambioPermisos(audit.getAuditarCambioPermisos() == null || Boolean.TRUE.equals(audit.getAuditarCambioPermisos()))
                .guardarIpDispositivo(audit.getGuardarIpDispositivo() == null || Boolean.TRUE.equals(audit.getGuardarIpDispositivo()))
                .guardarUserAgent(audit.getGuardarUserAgent() == null || Boolean.TRUE.equals(audit.getGuardarUserAgent()))
                .guardarFechaHora(audit.getGuardarFechaHora() == null || Boolean.TRUE.equals(audit.getGuardarFechaHora()))
                .guardarUsuarioActor(audit.getGuardarUsuarioActor() == null || Boolean.TRUE.equals(audit.getGuardarUsuarioActor()))
                .build();
    }

    private DocumentPermissionConfig buscarConfigActivaPorCampo(String campoId) {
        String id = normalizar(campoId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar campoId");
        }
        return repository.findByCampoIdAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No existe configuracion activa de permisos documentales para el campo " + id));
    }

    private List<DocumentPermissionRule> reglasVigentes(DocumentPermissionConfig config) {
        LocalDateTime now = LocalDateTime.now();
        return (config.getReglasPermiso() != null ? config.getReglasPermiso() : List.<DocumentPermissionRule>of()).stream()
                .filter(rule -> rule != null && Boolean.TRUE.equals(rule.getActivo()))
                .filter(rule -> rule.getAplicaHasta() == null || !rule.getAplicaHasta().isBefore(now))
                .toList();
    }

    private Optional<DocumentPermissionRule> buscarPrimeraRegla(
            List<DocumentPermissionRule> reglas,
            Predicate<DocumentPermissionRule> predicate
    ) {
        return reglas.stream().filter(predicate).findFirst();
    }

    private boolean permisoHabilitado(DocumentPermissionSet permisos, DocumentPermissionAction accion) {
        if (permisos == null || accion == null) {
            return false;
        }
        return switch (accion) {
            case LEER -> Boolean.TRUE.equals(permisos.getLeer());
            case SUBIR -> Boolean.TRUE.equals(permisos.getSubir());
            case DESCARGAR -> Boolean.TRUE.equals(permisos.getDescargar());
            case EDITAR -> Boolean.TRUE.equals(permisos.getEditar());
            case REEMPLAZAR -> Boolean.TRUE.equals(permisos.getReemplazar());
            case ELIMINAR -> Boolean.TRUE.equals(permisos.getEliminar());
            case ADMINISTRAR_PERMISOS -> Boolean.TRUE.equals(permisos.getAdministrarPermisos());
            case COLABORAR -> Boolean.TRUE.equals(permisos.getColaborar());
        };
    }

    private List<DocumentPermissionRule> reglasPermisoPorDefecto() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                reglaRol("CLIENTE", "Cliente", permisosBasicos(), now),
                reglaRol("FUNCIONARIO", "Funcionario", permisosBasicos(), now),
                reglaRol("JEFE_PROCESO", "Jefe de proceso", permisosAdministrativos(), now),
                reglaRol("ADMINISTRADOR", "Administrador", permisosAdministrativos(), now)
        );
    }

    private DocumentPermissionRule reglaRol(String sujetoId, String sujetoNombre, DocumentPermissionSet permisos, LocalDateTime now) {
        return DocumentPermissionRule.builder()
                .tipoSujeto(DocumentSubjectType.ROL)
                .sujetoId(sujetoId)
                .sujetoNombre(sujetoNombre)
                .permisos(permisos)
                .aplicaDesde(now)
                .activo(true)
                .build();
    }

    private DocumentPermissionSet permisosBasicos() {
        return DocumentPermissionSet.builder()
                .leer(true)
                .subir(true)
                .descargar(true)
                .editar(false)
                .reemplazar(false)
                .eliminar(false)
                .administrarPermisos(false)
                .colaborar(false)
                .build();
    }

    private DocumentPermissionSet permisosAdministrativos() {
        return DocumentPermissionSet.builder()
                .leer(true)
                .subir(true)
                .descargar(true)
                .editar(true)
                .reemplazar(true)
                .eliminar(true)
                .administrarPermisos(true)
                .colaborar(true)
                .build();
    }

    private DocumentAuditConfig auditoriaPorDefecto() {
        return DocumentAuditConfig.builder()
                .auditarVisualizacion(true)
                .auditarDescarga(true)
                .auditarImpresion(true)
                .auditarSubida(true)
                .auditarEdicion(true)
                .auditarEliminacion(true)
                .auditarCambioPermisos(true)
                .guardarIpDispositivo(true)
                .guardarUserAgent(true)
                .guardarFechaHora(true)
                .guardarUsuarioActor(true)
                .build();
    }

    private boolean iguales(String left, String right) {
        String l = normalizar(left);
        String r = normalizar(right);
        return l != null && r != null && l.equalsIgnoreCase(r);
    }

    private boolean rolesEquivalentes(String reglaRol, String actorRol) {
        String regla = normalizarRol(reglaRol);
        String actor = normalizarRol(actorRol);
        return regla != null && actor != null && regla.equals(actor);
    }

    private String normalizarRol(String rol) {
        String normalized = normalizar(rol);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(normalized)) {
            return "ADMINISTRADOR";
        }
        return normalized;
    }

    private boolean clienteAplica(String reglaCliente, String requestClienteId, String actorRol) {
        String regla = normalizar(reglaCliente);
        String clienteId = normalizar(requestClienteId);
        if (regla == null) {
            return false;
        }
        if (CLIENTE_INICIADOR_SUJETO_ID.equalsIgnoreCase(regla)) {
            return clienteId != null || esRolCliente(actorRol);
        }
        return clienteId != null && regla.equalsIgnoreCase(clienteId);
    }

    private boolean esRolCliente(String rol) {
        String normalized = normalizarRol(rol);
        return "USUARIO".equals(normalized) || "CLIENTE".equals(normalized);
    }

    private String resolverActor(String headerActor, String bodyActor) {
        String actor = normalizar(headerActor);
        if (actor != null) {
            return actor;
        }
        actor = normalizar(bodyActor);
        if (actor != null) {
            return actor;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id, X-Admin-User-Id o el usuario actor en el body");
    }

    private String normalizar(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
