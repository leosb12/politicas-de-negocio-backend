package com.leo.politicas_de_negocio.notifications.application.port;

import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryResult;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;

public interface PushNotificationSender {

    PushDeliveryResult send(String token, PushNotificationMessage message);
}
