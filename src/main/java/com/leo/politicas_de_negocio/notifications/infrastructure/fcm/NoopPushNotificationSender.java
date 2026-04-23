package com.leo.politicas_de_negocio.notifications.infrastructure.fcm;

import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryResult;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;
import com.leo.politicas_de_negocio.notifications.application.port.PushNotificationSender;

public class NoopPushNotificationSender implements PushNotificationSender {

    @Override
    public PushDeliveryResult send(String token, PushNotificationMessage message) {
        return PushDeliveryResult.failed(
                "FIREBASE_NOT_CONFIGURED",
                "Firebase Cloud Messaging esta deshabilitado o no tiene credenciales configuradas",
                false
        );
    }
}
