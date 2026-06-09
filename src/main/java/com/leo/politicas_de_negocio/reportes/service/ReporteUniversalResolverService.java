package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.FiltroDto;
import com.leo.politicas_de_negocio.reportes.dto.MetricaDto;
import com.leo.politicas_de_negocio.reportes.dto.OrdenamientoDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.reportes.model.EntidadReportable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteUniversalResolverService {

    private final ReporteCampoResolver campoResolver;
    private final ReporteCatalogoService catalogoService;

    /**
     * Resuelve y reescribe un plan de la IA para convertirlo en un plan ejecutable.
     * Si algún campo solicitado no es resoluble ni directa ni indirectamente, lanza IllegalArgumentException.
     */
    public void resolverPlan(ReporteResponseDto plan, String originalText) {
        if (plan == null) return;

        log.info("Iniciando resolución universal del plan. Entidad base original: {}", plan.getEntidadPrincipal());

        // 1. Detectar reporte compuesto/resumen ejecutivo
        if (originalText != null) {
            String text = originalText.toLowerCase().trim();
            if (text.contains("resumen ejecutivo") || text.contains("dashboard") || 
                text.contains("resumen del mes") || text.contains("resumen mensual") || 
                text.contains("metricas generales") || text.contains("resumen del sistema") ||
                text.contains("indicadores del mes") || text.contains("kpis del mes") ||
                text.contains("kpi del mes") || text.contains("indicadores generales") ||
                text.contains("resumen de indicadores") || text.contains("panel de control")) {
                
                plan.setIntencionDetectada("resumen_ejecutivo");
                plan.setTitulo("Resumen Ejecutivo");
                plan.setDescripcion("Métricas consolidadas del sistema.");
                plan.setEntidadPrincipal("instancias_politica");
                plan.setVisualizacion("resumen_ejecutivo");
                plan.setRequiereAclaracion(false);
                return;
            }
        }

        // 1b. Si la intención ya es cuellos_botella o analiticas, no validar (se maneja aparte)
        if ("cuellos_botella".equals(plan.getIntencionDetectada()) ||
                "analiticas".equalsIgnoreCase(plan.getEntidadPrincipal())) {
            return;
        }

        if (plan.getEntidadPrincipal() == null || plan.getEntidadPrincipal().isEmpty()) {
            plan.setEntidadPrincipal("instancias_politica");
        }

        // Mapear entidad si vino con alias
        EntidadReportable entPrincipal = catalogoService.obtenerEntidadPorNombreOAlias(plan.getEntidadPrincipal());
        if (entPrincipal != null) {
            plan.setEntidadPrincipal(entPrincipal.getNombreLogico());
        }

        // 2. Resolver campos solicitados
        if (plan.getCampos() != null) {
            Set<String> camposResueltos = new LinkedHashSet<>();
            for (String campo : plan.getCampos()) {
                boolean isMetricAlias = false;
                if (plan.getMetricas() != null) {
                    for (MetricaDto m : plan.getMetricas()) {
                        if (campo.equals(m.getAlias())) {
                            isMetricAlias = true;
                            break;
                        }
                    }
                }
                if (isMetricAlias) {
                    camposResueltos.add(campo);
                    continue;
                }

                // Auto-reparar si es una solicitud implícita de conteo/ranking
                if (("cantidad".equalsIgnoreCase(campo) || "conteo".equalsIgnoreCase(campo) || "total".equalsIgnoreCase(campo)) 
                        && plan.getAgrupaciones() != null && !plan.getAgrupaciones().isEmpty()) {
                    boolean metricaExiste = false;
                    if (plan.getMetricas() != null) {
                        for (MetricaDto m : plan.getMetricas()) {
                            if (m.getOperacion().equalsIgnoreCase("count")) {
                                metricaExiste = true;
                                campo = m.getAlias();
                                break;
                            }
                        }
                    }
                    if (!metricaExiste) {
                        if (plan.getMetricas() == null) {
                            plan.setMetricas(new ArrayList<>());
                        }
                        MetricaDto m = new MetricaDto();
                        m.setOperacion("count");
                        m.setCampo("id");
                        m.setAlias(campo);
                        plan.getMetricas().add(m);
                    }
                    camposResueltos.add(campo);
                    continue;
                }

                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), campo);
                if (rf == null) {
                    throw new IllegalArgumentException("El campo '" + campo + "' no se pudo resolver en la entidad principal '" 
                            + plan.getEntidadPrincipal() + "' ni a través de relaciones del catálogo.");
                }
                camposResueltos.add(rf.getOriginalFieldName());
            }
            plan.setCampos(new ArrayList<>(camposResueltos));
        }

        // 3. Resolver filtros
        if (plan.getFiltros() != null) {
            List<FiltroDto> filtrosResueltos = new ArrayList<>();
            for (FiltroDto filtro : plan.getFiltros()) {
                // Soportar aplanamiento de subconsultas anidadas de la IA
                if (filtro.getValor() instanceof Map) {
                    Map<?, ?> valMap = (Map<?, ?>) filtro.getValor();
                    if (valMap.containsKey("filtros") && valMap.get("filtros") instanceof List) {
                        List<?> nested = (List<?>) valMap.get("filtros");
                        for (Object nfObj : nested) {
                            if (nfObj instanceof Map) {
                                Map<?, ?> nfMap = (Map<?, ?>) nfObj;
                                String nestedCampo = String.valueOf(nfMap.get("campo"));
                                String nestedOp = String.valueOf(nfMap.get("operador"));
                                Object nestedVal = nfMap.get("valor");
                                
                                FiltroDto flatFiltro = new FiltroDto();
                                flatFiltro.setCampo(nestedCampo);
                                flatFiltro.setOperador(nestedOp);
                                flatFiltro.setValor(nestedVal);
                                
                                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), flatFiltro.getCampo());
                                if (rf != null) {
                                    flatFiltro.setCampo(rf.getOriginalFieldName());
                                    filtrosResueltos.add(flatFiltro);
                                }
                            }
                        }
                        continue; // Omitir el filtro anidado original
                    }
                }

                String campo = filtro.getCampo();
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), campo);
                if (rf == null) {
                    throw new IllegalArgumentException("El campo de filtro '" + campo + "' no es válido en la entidad principal '"
                            + plan.getEntidadPrincipal() + "' ni a través de relaciones.");
                }
                
                filtro.setCampo(rf.getOriginalFieldName());

                // Validar fecha en filtro
                if (filtro.getOperador() != null && isDateOperator(filtro.getOperador())) {
                    String targetEntity = rf.getPath().isEmpty() ? plan.getEntidadPrincipal() : rf.getPath().get(rf.getPath().size() - 1).getTarget();
                    EntidadReportable entTarget = catalogoService.obtenerEntidadPorNombreOAlias(targetEntity);
                    if (entTarget != null) {
                        boolean tieneFecha = entTarget.getCampos().stream().anyMatch(c -> "date".equalsIgnoreCase(c.getTipoDato()));
                        if (!tieneFecha) {
                            plan.setRequiereAclaracion(true);
                            plan.setPreguntaAclaratoria("La entidad " + targetEntity + " no tiene campo de fecha reportable.");
                            return;
                        }
                    }
                }
                filtrosResueltos.add(filtro);
            }
            plan.setFiltros(filtrosResueltos);
        }

        // 4. Resolver agrupaciones
        if (plan.getAgrupaciones() != null) {
            Set<String> agrupacionesResueltas = new LinkedHashSet<>();
            for (String grp : plan.getAgrupaciones()) {
                if ("politicaId".equalsIgnoreCase(grp)) {
                    grp = "politicaNombre";
                } else if ("creadaPor".equalsIgnoreCase(grp)) {
                    grp = "creadaPorNombre";
                } else if ("responsableId".equalsIgnoreCase(grp)) {
                    grp = "responsableNombre";
                } else if ("funcionarioAsignado".equalsIgnoreCase(grp)) {
                    grp = "funcionarioNombre";
                } else if ("usuarioId".equalsIgnoreCase(grp)) {
                    grp = "usuarioNombre";
                } else if ("subidoPor".equalsIgnoreCase(grp)) {
                    grp = "usuarioNombre";
                } else if ("departamentoId".equalsIgnoreCase(grp)) {
                    grp = "departamentoNombre";
                }
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), grp);
                if (rf == null) {
                    throw new IllegalArgumentException("El campo de agrupación '" + grp + "' no se pudo resolver.");
                }
                agrupacionesResueltas.add(rf.getOriginalFieldName());
            }
            plan.setAgrupaciones(new ArrayList<>(agrupacionesResueltas));
        }

        // 5. Resolver métricas
        if (plan.getMetricas() != null) {
            List<MetricaDto> metricasResueltas = new ArrayList<>();
            for (MetricaDto metrica : plan.getMetricas()) {
                String campo = metrica.getCampo();
                if (campo != null && !campo.equals("id") && !campo.equals("_id")) {
                    ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), campo);
                    if (rf == null) {
                        throw new IllegalArgumentException("El campo de métrica '" + campo + "' no se pudo resolver.");
                    }
                    metrica.setCampo(rf.getOriginalFieldName());
                }
                metricasResueltas.add(metrica);
            }
            plan.setMetricas(metricasResueltas);
        }

        // 6. Resolver ordenamiento
        if (plan.getOrdenamiento() != null) {
            List<OrdenamientoDto> ordenamientosResueltos = new ArrayList<>();
            for (OrdenamientoDto ord : plan.getOrdenamiento()) {
                boolean isMetricAlias = false;
                if (plan.getMetricas() != null) {
                    for (MetricaDto m : plan.getMetricas()) {
                        if (ord.getCampo().equals(m.getAlias())) {
                            isMetricAlias = true;
                            break;
                        }
                    }
                }
                if (isMetricAlias) {
                    ordenamientosResueltos.add(ord);
                } else {
                    ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(plan.getEntidadPrincipal(), ord.getCampo());
                    if (rf == null) {
                        throw new IllegalArgumentException("El campo de ordenamiento '" + ord.getCampo() + "' no se pudo resolver.");
                    }
                    ord.setCampo(rf.getOriginalFieldName());
                    ordenamientosResueltos.add(ord);
                }
            }
            plan.setOrdenamiento(ordenamientosResueltos);
        }

        // 7. Forzar visualización de tipo tabla si se piden listados con campos textuales/de nombres
        boolean tieneTextos = false;
        List<String> checkFields = new ArrayList<>();
        if (plan.getCampos() != null) checkFields.addAll(plan.getCampos());
        if (plan.getAgrupaciones() != null) checkFields.addAll(plan.getAgrupaciones());
        for (String f : checkFields) {
            String flc = f.toLowerCase();
            if (flc.contains("nombre") || flc.contains("correo") || flc.contains("usuario") || flc.contains("creadapor") || flc.contains("responsable") || flc.contains("funcionario")) {
                tieneTextos = true;
                break;
            }
        }
        if (tieneTextos && !"resumen_ejecutivo".equals(plan.getVisualizacion())) {
            plan.setVisualizacion("tabla");
        }
    }

    private boolean isDateOperator(String op) {
        String o = op.toLowerCase();
        return o.contains("mes") || o.contains("anio") || o.contains("dia") || o.contains("fecha") || o.contains("semana") || o.contains("hoy") || o.contains("ayer") || o.contains("rango");
    }
}
