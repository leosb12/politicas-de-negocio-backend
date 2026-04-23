package com.leo.politicas_de_negocio.notifications.application.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class PushDataPayload {

    private String type;
    private String tramiteId;
    private String tareaId;
    private String action;

    public Map<String, String> toMap() {
        Map<String, String> data = new LinkedHashMap<>();
        putIfPresent(data, "type", type);
        putIfPresent(data, "tramiteId", tramiteId);
        putIfPresent(data, "tareaId", tareaId);
        putIfPresent(data, "action", action);
        return data;
    }

    private void putIfPresent(Map<String, String> data, String key, String value) {
        if (StringUtils.hasText(value)) {
            data.put(key, value.trim());
        }
    }
}
