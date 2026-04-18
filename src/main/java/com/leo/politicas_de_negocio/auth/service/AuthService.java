package com.leo.politicas_de_negocio.auth.service;

import com.leo.politicas_de_negocio.auth.dto.LoginRequest;
import com.leo.politicas_de_negocio.auth.dto.LoginResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public LoginResponse loginWeb(LoginRequest request) {
        Usuario usuario = authenticateActiveUser(request);

        // Regla del parcial:
        // el rol USUARIO no puede entrar a la web.
        // debe parecer como si no existiera.
        if ("USUARIO".equalsIgnoreCase(usuario.getRol())) {
            throw invalidCredentialsException();
        }

        return LoginResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .departamentoId(usuario.getDepartamentoId())
                .build();
    }

    public LoginResponse loginMovil(LoginRequest request) {
        Usuario usuario = authenticateActiveUser(request);

        // En móvil sí puede entrar cualquier usuario activo,
        // incluyendo el rol USUARIO.
        return LoginResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .departamentoId(usuario.getDepartamentoId())
                .build();
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

    private ApiException invalidCredentialsException() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}