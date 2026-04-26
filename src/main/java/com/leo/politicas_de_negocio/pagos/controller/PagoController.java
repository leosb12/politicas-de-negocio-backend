package com.leo.politicas_de_negocio.pagos.controller;

import com.leo.politicas_de_negocio.pagos.dto.PagoResponse;
import com.leo.politicas_de_negocio.pagos.dto.PaypalLinkRequest;
import com.leo.politicas_de_negocio.pagos.dto.StripeCheckoutRequest;
import com.leo.politicas_de_negocio.pagos.service.PagoService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/stripe/crear-checkout")
    public ResponseEntity<PagoResponse> crearCheckoutStripe(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestBody StripeCheckoutRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pagoService.crearStripeCheckout(resolverActorUserId(userId, adminUserId), request));
    }

    @GetMapping("/stripe/verificar")
    public ResponseEntity<PagoResponse> verificarStripe(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestParam("sessionId") String sessionId
    ) {
        return ResponseEntity.ok(pagoService.verificarStripe(resolverActorUserId(userId, adminUserId), sessionId));
    }

    @PostMapping("/paypal/crear-link")
    public ResponseEntity<PagoResponse> crearLinkPaypal(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestBody PaypalLinkRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pagoService.crearPaypalLink(resolverActorUserId(userId, adminUserId), request));
    }

    @PostMapping("/paypal/{pagoId}/confirmar-manual")
    public ResponseEntity<PagoResponse> confirmarPaypalManual(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String pagoId
    ) {
        return ResponseEntity.ok(pagoService.confirmarPaypalManual(adminUserId, pagoId));
    }

    @GetMapping("/{pagoId}")
    public ResponseEntity<PagoResponse> obtenerPago(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String pagoId
    ) {
        return ResponseEntity.ok(pagoService.obtenerPago(resolverActorUserId(userId, adminUserId), pagoId));
    }

    private String resolverActorUserId(String userId, String adminUserId) {
        String normalizadoUser = normalizar(userId);
        if (normalizadoUser != null) {
            return normalizadoUser;
        }
        String normalizadoAdmin = normalizar(adminUserId);
        if (normalizadoAdmin != null) {
            return normalizadoAdmin;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id");
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
