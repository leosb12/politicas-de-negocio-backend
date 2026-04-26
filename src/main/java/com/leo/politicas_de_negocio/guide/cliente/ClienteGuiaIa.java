package com.leo.politicas_de_negocio.guide.cliente;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leo.politicas_de_negocio.analiticas.config.AnalyticsIaProperties;
import com.leo.politicas_de_negocio.guide.administrador.dto.RespuestaGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.SolicitudGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.funcionario.dto.RespuestaGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.SolicitudGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.RespuestaGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.SolicitudGuiaUsuarioMovil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class ClienteGuiaIa {

    private static final Logger log = LoggerFactory.getLogger(ClienteGuiaIa.class);
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final RestTemplate analyticsIaRestTemplate;
    private final AnalyticsIaProperties analyticsIaProperties;

    public RespuestaGuiaAdministrador guiarAdministrador(SolicitudGuiaAdministrador cargaUtil) {
        return publicarParaRespuesta("/api/ia/guide/admin", cargaUtil, RespuestaGuiaAdministrador.class);
    }

    public RespuestaGuiaFuncionario guiarFuncionario(SolicitudGuiaFuncionario cargaUtil) {
        return publicarParaRespuesta("/api/ia/guide/employee", cargaUtil, RespuestaGuiaFuncionario.class);
    }

    public RespuestaGuiaUsuarioMovil guiarUsuarioMovil(SolicitudGuiaUsuarioMovil cargaUtil) {
        return publicarParaRespuesta("/api/ia/guide/mobile-user", cargaUtil, RespuestaGuiaUsuarioMovil.class);
    }

    private <T> T publicarParaRespuesta(String ruta, Object cargaUtil, Class<T> tipoRespuesta) {
        String url = construirUrl(ruta);
        String cargaSerializada = jsonSeguro(cargaUtil);
        log.info("[GUIDE-IA-REQ] POST {} body={}", url, truncar(cargaSerializada));

        HttpEntity<Object> entidadSolicitud = new HttpEntity<>(cargaUtil, construirHeaders());

        try {
            ResponseEntity<String> entidadRespuesta = analyticsIaRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entidadSolicitud,
                    String.class
            );
            String cuerpo = entidadRespuesta.getBody();
            log.info("[GUIDE-IA-RES] {} status={} body={}", url, entidadRespuesta.getStatusCode().value(), truncar(cuerpo));
            if (cuerpo == null || cuerpo.isBlank()) {
                return null;
            }
            return JSON.readValue(cuerpo, tipoRespuesta);
        } catch (RestClientResponseException ex) {
            log.error(
                    "[GUIDE-IA-ERR] POST {} status={} requestBody={} responseBody={}",
                    url,
                    ex.getStatusCode().value(),
                    truncar(cargaSerializada),
                    truncar(ex.getResponseBodyAsString()),
                    ex
            );
            return null;
        } catch (RestClientException ex) {
            log.error(
                    "[GUIDE-IA-ERR] POST {} requestBody={} message={}",
                    url,
                    truncar(cargaSerializada),
                    ex.getMessage(),
                    ex
            );
            return null;
        } catch (JsonProcessingException ex) {
            log.error(
                    "[GUIDE-IA-ERR] POST {} response parse error message={}",
                    url,
                    ex.getMessage(),
                    ex
            );
            return null;
        }
    }

    private HttpHeaders construirHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String construirUrl(String ruta) {
        String baseUrl = analyticsIaProperties.getBaseUrl() != null
                ? analyticsIaProperties.getBaseUrl().trim()
                : "http://localhost:8001";
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + ruta;
    }

    private String jsonSeguro(Object valor) {
        if (valor == null) {
            return "null";
        }
        try {
            return JSON.writeValueAsString(valor);
        } catch (Exception ex) {
            return String.valueOf(valor);
        }
    }

    private String truncar(String texto) {
        if (texto == null) {
            return "null";
        }
        int limite = 3000;
        if (texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite) + "...";
    }
}
