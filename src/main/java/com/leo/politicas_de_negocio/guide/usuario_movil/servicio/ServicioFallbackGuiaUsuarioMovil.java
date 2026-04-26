package com.leo.politicas_de_negocio.guide.usuario_movil.servicio;

import com.leo.politicas_de_negocio.guide.comun.dto.AccionGuia;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.ContextoGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.RespuestaGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.ResumenProgresoGuiaUsuarioMovil;
import com.leo.politicas_de_negocio.guide.usuario_movil.dto.SolicitudGuiaUsuarioMovil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class ServicioFallbackGuiaUsuarioMovil {

    public RespuestaGuiaUsuarioMovil construir(SolicitudGuiaUsuarioMovil solicitud, String intencion) {
        ContextoGuiaUsuarioMovil contexto = solicitud.getContexto() != null
                ? solicitud.getContexto()
                : new ContextoGuiaUsuarioMovil();

        String estadoExplicado = construirEstadoExplicado(contexto.getEstadoTramite());
        String progresoExplicado = construirProgresoExplicado(contexto.getResumenProgreso());
        List<String> documentosFaltantes = limpiarLista(contexto.getDocumentosFaltantes());
        List<String> proximosPasos = limpiarLista(contexto.getProximosPasos());
        List<AccionGuia> accionesSugeridas = construirAccionesSugeridas(contexto.getAccionesDisponibles());
        String severidad = construirSeveridad(contexto);

        if ("EXPLICAR_PANTALLA".equals(intencion)
                || "QUE_PUEDO_HACER_AQUI".equals(intencion)
                || "GUIA_PASO_A_PASO".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaPantalla(solicitud))
                    .pasos(construirPasosPantalla(solicitud))
                    .estadoExplicado(estadoExplicado)
                    .progresoExplicado(progresoExplicado)
                    .documentosFaltantes(documentosFaltantes)
                    .proximosPasos(proximosPasos)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(severidad)
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("AYUDA_INICIAR_TRAMITE".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta("Si esta pantalla lo permite, puedes iniciar un tramite y luego revisar su avance desde tu lista de solicitudes.")
                    .pasos(List.of(
                            "Busca un tramite disponible para iniciar.",
                            "Completa la solicitud si el sistema te lo pide.",
                            "Despues revisa el estado desde tu lista de tramites."
                    ))
                    .accionesSugeridas(accionesSugeridas)
                    .severidad("INFO")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("AYUDA_SUBIR_DOCUMENTO".equals(intencion) || "EXPLICAR_DOCUMENTOS_FALTANTES".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaDocumentos(documentosFaltantes))
                    .pasos(construirPasosDocumentos(documentosFaltantes))
                    .documentosFaltantes(documentosFaltantes)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(documentosFaltantes.isEmpty() ? "INFO" : "WARNING")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLICAR_ESTADO_TRAMITE".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaEstado(contexto, estadoExplicado))
                    .pasos(List.of(
                            "Revisa la etapa actual del tramite.",
                            "Confirma si tienes observaciones o documentos pendientes.",
                            "Consulta que paso sigue despues de la revision actual."
                    ))
                    .estadoExplicado(estadoExplicado)
                    .progresoExplicado(progresoExplicado)
                    .documentosFaltantes(documentosFaltantes)
                    .proximosPasos(proximosPasos)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(severidad)
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLICAR_PROGRESO_TRAMITE".equals(intencion) || "EXPLICAR_HISTORIAL".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaProgreso(contexto, progresoExplicado))
                    .pasos(List.of(
                            "Revisa cuantos pasos ya se completaron.",
                            "Ubica la etapa actual de la solicitud.",
                            "Consulta los proximos pasos para saber que falta."
                    ))
                    .estadoExplicado(estadoExplicado)
                    .progresoExplicado(progresoExplicado)
                    .proximosPasos(proximosPasos)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(severidad)
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLICAR_ETAPA_ACTUAL".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaEtapaActual(contexto))
                    .pasos(List.of(
                            "Revisa el area actual del tramite.",
                            "Confirma si esa etapa requiere un documento o correccion adicional.",
                            "Consulta el siguiente paso del flujo."
                    ))
                    .estadoExplicado(estadoExplicado)
                    .proximosPasos(proximosPasos)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(severidad)
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLICAR_OBSERVACIONES".equals(intencion) || "EXPLICAR_RECHAZO".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaObservaciones(contexto))
                    .pasos(List.of(
                            "Revisa el motivo informado en observaciones.",
                            "Corrige o adjunta lo que te solicitaron.",
                            "Vuelve a consultar el estado cuando termines."
                    ))
                    .estadoExplicado(estadoExplicado)
                    .documentosFaltantes(documentosFaltantes)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad("EXPLICAR_RECHAZO".equals(intencion) ? "ERROR" : severidad)
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLICAR_PROXIMO_PASO".equals(intencion)) {
            return RespuestaGuiaUsuarioMovil.builder()
                    .respuesta(construirRespuestaSiguientePaso(proximosPasos))
                    .pasos(List.of(
                            "Confirma si la etapa actual ya esta completa.",
                            "Revisa si queda alguna observacion pendiente.",
                            "Consulta el siguiente paso esperado del flujo."
                    ))
                    .progresoExplicado(progresoExplicado)
                    .proximosPasos(proximosPasos)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad("INFO")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        return RespuestaGuiaUsuarioMovil.builder()
                .respuesta(construirRespuestaGeneral(solicitud))
                .pasos(construirPasosGenerales(solicitud))
                .estadoExplicado(estadoExplicado)
                .progresoExplicado(progresoExplicado)
                .documentosFaltantes(documentosFaltantes)
                .proximosPasos(proximosPasos)
                .accionesSugeridas(accionesSugeridas)
                .severidad(severidad)
                .intencion(intencion)
                .fuente("BACKEND_FALLBACK")
                .disponible(true)
                .build();
    }

    private String construirRespuestaPantalla(SolicitudGuiaUsuarioMovil solicitud) {
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        if ("INICIO_USUARIO".equals(pantalla)) {
            return "Estas en el inicio del usuario movil. Aqui puedes iniciar tramites si el sistema lo permite y revisar accesos a tus solicitudes.";
        }
        if ("LISTA_TRAMITES".equals(pantalla)) {
            return "Estas en la lista de tramites. Aqui puedes revisar tus solicitudes y entrar al detalle para consultar su estado.";
        }
        if ("DETALLE_TRAMITE".equals(pantalla) || "ESTADO_TRAMITE".equals(pantalla)) {
            return "Estas viendo el detalle del tramite. Aqui puedes entender el estado actual, la etapa en curso y lo que falta para avanzar.";
        }
        if ("FORMULARIO_SOLICITUD".equals(pantalla)) {
            return "Estas en un formulario de solicitud. Aqui debes completar la informacion requerida y adjuntar documentos si el sistema lo solicita.";
        }
        if ("PERFIL_USUARIO".equals(pantalla)) {
            return "Estas en tu perfil. Aqui puedes revisar tus datos personales y la informacion basica usada en tus tramites.";
        }
        if ("NOTIFICACIONES".equals(pantalla)) {
            return "Estas en notificaciones. Aqui puedes revisar avisos sobre cambios o avances en tus tramites.";
        }
        return "Puedo ayudarte a entender la pantalla actual, explicar el estado de tus tramites y decirte que falta para avanzar.";
    }

    private List<String> construirPasosPantalla(SolicitudGuiaUsuarioMovil solicitud) {
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        if ("LISTA_TRAMITES".equals(pantalla)) {
            return List.of(
                    "Busca el tramite que quieres revisar.",
                    "Entra al detalle para ver estado y etapa actual.",
                    "Si hay observaciones, revisa que te falta para avanzar."
            );
        }
        if ("DETALLE_TRAMITE".equals(pantalla) || "ESTADO_TRAMITE".equals(pantalla)) {
            return List.of(
                    "Revisa el estado actual de la solicitud.",
                    "Confirma en que etapa se encuentra tu tramite.",
                    "Consulta si tienes documentos u observaciones pendientes."
            );
        }
        return List.of(
                "Revisa la informacion principal de esta pantalla.",
                "Consulta que acciones tienes disponibles.",
                "Pregunta por el estado o el siguiente paso si necesitas mas detalle."
        );
    }

    private String construirRespuestaEstado(ContextoGuiaUsuarioMovil contexto, String estadoExplicado) {
        String etapa = contexto.getEtapaActual() != null ? contexto.getEtapaActual().getNombre() : null;
        if (normalizar(etapa) != null) {
            return "Tu tramite esta en " + etapa + ". " + textoSeguro(estadoExplicado, "");
        }
        return textoSeguro(estadoExplicado, "Tu tramite tiene un estado registrado y puedo ayudarte a interpretarlo.");
    }

    private String construirRespuestaProgreso(ContextoGuiaUsuarioMovil contexto, String progresoExplicado) {
        if (normalizar(progresoExplicado) != null) {
            return progresoExplicado;
        }
        return "Puedo orientarte sobre las etapas completadas, la etapa actual y lo que falta para cerrar el tramite.";
    }

    private String construirRespuestaEtapaActual(ContextoGuiaUsuarioMovil contexto) {
        if (contexto.getEtapaActual() == null || normalizar(contexto.getEtapaActual().getNombre()) == null) {
            return "No tengo una etapa actual detallada, pero si quieres puedo explicarte el estado general del tramite.";
        }

        StringBuilder respuesta = new StringBuilder("Tu solicitud esta en la etapa ");
        respuesta.append(contexto.getEtapaActual().getNombre()).append(".");
        if (normalizar(contexto.getEtapaActual().getDescripcion()) != null) {
            respuesta.append(" ").append(contexto.getEtapaActual().getDescripcion());
        }
        if (normalizar(contexto.getEtapaActual().getDepartamento()) != null) {
            respuesta.append(" El area actual es ").append(contexto.getEtapaActual().getDepartamento()).append(".");
        }
        return respuesta.toString();
    }

    private String construirRespuestaDocumentos(List<String> documentosFaltantes) {
        if (documentosFaltantes.isEmpty()) {
            return "Por ahora no veo documentos faltantes en el contexto disponible del tramite.";
        }
        return "Todavia faltan estos documentos: " + String.join(", ", documentosFaltantes) + ".";
    }

    private List<String> construirPasosDocumentos(List<String> documentosFaltantes) {
        if (documentosFaltantes.isEmpty()) {
            return List.of(
                    "Si esperabas un documento pendiente, revisa observaciones y detalle del tramite.",
                    "Vuelve a consultar el estado despues de una actualizacion del sistema."
            );
        }
        return List.of(
                "Revisa cada documento solicitado.",
                "Sube o corrige solo los documentos faltantes.",
                "Vuelve a consultar el estado despues del envio."
        );
    }

    private String construirRespuestaObservaciones(ContextoGuiaUsuarioMovil contexto) {
        List<String> observaciones = limpiarLista(contexto.getObservaciones());
        if (!observaciones.isEmpty()) {
            return "Tu tramite tiene observaciones. La principal es: " + observaciones.get(0);
        }
        return "No veo una observacion textual disponible, pero si el tramite esta detenido conviene revisar detalle e historial reciente.";
    }

    private String construirRespuestaSiguientePaso(List<String> proximosPasos) {
        if (proximosPasos.isEmpty()) {
            return "Todavia no tengo un siguiente paso detallado, pero puedo explicarte el estado y la etapa actual del tramite.";
        }
        return "El siguiente paso esperado de tu tramite es " + proximosPasos.get(0) + ".";
    }

    private String construirRespuestaGeneral(SolicitudGuiaUsuarioMovil solicitud) {
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        if ("LISTA_TRAMITES".equals(pantalla)) {
            return "Puedo ayudarte a entender tu lista de tramites, explicarte estados y decirte que hacer cuando una solicitud este observada o detenida.";
        }
        if ("DETALLE_TRAMITE".equals(pantalla) || "ESTADO_TRAMITE".equals(pantalla)) {
            return "Puedo explicarte en que estado va tu tramite, en que etapa esta y que falta para avanzar.";
        }
        return "Puedo ayudarte a entender la pantalla actual, iniciar tramites si el sistema lo permite y explicar el estado de tus solicitudes.";
    }

    private List<String> construirPasosGenerales(SolicitudGuiaUsuarioMovil solicitud) {
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        if ("DETALLE_TRAMITE".equals(pantalla) || "ESTADO_TRAMITE".equals(pantalla)) {
            return List.of(
                    "Preguntame que significa el estado actual.",
                    "Preguntame en que etapa va tu solicitud.",
                    "Preguntame que falta o que pasa despues."
            );
        }
        return List.of(
                "Preguntame que puedes hacer en esta pantalla.",
                "Preguntame como iniciar un tramite o como revisar uno ya creado."
        );
    }

    private String construirEstadoExplicado(String estadoTramite) {
        String estado = normalizarCodigo(estadoTramite);
        return switch (estado) {
            case "EN_PROCESO" -> "EN_PROCESO significa que tu tramite todavia esta siendo revisado.";
            case "DETENIDO" -> "DETENIDO significa que el tramite no puede avanzar por ahora.";
            case "RECHAZADO" -> "RECHAZADO significa que la solicitud no pudo continuar con la informacion actual.";
            case "FINALIZADO", "FINALIZADA" -> "FINALIZADO significa que el tramite ya termino.";
            case "CANCELADO" -> "CANCELADO significa que el tramite se cerro sin continuar.";
            default -> normalizar(estadoTramite) != null
                    ? "El estado actual informado es " + estadoTramite + "."
                    : null;
        };
    }

    private String construirProgresoExplicado(ResumenProgresoGuiaUsuarioMovil resumen) {
        if (resumen == null) {
            return null;
        }
        int total = Math.max(resumen.getPasosCompletados() + resumen.getPasosPendientes(), 0);
        List<String> partes = new ArrayList<>();
        if (total > 0) {
            partes.add("Llevas " + resumen.getPasosCompletados() + " de " + total + " etapas completadas.");
        }
        if (normalizar(resumen.getPasoActual()) != null) {
            partes.add("La etapa actual es " + resumen.getPasoActual() + ".");
        }
        if (resumen.getPorcentajeAvance() > 0) {
            partes.add("El avance estimado es " + resumen.getPorcentajeAvance() + "%.");
        }
        return partes.isEmpty() ? null : String.join(" ", partes);
    }

    private List<AccionGuia> construirAccionesSugeridas(List<String> accionesDisponibles) {
        LinkedHashSet<String> accionesNormalizadas = new LinkedHashSet<>();
        if (accionesDisponibles != null) {
            accionesDisponibles.stream()
                    .map(this::normalizarCodigo)
                    .filter(valor -> !valor.isBlank())
                    .forEach(accionesNormalizadas::add);
        }

        List<AccionGuia> acciones = new ArrayList<>();
        agregarAccion(acciones, accionesNormalizadas, "SUBIR_DOCUMENTO", "Subir documento pendiente");
        agregarAccion(acciones, accionesNormalizadas, "VER_OBSERVACIONES", "Ver observaciones");
        agregarAccion(acciones, accionesNormalizadas, "CONSULTAR_ESTADO", "Consultar estado del tramite");
        agregarAccion(acciones, accionesNormalizadas, "VER_HISTORIAL", "Ver historial del tramite");
        agregarAccion(acciones, accionesNormalizadas, "VER_DETALLE_TRAMITE", "Ver detalle del tramite");
        agregarAccion(acciones, accionesNormalizadas, "INICIAR_TRAMITE", "Iniciar tramite");
        return acciones.stream().limit(5).toList();
    }

    private void agregarAccion(
            List<AccionGuia> acciones,
            LinkedHashSet<String> accionesDisponibles,
            String accion,
            String etiqueta
    ) {
        if (!accionesDisponibles.contains(accion)) {
            return;
        }
        acciones.add(AccionGuia.builder().accion(accion).etiqueta(etiqueta).build());
    }

    private String construirSeveridad(ContextoGuiaUsuarioMovil contexto) {
        String estado = normalizarCodigo(contexto.getEstadoTramite());
        if ("RECHAZADO".equals(estado) || "CANCELADO".equals(estado)) {
            return "ERROR";
        }
        if (!limpiarLista(contexto.getDocumentosFaltantes()).isEmpty()
                || !limpiarLista(contexto.getObservaciones()).isEmpty()
                || "DETENIDO".equals(estado)) {
            return "WARNING";
        }
        if ("FINALIZADO".equals(estado) || "FINALIZADA".equals(estado)) {
            return "SUCCESS";
        }
        return "INFO";
    }

    private List<String> limpiarLista(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return List.of();
        }
        return valores.stream()
                .map(this::normalizar)
                .filter(valor -> valor != null)
                .limit(5)
                .toList();
    }

    private String textoSeguro(String valor, String respaldo) {
        return normalizar(valor) != null ? valor : respaldo;
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
