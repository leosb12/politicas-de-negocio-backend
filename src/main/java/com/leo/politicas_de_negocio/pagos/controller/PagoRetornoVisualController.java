package com.leo.politicas_de_negocio.pagos.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PagoRetornoVisualController {

    @GetMapping(value = "/pagos/stripe/success", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> stripeSuccess() {
        return ResponseEntity.ok(html("Pago exitoso. Vuelve a la app."));
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