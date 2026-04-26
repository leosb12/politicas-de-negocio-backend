package com.leo.politicas_de_negocio.pagos.service;

import com.leo.politicas_de_negocio.instancias.dto.CrearInstanciaRequest;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.pagos.config.PaymentsProperties;
import com.leo.politicas_de_negocio.pagos.dto.InicioInstanciaResponse;
import com.leo.politicas_de_negocio.pagos.model.Pago;
import com.leo.politicas_de_negocio.pagos.model.enums.EstadoPago;
import com.leo.politicas_de_negocio.pagos.repository.PagoRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private PoliticaNegocioRepository politicaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PoliticaNegocioService politicaNegocioService;
    @Mock
    private InstanciaPoliticaService instanciaPoliticaService;

    private AutoCloseable mocks;
    private PagoService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        PaymentsProperties properties = new PaymentsProperties();
        properties.getStripe().setSuccessUrl("http://localhost:3000/ok?session_id={CHECKOUT_SESSION_ID}");
        properties.getStripe().setCancelUrl("http://localhost:3000/cancel");
        properties.getPaypal().setBaseUrl("https://www.paypal.com/cgi-bin/webscr");
        properties.getPaypal().setBusinessEmail("demo@example.com");
        service = new PagoService(
                pagoRepository,
                politicaRepository,
                usuarioRepository,
                politicaNegocioService,
                instanciaPoliticaService,
                properties
        );
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_demo");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void evaluarInicioInstancia_debeResponderQueRequierePagoSinCrearInstancia() {
        Usuario actor = Usuario.builder().id("user-1").rol("USUARIO").activo(true).build();
        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Licencia")
                .estado(EstadoPolitica.ACTIVA)
                .requierePago(true)
                .montoPago(new BigDecimal("15.50"))
                .monedaPago("USD")
                .descripcionPago("Licencia municipal")
                .build();
        CrearInstanciaRequest request = new CrearInstanciaRequest();
        request.setPoliticaId("pol-1");

        when(usuarioRepository.findByIdAndActivo("user-1", true)).thenReturn(Optional.of(actor));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));

        InicioInstanciaResponse response = service.evaluarInicioInstancia("user-1", request);

        assertTrue(response.isRequierePago());
        assertEquals(new BigDecimal("15.50"), response.getMontoPago());
        assertEquals("USD", response.getMonedaPago());
        assertNull(response.getInstancia());
        verify(instanciaPoliticaService, never()).crearInstanciaDirecta(any(), any());
    }

    @Test
    void confirmarPaypalManual_debeIniciarInstanciaYMarcarPagoComoAprobadoManual() {
        Usuario admin = Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build();
        Pago pago = Pago.builder()
                .id("pay-1")
                .politicaId("pol-1")
                .usuarioId("user-1")
                .estado(EstadoPago.PENDIENTE_CONFIRMACION_PAYPAL)
                .monto(new BigDecimal("20"))
                .moneda("USD")
                .descripcion("Pago demo")
                .proveedor(com.leo.politicas_de_negocio.pagos.model.enums.ProveedorPago.PAYPAL)
                .codigoTramite("TRM-1")
                .build();
        InstanciaPolitica instancia = InstanciaPolitica.builder().id("inst-1").build();

        when(usuarioRepository.findByIdAndActivo("admin-1", true)).thenReturn(Optional.of(admin));
        when(pagoRepository.findById("pay-1")).thenReturn(Optional.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pagoRepository.findByUsuarioIdAndPoliticaIdAndEstadoInOrderByFechaCreacionDesc(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("pol-1"),
                anyCollection()
        )).thenReturn(List.of());
        when(instanciaPoliticaService.crearInstanciaDirecta(any(), any())).thenReturn(instancia);

        var response = service.confirmarPaypalManual("admin-1", "pay-1");

        assertEquals(EstadoPago.APROBADO_MANUALMENTE, response.getEstado());
        assertEquals("inst-1", response.getInstanciaId());
    }
}
