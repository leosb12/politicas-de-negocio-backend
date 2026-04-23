package com.leo.politicas_de_negocio.notifications.application;

import com.leo.politicas_de_negocio.notifications.dto.DeviceTokenResponse;
import com.leo.politicas_de_negocio.notifications.dto.RegisterDeviceTokenRequest;
import com.leo.politicas_de_negocio.notifications.model.DevicePlatform;
import com.leo.politicas_de_negocio.notifications.model.DeviceToken;
import com.leo.politicas_de_negocio.notifications.repository.DeviceTokenRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RegisterDeviceTokenUseCase {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UsuarioRepository usuarioRepository;

    public DeviceTokenResponse execute(String actorUserId, RegisterDeviceTokenRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        String token = requireToken(request);
        LocalDateTime now = LocalDateTime.now();

        DeviceToken deviceToken = deviceTokenRepository.findByToken(token)
                .map(existing -> updateExisting(existing, actor.getId(), request, now))
                .orElseGet(() -> createNew(actor.getId(), token, request, now));

        DeviceToken saved = saveIdempotently(deviceToken, actor.getId(), request, now);
        return toResponse(saved);
    }

    private DeviceToken updateExisting(
            DeviceToken existing,
            String userId,
            RegisterDeviceTokenRequest request,
            LocalDateTime now
    ) {
        existing.setUserId(userId);
        existing.setPlatform(DevicePlatform.from(request.getPlatform()));
        existing.setDeviceId(normalizeOptional(request.getDeviceId()));
        existing.setAppVersion(normalizeOptional(request.getAppVersion()));
        existing.setActive(true);
        existing.setLastSeenAt(now);
        existing.setDeactivatedAt(null);
        existing.setLastFailureCode(null);
        existing.setLastFailureAt(null);
        existing.setFailureCount(0);
        if (existing.getRegisteredAt() == null) {
            existing.setRegisteredAt(now);
        }
        return existing;
    }

    private DeviceToken createNew(String userId, String token, RegisterDeviceTokenRequest request, LocalDateTime now) {
        return DeviceToken.builder()
                .userId(userId)
                .token(token)
                .platform(DevicePlatform.from(request.getPlatform()))
                .deviceId(normalizeOptional(request.getDeviceId()))
                .appVersion(normalizeOptional(request.getAppVersion()))
                .active(true)
                .registeredAt(now)
                .lastSeenAt(now)
                .failureCount(0)
                .build();
    }

    private DeviceToken saveIdempotently(
            DeviceToken deviceToken,
            String userId,
            RegisterDeviceTokenRequest request,
            LocalDateTime now
    ) {
        try {
            return deviceTokenRepository.save(deviceToken);
        } catch (DuplicateKeyException ex) {
            return deviceTokenRepository.findByToken(deviceToken.getToken())
                    .map(existing -> deviceTokenRepository.save(updateExisting(existing, userId, request, now)))
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "No se pudo registrar el token del dispositivo"
                    ));
        }
    }

    private Usuario assertUsuarioActivo(String userId) {
        String normalized = normalizeRequired(userId, "Debe enviar X-User-Id o X-Admin-User-Id");
        return usuarioRepository.findByIdAndActivo(normalized, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
    }

    private String requireToken(RegisterDeviceTokenRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el token del dispositivo");
        }
        return normalizeRequired(request.getToken(), "El token del dispositivo es obligatorio");
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DeviceTokenResponse toResponse(DeviceToken deviceToken) {
        return DeviceTokenResponse.builder()
                .id(deviceToken.getId())
                .userId(deviceToken.getUserId())
                .platform(deviceToken.getPlatform())
                .deviceId(deviceToken.getDeviceId())
                .appVersion(deviceToken.getAppVersion())
                .active(deviceToken.getActive())
                .registeredAt(deviceToken.getRegisteredAt())
                .lastSeenAt(deviceToken.getLastSeenAt())
                .build();
    }
}
