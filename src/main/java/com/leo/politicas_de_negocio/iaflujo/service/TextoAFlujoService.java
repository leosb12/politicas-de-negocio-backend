package com.leo.politicas_de_negocio.iaflujo.service;

import com.leo.politicas_de_negocio.iaflujo.client.TextoAFlujoIaClient;
import com.leo.politicas_de_negocio.iaflujo.dto.TextoAFlujoRequest;
import com.leo.politicas_de_negocio.iaflujo.dto.TextoAFlujoResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TextoAFlujoService {

    private final TextoAFlujoIaClient textoAFlujoIaClient;

    public TextoAFlujoResponse generarFlujo(TextoAFlujoRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar la descripcion del flujo");
        }

        String descripcion = normalize(request.getDescripcion());
        if (descripcion == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La descripcion es obligatoria");
        }
        request.setDescripcion(descripcion);

        TextoAFlujoResponse response = textoAFlujoIaClient.generarFlujo(request);
        if (response == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo obtener una propuesta de flujo desde el servicio de IA"
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
