package com.leo.politicas_de_negocio.guide.administrador.servicio;

import com.leo.politicas_de_negocio.guide.administrador.dto.AccionSugeridaGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.CampoFormularioGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.ContextoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.NodoSeleccionadoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.ProblemaDetectadoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.ResponsableSugeridoGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.RespuestaGuiaAdministrador;
import com.leo.politicas_de_negocio.guide.administrador.dto.SolicitudGuiaAdministrador;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ServicioFallbackGuiaAdministrador {

    public RespuestaGuiaAdministrador construir(SolicitudGuiaAdministrador solicitud, String intencion) {
        ContextoGuiaAdministrador contexto = solicitud.getContexto() != null ? solicitud.getContexto() : new ContextoGuiaAdministrador();
        List<ProblemaDetectadoGuiaAdministrador> problemas = contexto.getProblemasDetectados() != null ? contexto.getProblemasDetectados() : List.of();
        List<AccionSugeridaGuiaAdministrador> acciones = contexto.getAccionesDisponibles() != null
                ? construirAccionesSugeridas(contexto)
                : List.of();

        if ("SUGGEST_ACTIVITY_FORM".equals(intencion)) {
            List<CampoFormularioGuiaAdministrador> formularioSugerido = sugerirFormulario(contexto.getNodoSeleccionado());
            return RespuestaGuiaAdministrador.builder()
                    .respuesta(construirRespuestaFormulario(contexto.getNodoSeleccionado()))
                    .pasos(List.of(
                            "Agrega primero el campo principal que define el resultado.",
                            "Luego agrega observaciones o evidencia si hace falta.",
                            "Marca como obligatorios solo los datos que bloquean el avance."
                    ))
                    .formularioSugerido(formularioSugerido)
                    .problemasDetectados(new ArrayList<>(problemas))
                    .accionesSugeridas(acciones)
                    .severidad("INFO")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("SUGGEST_RESPONSIBLE".equals(intencion)) {
            ResponsableSugeridoGuiaAdministrador responsable = sugerirResponsable(contexto.getNodoSeleccionado());
            return RespuestaGuiaAdministrador.builder()
                    .respuesta(construirRespuestaResponsable(contexto.getNodoSeleccionado(), responsable))
                    .pasos(List.of(
                            "Asigna un responsable que pueda ejecutar la actividad de punta a punta.",
                            "Si la tarea es interna, prioriza area o funcionario operativo.",
                            "Si solo pide datos al solicitante, usa un responsable orientado al iniciador."
                    ))
                    .responsableSugerido(responsable)
                    .problemasDetectados(new ArrayList<>(problemas))
                    .accionesSugeridas(acciones)
                    .severidad("INFO")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("VALIDATE_POLICY".equals(intencion) || "EXPLAIN_POLICY_ERROR".equals(intencion) || "HELP_ACTIVATE_POLICY".equals(intencion)) {
            if (problemas.isEmpty()) {
                return RespuestaGuiaAdministrador.builder()
                        .respuesta("La politica ya cumple las validaciones basicas para activarse. Te recomiendo guardar el flujo y hacer una ultima revision visual.")
                        .pasos(List.of(
                                "Guarda la politica.",
                                "Revisa conexiones y decisiones.",
                                "Activa la politica."
                        ))
                        .problemasDetectados(List.of())
                        .accionesSugeridas(acciones)
                        .severidad("SUCCESS")
                        .intencion(intencion)
                        .fuente("BACKEND_FALLBACK")
                        .disponible(true)
                        .build();
            }

            StringBuilder constructor = new StringBuilder("Todavia no puedes activar la politica. Detecte estos bloqueos: ");
            for (int i = 0; i < Math.min(3, problemas.size()); i++) {
                if (i > 0) {
                    constructor.append("; ");
                }
                constructor.append(problemas.get(i).getMensaje());
            }

            return RespuestaGuiaAdministrador.builder()
                    .respuesta(constructor.toString())
                    .pasos(construirPasosActivacion(problemas))
                    .problemasDetectados(new ArrayList<>(problemas))
                    .accionesSugeridas(acciones)
                    .severidad("ERROR")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLAIN_SCREEN".equals(intencion) || "WHAT_CAN_I_DO_HERE".equals(intencion) || "GUIDE_STEP_BY_STEP".equals(intencion)
                || "HELP_CREATE_POLICY".equals(intencion)) {
            return RespuestaGuiaAdministrador.builder()
                    .respuesta(construirRespuestaPantalla(solicitud))
                    .pasos(construirPasosPantalla(solicitud, intencion))
                    .problemasDetectados(new ArrayList<>(problemas))
                    .accionesSugeridas(acciones)
                    .severidad(problemas.isEmpty() ? "INFO" : "WARNING")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("SUGGEST_DECISION".equals(intencion)) {
            return RespuestaGuiaAdministrador.builder()
                    .respuesta("Para una decision conviene definir primero el campo del formulario que dispara la regla y luego conectar claramente la salida SI y la salida NO.")
                    .pasos(List.of(
                            "Identifica el campo que decide el camino.",
                            "Configura una condicion positiva y un camino alternativo.",
                            "Verifica que ambas ramas lleguen a un siguiente nodo valido."
                    ))
                    .problemasDetectados(new ArrayList<>(problemas))
                    .accionesSugeridas(acciones)
                    .severidad(problemas.isEmpty() ? "INFO" : "WARNING")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        return RespuestaGuiaAdministrador.builder()
                .respuesta(construirRespuestaGeneral(solicitud))
                .pasos(construirPasosGenerales(solicitud))
                .problemasDetectados(new ArrayList<>(problemas))
                .accionesSugeridas(acciones)
                .severidad(problemas.isEmpty() ? "INFO" : "WARNING")
                .intencion(intencion)
                .fuente("BACKEND_FALLBACK")
                .disponible(true)
                .build();
    }

    private String construirRespuestaPantalla(SolicitudGuiaAdministrador solicitud) {
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        ContextoGuiaAdministrador contexto = solicitud.getContexto();
        String nombrePolitica = contexto != null && contexto.getNombrePolitica() != null ? contexto.getNombrePolitica() : "esta politica";
        if ("POLICY_DESIGNER".equals(pantalla)) {
            return "Estas en el disenador de politicas de " + nombrePolitica
                    + ". Aqui puedes agregar nodos, responsables, formularios, decisiones y conexiones para completar el flujo.";
        }
        if ("POLICY_LIST".equals(pantalla)) {
            return "Estas en el modulo de politicas. Aqui puedes crear nuevas politicas, editar borradores, activar, pausar o desactivar flujos.";
        }
        return construirRespuestaGeneral(solicitud);
    }

    private List<String> construirPasosPantalla(SolicitudGuiaAdministrador solicitud, String intencion) {
        if ("GUIDE_STEP_BY_STEP".equals(intencion) && "POLICY_DESIGNER".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return List.of(
                    "Agrega un nodo de inicio.",
                    "Crea las actividades principales.",
                    "Asigna responsables a cada actividad.",
                    "Agrega formularios y decisiones.",
                    "Cierra con un nodo final y valida la activacion."
            );
        }

        if ("POLICY_DESIGNER".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return List.of(
                    "Confirma nodo de inicio y nodo final.",
                    "Define actividades y responsables.",
                    "Agrega formularios y conecta el flujo."
            );
        }

        return construirPasosGenerales(solicitud);
    }

    private String construirRespuestaGeneral(SolicitudGuiaAdministrador solicitud) {
        if ("POLICY_DESIGNER".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return "Puedo ayudarte a disenar la politica, sugerir responsables, proponer formularios y validar si ya puede activarse.";
        }
        return "Puedo orientarte segun la pantalla actual y, dentro del disenador, ayudarte con responsables, formularios y validacion de politicas.";
    }

    private List<String> construirPasosGenerales(SolicitudGuiaAdministrador solicitud) {
        if ("POLICY_DESIGNER".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return List.of(
                    "Preguntame que hacer aqui para explicarte la pantalla.",
                    "Preguntame que formulario o responsable conviene para una actividad.",
                    "Preguntame que falta antes de activar la politica."
            );
        }
        return List.of(
                "Abre una politica o entra al disenador para recibir ayuda mas contextual.",
                "Puedes pedirme una explicacion de la pantalla o del siguiente paso."
        );
    }

    private List<AccionSugeridaGuiaAdministrador> construirAccionesSugeridas(ContextoGuiaAdministrador contexto) {
        List<AccionSugeridaGuiaAdministrador> acciones = new ArrayList<>();
        List<String> accionesDisponibles = contexto.getAccionesDisponibles() != null ? contexto.getAccionesDisponibles() : List.of();
        agregarAccion(acciones, accionesDisponibles, "ADD_START_NODE", "Agregar nodo de inicio");
        agregarAccion(acciones, accionesDisponibles, "ADD_END_NODE", "Agregar nodo final");
        agregarAccion(acciones, accionesDisponibles, "ASSIGN_RESPONSIBLE", "Asignar responsables pendientes");
        agregarAccion(acciones, accionesDisponibles, "ADD_FORM_FIELD", "Completar formularios faltantes");
        agregarAccion(acciones, accionesDisponibles, "CONNECT_NODES", "Corregir conexiones y decisiones");
        agregarAccion(acciones, accionesDisponibles, "SAVE_POLICY", "Guardar politica");
        agregarAccion(acciones, accionesDisponibles, "ACTIVATE_POLICY", "Activar politica");
        return acciones.stream().limit(5).toList();
    }

    private void agregarAccion(
            List<AccionSugeridaGuiaAdministrador> acciones,
            List<String> accionesDisponibles,
            String accion,
            String etiqueta
    ) {
        if (!accionesDisponibles.contains(accion)) {
            return;
        }
        if (acciones.stream().anyMatch(item -> accion.equals(item.getAccion()))) {
            return;
        }
        acciones.add(AccionSugeridaGuiaAdministrador.builder().accion(accion).etiqueta(etiqueta).build());
    }

    private List<String> construirPasosActivacion(List<ProblemaDetectadoGuiaAdministrador> problemas) {
        List<String> pasos = new ArrayList<>();
        if (problemas.stream().anyMatch(problema -> "MISSING_START_NODE".equals(problema.getTipo()))) {
            pasos.add("Agrega un nodo de inicio.");
        }
        if (problemas.stream().anyMatch(problema -> "MISSING_END_NODE".equals(problema.getTipo()))) {
            pasos.add("Agrega un nodo final.");
        }
        if (problemas.stream().anyMatch(problema -> "ACTIVITIES_WITHOUT_RESPONSIBLE".equals(problema.getTipo()))) {
            pasos.add("Asigna responsable a todas las actividades.");
        }
        if (problemas.stream().anyMatch(problema -> "ACTIVITIES_WITHOUT_FORM".equals(problema.getTipo()))) {
            pasos.add("Completa formularios en las actividades que capturan o validan datos.");
        }
        if (problemas.stream().anyMatch(problema -> "DECISIONS_WITHOUT_ROUTES".equals(problema.getTipo())
                || "INVALID_CONNECTIONS".equals(problema.getTipo()))) {
            pasos.add("Corrige conexiones y asegura caminos validos en cada decision.");
        }
        if (pasos.isEmpty()) {
            pasos.add("Revisa la estructura general del flujo y guarda de nuevo la politica.");
        }
        return pasos.stream().limit(5).toList();
    }

    private String construirRespuestaFormulario(NodoSeleccionadoGuiaAdministrador nodoSeleccionado) {
        if (nodoSeleccionado != null && nodoSeleccionado.getNombre() != null) {
            return "Para la actividad " + nodoSeleccionado.getNombre()
                    + ", te conviene un formulario corto pero util para capturar los datos que realmente definen el siguiente paso.";
        }
        return "Te conviene un formulario enfocado en los datos que la actividad necesita capturar o validar.";
    }

    private String construirRespuestaResponsable(
            NodoSeleccionadoGuiaAdministrador nodoSeleccionado,
            ResponsableSugeridoGuiaAdministrador responsable
    ) {
        if (nodoSeleccionado != null && nodoSeleccionado.getNombre() != null && responsable != null) {
            return "Para la actividad " + nodoSeleccionado.getNombre() + ", conviene asignar "
                    + responsable.getNombre() + ". " + responsable.getMotivo();
        }
        if (responsable != null) {
            return "Conviene asignar " + responsable.getNombre() + ". " + responsable.getMotivo();
        }
        return "Conviene asignar un responsable que pueda ejecutar esa actividad de punta a punta.";
    }

    private ResponsableSugeridoGuiaAdministrador sugerirResponsable(NodoSeleccionadoGuiaAdministrador nodoSeleccionado) {
        if (nodoSeleccionado != null && nodoSeleccionado.getDepartamento() != null && !nodoSeleccionado.getDepartamento().isBlank()) {
            return ResponsableSugeridoGuiaAdministrador.builder()
                    .nombre(nodoSeleccionado.getDepartamento())
                    .motivo("Ese nodo ya esta ubicado en el area visual mas coherente con la tarea.")
                    .build();
        }

        String nombreNormalizado = normalizar(nodoSeleccionado != null ? nodoSeleccionado.getNombre() : null);
        if (contieneAlguna(nombreNormalizado, List.of("tecnica", "tecnico", "viabilidad", "instalacion", "inspeccion"))) {
            return ResponsableSugeridoGuiaAdministrador.builder()
                    .nombre("Departamento Tecnico")
                    .motivo("Porque la actividad requiere validacion tecnica, viabilidad o evidencia operativa.")
                    .build();
        }
        if (contieneAlguna(nombreNormalizado, List.of("document", "archivo", "adjunto"))) {
            return ResponsableSugeridoGuiaAdministrador.builder()
                    .nombre("Mesa de Entrada o Control Documental")
                    .motivo("Porque la tarea consiste en revisar integridad documental y respaldo adjunto.")
                    .build();
        }
        if (contieneAlguna(nombreNormalizado, List.of("aprob", "valida", "revision"))) {
            return ResponsableSugeridoGuiaAdministrador.builder()
                    .nombre("Area Aprobadora")
                    .motivo("Porque la actividad implica revision, validacion o aprobacion formal.")
                    .build();
        }
        return ResponsableSugeridoGuiaAdministrador.builder()
                .nombre("Departamento Operativo Responsable")
                .motivo("Conviene asignarlo al area que ejecuta o valida directamente la tarea.")
                .build();
    }

    private List<CampoFormularioGuiaAdministrador> sugerirFormulario(NodoSeleccionadoGuiaAdministrador nodoSeleccionado) {
        String nombreNormalizado = normalizar(nodoSeleccionado != null ? nodoSeleccionado.getNombre() : null);
        if (contieneAlguna(nombreNormalizado, List.of("tecnica", "tecnico", "viabilidad", "instalacion", "inspeccion"))) {
            return List.of(
                    campo("Es viable tecnicamente?", "BOOLEAN", true),
                    campo("Observaciones tecnicas", "TEXTAREA", false),
                    campo("Foto o evidencia del lugar", "FILE", false),
                    campo("Motivo de rechazo", "TEXTAREA", false)
            );
        }
        if (contieneAlguna(nombreNormalizado, List.of("document", "archivo", "adjunto"))) {
            return List.of(
                    campo("Documentacion completa?", "BOOLEAN", true),
                    campo("Documentos observados", "TEXTAREA", false),
                    campo("Archivo de respaldo", "FILE", false)
            );
        }
        if (contieneAlguna(nombreNormalizado, List.of("aprob", "valida", "revision"))) {
            return List.of(
                    campo("Resultado de la revision", "BOOLEAN", true),
                    campo("Observaciones", "TEXTAREA", false),
                    campo("Fecha de revision", "DATE", false)
            );
        }
        return List.of(
                campo("Resultado de la actividad", "BOOLEAN", true),
                campo("Observaciones", "TEXTAREA", false),
                campo("Evidencia adjunta", "FILE", false)
        );
    }

    private CampoFormularioGuiaAdministrador campo(String etiqueta, String tipo, boolean obligatorio) {
        return CampoFormularioGuiaAdministrador.builder()
                .etiqueta(etiqueta)
                .tipo(tipo)
                .obligatorio(obligatorio)
                .build();
    }

    private boolean contieneAlguna(String valor, List<String> fragmentos) {
        return fragmentos.stream().anyMatch(valor::contains);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
