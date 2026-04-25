package com.leo.politicas_de_negocio.guide.funcionario.servicio;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ResolvedorIntencionGuiaFuncionario {

    public String resolver(String pregunta, String pantalla) {
        String preguntaNormalizada = normalizar(pregunta);
        String pantallaNormalizada = normalizarCodigo(pantalla);

        if (contieneAlguna(preguntaNormalizada, List.of("que hago aqui", "donde estoy", "explica esta pantalla"))) {
            return "EXPLAIN_SCREEN";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("que puedo hacer aqui", "que puedo hacer", "que opciones tengo"))) {
            return "WHAT_CAN_I_DO_HERE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que tarea atiendo primero",
                "que tarea hago primero",
                "cual es la tarea mas urgente",
                "prioriza mis tareas"
        ))) {
            return "PRIORITIZE_TASKS";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "por que no puedo finalizar",
                "por que no puedo completar",
                "error al finalizar",
                "error al completar"
        ))) {
            return "EXPLAIN_COMPLETION_ERROR";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "puedo finalizar",
                "puedo completar",
                "que me falta",
                "faltan campos",
                "faltan datos"
        ))) {
            return "VALIDATE_BEFORE_COMPLETE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que pasa despues",
                "a donde pasa despues",
                "a donde pasa el tramite",
                "si marco si",
                "si marco no",
                "que pasa si marco"
        ))) {
            return "EXPLAIN_NEXT_STEP";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que significa este formulario",
                "que lleno aqui",
                "como lleno este formulario",
                "explica el formulario"
        ))) {
            return "EXPLAIN_FORM";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que significa este campo",
                "para que sirve este campo",
                "explica este campo"
        ))) {
            return "EXPLAIN_FIELD";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "ayudame a completar",
                "como lleno",
                "como redacto",
                "ayudame con observaciones",
                "como escribo las observaciones"
        ))) {
            return "HELP_COMPLETE_FORM";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que tarea estoy ejecutando",
                "que debo hacer",
                "explica la tarea",
                "explica esta actividad",
                "que se espera que haga"
        ))) {
            return "EXPLAIN_TASK";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "en que estado esta",
                "estado de la tarea",
                "esta atrasada",
                "esta vencida"
        ))) {
            return "EXPLAIN_TASK_STATUS";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "en que etapa esta",
                "que etapas ya pasaron",
                "que falta del tramite",
                "por que esta detenido",
                "progreso del tramite",
                "historial del tramite"
        ))) {
            return "EXPLAIN_WORKFLOW_PROGRESS";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("paso a paso", "guiame", "como hago esto", "como empiezo"))) {
            return "GUIDE_STEP_BY_STEP";
        }

        if ("TASK_FORM".equals(pantallaNormalizada) && contieneAlguna(preguntaNormalizada, List.of("formulario", "campo", "observacion"))) {
            return "EXPLAIN_FORM";
        }
        if ("TASK_HISTORY".equals(pantallaNormalizada) && contieneAlguna(preguntaNormalizada, List.of("tramite", "historial", "progreso"))) {
            return "EXPLAIN_WORKFLOW_PROGRESS";
        }

        return "GENERAL_EMPLOYEE_HELP";
    }

    private boolean contieneAlguna(String valor, List<String> opciones) {
        return opciones.stream().anyMatch(valor::contains);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalizado.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String normalizarCodigo(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }
}
