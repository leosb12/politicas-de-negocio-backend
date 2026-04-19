package com.leo.politicas_de_negocio.instancias.service;

import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.instancias.repository.HistorialInstanciaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class HistorialInstanciaServiceTest {

    @Mock
    private HistorialInstanciaRepository historialRepository;

    private AutoCloseable mocks;
    private HistorialInstanciaService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HistorialInstanciaService(historialRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void listarPorInstancia_debeRetornarOrdenadoPorFechaAsc() {
        HistorialInstancia e1 = HistorialInstancia.builder()
                .id("1")
                .instanciaId("inst-1")
                .fecha(LocalDateTime.now().minusMinutes(5))
                .build();

        HistorialInstancia e2 = HistorialInstancia.builder()
                .id("2")
                .instanciaId("inst-1")
                .fecha(LocalDateTime.now().minusMinutes(10))
                .build();

        when(historialRepository.findByInstanciaIdOrderByFechaAsc("inst-1"))
                .thenReturn(List.of(e1, e2));

        List<HistorialInstancia> result = service.listarPorInstancia("inst-1");

        assertEquals("2", result.get(0).getId());
        assertEquals("1", result.get(1).getId());
    }
}
