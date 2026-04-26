package com.leo.politicas_de_negocio.guide.controlador;

import com.leo.politicas_de_negocio.guide.usuario_movil.dto.RespuestaGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.SolicitudGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.servicio.ServicioGuiaUsuarioMovil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GuiaUsuarioMovilController {

    private final ServicioGuiaUsuarioMovil servicioGuiaUsuarioMovil;

    @PostMapping("/api/guide/mobile-user")
    public ResponseEntity<RespuestaGuiaUsuarioMovil> guiarUsuarioMovil(
            @RequestHeader("X-User-Id") String usuarioMovilId,
            @RequestBody SolicitudGuiaUsuarioMovil solicitud
    ) {
        return ResponseEntity.ok(servicioGuiaUsuarioMovil.guiarUsuarioMovil(usuarioMovilId, solicitud));
    }
}
