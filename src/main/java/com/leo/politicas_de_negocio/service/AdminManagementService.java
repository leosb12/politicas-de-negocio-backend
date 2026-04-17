package com.leo.politicas_de_negocio.service;

import com.leo.politicas_de_negocio.dto.admin.CreateRoleRequest;
import com.leo.politicas_de_negocio.dto.admin.CreateUserRequest;
import com.leo.politicas_de_negocio.dto.admin.RoleResponse;
import com.leo.politicas_de_negocio.dto.admin.UpdateRoleRequest;
import com.leo.politicas_de_negocio.dto.admin.UpdateUserRequest;
import com.leo.politicas_de_negocio.dto.admin.UpdateUserRoleRequest;
import com.leo.politicas_de_negocio.dto.admin.UserResponse;
import com.leo.politicas_de_negocio.exception.ApiException;
import com.leo.politicas_de_negocio.model.Rol;
import com.leo.politicas_de_negocio.model.Usuario;
import com.leo.politicas_de_negocio.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.repository.RolRepository;
import com.leo.politicas_de_negocio.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AdminManagementService {

    private static final Pattern ROLE_PATTERN = Pattern.compile("^[A-Z0-9_]{3,30}$");

    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final RolRepository rolRepository;

    public AdminManagementService(
            UsuarioRepository usuarioRepository,
            DepartamentoRepository departamentoRepository,
            RolRepository rolRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.departamentoRepository = departamentoRepository;
        this.rolRepository = rolRepository;
    }

    public UserResponse crearUsuario(String adminUserId, CreateUserRequest request) {
        assertAdmin(adminUserId);
        validateCreateUserRequest(request);

        String correo = normalizeEmail(request.getCorreo());
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        Rol rol = resolveRolActivo(request.getRol());
        String departamentoId = normalizeDepartamentoId(request.getDepartamentoId());

        Usuario usuario = Usuario.builder()
                .nombre(requireText(request.getNombre(), "nombre"))
                .correo(correo)
                .password(normalizePassword(request.getPassword()))
                .rol(rol.getNombre())
                .departamentoId(departamentoId)
                .activo(request.getActivo() == null ? true : request.getActivo())
                .fechaCreacion(LocalDateTime.now())
                .build();

        return toUserResponse(usuarioRepository.save(usuario));
    }

    public List<UserResponse> listarUsuarios(String adminUserId) {
        assertAdmin(adminUserId);

        return usuarioRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        usuario -> safeLower(usuario.getNombre()),
                        Comparator.naturalOrder()
                ))
                .map(this::toUserResponse)
                .toList();
    }

    public UserResponse obtenerUsuario(String adminUserId, String usuarioId) {
        assertAdmin(adminUserId);
        return toUserResponse(getUsuarioOrThrow(usuarioId));
    }

    public UserResponse actualizarUsuario(String adminUserId, String usuarioId, UpdateUserRequest request) {
        assertAdmin(adminUserId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para actualizar el usuario");
        }

        Usuario usuario = getUsuarioOrThrow(usuarioId);

        if (request.getNombre() != null) {
            usuario.setNombre(requireText(request.getNombre(), "nombre"));
        }

        if (request.getCorreo() != null) {
            String correo = normalizeEmail(request.getCorreo());
            boolean cambiaCorreo = !correo.equalsIgnoreCase(usuario.getCorreo());
            if (cambiaCorreo && usuarioRepository.existsByCorreoIgnoreCase(correo)) {
                throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
            }
            usuario.setCorreo(correo);
        }

        if (request.getPassword() != null) {
            usuario.setPassword(normalizePassword(request.getPassword()));
        }

        if (request.getDepartamentoId() != null) {
            usuario.setDepartamentoId(normalizeDepartamentoId(request.getDepartamentoId()));
        }

        if (request.getActivo() != null) {
            if (adminUserId.equals(usuarioId) && !request.getActivo()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "No puedes desactivar tu propia cuenta de administrador");
            }
            usuario.setActivo(request.getActivo());
        }

        return toUserResponse(usuarioRepository.save(usuario));
    }

    public UserResponse activarUsuario(String adminUserId, String usuarioId) {
        assertAdmin(adminUserId);
        Usuario usuario = getUsuarioOrThrow(usuarioId);
        usuario.setActivo(true);
        return toUserResponse(usuarioRepository.save(usuario));
    }

    public UserResponse desactivarUsuario(String adminUserId, String usuarioId) {
        assertAdmin(adminUserId);
        if (adminUserId.equals(usuarioId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No puedes desactivar tu propia cuenta de administrador");
        }

        Usuario usuario = getUsuarioOrThrow(usuarioId);
        usuario.setActivo(false);
        return toUserResponse(usuarioRepository.save(usuario));
    }

    public UserResponse asignarRol(String adminUserId, String usuarioId, UpdateUserRoleRequest request) {
        assertAdmin(adminUserId);
        if (request == null || request.getRol() == null || request.getRol().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar un rol válido");
        }

        Rol rol = resolveRolActivo(request.getRol());
        if (adminUserId.equals(usuarioId) && !"ADMIN".equalsIgnoreCase(rol.getNombre())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No puedes quitarte el rol ADMIN a ti mismo");
        }

        Usuario usuario = getUsuarioOrThrow(usuarioId);
        usuario.setRol(rol.getNombre());
        return toUserResponse(usuarioRepository.save(usuario));
    }

    public UserResponse quitarRol(String adminUserId, String usuarioId) {
        assertAdmin(adminUserId);
        if (adminUserId.equals(usuarioId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No puedes quitarte el rol ADMIN a ti mismo");
        }

        Usuario usuario = getUsuarioOrThrow(usuarioId);
        Rol rolUsuario = resolveRolActivo("USUARIO");
        usuario.setRol(rolUsuario.getNombre());
        return toUserResponse(usuarioRepository.save(usuario));
    }

    public RoleResponse crearRol(String adminUserId, CreateRoleRequest request) {
        assertAdmin(adminUserId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para crear el rol");
        }

        String nombre = normalizeRoleName(request.getNombre());
        if (rolRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ese rol ya existe");
        }

        Rol rol = Rol.builder()
                .nombre(nombre)
                .descripcion(cleanNullable(request.getDescripcion()))
                .activo(true)
                .sistema(false)
                .build();

        return toRoleResponse(rolRepository.save(rol));
    }

    public List<RoleResponse> listarRoles(String adminUserId) {
        assertAdmin(adminUserId);
        return rolRepository.findAllByOrderByNombreAsc().stream()
                .map(this::toRoleResponse)
                .toList();
    }

    public RoleResponse obtenerRol(String adminUserId, String rolId) {
        assertAdmin(adminUserId);
        return toRoleResponse(getRolOrThrow(rolId));
    }

    public RoleResponse actualizarRol(String adminUserId, String rolId, UpdateRoleRequest request) {
        assertAdmin(adminUserId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para actualizar el rol");
        }

        Rol rol = getRolOrThrow(rolId);

        if (request.getDescripcion() != null) {
            rol.setDescripcion(cleanNullable(request.getDescripcion()));
        }

        if (request.getActivo() != null) {
            if (!request.getActivo()) {
                validateRolCanBeDisabled(rol);
            }
            rol.setActivo(request.getActivo());
        }

        return toRoleResponse(rolRepository.save(rol));
    }

    public RoleResponse activarRol(String adminUserId, String rolId) {
        assertAdmin(adminUserId);
        Rol rol = getRolOrThrow(rolId);
        rol.setActivo(true);
        return toRoleResponse(rolRepository.save(rol));
    }

    public RoleResponse desactivarRol(String adminUserId, String rolId) {
        assertAdmin(adminUserId);
        Rol rol = getRolOrThrow(rolId);
        validateRolCanBeDisabled(rol);
        rol.setActivo(false);
        return toRoleResponse(rolRepository.save(rol));
    }

    public void eliminarRol(String adminUserId, String rolId) {
        assertAdmin(adminUserId);
        Rol rol = getRolOrThrow(rolId);

        if (Boolean.TRUE.equals(rol.getSistema())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se puede eliminar un rol del sistema");
        }

        long usuariosAsignados = usuarioRepository.countByRolIgnoreCase(rol.getNombre());
        if (usuariosAsignados > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "No se puede eliminar un rol que tiene usuarios asignados");
        }

        rolRepository.delete(rol);
    }

    private void validateRolCanBeDisabled(Rol rol) {
        if ("ADMIN".equalsIgnoreCase(rol.getNombre())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se puede desactivar el rol ADMIN");
        }

        long usuariosAsignados = usuarioRepository.countByRolIgnoreCase(rol.getNombre());
        if (usuariosAsignados > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede desactivar el rol porque tiene usuarios asignados");
        }
    }

    private Usuario getUsuarioOrThrow(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El id del usuario es obligatorio");
        }

        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private Rol getRolOrThrow(String rolId) {
        if (rolId == null || rolId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El id del rol es obligatorio");
        }

        return rolRepository.findById(rolId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
    }

    private void assertAdmin(String adminUserId) {
        if (adminUserId == null || adminUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }

        Usuario admin = usuarioRepository.findByIdAndActivo(adminUserId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));

        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede realizar esta acción");
        }
    }

    private Rol resolveRolActivo(String roleName) {
        String normalizedRoleName = normalizeRoleName(roleName);

        Rol rol = rolRepository.findByNombreIgnoreCase(normalizedRoleName)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El rol indicado no existe"));

        if (!Boolean.TRUE.equals(rol.getActivo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El rol indicado está desactivado");
        }

        return rol;
    }

    private void validateCreateUserRequest(CreateUserRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para crear el usuario");
        }

        requireText(request.getNombre(), "nombre");
        normalizeEmail(request.getCorreo());
        normalizePassword(request.getPassword());

        if (request.getRol() == null || request.getRol().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar un rol para el usuario");
        }
    }

    private String normalizeDepartamentoId(String departamentoId) {
        if (departamentoId == null) {
            return null;
        }

        String normalized = departamentoId.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (!departamentoRepository.existsById(normalized)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El departamento indicado no existe");
        }

        return normalized;
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El nombre del rol es obligatorio");
        }

        String normalized = roleName.trim()
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_');

        if (!ROLE_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El rol debe tener de 3 a 30 caracteres y solo usar letras, números o guion bajo");
        }

        return normalized;
    }

    private String normalizeEmail(String email) {
        String normalized = requireText(email, "correo").toLowerCase(Locale.ROOT);
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El correo no es válido");
        }
        return normalized;
    }

    private String normalizePassword(String password) {
        String normalized = requireText(password, "password");
        if (normalized.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }
        return value.trim();
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

    private RoleResponse toRoleResponse(Rol rol) {
        return RoleResponse.builder()
                .id(rol.getId())
                .nombre(rol.getNombre())
                .descripcion(rol.getDescripcion())
                .activo(rol.getActivo())
                .sistema(rol.getSistema())
                .build();
    }
}