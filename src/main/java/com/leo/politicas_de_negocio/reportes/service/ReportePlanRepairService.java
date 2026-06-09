package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.FiltroDto;
import com.leo.politicas_de_negocio.reportes.dto.MetricaDto;
import com.leo.politicas_de_negocio.reportes.dto.OrdenamientoDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.reportes.model.CampoReportable;
import com.leo.politicas_de_negocio.reportes.model.EntidadReportable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportePlanRepairService {

    private final ReporteCampoResolver campoResolver;
    private final ReporteCatalogoService catalogoService;

    public void repararPlan(ReporteResponseDto plan, String originalText) {
        if (plan == null) return;

        log.info("Iniciando reparación de plan. Entidad original: {}", plan.getEntidadPrincipal());

        // 0. Detectar si la intención es de resumen ejecutivo / dashboard
        if (originalText != null) {
            String text = originalText.toLowerCase().trim();
            if (text.contains("resumen ejecutivo") || text.contains("dashboard") || 
                text.contains("resumen del mes") || text.contains("resumen mensual") || 
                text.contains("metricas generales") || text.contains("resumen del sistema") ||
                text.contains("indicadores del mes") || text.contains("kpis del mes")) {
                
                plan.setIntencionDetectada("resumen_ejecutivo");
                plan.setTitulo("Resumen Ejecutivo");
                plan.setDescripcion("Métricas y KPIs consolidados del sistema.");
                plan.setEntidadPrincipal("instancias_politica"); // Default
                plan.setVisualizacion("resumen_ejecutivo");
                plan.setRequiereAclaracion(false);
                return;
            }
        }

        // 0b. Bypass para cuellos_botella — se maneja por ruta separada
        if ("cuellos_botella".equals(plan.getIntencionDetectada()) ||
                "analiticas".equalsIgnoreCase(plan.getEntidadPrincipal())) {
            return;
        }


        if (plan.getEntidadPrincipal() == null || plan.getEntidadPrincipal().isEmpty()) {
            plan.setEntidadPrincipal("instancias_politica"); // Fallback
        }

        // Mapear entidad si vino con alias
        EntidadReportable entPrincipal = catalogoService.obtenerEntidadPorNombreOAlias(plan.getEntidadPrincipal());
        if (entPrincipal != null) {
            plan.setEntidadPrincipal(entPrincipal.getNombreLogico());
        }

        // 1. Reparar filtros
        if (plan.getFiltros() != null) {
            List<FiltroDto> filtrosValidos = new ArrayList<>();
            for (FiltroDto filtro : plan.getFiltros()) {
                String campo = filtro.getCampo();
                
                // Resolver campo
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), campo);
                if (rf != null) {
                    filtro.setCampo(rf.getOriginalFieldName()); // Mantener nombre original para lógica, builder usará mongo path
                    
                    // D) Validar si es un filtro de fecha y si la entidad destino/actual tiene campos tipo fecha
                    if (filtro.getOperador() != null && isDateOperator(filtro.getOperador())) {
                        String targetEntity = rf.getPath().isEmpty() ? plan.getEntidadPrincipal() : rf.getPath().get(rf.getPath().size() - 1).getTarget();
                        EntidadReportable entTarget = catalogoService.obtenerEntidadPorNombreOAlias(targetEntity);
                        
                        if (entTarget != null) {
                            boolean tieneCampoFecha = entTarget.getCampos().stream()
                                    .anyMatch(c -> "date".equalsIgnoreCase(c.getTipoDato()));
                            
                            if (!tieneCampoFecha) {
                                // Buscar campo de fecha alternativo en la misma entidad
                                log.warn("La entidad {} no tiene un campo de fecha directo.", targetEntity);
                                plan.setRequiereAclaracion(true);
                                plan.setPreguntaAclaratoria("La entidad " + targetEntity + " no tiene campo de fecha reportable.");
                                return;
                            }
                        }
                    }
                    filtrosValidos.add(filtro);
                } else {
                    log.warn("Filtro con campo no resoluble: {} en entidad {}", campo, plan.getEntidadPrincipal());
                }
            }
            plan.setFiltros(filtrosValidos);
        }

        // 2. Reparar agrupaciones y métricas
        if (plan.getAgrupaciones() != null) {
            List<String> agrsValidos = new ArrayList<>();
            for (String grp : plan.getAgrupaciones()) {
                // Si la agrupación es invalida, ej: pagos con politicaId, ReporteCampoResolver lo resolverá a politicaNombre o similar
                if ("politicaId".equalsIgnoreCase(grp) && "pagos".equalsIgnoreCase(plan.getEntidadPrincipal())) {
                    grp = "politicaNombre";
                }
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), grp);
                if (rf != null) {
                    agrsValidos.add(rf.getOriginalFieldName());
                } else {
                    log.warn("Agrupación con campo no resoluble: {} en entidad {}", grp, plan.getEntidadPrincipal());
                }
            }
            plan.setAgrupaciones(agrsValidos);
        }

        // 3. Reparar campos solicitados
        if (plan.getCampos() != null) {
            List<String> camposValidos = new ArrayList<>();
            for (String c : plan.getCampos()) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), c);
                if (rf != null) {
                    camposValidos.add(rf.getOriginalFieldName());
                }
            }
            plan.setCampos(camposValidos);
        }

        // 4. Reparar ordenamiento
        if (plan.getOrdenamiento() != null) {
            List<OrdenamientoDto> ordsValidos = new ArrayList<>();
            for (OrdenamientoDto o : plan.getOrdenamiento()) {
                // Puede ser una métrica o un campo
                boolean isMetricAlias = false;
                if (plan.getMetricas() != null) {
                    for (MetricaDto m : plan.getMetricas()) {
                        if (o.getCampo().equals(m.getAlias())) {
                            isMetricAlias = true;
                            break;
                        }
                    }
                }
                if (isMetricAlias) {
                    ordsValidos.add(o);
                } else {
                    ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), o.getCampo());
                    if (rf != null) {
                        o.setCampo(rf.getOriginalFieldName());
                        ordsValidos.add(o);
                    }
                }
            }
            plan.setOrdenamiento(ordsValidos);
        }

        // 5. E) Corregir visualización para listas de usuarios/nombres
        boolean tieneListadosNombres = false;
        List<String> checkFields = new ArrayList<>();
        if (plan.getCampos() != null) checkFields.addAll(plan.getCampos());
        if (plan.getAgrupaciones() != null) checkFields.addAll(plan.getAgrupaciones());
        
        for (String f : checkFields) {
            String flc = f.toLowerCase();
            if (flc.contains("nombre") || flc.contains("correo") || flc.contains("usuario") || flc.contains("creadapor") || flc.contains("responsable") || flc.contains("funcionario")) {
                tieneListadosNombres = true;
                break;
            }
        }

        if (tieneListadosNombres && !"resumen_ejecutivo".equals(plan.getVisualizacion())) {
            plan.setVisualizacion("tabla");
        }
    }

    private boolean isDateOperator(String op) {
        String o = op.toLowerCase();
        return o.contains("mes") || o.contains("anio") || o.contains("dia") || o.contains("fecha") || o.contains("semana") || o.contains("hoy") || o.contains("ayer") || o.contains("rango");
    }
}
