package com.leo.politicas_de_negocio.notifications.application.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushNotificationMessage {

    private String title;
    private String body;
    private PushDataPayload data;
}
