package com.leo.politicas_de_negocio.notifications.dto;

import lombok.Data;

@Data
public class RegisterDeviceTokenRequest {

    private String userId;
    private String token;
    private String platform;
    private String role;
    private String deviceId;
    private String appVersion;
}
