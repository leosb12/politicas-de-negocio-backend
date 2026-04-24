package com.leo.politicas_de_negocio.analiticas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AnalyticsIaConfig {

    @Bean
    public RestTemplate analyticsIaRestTemplate(AnalyticsIaProperties analyticsIaProperties) {
        int timeoutMs = (int) Math.max(1000L, analyticsIaProperties.getTimeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }
}
