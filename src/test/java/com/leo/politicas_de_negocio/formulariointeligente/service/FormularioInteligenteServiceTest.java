package com.leo.politicas_de_negocio.formulariointeligente.service;

import com.leo.politicas_de_negocio.formulariointeligente.client.FormularioInteligenteIaClient;
import com.leo.politicas_de_negocio.formulariointeligente.dto.FormularioInteligenteRequest;
import com.leo.politicas_de_negocio.formulariointeligente.dto.FormularioInteligenteResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class FormularioInteligenteServiceTest {

    @Mock
    private FormularioInteligenteIaClient formularioInteligenteIaClient;

    private AutoCloseable mocks;
    private FormularioInteligenteService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new FormularioInteligenteService(formularioInteligenteIaClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void completarFormulario_debeDelegarAlClienteCuandoElRequestEsValido() {
        FormularioInteligenteRequest request = validRequest();
        FormularioInteligenteResponse response = new FormularioInteligenteResponse();
        when(formularioInteligenteIaClient.completarFormulario(request)).thenReturn(response);

        FormularioInteligenteResponse actual = service.completarFormulario(request);

        assertEquals(response, actual);
        assertEquals("Rechaza la solicitud", request.getUserPrompt());
    }

    @Test
    void completarFormulario_debeFallarSiFaltaElPrompt() {
        FormularioInteligenteRequest request = validRequest();
        request.setUserPrompt("   ");

        ApiException ex = assertThrows(ApiException.class, () -> service.completarFormulario(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void completarFormulario_debeFallarSiIaNoRespondeConPayloadUtil() {
        FormularioInteligenteRequest request = validRequest();
        when(formularioInteligenteIaClient.completarFormulario(request)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> service.completarFormulario(request));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    private FormularioInteligenteRequest validRequest() {
        FormularioInteligenteRequest request = new FormularioInteligenteRequest();
        request.setActivityId("task-1");
        request.setActivityName("Revision legal");
        request.setPolicyName("Instalacion de medidor");
        request.setUserPrompt("  Rechaza la solicitud  ");

        FormularioInteligenteRequest.FormFieldSchema field = new FormularioInteligenteRequest.FormFieldSchema();
        field.setId("decision");
        field.setLabel("Decision");
        field.setType("select");
        field.setRequired(true);
        field.setOptions(List.of("aprobado", "rechazado"));
        request.setFormSchema(List.of(field));
        return request;
    }
}
