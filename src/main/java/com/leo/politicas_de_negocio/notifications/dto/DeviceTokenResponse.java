package com.leo.politicas_de_negocio.notifications.dto;

import com.leo.politicas_de_negocio.notifications.model.DevicePlatform;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceTokenResponse {

    private String id;
    private String userId;
    private DevicePlatform platform;
    private String role;
    private String deviceId;
    private String appVersion;
    private Boolean active;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
}
