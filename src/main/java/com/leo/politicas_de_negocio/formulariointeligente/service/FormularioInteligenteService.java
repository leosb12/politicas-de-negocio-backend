package com.leo.politicas_de_negocio.formulariointeligente.service;

import com.leo.politicas_de_negocio.formulariointeligente.client.FormularioInteligenteIaClient;
import com.leo.politicas_de_negocio.formulariointeligente.dto.FormularioInteligenteRequest;
import com.leo.politicas_de_negocio.formulariointeligente.dto.FormularioInteligenteResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FormularioInteligenteService {

    private final FormularioInteligenteIaClient formularioInteligenteIaClient;

    public FormularioInteligenteResponse completarFormulario(FormularioInteligenteRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos del formulario");
        }

        if (normalize(request.getActivityId()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "activityId es obligatorio");
        }
        if (normalize(request.getActivityName()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "activityName es obligatorio");
        }
        if (normalize(request.getPolicyName()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "policyName es obligatorio");
        }
        if (request.getFormSchema() == null || request.getFormSchema().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "formSchema debe incluir al menos un campo");
        }

        String userPrompt = normalize(request.getUserPrompt());
        if (userPrompt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "userPrompt es obligatorio");
        }
        request.setUserPrompt(userPrompt);

        FormularioInteligenteResponse response = formularioInteligenteIaClient.completarFormulario(request);
        if (response == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo completar el formulario desde el servicio de IA"
            );
        }

        return response;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
