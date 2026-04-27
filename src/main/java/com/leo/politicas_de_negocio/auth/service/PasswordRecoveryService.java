package com.leo.politicas_de_negocio.auth.service;

import com.leo.politicas_de_negocio.auth.dto.ForgotPasswordRequest;
import com.leo.politicas_de_negocio.auth.dto.ResetPasswordRequest;
import com.leo.politicas_de_negocio.auth.model.PasswordResetToken;
import com.leo.politicas_de_negocio.auth.repository.PasswordResetTokenRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class PasswordRecoveryService {

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetEmailService passwordResetEmailService;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordRecoveryService(UsuarioRepository usuarioRepository,
                                   PasswordResetTokenRepository passwordResetTokenRepository,
                                   PasswordResetEmailService passwordResetEmailService,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.usuarioRepository = usuarioRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetEmailService = passwordResetEmailService;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl;
    }

    public void requestPasswordReset(ForgotPasswordRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el correo");
        }

        String email = normalizeEmail(request.getEmail());

        usuarioRepository.findByCorreoAndActivo(email, true).ifPresent(usuario -> {
            invalidatePreviousTokens(usuario.getId());

            String tokenValue = generateToken();
            LocalDateTime now = LocalDateTime.now();

            PasswordResetToken token = PasswordResetToken.builder()
                    .usuarioId(usuario.getId())
                    .token(tokenValue)
                    .fechaCreacion(now)
                    .fechaExpiracion(now.plusMinutes(TOKEN_EXPIRATION_MINUTES))
                    .usado(false)
                    .build();

            passwordResetTokenRepository.save(token);
            passwordResetEmailService.sendResetLink(usuario.getCorreo(), buildResetLink(tokenValue));
        });
    }

    public void resetPassword(ResetPasswordRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el token y la nueva contrasena");
        }

        String tokenValue = normalizeToken(request.getToken());
        String newPassword = normalizePassword(request.getNewPassword());

        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Token invalido"));

        if (Boolean.TRUE.equals(token.getUsado())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token ya usado");
        }

        if (token.getFechaExpiracion() == null || token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Token expirado");
        }

        Usuario usuario = usuarioRepository.findByIdAndActivo(token.getUsuarioId(), true)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Token invalido"));

        usuario.setPassword(passwordEncoder.encode(newPassword));
        usuarioRepository.save(usuario);

        token.setUsado(true);
        token.setFechaUso(LocalDateTime.now());
        passwordResetTokenRepository.save(token);
    }

    private void invalidatePreviousTokens(String usuarioId) {
        List<PasswordResetToken> activeTokens = passwordResetTokenRepository.findAllByUsuarioIdAndUsadoFalse(usuarioId);
        if (activeTokens.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (PasswordResetToken token : activeTokens) {
            token.setUsado(true);
            token.setFechaUso(now);
        }

        passwordResetTokenRepository.saveAll(activeTokens);
    }

    private String buildResetLink(String token) {
        String normalizedFrontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return normalizedFrontendUrl + "/reset-password?token=" + token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        String normalized = requireText(email, "email").toLowerCase(Locale.ROOT);
        if (!normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El correo no es valido");
        }

        return normalized;
    }

    private String normalizeToken(String token) {
        return requireText(token, "token");
    }

    private String normalizePassword(String password) {
        String normalized = requireText(password, "newPassword");
        if (normalized.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 6 caracteres");
        }

        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El campo " + fieldName + " es obligatorio");
        }

        return value.trim();
    }
}