package com.leo.politicas_de_negocio.pagos.service;

import com.leo.politicas_de_negocio.instancias.dto.CrearInstanciaRequest;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.pagos.config.PaymentsProperties;
import com.leo.politicas_de_negocio.pagos.dto.InicioInstanciaResponse;
import com.leo.politicas_de_negocio.pagos.dto.PagoResponse;
import com.leo.politicas_de_negocio.pagos.dto.PaypalLinkRequest;
import com.leo.politicas_de_negocio.pagos.dto.StripeCheckoutRequest;
import com.leo.politicas_de_negocio.pagos.model.Pago;
import com.leo.politicas_de_negocio.pagos.model.enums.EstadoPago;
import com.leo.politicas_de_negocio.pagos.model.enums.ProveedorPago;
import com.leo.politicas_de_negocio.pagos.repository.PagoRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final String DEFAULT_MONEDA = "USD";
    private static final Set<EstadoPago> ESTADOS_NO_FINALES = Set.of(
            EstadoPago.PENDIENTE,
            EstadoPago.PENDIENTE_CONFIRMACION_PAYPAL
    );
    private static final Set<EstadoPago> ESTADOS_APROBADOS = Set.of(
            EstadoPago.APROBADO,
            EstadoPago.APROBADO_MANUALMENTE
    );

    private final PagoRepository pagoRepository;
    private final PoliticaNegocioRepository politicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioService politicaNegocioService;
    private final InstanciaPoliticaService instanciaPoliticaService;
    private final PaymentsProperties paymentsProperties;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    public InicioInstanciaResponse evaluarInicioInstancia(String actorUserId, CrearInstanciaRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        PoliticaNegocio politica = cargarPoliticaActiva(request != null ? request.getPoliticaId() : null);
        politicaNegocioService.validarInicioPoliticaPorActor(actor, politica);

        if (!Boolean.TRUE.equals(politica.getRequierePago())) {
            InstanciaPolitica instancia = instanciaPoliticaService.crearInstanciaDirecta(actor.getId(), request);
            return InicioInstanciaResponse.builder()
                    .requierePago(false)
                    .mensaje("Instancia iniciada correctamente")
                    .politicaId(politica.getId())
                    .politicaNombre(politica.getNombre())
                    .instancia(instancia)
                    .build();
        }

        return InicioInstanciaResponse.builder()
                .requierePago(true)
                .mensaje("La politica requiere pago antes de iniciar la instancia")
                .politicaId(politica.getId())
                .politicaNombre(politica.getNombre())
                .montoPago(politica.getMontoPago())
                .monedaPago(resolveMoneda(politica))
                .descripcionPago(resolveDescripcion(politica))
                .build();
    }

    public PagoResponse crearStripeCheckout(String actorUserId, StripeCheckoutRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        validarUsuarioRequest(actor.getId(), request.getUsuarioId());
        PoliticaNegocio politica = cargarPoliticaPagada(request.getPoliticaId(), actor);
        validarMontoYDescripcionCliente(request.getMonto(), request.getDescripcion(), politica);

        Pago pagoAprobado = buscarPagoAprobadoSinDuplicar(actor.getId(), politica.getId());
        if (pagoAprobado != null) {
            return toResponse(pagoAprobado, null);
        }

        Pago pagoExistente = pagoRepository.findFirstByUsuarioIdAndPoliticaIdAndProveedorAndEstadoInOrderByFechaCreacionDesc(
                actor.getId(),
                politica.getId(),
                ProveedorPago.STRIPE,
                ESTADOS_NO_FINALES
        ).orElse(null);
        if (pagoExistente != null && normalizar(pagoExistente.getStripeSessionId()) != null) {
            return toResponse(pagoExistente, resolveStripeCheckoutUrlFromSession(pagoExistente.getStripeSessionId()));
        }

        validarStripeConfigurado();

        Pago pago = Pago.builder()
                .politicaId(politica.getId())
                .usuarioId(actor.getId())
                .proveedor(ProveedorPago.STRIPE)
                .monto(politica.getMontoPago())
                .moneda(resolveMoneda(politica))
                .descripcion(resolveDescripcion(politica))
                .estado(EstadoPago.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .codigoTramite(normalizar(request.getCodigoTramite()))
                .datosContexto(copiarMapa(request.getDatosContexto()))
                .build();
        pago = pagoRepository.save(pago);

        try {
            Stripe.apiKey = stripeSecretKey;
            Session session = Session.create(buildSessionParams(pago, politica, request));
            pago.setStripeSessionId(session.getId());
            pagoRepository.save(pago);
            return toResponse(pago, session.getUrl());
        } catch (StripeException ex) {
            pago.setEstado(EstadoPago.FALLIDO);
            pagoRepository.save(pago);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo crear la sesion de Stripe");
        }
    }

    public PagoResponse verificarStripe(String actorUserId, String sessionId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        String normalizedSessionId = normalizar(sessionId);
        if (normalizedSessionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar sessionId");
        }

        Pago pago = pagoRepository.findByStripeSessionId(normalizedSessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pago Stripe no encontrado"));
        validarPropietarioPago(actor, pago);

        if (ESTADOS_APROBADOS.contains(pago.getEstado()) && normalizar(pago.getInstanciaId()) != null) {
            return toResponse(pago, null);
        }

        try {
            Stripe.apiKey = stripeSecretKey;
            Session session = Session.retrieve(normalizedSessionId);
            String paymentStatus = normalizar(session.getPaymentStatus());
            if ("paid".equalsIgnoreCase(paymentStatus)) {
                aprobarPagoStripeYCrearInstanciaSiCorresponde(actor.getId(), pago);
            } else if ("unpaid".equalsIgnoreCase(paymentStatus)) {
                pago.setEstado(EstadoPago.PENDIENTE);
                pagoRepository.save(pago);
            }
            return toResponse(pagoRepository.findById(pago.getId()).orElse(pago), session.getUrl());
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo verificar la sesion de Stripe");
        }
    }

    public PagoResponse crearPaypalLink(String actorUserId, PaypalLinkRequest request) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        validarUsuarioRequest(actor.getId(), request.getUsuarioId());
        PoliticaNegocio politica = cargarPoliticaPagada(request.getPoliticaId(), actor);
        validarMontoYDescripcionCliente(request.getMonto(), request.getDescripcion(), politica);

        Pago pagoAprobado = buscarPagoAprobadoSinDuplicar(actor.getId(), politica.getId());
        if (pagoAprobado != null) {
            return toResponse(pagoAprobado, null);
        }

        Optional<Pago> pagoExistente = pagoRepository.findFirstByUsuarioIdAndPoliticaIdAndProveedorAndEstadoInOrderByFechaCreacionDesc(
                actor.getId(),
                politica.getId(),
                ProveedorPago.PAYPAL,
                ESTADOS_NO_FINALES
        );
        if (pagoExistente.isPresent()) {
            return toResponse(pagoExistente.get(), null);
        }

        Pago pago = Pago.builder()
                .politicaId(politica.getId())
                .usuarioId(actor.getId())
                .proveedor(ProveedorPago.PAYPAL)
                .monto(politica.getMontoPago())
                .moneda(resolveMoneda(politica))
                .descripcion(resolveDescripcion(politica))
                .estado(EstadoPago.PENDIENTE_CONFIRMACION_PAYPAL)
                .fechaCreacion(LocalDateTime.now())
                .codigoTramite(normalizar(request.getCodigoTramite()))
                .datosContexto(copiarMapa(request.getDatosContexto()))
                .build();
        pago.setPaypalUrl(buildPaypalUrl(pago));
        pago = pagoRepository.save(pago);
        return toResponse(pago, null);
    }

    public PagoResponse obtenerPago(String actorUserId, String pagoId) {
        Usuario actor = assertUsuarioActivo(actorUserId);
        Pago pago = buscarPago(pagoId);
        validarPropietarioPago(actor, pago);
        return toResponse(pago, null);
    }

    public PagoResponse confirmarPaypalManual(String adminUserId, String pagoId) {
        Usuario admin = assertUsuarioActivo(adminUserId);
        if (!"ADMIN".equalsIgnoreCase(normalizar(admin.getRol()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede confirmar pagos PayPal manualmente");
        }
        if (!paymentsProperties.getDemo().isManualPaypalApprovalEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "La confirmacion manual de PayPal no esta habilitada");
        }

        Pago pago = buscarPago(pagoId);
        if (pago.getProveedor() != ProveedorPago.PAYPAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El pago indicado no es de PayPal");
        }
        if (pago.getEstado() == EstadoPago.APROBADO_MANUALMENTE && normalizar(pago.getInstanciaId()) != null) {
            return toResponse(pago, null);
        }
        if (pago.getEstado() != EstadoPago.PENDIENTE_CONFIRMACION_PAYPAL) {
            throw new ApiException(HttpStatus.CONFLICT, "Solo se pueden aprobar manualmente pagos PayPal pendientes");
        }

        pago.setEstado(EstadoPago.APROBADO_MANUALMENTE);
        pago.setFechaConfirmacion(LocalDateTime.now());
        pagoRepository.save(pago);
        iniciarInstanciaDesdePago(pago);
        return toResponse(pagoRepository.findById(pago.getId()).orElse(pago), null);
    }

    private SessionCreateParams buildSessionParams(Pago pago, PoliticaNegocio politica, StripeCheckoutRequest request) {
        String successUrl = resolveSuccessUrl(request.getSuccessUrl());
        String cancelUrl = resolveCancelUrl(request.getCancelUrl());

        return SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(pago.getId())
                .putMetadata("pagoId", pago.getId())
                .putMetadata("politicaId", politica.getId())
                .putMetadata("usuarioId", pago.getUsuarioId())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(resolveMoneda(politica).toLowerCase(Locale.ROOT))
                                                .setUnitAmount(toStripeAmountInCents(politica.getMontoPago()))
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(resolveDescripcion(politica))
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    private void aprobarPagoStripeYCrearInstanciaSiCorresponde(String actorUserId, Pago pago) {
        if (!ESTADOS_APROBADOS.contains(pago.getEstado())) {
            pago.setEstado(EstadoPago.APROBADO);
            pago.setFechaConfirmacion(LocalDateTime.now());
            pagoRepository.save(pago);
        }
        iniciarInstanciaDesdePago(pago);
    }

    private void iniciarInstanciaDesdePago(Pago pago) {
        if (normalizar(pago.getInstanciaId()) != null) {
            return;
        }

        List<Pago> pagosAprobados = pagoRepository.findByUsuarioIdAndPoliticaIdAndEstadoInOrderByFechaCreacionDesc(
                pago.getUsuarioId(),
                pago.getPoliticaId(),
                ESTADOS_APROBADOS
        );
        for (Pago approved : pagosAprobados) {
            if (!pago.getId().equals(approved.getId()) && normalizar(approved.getInstanciaId()) != null) {
                pago.setInstanciaId(approved.getInstanciaId());
                pagoRepository.save(pago);
                return;
            }
        }

        CrearInstanciaRequest request = new CrearInstanciaRequest();
        request.setPoliticaId(pago.getPoliticaId());
        request.setCodigoTramite(pago.getCodigoTramite());
        request.setDatosContexto(copiarMapa(pago.getDatosContexto()));

        InstanciaPolitica instancia = instanciaPoliticaService.crearInstanciaDirecta(pago.getUsuarioId(), request);
        pago.setInstanciaId(instancia.getId());
        pagoRepository.save(pago);
    }

    private Pago buscarPago(String pagoId) {
        String normalizedId = normalizar(pagoId);
        if (normalizedId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar pagoId");
        }
        return pagoRepository.findById(normalizedId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private void validarPropietarioPago(Usuario actor, Pago pago) {
        if (actor.getId().equals(pago.getUsuarioId())) {
            return;
        }
        if ("ADMIN".equalsIgnoreCase(normalizar(actor.getRol()))) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "No tiene permisos para consultar este pago");
    }

    private void validarUsuarioRequest(String actorUserId, String requestUserId) {
        String normalizedRequestUser = normalizar(requestUserId);
        if (normalizedRequestUser != null && !actorUserId.equals(normalizedRequestUser)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "El usuario autenticado no coincide con usuarioId");
        }
    }

    private PoliticaNegocio cargarPoliticaActiva(String politicaId) {
        String normalizedPoliticaId = normalizar(politicaId);
        if (normalizedPoliticaId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar politicaId");
        }
        PoliticaNegocio politica = politicaRepository.findById(normalizedPoliticaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Politica no encontrada con ID: " + normalizedPoliticaId));
        if (politica.getEstado() != EstadoPolitica.ACTIVA) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Solo se puede iniciar una instancia con una politica ACTIVA");
        }
        return politica;
    }

    private PoliticaNegocio cargarPoliticaPagada(String politicaId, Usuario actor) {
        PoliticaNegocio politica = cargarPoliticaActiva(politicaId);
        politicaNegocioService.validarInicioPoliticaPorActor(actor, politica);
        if (!Boolean.TRUE.equals(politica.getRequierePago())) {
            throw new ApiException(HttpStatus.CONFLICT, "La politica indicada no requiere pago");
        }
        if (politica.getMontoPago() == null || politica.getMontoPago().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "La politica tiene configuracion de pago invalida");
        }
        return politica;
    }

    private Usuario assertUsuarioActivo(String actorUserId) {
        String normalizedId = normalizar(actorUserId);
        if (normalizedId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id");
        }
        return usuarioRepository.findByIdAndActivo(normalizedId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no autorizado"));
    }

    private void validarStripeConfigurado() {
        if (normalizar(stripeSecretKey) == null) {
            throw new ApiException(HttpStatus.CONFLICT, "stripe.secret-key no esta configurada");
        }
    }

    private void validarMontoYDescripcionCliente(BigDecimal monto, String descripcion, PoliticaNegocio politica) {
        if (monto != null && monto.compareTo(politica.getMontoPago()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El monto enviado no coincide con el configurado en la politica");
        }
        String normalizedDescripcion = normalizar(descripcion);
        if (normalizedDescripcion != null && !normalizedDescripcion.equals(resolveDescripcion(politica))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La descripcion enviada no coincide con la politica");
        }
    }

    private String resolveMoneda(PoliticaNegocio politica) {
        String moneda = normalizar(politica.getMonedaPago());
        return moneda != null ? moneda.toUpperCase(Locale.ROOT) : DEFAULT_MONEDA;
    }

    private String resolveDescripcion(PoliticaNegocio politica) {
        String descripcion = normalizar(politica.getDescripcionPago());
        return descripcion != null ? descripcion : politica.getNombre();
    }

    private Long toStripeAmountInCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String resolveSuccessUrl(String requestSuccessUrl) {
        String successUrl = normalizar(requestSuccessUrl);
        if (successUrl == null) {
            successUrl = normalizar(paymentsProperties.getStripe().getSuccessUrl());
        }
        if (successUrl == null) {
            throw new ApiException(HttpStatus.CONFLICT, "No hay successUrl configurada para Stripe");
        }
        return successUrl.contains("{CHECKOUT_SESSION_ID}")
                ? successUrl
                : UriComponentsBuilder.fromUriString(successUrl)
                .queryParam("session_id", "{CHECKOUT_SESSION_ID}")
                .build(false)
                .toUriString();
    }

    private String resolveCancelUrl(String requestCancelUrl) {
        String cancelUrl = normalizar(requestCancelUrl);
        if (cancelUrl == null) {
            cancelUrl = normalizar(paymentsProperties.getStripe().getCancelUrl());
        }
        if (cancelUrl == null) {
            throw new ApiException(HttpStatus.CONFLICT, "No hay cancelUrl configurada para Stripe");
        }
        return cancelUrl;
    }

    private String buildPaypalUrl(Pago pago) {
        String baseUrl = normalizar(paymentsProperties.getPaypal().getBaseUrl());
        String businessEmail = normalizar(paymentsProperties.getPaypal().getBusinessEmail());
        if (baseUrl == null || businessEmail == null) {
            throw new ApiException(HttpStatus.CONFLICT, "La configuracion de PayPal no esta completa");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("cmd", "_xclick")
                .queryParam("business", businessEmail)
                .queryParam("amount", pago.getMonto())
                .queryParam("currency_code", pago.getMoneda())
                .queryParam("item_name", resolveSafeDescripcionPago(pago));

        String returnUrl = normalizar(paymentsProperties.getPaypal().getReturnUrl());
        if (returnUrl != null) {
            builder.queryParam("return", returnUrl)
                    .queryParam("custom", pago.getId());
        }

        String cancelUrl = normalizar(paymentsProperties.getPaypal().getCancelUrl());
        if (cancelUrl != null) {
            builder.queryParam("cancel_return", cancelUrl);
        }

        return builder.build(false).toUriString();
    }

    private String resolveSafeDescripcionPago(Pago pago) {
        String descripcion = normalizar(pago.getDescripcion());
        return descripcion != null ? descripcion : "Pago de politica";
    }

    private Pago buscarPagoAprobadoSinDuplicar(String usuarioId, String politicaId) {
        List<Pago> pagosAprobados = pagoRepository.findByUsuarioIdAndPoliticaIdAndEstadoInOrderByFechaCreacionDesc(
                usuarioId,
                politicaId,
                ESTADOS_APROBADOS
        );
        return pagosAprobados.isEmpty() ? null : pagosAprobados.get(0);
    }

    private String resolveStripeCheckoutUrlFromSession(String sessionId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            return Session.retrieve(sessionId).getUrl();
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> copiarMapa(Map<String, Object> datosContexto) {
        return datosContexto == null ? Map.of() : new LinkedHashMap<>(datosContexto);
    }

    private PagoResponse toResponse(Pago pago, String stripeCheckoutUrl) {
        return PagoResponse.builder()
                .id(pago.getId())
                .politicaId(pago.getPoliticaId())
                .instanciaId(pago.getInstanciaId())
                .usuarioId(pago.getUsuarioId())
                .proveedor(pago.getProveedor())
                .monto(pago.getMonto())
                .moneda(pago.getMoneda())
                .descripcion(pago.getDescripcion())
                .estado(pago.getEstado())
                .stripeSessionId(pago.getStripeSessionId())
                .stripeCheckoutUrl(stripeCheckoutUrl)
                .paypalUrl(pago.getPaypalUrl())
                .fechaCreacion(pago.getFechaCreacion())
                .fechaConfirmacion(pago.getFechaConfirmacion())
                .build();
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
