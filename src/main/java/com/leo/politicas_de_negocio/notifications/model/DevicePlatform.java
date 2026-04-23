package com.leo.politicas_de_negocio.notifications.model;

import java.util.Locale;

public enum DevicePlatform {
    ANDROID,
    IOS,
    WEB,
    UNKNOWN;

    public static DevicePlatform from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ANDROID" -> ANDROID;
            case "IOS", "I_OS", "APPLE" -> IOS;
            case "WEB", "BROWSER" -> WEB;
            default -> UNKNOWN;
        };
    }
}
