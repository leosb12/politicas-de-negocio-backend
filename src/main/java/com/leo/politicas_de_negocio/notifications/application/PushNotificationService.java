package com.leo.politicas_de_negocio.notifications.application;

import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryReport;
import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryResult;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;
import com.leo.politicas_de_negocio.notifications.application.port.PushNotificationSender;
import com.leo.politicas_de_negocio.notifications.model.DeviceToken;
import com.leo.politicas_de_negocio.notifications.repository.DeviceTokenRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final String ERROR_ENVIO_INESPERADO = "PUSH_SEND_UNEXPECTED_ERROR";

    private final DeviceTokenRepository deviceTokenRepository;
    private final PushNotificationSender pushNotificationSender;

    public PushDeliveryResult sendToToken(String token, PushNotificationMessage message) {
        String normalizedToken = requireText(token, "El token destino es obligatorio");
        return sendSafely(normalizedToken, message);
    }

    public PushDeliveryReport sendToUser(String userId, PushNotificationMessage message) {
        String normalizedUserId = requireText(userId, "El usuario destino es obligatorio");
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdAndActiveTrue(normalizedUserId);
        if (tokens.isEmpty()) {
            return PushDeliveryReport.empty();
        }

        List<PushDeliveryResult> results = new ArrayList<>();
        int deactivatedTokens = 0;
        LocalDateTime now = LocalDateTime.now();

        for (DeviceToken token : tokens) {
            PushDeliveryResult result = sendSafely(token.getToken(), message);
            results.add(result);

            if (result.isSuccess()) {
                clearFailureStateIfNeeded(token);
                continue;
            }

            registerFailure(token, result, now);
            if (result.isInvalidToken()) {
                deactivateToken(token, now);
                deactivatedTokens++;
            }
            deviceTokenRepository.save(token);
        }

        return PushDeliveryReport.from(results, deactivatedTokens);
    }

    private PushDeliveryResult sendSafely(String token, PushNotificationMessage message) {
        try {
            return pushNotificationSender.send(token, message);
        } catch (RuntimeException ex) {
            return PushDeliveryResult.failed(ERROR_ENVIO_INESPERADO, ex.getMessage(), false);
        }
    }

    private void clearFailureStateIfNeeded(DeviceToken token) {
        if (token.getFailureCount() == null || token.getFailureCount() == 0) {
            return;
        }

        token.setFailureCount(0);
        token.setLastFailureCode(null);
        token.setLastFailureAt(null);
        deviceTokenRepository.save(token);
    }

    private void registerFailure(DeviceToken token, PushDeliveryResult result, LocalDateTime now) {
        int failureCount = token.getFailureCount() != null ? token.getFailureCount() : 0;
        token.setFailureCount(failureCount + 1);
        token.setLastFailureCode(result.getErrorCode());
        token.setLastFailureAt(now);
    }

    private void deactivateToken(DeviceToken token, LocalDateTime now) {
        token.setActive(false);
        token.setDeactivatedAt(now);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
