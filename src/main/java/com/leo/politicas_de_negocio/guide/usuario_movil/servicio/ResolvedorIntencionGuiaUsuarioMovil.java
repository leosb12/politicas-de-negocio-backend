package com.leo.politicas_de_negocio.guide.usuario_movil.servicio;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class ResolvedorIntencionGuiaUsuarioMovil {

    public String resolver(String pregunta, String pantalla) {
        String preguntaNormalizada = normalizar(pregunta);
        String pantallaNormalizada = normalizarCodigo(pantalla);

        if (contieneAlguna(preguntaNormalizada, List.of(
                "que hago aqui",
                "donde estoy",
                "explica esta pantalla",
                "para que sirve esta pantalla"
        ))) {
            return "EXPLICAR_PANTALLA";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que puedo hacer aqui",
                "que puedo hacer",
                "que opciones tengo",
                "que acciones tengo"
        ))) {
            return "QUE_PUEDO_HACER_AQUI";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "como inicio un tramite",
                "como iniciar",
                "iniciar tramite",
                "nuevo tramite"
        ))) {
            return "AYUDA_INICIAR_TRAMITE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "subir documento",
                "adjuntar documento",
                "cargar documento",
                "enviar documento"
        ))) {
            return "AYUDA_SUBIR_DOCUMENTO";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "por que fue rechazado",
                "por que lo rechazaron",
                "rechazado",
                "rechazada"
        ))) {
            return "EXPLICAR_RECHAZO";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que documentos me faltan",
                "documentos faltantes",
                "que documento falta",
                "me falta documento"
        ))) {
            return "EXPLICAR_DOCUMENTOS_FALTANTES";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "observado",
                "observaciones",
                "que significa esta observacion"
        ))) {
            return "EXPLICAR_OBSERVACIONES";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "historial",
                "que ya paso",
                "que etapas ya pasaron"
        ))) {
            return "EXPLICAR_HISTORIAL";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "que pasa despues",
                "proximo paso",
                "que sigue despues",
                "cuanto podria tardar"
        ))) {
            return "EXPLICAR_PROXIMO_PASO";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "en que etapa va",
                "etapa actual",
                "quien lo esta revisando"
        ))) {
            return "EXPLICAR_ETAPA_ACTUAL";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "progreso del tramite",
                "como va mi tramite",
                "ya termino mi tramite",
                "que falta para que avance"
        ))) {
            return "EXPLICAR_PROGRESO_TRAMITE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "en que estado esta mi tramite",
                "estado del tramite",
                "que significa este estado",
                "por que esta detenido",
                "esta en proceso"
        ))) {
            return "EXPLICAR_ESTADO_TRAMITE";
        }
        if (contieneAlguna(preguntaNormalizada, List.of(
                "paso a paso",
                "guiame",
                "como empiezo"
        ))) {
            return "GUIA_PASO_A_PASO";
        }

        if ("LISTA_TRAMITES".equals(pantallaNormalizada)
                && contieneAlguna(preguntaNormalizada, List.of("tramite", "lista", "estado"))) {
            return "EXPLICAR_PANTALLA";
        }

        return "AYUDA_GENERAL_USUARIO_MOVIL";
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
