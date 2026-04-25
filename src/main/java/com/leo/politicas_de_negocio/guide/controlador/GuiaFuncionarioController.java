package com.leo.politicas_de_negocio.guide.controlador;

import com.leo.politicas_de_negocio.guide.funcionario.dto.RespuestaGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.SolicitudGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.servicio.ServicioGuiaFuncionario;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GuiaFuncionarioController {

    private final ServicioGuiaFuncionario servicioGuiaFuncionario;

    @PostMapping("/api/guide/employee")
    public ResponseEntity<RespuestaGuiaFuncionario> guiarFuncionario(
            @RequestHeader("X-User-Id") String funcionarioUserId,
            @RequestBody SolicitudGuiaFuncionario solicitud
    ) {
        return ResponseEntity.ok(servicioGuiaFuncionario.guiarFuncionario(funcionarioUserId, solicitud));
    }
}
