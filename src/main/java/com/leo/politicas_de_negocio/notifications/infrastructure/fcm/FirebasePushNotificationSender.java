package com.leo.politicas_de_negocio.notifications.infrastructure.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryResult;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;
import com.leo.politicas_de_negocio.notifications.application.port.PushNotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Map;

@RequiredArgsConstructor
public class FirebasePushNotificationSender implements PushNotificationSender {

    private final FirebaseMessaging firebaseMessaging;

    @Override
    public PushDeliveryResult send(String token, PushNotificationMessage message) {
        try {
            String messageId = firebaseMessaging.send(toFirebaseMessage(token, message));
            return PushDeliveryResult.sent(messageId);
        } catch (FirebaseMessagingException ex) {
            MessagingErrorCode messagingErrorCode = ex.getMessagingErrorCode();
            String errorCode = resolveErrorCode(ex, messagingErrorCode);
            return PushDeliveryResult.failed(errorCode, ex.getMessage(), isInvalidToken(messagingErrorCode));
        }
    }

    private Message toFirebaseMessage(String token, PushNotificationMessage message) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setContentAvailable(true)
                                .build())
                        .build());

        if (message != null && (StringUtils.hasText(message.getTitle()) || StringUtils.hasText(message.getBody()))) {
            builder.setNotification(Notification.builder()
                    .setTitle(normalizeNullable(message.getTitle()))
                    .setBody(normalizeNullable(message.getBody()))
                    .build());
        }

        Map<String, String> data = message != null && message.getData() != null
                ? message.getData().toMap()
                : Map.of();
        if (!data.isEmpty()) {
            builder.putAllData(data);
        }

        return builder.build();
    }

    private boolean isInvalidToken(MessagingErrorCode errorCode) {
        return errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private String resolveErrorCode(FirebaseMessagingException ex, MessagingErrorCode messagingErrorCode) {
        if (messagingErrorCode != null) {
            return messagingErrorCode.name();
        }
        if (ex.getErrorCode() != null) {
            return ex.getErrorCode().name();
        }
        return "FIREBASE_MESSAGING_ERROR";
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
