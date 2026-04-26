package com.leo.politicas_de_negocio.iaflujo.service;

import com.leo.politicas_de_negocio.iaflujo.client.TextoAFlujoIaClient;
import com.leo.politicas_de_negocio.iaflujo.dto.TextoAFlujoRequest;
import com.leo.politicas_de_negocio.iaflujo.dto.TextoAFlujoResponse;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class TextoAFlujoServiceTest {

    @Mock
    private TextoAFlujoIaClient textoAFlujoIaClient;

    private AutoCloseable mocks;
    private TextoAFlujoService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new TextoAFlujoService(textoAFlujoIaClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void generarFlujo_debeDelegarAlClienteCuandoElRequestEsValido() {
        TextoAFlujoRequest request = new TextoAFlujoRequest();
        request.setDescripcion("  Crear flujo de aprobacion  ");

        TextoAFlujoResponse response = new TextoAFlujoResponse();
        when(textoAFlujoIaClient.generarFlujo(request)).thenReturn(response);

        TextoAFlujoResponse actual = service.generarFlujo(request);

        assertEquals(response, actual);
        assertEquals("Crear flujo de aprobacion", request.getDescripcion());
    }

    @Test
    void generarFlujo_debeFallarSiLaDescripcionNoExiste() {
        TextoAFlujoRequest request = new TextoAFlujoRequest();

        ApiException ex = assertThrows(ApiException.class, () -> service.generarFlujo(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void generarFlujo_debeFallarSiIaNoRespondeConPayloadUtil() {
        TextoAFlujoRequest request = new TextoAFlujoRequest();
        request.setDescripcion("Crear flujo");
        when(textoAFlujoIaClient.generarFlujo(request)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> service.generarFlujo(request));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }
}
