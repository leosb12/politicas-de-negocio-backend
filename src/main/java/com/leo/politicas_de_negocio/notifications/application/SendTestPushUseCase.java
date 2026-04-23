package com.leo.politicas_de_negocio.notifications.application;

import com.leo.politicas_de_negocio.notifications.application.model.PushDataPayload;
import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryReport;
import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryResult;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;
import com.leo.politicas_de_negocio.notifications.dto.PushResultResponse;
import com.leo.politicas_de_negocio.notifications.dto.PushSendResponse;
import com.leo.politicas_de_negocio.notifications.dto.TestPushRequest;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SendTestPushUseCase {

    private static final String DEFAULT_TITLE = "Notificacion de prueba";
    private static final String DEFAULT_BODY = "Tu backend ya puede enviar push notifications.";

    private final PushNotificationService pushNotificationService;
    private final UsuarioRepository usuarioRepository;

    public PushSendResponse execute(String actorUserId, TestPushRequest request) {
        String userId = assertUsuarioActivo(actorUserId);
        PushNotificationMessage message = buildMessage(request);
        PushDeliveryReport report = pushNotificationService.sendToUser(userId, message);
        return toResponse(report);
    }

    private String assertUsuarioActivo(String userId) {
        String normalized = requireText(userId, "Debe enviar X-User-Id o X-Admin-User-Id");
        return usuarioRepository.findByIdAndActivo(normalized, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"))
                .getId();
    }

    private PushNotificationMessage buildMessage(TestPushRequest request) {
        return PushNotificationMessage.builder()
                .title(resolveOrDefault(request != null ? request.getTitle() : null, DEFAULT_TITLE))
                .body(resolveOrDefault(request != null ? request.getBody() : null, DEFAULT_BODY))
                .data(PushDataPayload.builder()
                        .type(request != null ? request.getType() : null)
                        .tramiteId(request != null ? request.getTramiteId() : null)
                        .tareaId(request != null ? request.getTareaId() : null)
                        .action(request != null ? request.getAction() : null)
                        .build())
                .build();
    }

    private PushSendResponse toResponse(PushDeliveryReport report) {
        List<PushResultResponse> results = report.getResults().stream()
                .map(this::toResultResponse)
                .toList();

        return PushSendResponse.builder()
                .totalTokens(report.getTotalTokens())
                .enviados(report.getSuccessCount())
                .fallidos(report.getFailureCount())
                .tokensDesactivados(report.getDeactivatedTokenCount())
                .resultados(results)
                .build();
    }

    private PushResultResponse toResultResponse(PushDeliveryResult result) {
        return PushResultResponse.builder()
                .success(result.isSuccess())
                .messageId(result.getMessageId())
                .errorCode(result.getErrorCode())
                .invalidToken(result.isInvalidToken())
                .build();
    }

    private String resolveOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
