package com.leo.politicas_de_negocio.reportes.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Catálogo seguro de entidades y campos permitidos.
 * Solo se ejecutan consultas contra las entidades y campos registrados aquí.
 * La IA no puede forzar consultas fuera de este catálogo.
 */
@Service
public class ReporteCatalogoService {

    private final Map<String, List<String>> catalogoSeguro;

    public ReporteCatalogoService() {
        catalogoSeguro = new LinkedHashMap<>();
        
        // Instancias de políticas / trámites
        catalogoSeguro.put("instancias_politica", Arrays.asList(
                "id", "codigoTramite", "estadoInstancia", "fechaCreacion", "fechaFinalizacion",
                "creadaPor", "departamentoId", "departamentoActual", "politicaId", "politicaNombre",
                "funcionarioAsignado", "requierePago", "prioridad", "estado"
        ));
        
        // Políticas de negocio
        catalogoSeguro.put("politicas_negocio", Arrays.asList(
                "id", "nombre", "categoria", "estado", "requierePago", "version",
                "fechaCreacion", "descripcion", "activo"
        ));
        
        // Usuarios
        catalogoSeguro.put("usuarios", Arrays.asList(
                "id", "nombre", "correo", "rol", "departamentoId", "activo",
                "fechaRegistro", "telefono"
        ));
        
        // Pagos
        catalogoSeguro.put("pagos", Arrays.asList(
                "id", "instanciaPoliticaId", "politicaId", "monto", "estado",
                "fechaCreacion", "metodoPago", "referencia"
        ));
        
        // Tareas de actividad
        catalogoSeguro.put("tareas_actividad", Arrays.asList(
                "id", "instanciaId", "responsableId", "estado", "fechaCreacion",
                "fechaLimite", "fechaCompletado", "actividadNombre", "tipo"
        ));
        
        // Departamentos
        catalogoSeguro.put("departamentos", Arrays.asList(
                "id", "nombre", "responsableId", "descripcion", "activo"
        ));
        
        // Notificaciones
        catalogoSeguro.put("notificaciones", Arrays.asList(
                "id", "usuarioId", "tipo", "mensaje", "leida", "fechaCreacion"
        ));
        
        // Formularios dinámicos
        catalogoSeguro.put("formularios_dinamicos", Arrays.asList(
                "id", "instanciaId", "politicaId", "datos", "fechaCreacion"
        ));
        
        // Requisitos iniciales
        catalogoSeguro.put("requisitos_iniciales", Arrays.asList(
                "id", "politicaId", "nombre", "tipo", "obligatorio"
        ));
        
        // Trazabilidad
        catalogoSeguro.put("trazabilidad", Arrays.asList(
                "id", "instanciaId", "accion", "usuarioId", "fechaAccion",
                "detalle", "nodoId", "nodoNombre"
        ));
        
        // Predicciones IA
        catalogoSeguro.put("predicciones_ia", Arrays.asList(
                "id", "instanciaId", "politicaId", "cuelloBotella", "riesgoDemora",
                "prioridadRecomendada", "rutaRecomendada", "fechaPrediccion"
        ));
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
        if (campo == null || campo.isEmpty()) return true;
        return catalogoSeguro.get(entidad).contains(campo) || campo.equals("id") || campo.equals("monto") || campo.equals("_id");
    }
    
    /**
     * Valida un conjunto de campos contra una entidad.
     * @return Lista de campos no permitidos (vacía si todo es válido)
     */
    public List<String> validarCampos(String entidad, List<String> campos) {
        if (campos == null || campos.isEmpty()) return Collections.emptyList();
        List<String> invalidos = new ArrayList<>();
        for (String campo : campos) {
            if (!esCampoPermitido(entidad, campo)) {
                invalidos.add(campo);
            }
        }
        return invalidos;
    }
}
