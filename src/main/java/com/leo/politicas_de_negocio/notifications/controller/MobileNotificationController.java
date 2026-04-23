package com.leo.politicas_de_negocio.notifications.controller;

import com.leo.politicas_de_negocio.notifications.application.SendTestPushUseCase;
import com.leo.politicas_de_negocio.notifications.dto.PushSendResponse;
import com.leo.politicas_de_negocio.notifications.dto.TestPushRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/notifications")
@RequiredArgsConstructor
public class MobileNotificationController {

    private final SendTestPushUseCase sendTestPushUseCase;
    private final MobileActorResolver mobileActorResolver;

    @PostMapping("/test")
    public PushSendResponse sendTestPush(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestBody(required = false) TestPushRequest request
    ) {
        String actorUserId = mobileActorResolver.resolve(userId, adminUserId);
        return sendTestPushUseCase.execute(actorUserId, request);
    }
}
