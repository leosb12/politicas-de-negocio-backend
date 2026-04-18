package com.leo.politicas_de_negocio.config;

import com.leo.politicas_de_negocio.dto.politica.colaboracion.NodoBloqueoResponse;
import com.leo.politicas_de_negocio.service.PoliticaPresenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PoliticaPresenciaSessionListener {

    private final PoliticaPresenciaService presenciaService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        PoliticaPresenciaService.ResultadoDesconexion resultado = presenciaService.desconectarSesion(event.getSessionId());
        if (resultado == null) {
            return;
        }

        messagingTemplate.convertAndSend(topicPresencia(resultado.politicaId()), resultado.presencia());
        for (NodoBloqueoResponse bloqueo : resultado.bloqueosActualizados()) {
            messagingTemplate.convertAndSend(topicBloqueos(resultado.politicaId()), bloqueo);
        }
    }

    private String topicPresencia(String politicaId) {
        return "/topic/politicas/" + politicaId + "/presencia";
    }

    private String topicBloqueos(String politicaId) {
        return "/topic/politicas/" + politicaId + "/nodos-bloqueados";
    }
}
