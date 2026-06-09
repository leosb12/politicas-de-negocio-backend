package com.leo.politicas_de_negocio.reportes.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReporteValorNormalizer {

    private final ReporteCatalogoService catalogoService;
    private final ReporteCampoResolver campoResolver;

    public Object normalizarValorFiltro(String entidad, String campo, Object valorUsuario) {
        if (valorUsuario == null || !(valorUsuario instanceof String)) {
            return valorUsuario;
        }

        String targetEntity = entidad;
        String targetField = campo;

        ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(entidad, campo);
        if (rf != null) {
            targetField = rf.getTargetFieldName();
            if (rf.getPath() != null && !rf.getPath().isEmpty()) {
                targetEntity = rf.getPath().get(rf.getPath().size() - 1).getTarget();
            }
        }

        String valorStr = ((String) valorUsuario).trim().toLowerCase();
        List<Object> valoresReales = catalogoService.obtenerValoresDistintos(targetEntity, targetField);
        
        List<String> valoresStrings = valoresReales.stream()
                .filter(v -> v instanceof String)
                .map(v -> ((String) v).trim().toUpperCase())
                .collect(Collectors.toList());

        // 1. Exact match (case insensitive)
        for (String real : valoresStrings) {
            if (real.equalsIgnoreCase(valorStr)) {
                return real;
            }
        }

        // 2. Equivalencias semánticas
        if (esEquivalenteEnCurso(valorStr)) {
            return buscarValorReal(valoresStrings, "EN_CURSO", "PENDIENTE", "ACTIVO", "EN_PROCESO");
        }
        
        if (esEquivalenteFinalizado(valorStr)) {
            return buscarValorReal(valoresStrings, "FINALIZADA", "COMPLETADA", "TERMINADA", "CERRADA", "FINALIZADO", "COMPLETADO", "TERMINADO", "CERRADO");
        }
        
        if (esEquivalenteRechazado(valorStr)) {
            return buscarValorReal(valoresStrings, "RECHAZADA", "DENEGADA", "RECHAZADO", "DENEGADO");
        }

        // 3. Partial match as fallback
        for (String real : valoresStrings) {
            if (real.contains(valorStr.toUpperCase()) || valorStr.toUpperCase().contains(real)) {
                return real;
            }
        }

        // Si no se encuentra equivalencia, devolver mayúsculas por defecto (o el original si no es estado)
        if (campo.toLowerCase().contains("estado")) {
             return ((String) valorUsuario).trim().toUpperCase();
        }
        return valorUsuario;
    }

    private boolean esEquivalenteEnCurso(String valor) {
        return valor.equals("en curso") || valor.equals("curso") || valor.equals("abierto") || 
               valor.equals("activo") || valor.equals("abiertos") || valor.equals("activos") || 
               valor.equals("en proceso") || valor.equals("pendiente") || valor.equals("pendientes") || 
               valor.equals("sin finalizar");
    }

    private boolean esEquivalenteFinalizado(String valor) {
        return valor.equals("finalizado") || valor.equals("finalizada") || valor.equals("terminado") || 
               valor.equals("terminada") || valor.equals("completado") || valor.equals("completados") || 
               valor.equals("cerrado") || valor.equals("cerrados") || valor.equals("finalizados") ||
               valor.equals("realizadas") || valor.equals("hechas");
    }

    private boolean esEquivalenteRechazado(String valor) {
        return valor.equals("rechazado") || valor.equals("rechazada") || valor.equals("rechazados") || 
               valor.equals("denegado") || valor.equals("denegados");
    }

    private String buscarValorReal(List<String> valoresReales, String... candidatos) {
        for (String candidato : candidatos) {
            if (valoresReales.contains(candidato)) {
                return candidato;
            }
        }
        // Fallback al primer candidato si no hay coincidencias (evita romper filtros que no existan pero deban formarse)
        return candidatos[0];
    }
}
