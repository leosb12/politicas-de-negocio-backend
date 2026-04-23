package com.leo.politicas_de_negocio.notifications.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PushSendResponse {

    private int totalTokens;
    private int enviados;
    private int fallidos;
    private int tokensDesactivados;
    private List<PushResultResponse> resultados;
}
