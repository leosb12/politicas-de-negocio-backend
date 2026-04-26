package com.leo.politicas_de_negocio.notifications.controller;

import com.leo.politicas_de_negocio.notifications.application.RegisterDeviceTokenUseCase;
import com.leo.politicas_de_negocio.notifications.dto.DeviceTokenResponse;
import com.leo.politicas_de_negocio.notifications.dto.RegisterDeviceTokenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push/tokens")
@RequiredArgsConstructor
public class PushDeviceTokenController {

    private final RegisterDeviceTokenUseCase registerDeviceTokenUseCase;
    private final MobileActorResolver mobileActorResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceTokenResponse register(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestBody RegisterDeviceTokenRequest request
    ) {
        String actorUserId = mobileActorResolver.resolve(userId, adminUserId);
        return registerDeviceTokenUseCase.execute(actorUserId, request);
    }
}
