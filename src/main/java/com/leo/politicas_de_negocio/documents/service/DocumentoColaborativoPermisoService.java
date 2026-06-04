package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentoColaborativoPermisoService {

    private final TareaActividadRepository tareaActividadRepository;

    public DocumentoColaborativoPermisosDto evaluarPermisos(
            DocumentoColaborativoMetadata metadata,
            Usuario usuario,
            String rol,
            String departamentoId,
            InstanciaPolitica instancia
    ) {
        return evaluarPermisos(metadata, usuario, rol, departamentoId, instancia, null);
    }

    public DocumentoColaborativoPermisosDto evaluarPermisos(
            DocumentoColaborativoMetadata metadata,
            Usuario usuario,
            String rol,
            String departamentoId,
            InstanciaPolitica instancia,
            TareaActividad tareaActual
    ) {
        String usuarioId = usuario != null ? usuario.getId() : null;
        String rolNormalizado = normalizar(rol != null ? rol : (usuario != null ? usuario.getRol() : null));
        String departamentoActual = departamentoId != null ? departamentoId : (usuario != null ? usuario.getDepartamentoId() : null);
        DocumentoColaborativoMetadata.PermisosEdicion permisosEdicion = metadata != null ? metadata.getPermisosEdicion() : null;
        DocumentoColaborativoMetadata.PermisosLectura permisosLectura = metadata != null ? metadata.getPermisosLectura() : null;

        boolean puedeEditar = usuarioEnLista(usuarioId, permisosEdicion != null ? permisosEdicion.getUsuarios() : null)
                || valorEnLista(rolNormalizado, permisosEdicion != null ? permisosEdicion.getRoles() : null)
                || usuarioEnLista(departamentoActual, permisosEdicion != null ? permisosEdicion.getDepartamentos() : null);

        boolean puedeLeer = puedeEditar
                || usuarioEnLista(usuarioId, permisosLectura != null ? permisosLectura.getUsuarios() : null)
                || valorEnLista(rolNormalizado, permisosLectura != null ? permisosLectura.getRoles() : null)
                || usuarioEnLista(departamentoActual, permisosLectura != null ? permisosLectura.getDepartamentos() : null)
                || esClienteIniciadorConLectura(usuarioId, instancia, permisosLectura);

        DocumentoColaborativoMetadata.PermisosAdicionales permisosAdic = metadata != null ? metadata.getPermisosAdicionales() : null;
        boolean puedeDescargar = evaluarPermisoAccion(
                metadata != null ? metadata.getPermisosDescarga() : null,
                permisosAdic != null ? permisosAdic.getPuedeDescargar() : null,
                usuarioId,
                rolNormalizado,
                departamentoActual
        );
        boolean puedeImprimir = evaluarPermisoAccion(
                metadata != null ? metadata.getPermisosImpresion() : null,
                permisosAdic != null ? permisosAdic.getPuedeImprimir() : null,
                usuarioId,
                rolNormalizado,
                departamentoActual
        );
        boolean puedeComentar = evaluarPermisoAccion(
                metadata != null ? metadata.getPermisosComentarios() : null,
                permisosAdic != null ? permisosAdic.getPuedeComentar() : null,
                usuarioId,
                rolNormalizado,
                departamentoActual
        );
        boolean puedeReemplazar = evaluarPermisoAccion(
                metadata != null ? metadata.getPermisosReemplazo() : null,
                permisosAdic != null ? permisosAdic.getPuedeReemplazar() : null,
                usuarioId,
                rolNormalizado,
                departamentoActual
        );
        boolean puedeEliminar = evaluarPermisoAccion(
                metadata != null ? metadata.getPermisosEliminacion() : null,
                permisosAdic != null ? permisosAdic.getPuedeEliminar() : null,
                usuarioId,
                rolNormalizado,
                departamentoActual
        );
        boolean puedeCompartirInternamente = evaluarPermisoAccion(
                metadata != null ? metadata.getPermisosCompartirInternamente() : null,
                permisosAdic != null ? permisosAdic.getPuedeCompartirInternamente() : null,
                usuarioId,
                rolNormalizado,
                departamentoActual
        );

        return DocumentoColaborativoPermisosDto.builder()
                .puedeLeer(puedeLeer)
                .puedeEditar(puedeEditar)
                .puedeDescargar(puedeDescargar)
                .puedeImprimir(puedeImprimir)
                .puedeComentar(puedeComentar)
                .puedeReemplazar(puedeReemplazar)
                .puedeEliminar(puedeEliminar)
                .puedeCompartirInternamente(puedeCompartirInternamente)
                .build();
    }

    private boolean evaluarPermisoAccion(
            DocumentoColaborativoMetadata.PermisosAccion permisos,
            Boolean permisoGlobalLegacy,
            String usuarioId,
            String rol,
            String departamentoId
    ) {
        if (permisos != null) {
            return usuarioEnLista(usuarioId, permisos.getUsuarios())
                    || valorEnLista(rol, permisos.getRoles())
                    || usuarioEnLista(departamentoId, permisos.getDepartamentos());
        }
        return Boolean.TRUE.equals(permisoGlobalLegacy);
    }

    private boolean cumpleModoColaboracion(
            String modo,
            DocumentoColaborativoMetadata metadata,
            String usuarioId,
            String rol,
            String departamentoId,
            DocumentoColaborativoMetadata.PermisosEdicion permisosEdicion,
            TareaActividad tareaActual
    ) {
        if (modo == null || modo.isBlank()) {
            return false;
        }

        String modoNormalizado = normalizar(modo);
        return switch (modoNormalizado) {
            case "DEPARTAMENTO" -> usuarioEnLista(departamentoId, permisosEdicion != null ? permisosEdicion.getDepartamentos() : null)
                    || perteneceAlDepartamentoResponsable(metadata, departamentoId, tareaActual);
            case "USUARIOS_ESPECIFICOS" -> usuarioEnLista(usuarioId, permisosEdicion != null ? permisosEdicion.getUsuarios() : null);
            case "ROLES" -> valorEnLista(rol, permisosEdicion != null ? permisosEdicion.getRoles() : null);
            case "FUNCIONARIO_RESPONSABLE" -> esFuncionarioResponsable(metadata, usuarioId, tareaActual);
            case "ADMIN_JEFE" -> esAdminOJefe(rol);
            case "PERSONALIZADO" -> usuarioEnLista(usuarioId, permisosEdicion != null ? permisosEdicion.getUsuarios() : null)
                    || valorEnLista(rol, permisosEdicion != null ? permisosEdicion.getRoles() : null)
                    || usuarioEnLista(departamentoId, permisosEdicion != null ? permisosEdicion.getDepartamentos() : null);
            default -> false;
        };
    }

    private boolean perteneceAlDepartamentoResponsable(
            DocumentoColaborativoMetadata metadata,
            String departamentoId,
            TareaActividad tareaActual
    ) {
        if (metadata == null || departamentoId == null || departamentoId.isBlank()) {
            return false;
        }
        for (TareaActividad task : tareasDelDocumento(metadata, tareaActual)) {
            if ("DEPARTAMENTO".equalsIgnoreCase(task.getResponsableTipo())
                    && departamentoId.equals(task.getResponsableId())) {
                return true;
            }
        }
        return false;
    }

    private boolean esFuncionarioResponsable(
            DocumentoColaborativoMetadata metadata,
            String usuarioId,
            TareaActividad tareaActual
    ) {
        if (metadata == null || usuarioId == null || usuarioId.isBlank()) {
            return false;
        }
        for (TareaActividad task : tareasDelDocumento(metadata, tareaActual)) {
            if (usuarioId.equals(task.getAsignadoA())
                    || usuarioId.equals(task.getResponsableId()) && "USUARIO".equalsIgnoreCase(task.getResponsableTipo())
                    || task.getParticipantesIds() != null && task.getParticipantesIds().contains(usuarioId)) {
                return true;
            }
        }
        return false;
    }

    private List<TareaActividad> tareasDelDocumento(DocumentoColaborativoMetadata metadata, TareaActividad tareaActual) {
        if (tareaActual != null && contieneCampo(tareaActual, metadata.getCampoFormularioId())) {
            return List.of(tareaActual);
        }
        if (metadata.getTramiteId() == null || metadata.getTramiteId().isBlank()) {
            return Collections.emptyList();
        }
        return tareaActividadRepository.findByInstanciaIdOrderByFechaCreacionAsc(metadata.getTramiteId())
                .stream()
                .filter(task -> contieneCampo(task, metadata.getCampoFormularioId()))
                .toList();
    }

    private boolean esClienteIniciadorConLectura(
            String usuarioId,
            InstanciaPolitica instancia,
            DocumentoColaborativoMetadata.PermisosLectura permisosLectura
    ) {
        return usuarioId != null
                && instancia != null
                && Boolean.TRUE.equals(permisosLectura != null ? permisosLectura.getIncluirClienteIniciador() : null)
                && usuarioId.equals(instancia.getCreadaPor());
    }

    private boolean esAdminOJefe(String rol) {
        return "ADMINISTRADOR".equals(rol) || "JEFE_PROCESO".equals(rol);
    }

    private boolean usuarioEnLista(String value, List<String> values) {
        if (value == null || value.isBlank() || values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(item -> value.equals(item));
    }

    private boolean valorEnLista(String value, List<String> values) {
        if (value == null || value.isBlank() || values == null || values.isEmpty()) {
            return false;
        }
        return values.stream().anyMatch(item -> value.equals(normalizar(item)));
    }

    private String normalizar(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private boolean contieneCampo(TareaActividad task, String campoId) {
        if (task == null || task.getFormularioDefinicion() == null || campoId == null) {
            return false;
        }
        return task.getFormularioDefinicion().stream()
                .anyMatch(c -> c != null && campoId.equals(c.getCampo()));
    }
}
