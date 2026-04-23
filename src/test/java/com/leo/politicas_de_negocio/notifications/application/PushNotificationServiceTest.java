package com.leo.politicas_de_negocio.notifications.application;

import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryReport;
import com.leo.politicas_de_negocio.notifications.application.model.PushDeliveryResult;
import com.leo.politicas_de_negocio.notifications.application.model.PushNotificationMessage;
import com.leo.politicas_de_negocio.notifications.model.DevicePlatform;
import com.leo.politicas_de_negocio.notifications.model.DeviceToken;
import com.leo.politicas_de_negocio.notifications.repository.DeviceTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotificationServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void sendToUser_debeEnviarATodosLosTokensActivos() {
        DeviceToken tokenA = token("dt-1", "token-a");
        DeviceToken tokenB = token("dt-2", "token-b");
        PushNotificationService service = new PushNotificationService(
                deviceTokenRepository,
                (token, message) -> PushDeliveryResult.sent("msg-" + token)
        );

        when(deviceTokenRepository.findByUserIdAndActiveTrue("u-1")).thenReturn(List.of(tokenA, tokenB));

        PushDeliveryReport report = service.sendToUser("u-1", PushNotificationMessage.builder().build());

        assertEquals(2, report.getTotalTokens());
        assertEquals(2, report.getSuccessCount());
        assertEquals(0, report.getFailureCount());
    }

    @Test
    void sendToUser_debeDesactivarTokensInvalidos() {
        DeviceToken invalidToken = token("dt-1", "token-invalido");
        PushNotificationService service = new PushNotificationService(
                deviceTokenRepository,
                (token, message) -> PushDeliveryResult.failed("UNREGISTERED", "Token no registrado", true)
        );

        when(deviceTokenRepository.findByUserIdAndActiveTrue("u-1")).thenReturn(List.of(invalidToken));

        PushDeliveryReport report = service.sendToUser("u-1", PushNotificationMessage.builder().build());

        assertEquals(1, report.getFailureCount());
        assertEquals(1, report.getDeactivatedTokenCount());
        assertFalse(invalidToken.getActive());
        assertEquals("UNREGISTERED", invalidToken.getLastFailureCode());
        verify(deviceTokenRepository).save(invalidToken);
    }

    private DeviceToken token(String id, String value) {
        return DeviceToken.builder()
                .id(id)
                .userId("u-1")
                .token(value)
                .platform(DevicePlatform.ANDROID)
                .active(true)
                .failureCount(0)
                .build();
    }
}
