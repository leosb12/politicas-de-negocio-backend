package com.leo.politicas_de_negocio.guide.controlador;

import com.leo.politicas_de_negocio.guide.administrador.dto.RespuestaGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.SolicitudGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.servicio.ServicioGuiaAdministrador;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GuiaAdministradorController {

    private final ServicioGuiaAdministrador servicioGuiaAdministrador;

    @PostMapping("/api/guide/admin")
    public ResponseEntity<RespuestaGuiaAdministrador> guiarAdministrador(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @RequestBody SolicitudGuiaAdministrador solicitud
    ) {
        return ResponseEntity.ok(servicioGuiaAdministrador.guiarAdministrador(adminUserId, solicitud));
    }
}
