package com.leo.politicas_de_negocio.notifications.application.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushDeliveryResult {

    private boolean success;
    private String messageId;
    private String errorCode;
    private String errorMessage;
    private boolean invalidToken;

    public static PushDeliveryResult sent(String messageId) {
        return PushDeliveryResult.builder()
                .success(true)
                .messageId(messageId)
                .build();
    }

    public static PushDeliveryResult failed(String errorCode, String errorMessage, boolean invalidToken) {
        return PushDeliveryResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .invalidToken(invalidToken)
                .build();
    }
}
