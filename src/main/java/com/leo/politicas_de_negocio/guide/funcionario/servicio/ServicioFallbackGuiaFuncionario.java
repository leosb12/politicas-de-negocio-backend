package com.leo.politicas_de_negocio.guide.funcionario.servicio;

import com.leo.politicas_de_negocio.guide.comun.dto.AccionGuia;
import com.leo.politicas_de_negocio.guide.funcionario.dto.AyudaCampoGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.CampoFaltanteGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.CampoFormularioGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.ContextoGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.FormularioGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.ItemColaTareaGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.NodoActualGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.PasoPosibleGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.RespuestaGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.SolicitudGuiaFuncionario;
import com.leo.politicas_de_negocio.guide.funcionario.dto.SugerenciaPrioridadGuiaFuncionario;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class ServicioFallbackGuiaFuncionario {

    public RespuestaGuiaFuncionario construir(SolicitudGuiaFuncionario solicitud, String intencion) {
        ContextoGuiaFuncionario contexto = solicitud.getContexto() != null ? solicitud.getContexto() : new ContextoGuiaFuncionario();
        List<CampoFaltanteGuiaFuncionario> camposFaltantes = construirCamposFaltantes(contexto.getFormulario());
        List<AyudaCampoGuiaFuncionario> ayudaCampos = construirAyudaCampos(contexto.getFormulario());
        SugerenciaPrioridadGuiaFuncionario sugerenciaPrioridad = construirSugerenciaPrioridad(contexto.getColaTareas());
        String explicacionSiguientePaso = construirExplicacionSiguientePaso(contexto.getPasosPosibles());
        List<AccionGuia> accionesSugeridas = construirAccionesSugeridas(contexto, camposFaltantes);

        if ("EXPLAIN_FORM".equals(intencion) || "EXPLAIN_FIELD".equals(intencion) || "HELP_COMPLETE_FORM".equals(intencion)) {
            return RespuestaGuiaFuncionario.builder()
                    .respuesta(construirRespuestaFormulario(contexto, camposFaltantes))
                    .pasos(construirPasosFormulario(camposFaltantes))
                    .ayudaCampos(ayudaCampos)
                    .camposFaltantes(camposFaltantes)
                    .explicacionSiguientePaso("EXPLAIN_FIELD".equals(intencion) ? null : explicacionSiguientePaso)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(camposFaltantes.isEmpty() ? "INFO" : "ERROR")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("VALIDATE_BEFORE_COMPLETE".equals(intencion) || "EXPLAIN_COMPLETION_ERROR".equals(intencion)) {
            return RespuestaGuiaFuncionario.builder()
                    .respuesta(construirRespuestaFinalizacion(contexto, camposFaltantes))
                    .pasos(construirPasosFinalizacion(contexto, camposFaltantes))
                    .camposFaltantes(camposFaltantes)
                    .explicacionSiguientePaso(camposFaltantes.isEmpty() ? explicacionSiguientePaso : null)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(camposFaltantes.isEmpty() ? construirSeveridadEstado(contexto) : "ERROR")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("PRIORITIZE_TASKS".equals(intencion)) {
            return RespuestaGuiaFuncionario.builder()
                    .respuesta(construirRespuestaPrioridad(sugerenciaPrioridad))
                    .pasos(List.of(
                            "Atiende primero las tareas atrasadas.",
                            "Luego prioriza las tareas de prioridad alta.",
                            "Si varias se ven iguales, comienza por la mas antigua."
                    ))
                    .sugerenciaPrioridad(sugerenciaPrioridad)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(sugerenciaPrioridad != null ? "WARNING" : "INFO")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLAIN_NEXT_STEP".equals(intencion)) {
            return RespuestaGuiaFuncionario.builder()
                    .respuesta(construirRespuestaSiguientePaso(explicacionSiguientePaso))
                    .pasos(List.of(
                            "Revisa la decision o el resultado que registraras.",
                            "Confirma a que area o actividad pasara despues.",
                            "Completa la tarea solo cuando ese resultado sea correcto."
                    ))
                    .explicacionSiguientePaso(explicacionSiguientePaso)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad("INFO")
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLAIN_TASK_STATUS".equals(intencion) || "EXPLAIN_WORKFLOW_PROGRESS".equals(intencion) || "EXPLAIN_TASK".equals(intencion)) {
            return RespuestaGuiaFuncionario.builder()
                    .respuesta(construirRespuestaTarea(contexto, intencion))
                    .pasos(construirPasosTarea(contexto, intencion))
                    .ayudaCampos(ayudaCampos.stream().limit(2).toList())
                    .camposFaltantes(camposFaltantes.stream().limit(2).toList())
                    .explicacionSiguientePaso(explicacionSiguientePaso)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(construirSeveridadEstado(contexto))
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        if ("EXPLAIN_SCREEN".equals(intencion) || "WHAT_CAN_I_DO_HERE".equals(intencion) || "GUIDE_STEP_BY_STEP".equals(intencion)) {
            return RespuestaGuiaFuncionario.builder()
                    .respuesta(construirRespuestaPantalla(solicitud))
                    .pasos(construirPasosPantalla(solicitud))
                    .sugerenciaPrioridad("EMPLOYEE_DASHBOARD".equals(normalizarCodigo(solicitud.getPantalla())) ? sugerenciaPrioridad : null)
                    .accionesSugeridas(accionesSugeridas)
                    .severidad(construirSeveridadEstado(contexto))
                    .intencion(intencion)
                    .fuente("BACKEND_FALLBACK")
                    .disponible(true)
                    .build();
        }

        return RespuestaGuiaFuncionario.builder()
                .respuesta(construirRespuestaGeneral(solicitud))
                .pasos(construirPasosGenerales(solicitud))
                .ayudaCampos(ayudaCampos.stream().limit(2).toList())
                .sugerenciaPrioridad("EMPLOYEE_DASHBOARD".equals(normalizarCodigo(solicitud.getPantalla())) ? sugerenciaPrioridad : null)
                .accionesSugeridas(accionesSugeridas)
                .severidad(construirSeveridadEstado(contexto))
                .intencion(intencion)
                .fuente("BACKEND_FALLBACK")
                .disponible(true)
                .build();
    }

    private List<AyudaCampoGuiaFuncionario> construirAyudaCampos(FormularioGuiaFuncionario formulario) {
        if (formulario == null || formulario.getCampos() == null) {
            return List.of();
        }

        return formulario.getCampos().stream()
                .filter(Objects::nonNull)
                .limit(6)
                .map(campo -> {
                    AyudaCampoGuiaFuncionario ayudaCampo = AyudaCampoGuiaFuncionario.builder()
                            .campo(campo.getNombre())
                            .ayuda(textoAyudaCampo(campo))
                            .build();
                    return ayudaCampo;
                })
                .toList();
    }

    private List<CampoFaltanteGuiaFuncionario> construirCamposFaltantes(FormularioGuiaFuncionario formulario) {
        if (formulario == null || formulario.getCamposObligatoriosFaltantes() == null || formulario.getCamposObligatoriosFaltantes().isEmpty()) {
            return List.of();
        }

        List<CampoFaltanteGuiaFuncionario> camposFaltantes = new ArrayList<>();
        for (String nombreCampo : formulario.getCamposObligatoriosFaltantes()) {
            CampoFormularioGuiaFuncionario campo = formulario.getCampos().stream()
                    .filter(Objects::nonNull)
                    .filter(item -> normalizar(item.getNombre()).equals(normalizar(nombreCampo)))
                    .findFirst()
                    .orElse(null);
            String etiqueta = campo != null && normalizar(campo.getEtiqueta()) != null ? campo.getEtiqueta() : nombreCampo;
            camposFaltantes.add(CampoFaltanteGuiaFuncionario.builder()
                    .campo(nombreCampo)
                    .mensaje("Debes completar " + etiqueta + " antes de finalizar la tarea.")
                    .build());
            if (camposFaltantes.size() >= 6) {
                break;
            }
        }
        return camposFaltantes;
    }

    private SugerenciaPrioridadGuiaFuncionario construirSugerenciaPrioridad(List<ItemColaTareaGuiaFuncionario> colaTareas) {
        if (colaTareas == null || colaTareas.isEmpty()) {
            return null;
        }

        ItemColaTareaGuiaFuncionario tareaPrioritaria = colaTareas.stream()
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(ItemColaTareaGuiaFuncionario::isAtrasada).reversed()
                                .thenComparing(item -> "HIGH".equals(normalizarCodigo(item.getPrioridad())), Comparator.reverseOrder())
                                .thenComparing(item -> item.getHorasAntiguedad() != null ? item.getHorasAntiguedad() : 0, Comparator.reverseOrder())
                )
                .findFirst()
                .orElse(null);

        if (tareaPrioritaria == null) {
            return null;
        }

        String motivo = "Es la tarea mas conveniente para avanzar primero.";
        if (tareaPrioritaria.isAtrasada()) {
            motivo = "Esta tarea esta atrasada y conviene resolverla primero para evitar mas demora.";
        } else if ("HIGH".equals(normalizarCodigo(tareaPrioritaria.getPrioridad()))) {
            motivo = "Esta tarea tiene prioridad alta dentro de tu bandeja.";
        } else if (tareaPrioritaria.getHorasAntiguedad() != null && tareaPrioritaria.getHorasAntiguedad() >= 24) {
            motivo = "Esta tarea ya lleva bastante tiempo pendiente y conviene resolverla antes que las mas recientes.";
        }

        return SugerenciaPrioridadGuiaFuncionario.builder()
                .idTareaRecomendada(tareaPrioritaria.getIdTarea())
                .motivo(motivo)
                .build();
    }

    private String construirExplicacionSiguientePaso(List<PasoPosibleGuiaFuncionario> pasosPosibles) {
        if (pasosPosibles == null || pasosPosibles.isEmpty()) {
            return null;
        }

        return pasosPosibles.stream()
                .filter(Objects::nonNull)
                .limit(4)
                .map(paso -> {
                    String condicion = normalizar(paso.getCondicion()) != null ? paso.getCondicion() : "Al finalizar";
                    String siguienteNodo = normalizar(paso.getSiguienteNodo()) != null ? paso.getSiguienteNodo() : "el siguiente paso del flujo";
                    String departamento = normalizar(paso.getSiguienteDepartamento()) != null ? " en " + paso.getSiguienteDepartamento() : "";
                    return condicion + ", el tramite pasara a " + siguienteNodo + departamento + ".";
                })
                .reduce((izq, der) -> izq + " " + der)
                .orElse(null);
    }

    private List<AccionGuia> construirAccionesSugeridas(
            ContextoGuiaFuncionario contexto,
            List<CampoFaltanteGuiaFuncionario> camposFaltantes
    ) {
        LinkedHashSet<String> disponibles = new LinkedHashSet<>();
        if (contexto.getAccionesDisponibles() != null) {
            contexto.getAccionesDisponibles().stream()
                    .map(this::normalizarCodigo)
                    .filter(valor -> !valor.isBlank())
                    .forEach(disponibles::add);
        }

        List<AccionGuia> acciones = new ArrayList<>();
        if (!camposFaltantes.isEmpty()) {
            acciones.add(AccionGuia.builder()
                    .accion("COMPLETE_REQUIRED_FIELDS")
                    .etiqueta("Completar campos obligatorios")
                    .build());
        }
        agregarAccion(acciones, disponibles, "START_TASK", "Tomar o iniciar tarea");
        agregarAccion(acciones, disponibles, "SAVE_FORM", "Guardar avance");
        agregarAccion(acciones, disponibles, "COMPLETE_TASK", "Finalizar tarea");
        agregarAccion(acciones, disponibles, "FILL_FORM_WITH_AI", "Completar formulario con IA");
        agregarAccion(acciones, disponibles, "ASK_HELP", "Pedir ayuda contextual");
        return acciones.stream().limit(5).toList();
    }

    private void agregarAccion(List<AccionGuia> acciones, LinkedHashSet<String> disponibles, String accion, String etiqueta) {
        if (!disponibles.contains(accion)) {
            return;
        }
        if (acciones.stream().anyMatch(item -> accion.equals(item.getAccion()))) {
            return;
        }
        acciones.add(AccionGuia.builder().accion(accion).etiqueta(etiqueta).build());
    }

    private String construirRespuestaPantalla(SolicitudGuiaFuncionario solicitud) {
        ContextoGuiaFuncionario contexto = solicitud.getContexto() != null ? solicitud.getContexto() : new ContextoGuiaFuncionario();
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        if ("EMPLOYEE_DASHBOARD".equals(pantalla)) {
            int pendientes = contexto.getResumenDashboard() != null ? contexto.getResumenDashboard().getTareasPendientes() : 0;
            int enProceso = contexto.getResumenDashboard() != null ? contexto.getResumenDashboard().getTareasEnProceso() : 0;
            int completadas = contexto.getResumenDashboard() != null ? contexto.getResumenDashboard().getTareasCompletadas() : 0;
            int atrasadas = contexto.getResumenDashboard() != null ? contexto.getResumenDashboard().getTareasAtrasadas() : 0;
            return "Estas en tu bandeja de trabajo. Aqui puedes revisar tus tareas pendientes, en proceso y completadas. "
                    + "Ahora mismo tienes " + pendientes + " pendiente(s), " + enProceso + " en proceso, "
                    + completadas + " completada(s) y " + atrasadas + " atrasada(s).";
        }
        if ("TASK_FORM".equals(pantalla)) {
            String nombreTarea = contexto.getNodoActual() != null && normalizar(contexto.getNodoActual().getNombre()) != null
                    ? contexto.getNodoActual().getNombre()
                    : "esta tarea";
            return "Estas completando el formulario de " + nombreTarea
                    + ". Aqui debes revisar los campos obligatorios, guardar avances y finalizar solo cuando la informacion este completa.";
        }
        if ("TASK_HISTORY".equals(pantalla)) {
            return "Estas viendo el seguimiento del tramite. Aqui puedes revisar en que etapa va, que pasos ya se completaron y que falta para cerrar el flujo.";
        }
        return "Estas en el detalle operativo de tu tarea. Aqui puedes revisar la actividad actual, el tramite asociado y lo que debes completar para avanzar.";
    }

    private List<String> construirPasosPantalla(SolicitudGuiaFuncionario solicitud) {
        String pantalla = normalizarCodigo(solicitud.getPantalla());
        if ("EMPLOYEE_DASHBOARD".equals(pantalla)) {
            return List.of(
                    "Revisa primero las tareas atrasadas o con prioridad alta.",
                    "Abre la tarea que quieras atender para ver su detalle.",
                    "Si una tarea ya esta en proceso, termina sus campos antes de saltar a otra."
            );
        }
        if ("TASK_FORM".equals(pantalla)) {
            return List.of(
                    "Lee el nombre de la actividad y el objetivo del formulario.",
                    "Completa primero los campos obligatorios.",
                    "Guarda avances o finaliza cuando ya no falte informacion."
            );
        }
        if ("TASK_HISTORY".equals(pantalla)) {
            return List.of(
                    "Revisa la etapa actual del tramite.",
                    "Identifica que pasos ya se completaron.",
                    "Confirma cual es el siguiente paso del flujo."
            );
        }
        return List.of(
                "Revisa la actividad actual.",
                "Completa el formulario u observaciones necesarias.",
                "Finaliza cuando toda la informacion este validada."
        );
    }

    private String construirRespuestaTarea(ContextoGuiaFuncionario contexto, String intencion) {
        if ("EXPLAIN_WORKFLOW_PROGRESS".equals(intencion) && contexto.getResumenHistorial() != null) {
            return "El tramite va en " + textoSeguro(contexto.getResumenHistorial().getPasoActual(), "la etapa actual")
                    + ". Ya se completaron " + contexto.getResumenHistorial().getPasosCompletados()
                    + " paso(s) y quedan " + contexto.getResumenHistorial().getPasosPendientes() + " pendiente(s).";
        }
        if ("EXPLAIN_TASK_STATUS".equals(intencion)) {
            String estado = textoSeguro(contexto.getEstadoTarea(), "PENDING");
            if ("OVERDUE".equals(normalizarCodigo(estado))) {
                return "Tu tarea esta atrasada. Conviene revisarla cuanto antes para no bloquear el tramite.";
            }
            return "La tarea actual esta en estado " + estado + ". Revisa el formulario y confirma si ya puedes continuar o finalizar.";
        }
        NodoActualGuiaFuncionario nodoActual = contexto.getNodoActual();
        if (nodoActual != null && normalizar(nodoActual.getNombre()) != null) {
            return "Estas trabajando en " + nodoActual.getNombre() + ". " + textoSeguro(
                    nodoActual.getDescripcion(),
                    "Debes ejecutar esta actividad operativa y registrar el resultado correctamente."
            );
        }
        return "Debes revisar la actividad actual, completar el formulario correspondiente y registrar el resultado antes de finalizar.";
    }

    private List<String> construirPasosTarea(ContextoGuiaFuncionario contexto, String intencion) {
        if ("EXPLAIN_WORKFLOW_PROGRESS".equals(intencion)) {
            return List.of(
                    "Revisa la etapa actual del tramite.",
                    "Confirma que informacion ya se completo antes de tu actividad.",
                    "Verifica cual es el siguiente paso despues de terminar tu tarea."
            );
        }
        return List.of(
                "Confirma el objetivo de la actividad actual.",
                "Revisa si ya tienes todos los datos necesarios.",
                "Completa el formulario y valida el resultado antes de finalizar."
        );
    }

    private String construirRespuestaFormulario(ContextoGuiaFuncionario contexto, List<CampoFaltanteGuiaFuncionario> camposFaltantes) {
        if (!camposFaltantes.isEmpty()) {
            return "Este formulario todavia tiene campos obligatorios pendientes. Completa primero esos datos antes de intentar finalizar.";
        }
        if (contexto.getNodoActual() != null && normalizar(contexto.getNodoActual().getNombre()) != null) {
            return "Este formulario corresponde a " + contexto.getNodoActual().getNombre()
                    + ". Debes llenarlo con la informacion minima necesaria para que el tramite avance sin errores.";
        }
        return "Debes completar este formulario con los datos requeridos para que la actividad pueda avanzar correctamente.";
    }

    private List<String> construirPasosFormulario(List<CampoFaltanteGuiaFuncionario> camposFaltantes) {
        if (!camposFaltantes.isEmpty()) {
            return List.of(
                    "Completa primero los campos obligatorios senalados.",
                    "Revisa si tus respuestas son consistentes con la actividad.",
                    "Vuelve a intentar finalizar cuando ya no falte informacion."
            );
        }
        return List.of(
                "Revisa todos los campos antes de guardar o finalizar.",
                "Usa observaciones para dejar contexto util.",
                "Finaliza la tarea cuando la informacion este completa."
        );
    }

    private String construirRespuestaFinalizacion(
            ContextoGuiaFuncionario contexto,
            List<CampoFaltanteGuiaFuncionario> camposFaltantes
    ) {
        if (!camposFaltantes.isEmpty()) {
            return "No puedes finalizar todavia. Faltan datos obligatorios en "
                    + camposFaltantes.stream().limit(3).map(CampoFaltanteGuiaFuncionario::getCampo).reduce((izq, der) -> izq + ", " + der).orElse("el formulario")
                    + ".";
        }
        if ("PENDING".equals(normalizarCodigo(contexto.getEstadoTarea()))
                && contexto.getAccionesDisponibles() != null
                && contexto.getAccionesDisponibles().contains("START_TASK")) {
            return "Antes de finalizar, primero debes tomar o iniciar la tarea para trabajarla correctamente.";
        }
        return "La tarea parece lista para finalizar. Haz una ultima revision de campos y observaciones antes de completar la actividad.";
    }

    private List<String> construirPasosFinalizacion(
            ContextoGuiaFuncionario contexto,
            List<CampoFaltanteGuiaFuncionario> camposFaltantes
    ) {
        if (!camposFaltantes.isEmpty()) {
            return List.of(
                    "Completa primero los campos obligatorios indicados.",
                    "Revisa si tus respuestas coinciden con la actividad actual.",
                    "Vuelve a intentar finalizar cuando ya no falte informacion."
            );
        }
        if ("PENDING".equals(normalizarCodigo(contexto.getEstadoTarea()))) {
            return List.of(
                    "Toma o inicia la tarea.",
                    "Revisa el formulario y confirma tus datos.",
                    "Finaliza la tarea cuando el resultado ya este validado."
            );
        }
        return List.of(
                "Haz una revision final del formulario.",
                "Verifica observaciones y evidencia si corresponde.",
                "Finaliza la tarea para que el flujo continue."
        );
    }

    private String construirRespuestaPrioridad(SugerenciaPrioridadGuiaFuncionario sugerenciaPrioridad) {
        if (sugerenciaPrioridad != null && normalizar(sugerenciaPrioridad.getIdTareaRecomendada()) != null) {
            return "Te conviene atender primero la tarea " + sugerenciaPrioridad.getIdTareaRecomendada() + ". "
                    + sugerenciaPrioridad.getMotivo();
        }
        return "Revisa primero las tareas atrasadas, luego las de prioridad alta y despues las mas antiguas que sigan pendientes.";
    }

    private String construirRespuestaSiguientePaso(String explicacionSiguientePaso) {
        if (normalizar(explicacionSiguientePaso) != null) {
            return explicacionSiguientePaso;
        }
        return "Despues de completar tu actividad, el tramite continuara segun las conexiones definidas en el flujo y el resultado registrado.";
    }

    private String construirRespuestaGeneral(SolicitudGuiaFuncionario solicitud) {
        if ("TASK_FORM".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return "Puedo ayudarte a completar el formulario, revisar campos obligatorios y explicarte que pasa despues de finalizar.";
        }
        if ("EMPLOYEE_DASHBOARD".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return "Puedo ayudarte a priorizar tu bandeja, entender el estado de tus tareas y decirte cual conviene atender primero.";
        }
        return "Puedo orientarte segun la pantalla actual, explicarte la actividad en curso y ayudarte a completar correctamente tu tarea.";
    }

    private List<String> construirPasosGenerales(SolicitudGuiaFuncionario solicitud) {
        if ("TASK_FORM".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return List.of(
                    "Preguntame que significa este formulario.",
                    "Preguntame que me falta para finalizar.",
                    "Preguntame que pasa despues de completar."
            );
        }
        if ("EMPLOYEE_DASHBOARD".equals(normalizarCodigo(solicitud.getPantalla()))) {
            return List.of(
                    "Preguntame que tarea conviene atender primero.",
                    "Abre una tarea para recibir ayuda mas contextual.",
                    "Usa el bot cuando tengas dudas sobre el flujo o el formulario."
            );
        }
        return List.of(
                "Abre una tarea o formulario para recibir ayuda mas contextual.",
                "Preguntame que debes hacer o que te falta para finalizar."
        );
    }

    private String construirSeveridadEstado(ContextoGuiaFuncionario contexto) {
        if ("OVERDUE".equals(normalizarCodigo(contexto.getEstadoTarea()))) {
            return "WARNING";
        }
        if ("COMPLETED".equals(normalizarCodigo(contexto.getEstadoTarea()))) {
            return "SUCCESS";
        }
        return "INFO";
    }

    private String textoAyudaCampo(CampoFormularioGuiaFuncionario campo) {
        String tipo = normalizarCodigo(campo.getTipo());
        String etiqueta = textoSeguro(campo.getEtiqueta(), campo.getNombre());
        if ("BOOLEAN".equals(tipo)) {
            return "Marca Si o No segun el resultado operativo de " + etiqueta + ".";
        }
        if ("FILE".equals(tipo) || "ARCHIVO".equals(tipo)) {
            return "Adjunta evidencia o respaldo solo si es necesario para validar " + etiqueta + ".";
        }
        if ("DATE".equals(tipo) || "FECHA".equals(tipo)) {
            return "Registra la fecha correspondiente a " + etiqueta + " en el formato esperado por el sistema.";
        }
        if ("NUMBER".equals(tipo) || "NUMERO".equals(tipo)) {
            return "Ingresa el dato numerico requerido para " + etiqueta + ".";
        }
        return "Completa " + etiqueta + " con informacion clara y util para el siguiente paso del tramite.";
    }

    private String textoSeguro(String valor, String respaldo) {
        return normalizar(valor) != null ? valor : respaldo;
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String valorNormalizado = valor.trim();
        return valorNormalizado.isEmpty() ? null : valorNormalizado;
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
