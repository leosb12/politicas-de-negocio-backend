package com.leo.politicas_de_negocio.pagos.controller;

import com.leo.politicas_de_negocio.pagos.service.PagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PagoRetornoVisualController {

    private final PagoService pagoService;

    @GetMapping(value = "/pagos/stripe/success", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> stripeSuccess(
            @RequestParam(value = "session_id", required = false) String sessionIdLegacy,
            @RequestParam(value = "sessionId", required = false) String sessionId
    ) {
        String resolvedSessionId = sessionId != null && !sessionId.isBlank() ? sessionId : sessionIdLegacy;
        if (resolvedSessionId != null && !resolvedSessionId.isBlank()) {
            try {
                pagoService.verificarStripeDesdeRetornoPublico(resolvedSessionId);
            } catch (Exception ex) {
                return ResponseEntity.ok(html("Pago recibido. Vuelve a la app para verificar."));
            }
        }
        return ResponseEntity.ok(html("Pago realizado con éxito. Puedes volver a la aplicación y actualizar la sección \"Mis Trámites\" para ver el estado."));
    }

    @GetMapping(value = "/pagos/cancelado", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pagoCancelado() {
        return ResponseEntity.ok(html("Pago cancelado. Vuelve a la app."));
    }

    @GetMapping(value = "/pagos/paypal/retorno", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paypalRetorno() {
        return ResponseEntity.ok(html("Pago PayPal recibido. Vuelve a la app para verificar."));
    }

    private String html(String message) {
        return "<!doctype html>"
                + "<html lang=\"es\">"
                + "<head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Retorno de pago</title>"
                + "</head>"
                + "<body style=\"font-family:Arial,sans-serif;padding:24px;line-height:1.4;\">"
                + "<h1 style=\"margin:0 0 12px 0;font-size:24px;\">" + escapeHtml(message) + "</h1>"
                + "</body>"
                + "</html>";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}