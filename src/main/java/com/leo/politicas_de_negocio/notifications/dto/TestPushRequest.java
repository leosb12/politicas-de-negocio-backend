package com.leo.politicas_de_negocio.notifications.dto;

import lombok.Data;

@Data
public class TestPushRequest {

    private String title;
    private String body;
    private String type;
    private String tramiteId;
    private String tareaId;
    private String action;
}
