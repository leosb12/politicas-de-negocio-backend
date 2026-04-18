package com.leo.politicas_de_negocio.controller;

import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionErrorResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEstadoResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEventoRequest;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEventoResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.NodoBloqueoResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.NodoEdicionRequest;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.PresenciaJoinRequest;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.PresenciaPoliticaResponse;
import com.leo.politicas_de_negocio.exception.ApiException;
import com.leo.politicas_de_negocio.service.PoliticaColaboracionService;
import com.leo.politicas_de_negocio.service.PoliticaPresenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PoliticaColaboracionWsController {

    private final PoliticaColaboracionService colaboracionService;
    private final PoliticaPresenciaService presenciaService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/politicas/{politicaId}/eventos")
    public void recibirEvento(
            @DestinationVariable String politicaId,
            @Payload ColaboracionEventoRequest request
    ) {
        try {
            ColaboracionEventoResponse response = colaboracionService.aplicarEvento(politicaId, request);
            messagingTemplate.convertAndSend(topicEventos(politicaId), response);
        } catch (ApiException ex) {
            ColaboracionErrorResponse error = ColaboracionErrorResponse.builder()
                    .politicaId(politicaId)
                    .eventId(request != null ? request.getEventId() : null)
                    .codigo(String.valueOf(ex.getStatus().value()))
                    .mensaje(ex.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSend(topicErrores(politicaId), error);
        }
    }

    @MessageMapping("/politicas/{politicaId}/sync")
    public void solicitarSync(
            @DestinationVariable String politicaId,
            @Payload Map<String, String> request
    ) {
        String adminUserId = request != null ? request.get("actorUserId") : null;

        try {
            ColaboracionEstadoResponse estado = colaboracionService.obtenerEstadoActual(adminUserId, politicaId);
            messagingTemplate.convertAndSend(topicEstado(politicaId), estado);
        } catch (ApiException ex) {
            ColaboracionErrorResponse error = ColaboracionErrorResponse.builder()
                    .politicaId(politicaId)
                    .codigo(String.valueOf(ex.getStatus().value()))
                    .mensaje(ex.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSend(topicErrores(politicaId), error);
        }
    }

    @MessageMapping("/politicas/{politicaId}/presencia/join")
    public void registrarPresencia(
            @DestinationVariable String politicaId,
            @Payload PresenciaJoinRequest request,
            SimpMessageHeaderAccessor headers
    ) {
        try {
            PresenciaPoliticaResponse presencia =
                    presenciaService.registrarSesion(politicaId, headers.getSessionId(), request);
            messagingTemplate.convertAndSend(topicPresencia(politicaId), presencia);
        } catch (ApiException ex) {
            messagingTemplate.convertAndSend(topicErrores(politicaId), construirError(politicaId, null, ex));
        }
    }

    @MessageMapping("/politicas/{politicaId}/presencia/leave")
    public void removerPresencia(
            @DestinationVariable String politicaId,
            SimpMessageHeaderAccessor headers
    ) {
        try {
            PoliticaPresenciaService.ResultadoDesconexion resultado =
                    presenciaService.desregistrarSesion(politicaId, headers.getSessionId());

            if (resultado == null) {
                return;
            }

            messagingTemplate.convertAndSend(topicPresencia(politicaId), resultado.presencia());
            for (NodoBloqueoResponse bloqueo : resultado.bloqueosActualizados()) {
                messagingTemplate.convertAndSend(topicBloqueos(politicaId), bloqueo);
            }
        } catch (ApiException ex) {
            messagingTemplate.convertAndSend(topicErrores(politicaId), construirError(politicaId, null, ex));
        }
    }

    @MessageMapping("/politicas/{politicaId}/nodos/edicion")
    public void actualizarEdicionNodo(
            @DestinationVariable String politicaId,
            @Payload NodoEdicionRequest request,
            SimpMessageHeaderAccessor headers
    ) {
        try {
            NodoBloqueoResponse bloqueo = presenciaService.actualizarEdicionNodo(
                    politicaId,
                    headers.getSessionId(),
                    request
            );
            messagingTemplate.convertAndSend(topicBloqueos(politicaId), bloqueo);
        } catch (ApiException ex) {
            messagingTemplate.convertAndSend(
                    topicErrores(politicaId),
                    construirError(politicaId, request != null ? request.getActorUserId() : null, ex)
            );
        }
    }

    @MessageMapping("/politicas/{politicaId}/nodos/edicion/sync")
    public void syncBloqueosNodos(
            @DestinationVariable String politicaId,
            @Payload Map<String, String> request
    ) {
        String adminUserId = request != null ? request.get("actorUserId") : null;

        try {
            List<NodoBloqueoResponse> bloqueos = presenciaService.obtenerBloqueosActivos(adminUserId, politicaId);
            for (NodoBloqueoResponse bloqueo : bloqueos) {
                messagingTemplate.convertAndSend(topicBloqueos(politicaId), bloqueo);
            }
        } catch (ApiException ex) {
            messagingTemplate.convertAndSend(topicErrores(politicaId), construirError(politicaId, adminUserId, ex));
        }
    }

    private String topicEventos(String politicaId) {
        return "/topic/politicas/" + politicaId + "/eventos";
    }

    private String topicEstado(String politicaId) {
        return "/topic/politicas/" + politicaId + "/estado";
    }

    private String topicErrores(String politicaId) {
        return "/topic/politicas/" + politicaId + "/errores";
    }

    private String topicPresencia(String politicaId) {
        return "/topic/politicas/" + politicaId + "/presencia";
    }

    private String topicBloqueos(String politicaId) {
        return "/topic/politicas/" + politicaId + "/nodos-bloqueados";
    }

    private ColaboracionErrorResponse construirError(String politicaId, String eventId, ApiException ex) {
        return ColaboracionErrorResponse.builder()
                .politicaId(politicaId)
                .eventId(eventId)
                .codigo(String.valueOf(ex.getStatus().value()))
                .mensaje(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
