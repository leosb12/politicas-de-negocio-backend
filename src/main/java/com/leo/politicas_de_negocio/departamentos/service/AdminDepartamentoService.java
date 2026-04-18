package com.leo.politicas_de_negocio.departamentos.service;

import com.leo.politicas_de_negocio.departamentos.dto.CreateDepartamentoRequest;
import com.leo.politicas_de_negocio.departamentos.dto.DepartamentoResponse;
import com.leo.politicas_de_negocio.departamentos.dto.ReasignarDepartamentoRequest;
import com.leo.politicas_de_negocio.departamentos.dto.UpdateDepartamentoRequest;
import com.leo.politicas_de_negocio.usuarios.dto.UserResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminDepartamentoService {

    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public AdminDepartamentoService(
            DepartamentoRepository departamentoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.departamentoRepository = departamentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public DepartamentoResponse crearDepartamento(String adminUserId, CreateDepartamentoRequest request) {
        assertAdmin(adminUserId);

        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para crear el departamento");
        }

        String nombre = normalizeName(request.getNombre());
        if (departamentoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un departamento con ese nombre");
        }

        Departamento departamento = Departamento.builder()
                .nombre(nombre)
                .descripcion(cleanNullable(request.getDescripcion()))
                .activo(true)
                .build();

        Departamento saved = departamentoRepository.save(departamento);
        return toDepartamentoResponse(saved, 0L);
    }

    public List<DepartamentoResponse> listarDepartamentos(String adminUserId) {
        assertAdmin(adminUserId);

        return departamentoRepository.findAllByOrderByNombreAsc().stream()
                .map(departamento -> toDepartamentoResponse(
                        departamento,
                        usuarioRepository.countByDepartamentoId(departamento.getId())
                ))
                .toList();
    }

    public DepartamentoResponse obtenerDepartamento(String adminUserId, String departamentoId) {
        assertAdmin(adminUserId);
        Departamento departamento = getDepartamentoOrThrow(departamentoId);
        return toDepartamentoResponse(departamento, usuarioRepository.countByDepartamentoId(departamento.getId()));
    }

    public DepartamentoResponse actualizarDepartamento(
            String adminUserId,
            String departamentoId,
            UpdateDepartamentoRequest request
    ) {
        assertAdmin(adminUserId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para actualizar el departamento");
        }

        Departamento departamento = getDepartamentoOrThrow(departamentoId);

        if (request.getNombre() != null) {
            String nombre = normalizeName(request.getNombre());
            boolean cambioNombre = !nombre.equalsIgnoreCase(departamento.getNombre());
            if (cambioNombre && departamentoRepository.existsByNombreIgnoreCase(nombre)) {
                throw new ApiException(HttpStatus.CONFLICT, "Ya existe un departamento con ese nombre");
            }
            departamento.setNombre(nombre);
        }

        if (request.getDescripcion() != null) {
            departamento.setDescripcion(cleanNullable(request.getDescripcion()));
        }

        if (request.getActivo() != null) {
            if (!request.getActivo()) {
                validateCanDisableOrDelete(departamento, false);
            }
            departamento.setActivo(request.getActivo());
        }

        Departamento saved = departamentoRepository.save(departamento);
        return toDepartamentoResponse(saved, usuarioRepository.countByDepartamentoId(saved.getId()));
    }

    public DepartamentoResponse activarDepartamento(String adminUserId, String departamentoId) {
        assertAdmin(adminUserId);
        Departamento departamento = getDepartamentoOrThrow(departamentoId);
        departamento.setActivo(true);
        Departamento saved = departamentoRepository.save(departamento);
        return toDepartamentoResponse(saved, usuarioRepository.countByDepartamentoId(saved.getId()));
    }

    public DepartamentoResponse desactivarDepartamento(String adminUserId, String departamentoId) {
        assertAdmin(adminUserId);
        Departamento departamento = getDepartamentoOrThrow(departamentoId);
        validateCanDisableOrDelete(departamento, false);
        departamento.setActivo(false);
        Departamento saved = departamentoRepository.save(departamento);
        return toDepartamentoResponse(saved, usuarioRepository.countByDepartamentoId(saved.getId()));
    }

    public void eliminarDepartamento(String adminUserId, String departamentoId) {
        assertAdmin(adminUserId);
        Departamento departamento = getDepartamentoOrThrow(departamentoId);
        validateCanDisableOrDelete(departamento, true);
        departamentoRepository.delete(departamento);
    }

    public List<UserResponse> listarUsuariosPorDepartamento(String adminUserId, String departamentoId) {
        assertAdmin(adminUserId);
        Departamento departamento = getDepartamentoOrThrow(departamentoId);

        return usuarioRepository.findAllByDepartamentoId(departamento.getId()).stream()
                .sorted(Comparator.comparing(
                        usuario -> safeLower(usuario.getNombre()),
                        Comparator.naturalOrder()
                ))
                .map(this::toUserResponse)
                .toList();
    }

    public DepartamentoResponse reasignarUsuarios(
            String adminUserId,
            String departamentoId,
            ReasignarDepartamentoRequest request
    ) {
        Usuario admin = assertAdmin(adminUserId);
        Departamento origen = getDepartamentoOrThrow(departamentoId);

        if (request == null || request.getDepartamentoDestinoId() == null || request.getDepartamentoDestinoId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar un departamento destino");
        }

        Departamento destino = getDepartamentoOrThrow(request.getDepartamentoDestinoId().trim());
        if (origen.getId().equals(destino.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El departamento destino no puede ser igual al origen");
        }

        if (!Boolean.TRUE.equals(destino.getActivo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El departamento destino está desactivado");
        }

        List<Usuario> usuarios = usuarioRepository.findAllByDepartamentoId(origen.getId());
        for (Usuario usuario : usuarios) {
            if (admin.getId().equals(usuario.getId()) && "ADMIN".equalsIgnoreCase(usuario.getRol())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "No puedes reasignarte automáticamente a otro departamento en esta operación");
            }

            usuario.setDepartamentoId(destino.getId());
        }

        if (!usuarios.isEmpty()) {
            usuarioRepository.saveAll(usuarios);
        }

        return toDepartamentoResponse(origen, usuarioRepository.countByDepartamentoId(origen.getId()));
    }

    private void validateCanDisableOrDelete(Departamento departamento, boolean deleting) {
        long usuariosAsignados = usuarioRepository.countByDepartamentoId(departamento.getId());
        if (usuariosAsignados > 0) {
            String accion = deleting ? "eliminar" : "desactivar";
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede " + accion + " el departamento porque tiene usuarios asignados");
        }
    }

    private Departamento getDepartamentoOrThrow(String departamentoId) {
        if (departamentoId == null || departamentoId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El id del departamento es obligatorio");
        }

        return departamentoRepository.findById(departamentoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Departamento no encontrado"));
    }

    private Usuario assertAdmin(String adminUserId) {
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }

        Usuario admin = usuarioRepository.findByIdAndActivo(adminUserId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));

        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede realizar esta acción");
        }

        return admin;
    }

    private String normalizeName(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre del departamento es obligatorio");
        }

        String normalized = nombre.trim();
        if (normalized.length() < 3 || normalized.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El nombre del departamento debe tener entre 3 y 80 caracteres");
        }

        return normalized;
    }

    private String cleanNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private DepartamentoResponse toDepartamentoResponse(Departamento departamento, long totalUsuarios) {
        return DepartamentoResponse.builder()
                .id(departamento.getId())
                .nombre(departamento.getNombre())
                .descripcion(departamento.getDescripcion())
                .activo(departamento.getActivo())
                .totalUsuarios(totalUsuarios)
                .build();
    }

    private UserResponse toUserResponse(Usuario usuario) {
        return UserResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .departamentoId(usuario.getDepartamentoId())
                .activo(usuario.getActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .build();
    }
}