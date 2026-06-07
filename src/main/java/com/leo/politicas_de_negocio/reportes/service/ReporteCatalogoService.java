package com.leo.politicas_de_negocio.reportes.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReporteCatalogoService {

    private final Map<String, List<String>> catalogoSeguro;

    public ReporteCatalogoService() {
        catalogoSeguro = new HashMap<>();
        catalogoSeguro.put("instancias_politica", Arrays.asList("id", "codigoTramite", "estadoInstancia", "fechaCreacion", "creadaPor", "departamentoId"));
        catalogoSeguro.put("politicas_negocio", Arrays.asList("id", "nombre", "categoria", "estado", "requierePago"));
        catalogoSeguro.put("usuarios", Arrays.asList("id", "nombre", "correo", "rol", "departamentoId", "activo"));
        catalogoSeguro.put("pagos", Arrays.asList("id", "instanciaPoliticaId", "politicaId", "monto", "estado", "fechaCreacion"));
        catalogoSeguro.put("tareas_actividad", Arrays.asList("id", "instanciaId", "responsableId", "estado", "fechaCreacion"));
        catalogoSeguro.put("departamentos", Arrays.asList("id", "nombre", "responsableId"));
    }

    public Map<String, List<String>> getCatalogo() {
        return catalogoSeguro;
    }

    public boolean esEntidadPermitida(String entidad) {
        if (entidad == null) return false;
        return catalogoSeguro.containsKey(entidad);
    }

    public boolean esCampoPermitido(String entidad, String campo) {
        if (!esEntidadPermitida(entidad)) return false;
        if (campo == null || campo.isEmpty()) return true; // Para count sobre id a veces pasa vacio
        return catalogoSeguro.get(entidad).contains(campo) || campo.equals("id") || campo.equals("monto");
    }
}
