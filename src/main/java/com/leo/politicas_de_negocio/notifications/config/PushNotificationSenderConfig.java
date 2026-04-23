package com.leo.politicas_de_negocio.notifications.config;

import com.google.firebase.messaging.FirebaseMessaging;
import com.leo.politicas_de_negocio.notifications.application.port.PushNotificationSender;
import com.leo.politicas_de_negocio.notifications.infrastructure.fcm.FirebasePushNotificationSender;
import com.leo.politicas_de_negocio.notifications.infrastructure.fcm.NoopPushNotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PushNotificationSenderConfig {

    @Bean
    @ConditionalOnBean(FirebaseMessaging.class)
    public PushNotificationSender firebasePushNotificationSender(FirebaseMessaging firebaseMessaging) {
        return new FirebasePushNotificationSender(firebaseMessaging);
    }

    @Bean
    @ConditionalOnMissingBean(PushNotificationSender.class)
    public PushNotificationSender noopPushNotificationSender() {
        return new NoopPushNotificationSender();
    }
}
