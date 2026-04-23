package com.leo.politicas_de_negocio.notifications.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushResultResponse {

    private boolean success;
    private String messageId;
    private String errorCode;
    private boolean invalidToken;
}
