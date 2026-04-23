package com.leo.politicas_de_negocio.notifications.controller;

import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MobileActorResolver {

    public String resolve(String userId, String adminUserId) {
        String normalizedUser = normalize(userId);
        if (normalizedUser != null) {
            return normalizedUser;
        }

        String normalizedAdmin = normalize(adminUserId);
        if (normalizedAdmin != null) {
            return normalizedAdmin;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
