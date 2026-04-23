package com.leo.politicas_de_negocio.notifications.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notification_device_tokens")
@CompoundIndexes({
        @CompoundIndex(name = "idx_device_tokens_user_active", def = "{'userId': 1, 'active': 1}"),
        @CompoundIndex(name = "idx_device_tokens_platform_active", def = "{'platform': 1, 'active': 1}")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String token;

    private DevicePlatform platform;
    private String deviceId;
    private String appVersion;
    private Boolean active;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime deactivatedAt;
    private String lastFailureCode;
    private LocalDateTime lastFailureAt;
    private Integer failureCount;
}
