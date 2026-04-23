package com.leo.politicas_de_negocio.notifications.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Configuration
@RequiredArgsConstructor
public class FirebaseNotificationsConfig {

    private static final String FIREBASE_APP_NAME = "politicas-de-negocio-notifications";

    private final FirebaseNotificationsProperties properties;

    @Bean(destroyMethod = "delete")
    @ConditionalOnProperty(prefix = "app.notifications.firebase", name = "enabled", havingValue = "true")
    public FirebaseApp firebaseNotificationsApp() {
        return FirebaseApp.getApps().stream()
                .filter(app -> FIREBASE_APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(this::initializeFirebaseApp);
    }

    @Bean
    @ConditionalOnBean(FirebaseApp.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseNotificationsApp) {
        return FirebaseMessaging.getInstance(firebaseNotificationsApp);
    }

    private FirebaseApp initializeFirebaseApp() {
        try {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(resolveCredentials());

            if (StringUtils.hasText(properties.getProjectId())) {
                builder.setProjectId(properties.getProjectId().trim());
            }

            return FirebaseApp.initializeApp(builder.build(), FIREBASE_APP_NAME);
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "No se pudo inicializar Firebase Admin SDK. Configure FIREBASE_SERVICE_ACCOUNT_BASE64, "
                            + "FIREBASE_CREDENTIALS_PATH, FIREBASE_SERVICE_ACCOUNT_PATH o GOOGLE_APPLICATION_CREDENTIALS.",
                    ex
            );
        }
    }

    private GoogleCredentials resolveCredentials() throws IOException {
        if (StringUtils.hasText(properties.getServiceAccountBase64())) {
            byte[] decoded = Base64.getDecoder().decode(properties.getServiceAccountBase64().trim());
            try (InputStream inputStream = new ByteArrayInputStream(decoded)) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        if (StringUtils.hasText(properties.getServiceAccountPath())) {
            try (InputStream inputStream = Files.newInputStream(Path.of(properties.getServiceAccountPath().trim()))) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }

        return GoogleCredentials.getApplicationDefault();
    }
}
