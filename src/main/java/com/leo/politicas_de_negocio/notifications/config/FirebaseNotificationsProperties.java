package com.leo.politicas_de_negocio.notifications.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notifications.firebase")
@Data
public class FirebaseNotificationsProperties {

    private boolean enabled = false;
    private String projectId;
    private String serviceAccountPath;
    private String serviceAccountBase64;
}
