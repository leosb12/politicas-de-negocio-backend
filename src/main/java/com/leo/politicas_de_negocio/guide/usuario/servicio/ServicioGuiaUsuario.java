package com.leo.politicas_de_negocio.guide.usuario.servicio;

import com.leo.politicas_de_negocio.guide.usuario.dto.RespuestaGuiaUsuario;
import com.leo.politicas_de_negocio.guide.usuario.dto.SolicitudGuiaUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioGuiaUsuario {

    private final ServicioFallbackGuiaUsuario servicioFallbackGuiaUsuario;
    private final ResolvedorIntencionGuiaUsuario resolvedorIntencionGuiaUsuario;

    public RespuestaGuiaUsuario guiarUsuario(SolicitudGuiaUsuario solicitud) {
        String intencion = resolvedorIntencionGuiaUsuario.resolver(solicitud != null ? solicitud.getPregunta() : null);
        return servicioFallbackGuiaUsuario.construir(solicitud != null ? solicitud : new SolicitudGuiaUsuario(), intencion);
    }
}
