package com.leo.politicas_de_negocio.pagos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.payments")
public class PaymentsProperties {

    private Stripe stripe = new Stripe();
    private Paypal paypal = new Paypal();
    private Demo demo = new Demo();

    @Data
    public static class Stripe {
        private String successUrl;
        private String cancelUrl;
    }

    @Data
    public static class Paypal {
        private String baseUrl;
        private String businessEmail;
        private String returnUrl;
        private String cancelUrl;
    }

    @Data
    public static class Demo {
        private boolean manualPaypalApprovalEnabled = true;
    }
}
