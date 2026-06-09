package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReporteJsonNormalizer {

    private final ReporteCatalogoService catalogoService;
    private final ReporteValorNormalizer valorNormalizer;

    public ReporteJsonNormalizer(ReporteCatalogoService catalogoService, ReporteValorNormalizer valorNormalizer) {
        this.catalogoService = catalogoService;
        this.valorNormalizer = valorNormalizer;
    }

    public void normalizar(ReporteResponseDto definicion, String originalText) {
        if (definicion == null) return;

        if (definicion.getFormatoSalida() == null || definicion.getFormatoSalida().isEmpty()) {
            definicion.setFormatoSalida("pantalla");
        }
        if (definicion.getVisualizacion() == null || definicion.getVisualizacion().isEmpty()) {
            definicion.setVisualizacion("tabla");
        }
        if (definicion.getLimite() == null || definicion.getLimite() <= 0) {
            definicion.setLimite(50);
        }

        if (originalText != null) {
            String text = originalText.toLowerCase();
            
            // === PRIORIDAD: Detección de consultas de dinero/monto → forzar entidad pagos ===
            boolean esDinero = text.contains("dinero") || text.contains("monto total")
                    || text.contains("recaudado") || text.contains("recaudacion")
                    || text.contains("cuanto se genero") || text.contains("cuánto se generó")
                    || text.contains("ingresos") || text.contains("cobrado") || text.contains("cobro")
                    || (text.contains("generado") && (text.contains("politica") || text.contains("pago")))
                    || (text.contains("monto") && text.contains("pago"));
            if (esDinero && !"pagos".equalsIgnoreCase(definicion.getEntidadPrincipal())) {
                definicion.setEntidadPrincipal("pagos");
                // Asegurar métricas de pagos si no están definidas
                if (definicion.getMetricas() == null) definicion.setMetricas(new ArrayList<>());
                boolean hasSum = definicion.getMetricas().stream()
                        .anyMatch(m -> "sum".equalsIgnoreCase(m.getOperacion()));
                if (!hasSum) {
                    MetricaDto sumM = new MetricaDto();
                    sumM.setOperacion("sum");
                    sumM.setCampo("monto");
                    sumM.setAlias("montoTotal");
                    definicion.getMetricas().add(sumM);
                }
                boolean hasCount = definicion.getMetricas().stream()
                        .anyMatch(m -> "count".equalsIgnoreCase(m.getOperacion()));
                if (!hasCount) {
                    MetricaDto cntM = new MetricaDto();
                    cntM.setOperacion("count");
                    cntM.setCampo("id");
                    cntM.setAlias("cantidadPagos");
                    definicion.getMetricas().add(cntM);
                }
            } else if (text.contains("políticas creadas") || text.contains("politicas creadas")
                    || text.contains("workflows configurados") || text.contains("flujos existentes")) {

                definicion.setEntidadPrincipal("politicas_negocio");
            } else if (text.contains("políticas iniciadas") || text.contains("workflows iniciados") || text.contains("trámites iniciados") || text.contains("solicitudes iniciadas")) {
                definicion.setEntidadPrincipal("instancias_politica");
            } else if (text.contains("política más usada") || text.contains("workflow se usa más") || text.contains("trámite fue más iniciado") || text.contains("políticas más usadas")) {
                definicion.setEntidadPrincipal("instancias_politica");
                if (definicion.getAgrupaciones() == null) definicion.setAgrupaciones(new ArrayList<>());
                if (!definicion.getAgrupaciones().contains("politicaId")) definicion.getAgrupaciones().add("politicaId");
            } else if (text.contains("tareas de una política") || text.contains("nodos del workflow") || text.contains("tareas de la política") || text.contains("nodos de la política") || text.contains("pasos de") || text.contains("tareas del workflow") || text.contains("nodos de")) {
                definicion.setEntidadPrincipal("politicas_negocio");
            } else if (text.contains("tareas pendientes") || text.contains("tarea asignada") || text.contains("tareas asignadas") || text.contains("tareas de soporte") || text.contains("tareas en curso") || text.contains("tareas se completaron")) {
                definicion.setEntidadPrincipal("tareas_actividad");
            }
        }

        if (definicion.getEntidadPrincipal() != null) {
            definicion.setEntidadPrincipal(mapEntidad(definicion.getEntidadPrincipal()));
        }

        if (definicion.getMetricas() != null) {
            for (MetricaDto metrica : definicion.getMetricas()) {
                metrica.setCampo(mapCampo(definicion.getEntidadPrincipal(), metrica.getCampo()));
                if (metrica.getAlias() == null || metrica.getAlias().isEmpty()) {
                    metrica.setAlias("valor");
                }
            }
        }

        if (definicion.getCampos() != null) {
            List<String> campos = new ArrayList<>();
            for (String c : definicion.getCampos()) {
                campos.add(mapCampo(definicion.getEntidadPrincipal(), c));
            }
            
            // Enrich fields if requested in text
            if (originalText != null) {
                String text = originalText.toLowerCase();
                if (text.contains("nombre") || text.contains("quien") || text.contains("correo") || text.contains("cual") || text.contains("inició") || text.contains("iniciador") || text.contains("usuario")) {
                    List<String> enrichedCampos = new ArrayList<>(campos);
                    com.leo.politicas_de_negocio.reportes.model.EntidadReportable ent = catalogoService.obtenerEntidadPorNombreOAlias(definicion.getEntidadPrincipal());
                    if (ent != null && ent.getRelaciones() != null) {
                        for (String c : campos) {
                            for (com.leo.politicas_de_negocio.reportes.model.RelacionReportable rel : ent.getRelaciones()) {
                                if (rel.getCampoLocal().equals(c) && rel.getCamposEnriquecidos() != null) {
                                    for (String enr : rel.getCamposEnriquecidos()) {
                                        String logicalName = c + enr.substring(0, 1).toUpperCase() + enr.substring(1);
                                        if (!enrichedCampos.contains(logicalName)) {
                                            enrichedCampos.add(logicalName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    campos = enrichedCampos;
                }
            }
            
            definicion.setCampos(campos);
        }

        if (definicion.getAgrupaciones() != null) {
            List<String> agrupaciones = new ArrayList<>();
            for (String grp : definicion.getAgrupaciones()) {
                agrupaciones.add(mapCampo(definicion.getEntidadPrincipal(), grp));
            }
            
            // Fix for multiple groupings requesting users/clients
            if (agrupaciones.size() > 1 && originalText != null) {
                String text = originalText.toLowerCase();
                if (text.contains("quienes") || text.contains("quiénes") || text.contains("usuarios") || 
                    text.contains("clientes") || text.contains("solicitantes") || text.contains("iniciaron") || 
                    text.contains("personas") || text.contains("quien inició") || text.contains("quién inició")) {
                    
                    String mainGroup = agrupaciones.get(0);
                    String userGroup = agrupaciones.get(1); // usually "creadaPor" or similar
                    
                    // Keep only the first grouping
                    List<String> newGroups = new ArrayList<>();
                    newGroups.add(mainGroup);
                    agrupaciones = newGroups;
                    
                    // Add addToSet metric
                    if (definicion.getMetricas() == null) definicion.setMetricas(new ArrayList<>());
                    
                    boolean hasCount = false;
                    for (MetricaDto m : definicion.getMetricas()) {
                        if (m.getOperacion().equalsIgnoreCase("count")) hasCount = true;
                    }
                    
                    if (!hasCount) {
                        MetricaDto countMetric = new MetricaDto();
                        countMetric.setOperacion("count");
                        countMetric.setCampo("id");
                        countMetric.setAlias("cantidadTramites");
                        definicion.getMetricas().add(countMetric);
                    }
                    
                    MetricaDto addToSetMetric = new MetricaDto();
                    addToSetMetric.setOperacion("addToSet");
                    addToSetMetric.setCampo(userGroup);
                    addToSetMetric.setAlias("usuariosIniciadores");
                    definicion.getMetricas().add(addToSetMetric);
                    
                    definicion.setVisualizacion("tabla");
                    
                    if (definicion.getOrdenamiento() == null || definicion.getOrdenamiento().isEmpty()) {
                        List<OrdenamientoDto> ords = new ArrayList<>();
                        OrdenamientoDto ord = new OrdenamientoDto();
                        ord.setCampo("cantidadTramites");
                        ord.setDireccion("desc");
                        ords.add(ord);
                        definicion.setOrdenamiento(ords);
                    }
                }
            }
            
            definicion.setAgrupaciones(agrupaciones);
        }

        if (definicion.getFiltros() != null) {
            for (FiltroDto filtro : definicion.getFiltros()) {
                filtro.setCampo(mapCampo(definicion.getEntidadPrincipal(), filtro.getCampo()));
                filtro.setValor(valorNormalizer.normalizarValorFiltro(definicion.getEntidadPrincipal(), filtro.getCampo(), filtro.getValor()));
            }
        }

        if (definicion.getOrdenamiento() != null) {
            for (OrdenamientoDto ord : definicion.getOrdenamiento()) {
                boolean isAlias = false;
                if (definicion.getMetricas() != null) {
                    for (MetricaDto m : definicion.getMetricas()) {
                        if (ord.getCampo().equals(m.getAlias())) {
                            isAlias = true;
                            break;
                        }
                    }
                }
                if (!isAlias) {
                    ord.setCampo(mapCampo(definicion.getEntidadPrincipal(), ord.getCampo()));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void normalizarPlan(Map<String, Object> plan) {
        if (plan == null) return;

        if (!plan.containsKey("formatoSalida")) plan.put("formatoSalida", "pantalla");
        if (!plan.containsKey("visualizacion")) plan.put("visualizacion", "tabla");
        if (!plan.containsKey("limite")) plan.put("limite", 50);

        if (plan.containsKey("entidadPrincipal")) {
            plan.put("entidadPrincipal", mapEntidad((String) plan.get("entidadPrincipal")));
        }

        if (plan.containsKey("campos")) {
            List<String> campos = (List<String>) plan.get("campos");
            if (campos != null) {
                List<String> mapped = new ArrayList<>();
                for (String c : campos) {
                    mapped.add(mapCampo((String) plan.get("entidadPrincipal"), c));
                }
                plan.put("campos", mapped);
            }
        }

        if (plan.containsKey("agrupaciones")) {
            List<String> agrs = (List<String>) plan.get("agrupaciones");
            if (agrs != null) {
                List<String> mapped = new ArrayList<>();
                for (String a : agrs) {
                    mapped.add(mapCampo((String) plan.get("entidadPrincipal"), a));
                }
                plan.put("agrupaciones", mapped);
            }
        }

        if (plan.containsKey("filtros")) {
            List<Map<String, Object>> filtros = (List<Map<String, Object>>) plan.get("filtros");
            if (filtros != null) {
                for (Map<String, Object> f : filtros) {
                    String campo = mapCampo((String) plan.get("entidadPrincipal"), (String) f.get("campo"));
                    f.put("campo", campo);
                    f.put("valor", valorNormalizer.normalizarValorFiltro((String) plan.get("entidadPrincipal"), campo, f.get("valor")));
                }
            }
        }
        
        if (plan.containsKey("ordenamiento")) {
            List<Map<String, String>> ords = (List<Map<String, String>>) plan.get("ordenamiento");
            if (ords != null) {
                for (Map<String, String> o : ords) {
                    String c = o.get("campo");
                    o.put("campo", mapCampo((String) plan.get("entidadPrincipal"), c));
                }
            }
        }
    }

    private String mapEntidad(String entidad) {
        if (entidad == null) return null;
        com.leo.politicas_de_negocio.reportes.model.EntidadReportable ent = catalogoService.obtenerEntidadPorNombreOAlias(entidad);
        if (ent != null) return ent.getNombreLogico();
        return entidad.trim();
    }

    private String mapCampo(String entidadLogica, String campo) {
        if (campo == null || entidadLogica == null) return campo;
        com.leo.politicas_de_negocio.reportes.model.EntidadReportable ent = catalogoService.obtenerEntidadPorNombreOAlias(entidadLogica);
        if (ent != null) {
            com.leo.politicas_de_negocio.reportes.model.CampoReportable c = catalogoService.obtenerCampoDeEntidad(ent, campo);
            if (c != null) return c.getNombreLogico();
        }
        String c = campo.trim();
        if (c.equalsIgnoreCase("id")) return "_id";
        return c;
    }


}
