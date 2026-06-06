package com.leo.politicas_de_negocio.movilia.clasificacion.controller;

import com.leo.politicas_de_negocio.movilia.clasificacion.dto.ClasificarSolicitudMovilRequest;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.ClasificarSolicitudMovilResponse;
import com.leo.politicas_de_negocio.movilia.clasificacion.service.MovilClasificacionSolicitudService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MovilClasificacionSolicitudController {

    private final MovilClasificacionSolicitudService service;

    @PostMapping("/api/movil/ia/clasificar-solicitud")
    public ResponseEntity<ClasificarSolicitudMovilResponse> clasificarSolicitud(
            @RequestHeader("X-User-Id") String usuarioMovilId,
            @RequestBody ClasificarSolicitudMovilRequest request
    ) {
        return ResponseEntity.ok(service.clasificar(usuarioMovilId, request));
    }
}
