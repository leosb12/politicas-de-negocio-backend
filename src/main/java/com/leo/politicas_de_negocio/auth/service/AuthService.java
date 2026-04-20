package com.leo.politicas_de_negocio.auth.service;

import com.leo.politicas_de_negocio.auth.dto.LoginRequest;
import com.leo.politicas_de_negocio.auth.dto.LoginResponse;
import com.leo.politicas_de_negocio.auth.dto.RegisterMovilRequest;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class AuthService {

    private static final String ROL_MOVIL_DEFAULT = "USUARIO";

    private final UsuarioRepository usuarioRepository;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public LoginResponse loginWeb(LoginRequest request) {
        Usuario usuario = authenticateActiveUser(request);

        // Regla del parcial:
        // el rol USUARIO no puede entrar a la web.
        // debe ver un mensaje claro para entrar por la app movil.
        if ("USUARIO".equalsIgnoreCase(usuario.getRol())) {
            throw mobileUserWebAccessException();
        }

        return toLoginResponse(usuario);
    }

    public LoginResponse loginMovil(LoginRequest request) {
        Usuario usuario = authenticateActiveUser(request);

        // En móvil sí puede entrar cualquier usuario activo,
        // incluyendo el rol USUARIO.
        return toLoginResponse(usuario);
    }

    public LoginResponse registerMovil(RegisterMovilRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar datos para registrarse");
        }

        String nombre = requireText(request.getNombre(), "nombre");
        String correo = normalizeEmail(request.getCorreo());
        String password = normalizePassword(request.getPassword());

        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        Usuario nuevoUsuario = Usuario.builder()
                .nombre(nombre)
                .correo(correo)
                .password(password)
                .rol(ROL_MOVIL_DEFAULT)
                .departamentoId(null)
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        Usuario creado = usuarioRepository.save(nuevoUsuario);
        return toLoginResponse(creado);
    }

    private Usuario authenticateActiveUser(LoginRequest request) {
        if (request == null || isBlank(request.getCorreo()) || isBlank(request.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Correo y contraseña son obligatorios");
        }

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCaseAndActivo(request.getCorreo().trim(), true)
                .orElseThrow(this::invalidCredentialsException);

        if (!Objects.equals(usuario.getPassword(), request.getPassword())) {
            throw invalidCredentialsException();
        }

        return usuario;
    }

    private LoginResponse toLoginResponse(Usuario usuario) {
        return LoginResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .departamentoId(usuario.getDepartamentoId())
                .build();
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

    private ApiException invalidCredentialsException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    private ApiException mobileUserWebAccessException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Usuario movil, ingrese desde la aplicacion");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}