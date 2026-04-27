package com.leo.politicas_de_negocio.auth.service;

import com.leo.politicas_de_negocio.auth.dto.ChangePasswordRequest;
import com.leo.politicas_de_negocio.auth.dto.FuncionarioDepartamentoResponse;
import com.leo.politicas_de_negocio.auth.dto.LoginRequest;
import com.leo.politicas_de_negocio.auth.dto.LoginResponse;
import com.leo.politicas_de_negocio.auth.dto.RegisterMovilRequest;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class AuthService {

    private static final String ROL_MOVIL_DEFAULT = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioRepository usuarioRepository,
            DepartamentoRepository departamentoRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = usuarioRepository;
        this.departamentoRepository = departamentoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse loginWeb(LoginRequest request) {
        Usuario usuario = authenticateActiveUser(request);

        if ("USUARIO".equalsIgnoreCase(usuario.getRol())) {
            throw mobileUserWebAccessException();
        }

        return toLoginResponse(usuario);
    }

    public LoginResponse loginMovil(LoginRequest request) {
        Usuario usuario = authenticateActiveUser(request);
        return toLoginResponse(usuario);
    }

    public LoginResponse registerMovil(RegisterMovilRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para registrarse");
        }

        String nombre = requireText(request.getNombre(), "nombre");
        String correo = normalizeEmail(request.getCorreo());
        String password = normalizePassword(request.getPassword());

        if (usuarioRepository.existsByCorreo(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        Usuario nuevoUsuario = Usuario.builder()
                .nombre(nombre)
                .correo(correo)
                .password(passwordEncoder.encode(password))
                .rol(ROL_MOVIL_DEFAULT)
                .departamentoId(null)
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Usuario creado = usuarioRepository.save(nuevoUsuario);
        return toLoginResponse(creado);
    }

    public void changePassword(ChangePasswordRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos para cambiar la contrasena");
        }

        String correo = normalizeEmail(request.getCorreo());
        String passwordActual = requireText(request.getPasswordActual(), "passwordActual");
        String nuevaContrasena = normalizePassword(request.getNuevaContrasena());
        String confirmarNuevaContrasena = normalizePassword(request.getConfirmarNuevaContrasena());

        if (!nuevaContrasena.equals(confirmarNuevaContrasena)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Las contrasenas nuevas no coinciden");
        }

        if (passwordActual.equals(nuevaContrasena)) {
            throw new ApiException(HttpStatus.CONFLICT, "La nueva contrasena debe ser diferente a la actual");
        }

        Usuario usuario = usuarioRepository
                .findByCorreoAndActivo(correo, true)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No se encontro un usuario activo con ese correo"));

        if (!passwordMatches(usuario.getPassword(), passwordActual)) {
            throw invalidCredentialsException();
        }

        usuario.setPassword(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);
    }

    public FuncionarioDepartamentoResponse getFuncionarioDepartment(String funcionarioUserId) {
        Usuario funcionario = validateFuncionario(funcionarioUserId);
        String departamentoId = requireOptionalText(funcionario.getDepartamentoId());

        if (departamentoId == null) {
            return FuncionarioDepartamentoResponse.builder()
                    .id(null)
                    .nombre(null)
                    .build();
        }

        String departamentoNombre = departamentoRepository.findById(departamentoId)
                .map(departamento -> requireOptionalText(departamento.getNombre()))
                .orElse(departamentoId);

        return FuncionarioDepartamentoResponse.builder()
                .id(departamentoId)
                .nombre(departamentoNombre)
                .build();
    }

    private Usuario authenticateActiveUser(LoginRequest request) {
        if (request == null || isBlank(request.getCorreo()) || isBlank(request.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Correo y contrasena son obligatorios");
        }

        String correo = normalizeEmail(request.getCorreo());

        Usuario usuario = usuarioRepository
                .findByCorreoAndActivo(correo, true)
                .orElseThrow(this::invalidCredentialsException);

        if (!passwordMatches(usuario.getPassword(), request.getPassword())) {
            throw invalidCredentialsException();
        }

        return usuario;
    }

    private boolean passwordMatches(String storedPassword, String rawPassword) {
        if (storedPassword == null || rawPassword == null) {
            return false;
        }

        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        return Objects.equals(storedPassword, rawPassword);
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private LoginResponse toLoginResponse(Usuario usuario) {
        String departamentoId = usuario.getDepartamentoId();

        return LoginResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .departamentoId(departamentoId)
                .departamentoNombre(resolveDepartmentName(departamentoId))
                .build();
    }

    private Usuario validateFuncionario(String funcionarioUserId) {
        String funcionarioId = requireOptionalText(funcionarioUserId);
        if (funcionarioId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-User-Id");
        }

        Usuario funcionario = usuarioRepository
                .findByIdAndActivo(funcionarioId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));

        if (!"FUNCIONARIO".equalsIgnoreCase(funcionario.getRol())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Este endpoint solo esta disponible para el rol FUNCIONARIO"
            );
        }

        return funcionario;
    }

    private String normalizeEmail(String email) {
        String normalized = requireText(email, "correo").toLowerCase(Locale.ROOT);
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El correo no es valido");
        }

        return normalized;
    }

    private String normalizePassword(String password) {
        String normalized = requireText(password, "password");
        if (normalized.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 6 caracteres");
        }

        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }

        return value.trim();
    }

    private String requireOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveDepartmentName(String departamentoId) {
        String normalizedDepartmentId = requireOptionalText(departamentoId);
        if (normalizedDepartmentId == null) {
            return null;
        }

        return departamentoRepository.findById(normalizedDepartmentId)
                .map(departamento -> requireOptionalText(departamento.getNombre()))
                .orElse(normalizedDepartmentId);
    }

    private ApiException invalidCredentialsException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
    }

    private ApiException mobileUserWebAccessException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Usuario movil, ingrese desde la aplicacion");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
