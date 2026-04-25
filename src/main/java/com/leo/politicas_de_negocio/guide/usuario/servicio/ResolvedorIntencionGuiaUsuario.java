package com.leo.politicas_de_negocio.guide.usuario.servicio;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ResolvedorIntencionGuiaUsuario {

    public String resolver(String pregunta) {
        String preguntaNormalizada = normalizar(pregunta);
        if (contieneAlguna(preguntaNormalizada, List.of("que hago aqui", "donde estoy", "explica esta pantalla"))) {
            return "EXPLAIN_SCREEN";
        }
        if (contieneAlguna(preguntaNormalizada, List.of("que puedo hacer", "que opciones tengo"))) {
            return "WHAT_CAN_I_DO_HERE";
        }
        return "GENERAL_USER_HELP";
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
}
