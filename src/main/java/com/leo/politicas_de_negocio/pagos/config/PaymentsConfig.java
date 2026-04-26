package com.leo.politicas_de_negocio.pagos.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentsProperties.class)
public class PaymentsConfig {
}
