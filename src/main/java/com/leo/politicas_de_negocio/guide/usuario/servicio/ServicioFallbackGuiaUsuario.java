package com.leo.politicas_de_negocio.guide.usuario.servicio;

import com.leo.politicas_de_negocio.guide.comun.dto.AccionGuia;
import com.leo.politicas_de_negocio.guide.usuario.dto.ContextoGuiaUsuario;
import com.leo.politicas_de_negocio.guide.usuario.dto.RespuestaGuiaUsuario;
import com.leo.politicas_de_negocio.guide.usuario.dto.SolicitudGuiaUsuario;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ServicioFallbackGuiaUsuario {

    public RespuestaGuiaUsuario construir(SolicitudGuiaUsuario solicitud, String intencion) {
        ContextoGuiaUsuario contexto = solicitud.getContexto() != null ? solicitud.getContexto() : new ContextoGuiaUsuario();
        List<AccionGuia> accionesSugeridas = new ArrayList<>();
        if (contexto.getAccionesDisponibles() != null) {
            contexto.getAccionesDisponibles().stream()
                    .limit(3)
                    .forEach(accion -> accionesSugeridas.add(AccionGuia.builder().accion(accion).etiqueta(accion).build()));
        }

        return RespuestaGuiaUsuario.builder()
                .respuesta("Puedo darte orientacion general sobre la pantalla actual sin alterar los endpoints existentes.")
                .pasos(List.of(
                        "Consulta la pantalla actual.",
                        "Identifica la accion que quieres ejecutar.",
                        "Si necesitas mas detalle, integra luego un flujo especifico por rol."
                ))
                .intencion(intencion)
                .accionesSugeridas(accionesSugeridas)
                .build();
    }
}
