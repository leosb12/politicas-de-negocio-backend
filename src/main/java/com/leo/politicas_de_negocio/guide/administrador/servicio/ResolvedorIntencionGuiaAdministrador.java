package com.leo.politicas_de_negocio.guide.administrador.servicio;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ResolvedorIntencionGuiaAdministrador {

    public String resolver(String pregunta, String pantalla) {
        String preguntaNormalizada = normalizar(pregunta);

        if (contieneAlguna(preguntaNormalizada, List.of("que hago aqui", "donde estoy", "explica esta pantalla"))) {
            return "EXPLAIN_SCREEN";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("que puedo hacer aqui", "que puedo hacer", "que acciones tengo"))) {
            return "WHAT_CAN_I_DO_HERE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("responsable", "a quien asigno", "quien deberia"))) {
            return "SUGGEST_RESPONSIBLE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("formulario", "campo", "que le pongo", "sugerime formulario"))) {
            return "SUGGEST_ACTIVITY_FORM";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("decision", "rama si", "rama no"))) {
            return "SUGGEST_DECISION";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("que sigue", "siguiente actividad", "siguiente paso"))) {
            return "SUGGEST_NEXT_ACTIVITY";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("puedo activar", "que me falta", "valida la politica"))) {
            return "VALIDATE_POLICY";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("por que no puedo activar", "que significa este error", "por que da error"))) {
            return "EXPLAIN_POLICY_ERROR";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("paso a paso", "guiame", "como empiezo"))) {
            return "GUIDE_STEP_BY_STEP";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("optimiza", "mejora esta politica", "como mejorar"))) {
            return "OPTIMIZE_POLICY";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("crear politica", "nueva politica", "como crear"))) {
            return "HELP_CREATE_POLICY";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("activar politica", "como activo", "como desactivo"))) {
            return "HELP_ACTIVATE_POLICY";
        }
        if ("POLICY_DESIGNER".equals(normalizarCodigo(pantalla)) && contieneAlguna(preguntaNormalizada, List.of("ayuda", "orientame"))) {
            return "EXPLAIN_SCREEN";
        }
        return "GENERAL_ADMIN_HELP";
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
