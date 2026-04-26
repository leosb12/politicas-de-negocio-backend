package com.leo.politicas_de_negocio.notifications.application;

import com.leo.politicas_de_negocio.notifications.dto.DeviceTokenResponse;
import com.leo.politicas_de_negocio.notifications.dto.RegisterDeviceTokenRequest;
import com.leo.politicas_de_negocio.notifications.model.DevicePlatform;
import com.leo.politicas_de_negocio.notifications.model.DeviceToken;
import com.leo.politicas_de_negocio.notifications.repository.DeviceTokenRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterDeviceTokenUseCaseTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AutoCloseable mocks;
    private RegisterDeviceTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        useCase = new RegisterDeviceTokenUseCase(deviceTokenRepository, usuarioRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void execute_debeCrearTokenActivoParaUsuario() {
        RegisterDeviceTokenRequest request = request(" fcm-token ", "android");
        request.setUserId("u-1");
        request.setRole("FUNCIONARIO");
        Usuario usuario = Usuario.builder().id("u-1").rol("FUNCIONARIO").activo(true).build();

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(usuario));
        when(deviceTokenRepository.findByToken("fcm-token")).thenReturn(Optional.empty());
        when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocation -> {
            DeviceToken token = invocation.getArgument(0);
            token.setId("dt-1");
            return token;
        });

        DeviceTokenResponse response = useCase.execute("u-1", request);

        assertEquals("dt-1", response.getId());
        assertEquals("u-1", response.getUserId());
        assertEquals(DevicePlatform.ANDROID, response.getPlatform());
        assertEquals("FUNCIONARIO", response.getRole());
        assertEquals(true, response.getActive());
        assertNotNull(response.getRegisteredAt());
        assertNotNull(response.getLastSeenAt());
    }

    @Test
    void execute_debeReasignarTokenExistenteAlUsuarioActual() {
        RegisterDeviceTokenRequest request = request("fcm-token", "ios");
        request.setUserId("u-2");
        request.setRole("ADMIN");
        Usuario usuario = Usuario.builder().id("u-2").rol("ADMIN").activo(true).build();
        DeviceToken existente = DeviceToken.builder()
                .id("dt-1")
                .userId("u-1")
                .token("fcm-token")
                .platform(DevicePlatform.ANDROID)
                .role("FUNCIONARIO")
                .active(false)
                .registeredAt(LocalDateTime.now().minusDays(3))
                .failureCount(2)
                .lastFailureCode("UNREGISTERED")
                .build();

        when(usuarioRepository.findByIdAndActivo("u-2", true)).thenReturn(Optional.of(usuario));
        when(deviceTokenRepository.findByToken("fcm-token")).thenReturn(Optional.of(existente));
        when(deviceTokenRepository.save(any(DeviceToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceTokenResponse response = useCase.execute("u-2", request);

        assertEquals("u-2", response.getUserId());
        assertEquals(DevicePlatform.IOS, response.getPlatform());
        assertEquals("ADMIN", response.getRole());
        assertEquals(true, response.getActive());
        assertEquals(0, existente.getFailureCount());
        verify(deviceTokenRepository).save(existente);
    }

    @Test
    void execute_debeRechazarRegistroSiUserIdNoCoincideConActor() {
        RegisterDeviceTokenRequest request = request("web-token", "web");
        request.setUserId("otro-usuario");
        Usuario usuario = Usuario.builder().id("u-3").rol("FUNCIONARIO").activo(true).build();

        when(usuarioRepository.findByIdAndActivo("u-3", true)).thenReturn(Optional.of(usuario));

        assertThrows(RuntimeException.class, () -> useCase.execute("u-3", request));
    }

    private RegisterDeviceTokenRequest request(String token, String platform) {
        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setToken(token);
        request.setPlatform(platform);
        request.setDeviceId("device-1");
        request.setAppVersion("1.0.0");
        return request;
    }
}
