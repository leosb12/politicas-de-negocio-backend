package com.leo.politicas_de_negocio.workflow_metricas.service;

import com.leo.politicas_de_negocio.workflow_metricas.model.MetricaInstancia;
import com.leo.politicas_de_negocio.workflow_metricas.model.MetricaNodo;
import com.leo.politicas_de_negocio.workflow_metricas.repository.MetricaInstanciaRepository;
import com.leo.politicas_de_negocio.workflow_metricas.repository.MetricaNodoRepository;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowMetricasService {

    private final MetricaInstanciaRepository metricaInstanciaRepository;
    private final MetricaNodoRepository metricaNodoRepository;
    private final InstanciaPoliticaRepository instanciaRepository;
    private final PoliticaNegocioRepository politicaRepository;
    private final DepartamentoRepository departamentoRepository;

    public void registrarEntradaNodo(String idInstancia, String idNodo) {
        log.info("Registrando entrada al nodo {} para la instancia {}", idNodo, idInstancia);
        
        InstanciaPolitica instancia = instanciaRepository.findById(idInstancia).orElse(null);
        if (instancia == null) return;
        
        PoliticaNegocio politica = politicaRepository.findById(instancia.getPoliticaId()).orElse(null);
        if (politica == null) return;
        
        Nodo nodoDef = buscarNodoDefinicion(politica, idNodo);
        String nombreNodo = nodoDef != null ? nodoDef.getNombre() : "Desconocido";
        String tipoNodo = nodoDef != null && nodoDef.getTipo() != null ? nodoDef.getTipo().name() : "Desconocido";
        String departamentoId = nodoDef != null ? nodoDef.getDepartamentoId() : null;
        String carrilNombre = "Sistema";
        
        if (departamentoId != null) {
            Departamento depto = departamentoRepository.findById(departamentoId).orElse(null);
            if (depto != null) {
                carrilNombre = depto.getNombre();
            }
        }
        
        MetricaNodo ultimoRegistro = metricaNodoRepository.findFirstByIdInstanciaAndIdNodoOrderByFechaEntradaDesc(idInstancia, idNodo).orElse(null);
        boolean esRetorno = ultimoRegistro != null; // Si ya habíamos entrado a este nodo antes, es un retorno o ciclo.

        // Crear registro de nodo
        MetricaNodo metricaNodo = MetricaNodo.builder()
                .idInstancia(idInstancia)
                .idPolitica(politica.getId())
                .idNodo(idNodo)
                .nombreNodo(nombreNodo)
                .tipoNodo(tipoNodo)
                .departamento(departamentoId)
                .carrilId(departamentoId)
                .carrilNombre(carrilNombre)
                .esRetorno(esRetorno)
                .fechaEntrada(LocalDateTime.now())
                .estadoNodo("ACTIVO")
                .build();
                
        metricaNodoRepository.save(metricaNodo);
        
        // Actualizar métrica de instancia
        MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia)
            .orElseGet(() -> crearMetricaInstanciaInicial(instancia, politica));
            
        if (metricaInstancia.getNodosVisitados() == null) metricaInstancia.setNodosVisitados(new ArrayList<>());
        if (!metricaInstancia.getNodosVisitados().contains(idNodo)) {
            metricaInstancia.getNodosVisitados().add(idNodo);
        }
        
        if (metricaInstancia.getActividadesVisitadas() == null) metricaInstancia.setActividadesVisitadas(new ArrayList<>());
        if ("ACTIVIDAD".equals(tipoNodo)) {
            metricaInstancia.getActividadesVisitadas().add(nombreNodo);
        }
        
        if (metricaInstancia.getCarrilesVisitados() == null) metricaInstancia.setCarrilesVisitados(new ArrayList<>());
        if (carrilNombre != null && !metricaInstancia.getCarrilesVisitados().contains(carrilNombre)) {
            metricaInstancia.getCarrilesVisitados().add(carrilNombre);
        }
        
        if (departamentoId != null) {
            if (metricaInstancia.getDepartamentosParticipantes() == null) metricaInstancia.setDepartamentosParticipantes(new ArrayList<>());
            if (!metricaInstancia.getDepartamentosParticipantes().contains(departamentoId)) {
                metricaInstancia.getDepartamentosParticipantes().add(departamentoId);
            }
        }
        
        if (esRetorno) {
            metricaInstancia.setCantidadRetornos(metricaInstancia.getCantidadRetornos() + 1);
            metricaInstancia.setCantidadReprocesos(metricaInstancia.getCantidadReprocesos() + 1);
        }
        if ("DECISION".equals(tipoNodo)) metricaInstancia.setCantidadDecisiones(metricaInstancia.getCantidadDecisiones() + 1);
        if ("FORK".equals(tipoNodo)) metricaInstancia.setCantidadForks(metricaInstancia.getCantidadForks() + 1);
        if ("JOIN".equals(tipoNodo)) metricaInstancia.setCantidadJoins(metricaInstancia.getCantidadJoins() + 1);
        
        if (metricaInstancia.getRutaEjecutada() == null) metricaInstancia.setRutaEjecutada(new ArrayList<>());
        metricaInstancia.getRutaEjecutada().add(idNodo);
        metricaInstancia.setEstadoActual(instancia.getEstadoInstancia().name());
        
        metricaInstancia.setRutaEjecutadaLegible(String.join(" -> ", armarRutaLegible(politica, metricaInstancia.getRutaEjecutada())));
        metricaInstancia.setRutaEjecutadaCodificada(metricaInstancia.getRutaEjecutada().toString().hashCode() + "");
        
        metricaInstanciaRepository.save(metricaInstancia);
    }

    public void registrarSalidaNodo(String idInstancia, String idNodo) {
        log.info("Registrando salida del nodo {} para la instancia {}", idNodo, idInstancia);
        
        MetricaNodo metricaNodo = metricaNodoRepository.findFirstByIdInstanciaAndIdNodoOrderByFechaEntradaDesc(idInstancia, idNodo)
            .orElse(null);
            
        if (metricaNodo != null && metricaNodo.getFechaSalida() == null) {
            LocalDateTime ahora = LocalDateTime.now();
            metricaNodo.setFechaSalida(ahora);
            metricaNodo.setEstadoNodo("COMPLETADO");
            
            if (metricaNodo.getFechaEntrada() != null) {
                long minutos = Duration.between(metricaNodo.getFechaEntrada(), ahora).toMinutes();
                metricaNodo.setDuracionEnMinutos(minutos);
            }
            metricaNodoRepository.save(metricaNodo);
        }
        
        // Actualizar instancia a ver si ya finalizó
        InstanciaPolitica instancia = instanciaRepository.findById(idInstancia).orElse(null);
        if (instancia != null) {
            MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia).orElse(null);
            if (metricaInstancia != null) {
                metricaInstancia.setEstadoActual(instancia.getEstadoInstancia().name());
                if ("FINALIZADA".equals(instancia.getEstadoInstancia().name())) {
                    metricaInstancia.setFechaFin(instancia.getFechaFinalizacion() != null ? instancia.getFechaFinalizacion() : LocalDateTime.now());
                    if (metricaInstancia.getFechaInicio() != null && metricaInstancia.getFechaFin() != null) {
                        metricaInstancia.setDuracionTotal(Duration.between(metricaInstancia.getFechaInicio(), metricaInstancia.getFechaFin()).toMinutes());
                    }
                    metricaInstancia.setEstadoFinal(instancia.getEstadoInstancia().name());
                }
                metricaInstanciaRepository.save(metricaInstancia);
            }
        }
    }

    private MetricaInstancia crearMetricaInstanciaInicial(InstanciaPolitica instancia, PoliticaNegocio politica) {
        return MetricaInstancia.builder()
            .idInstancia(instancia.getId())
            .idPolitica(politica.getId())
            .nombrePolitica(politica.getNombre())
            .usuarioSolicitante(instancia.getCreadaPor())
            .fechaInicio(instancia.getFechaCreacion())
            .estadoActual(instancia.getEstadoInstancia().name())
            .rutaEjecutada(new ArrayList<>())
            .nodosVisitados(new ArrayList<>())
            .departamentosParticipantes(new ArrayList<>())
            .funcionariosAsignados(new ArrayList<>())
            .carrilesVisitados(new ArrayList<>())
            .actividadesVisitadas(new ArrayList<>())
            .decisionesTomadas(new ArrayList<>())
            .build();
    }
    
    private List<String> armarRutaLegible(PoliticaNegocio politica, List<String> rutaIds) {
        List<String> legibles = new ArrayList<>();
        if (rutaIds == null) return legibles;
        for (String id : rutaIds) {
            Nodo n = buscarNodoDefinicion(politica, id);
            if (n != null) {
                legibles.add((n.getTipo() != null ? n.getTipo().name() : "NODO") + (n.getNombre() != null ? ": " + n.getNombre() : ""));
            } else {
                legibles.add(id);
            }
        }
        return legibles;
    }
    
    private Nodo buscarNodoDefinicion(PoliticaNegocio politica, String idNodo) {
        if (politica.getNodos() == null) return null;
        return politica.getNodos().stream()
                .filter(n -> idNodo.equals(n.getId()))
                .findFirst()
                .orElse(null);
    }
    
    public void registrarObservacion(String idInstancia, String idNodo, String observacion) {
        MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia).orElse(null);
        if (metricaInstancia != null) {
            metricaInstancia.setCantidadObservaciones(metricaInstancia.getCantidadObservaciones() + 1);
            metricaInstanciaRepository.save(metricaInstancia);
        }
        
        MetricaNodo metricaNodo = metricaNodoRepository.findFirstByIdInstanciaAndIdNodoOrderByFechaEntradaDesc(idInstancia, idNodo).orElse(null);
        if (metricaNodo != null && observacion != null && !observacion.isBlank()) {
            if (metricaNodo.getObservaciones() == null) metricaNodo.setObservaciones(new ArrayList<>());
            metricaNodo.getObservaciones().add(observacion);
            metricaNodoRepository.save(metricaNodo);
        }
    }

    public void registrarRechazo(String idInstancia, String idNodo, String motivo) {
        MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia).orElse(null);
        if (metricaInstancia != null) {
            metricaInstancia.setCantidadRechazos(metricaInstancia.getCantidadRechazos() + 1);
            metricaInstanciaRepository.save(metricaInstancia);
        }
    }

    public void registrarReenvio(String idInstancia, String idNodo) {
        MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia).orElse(null);
        if (metricaInstancia != null) {
            metricaInstancia.setCantidadReenvios(metricaInstancia.getCantidadReenvios() + 1);
            metricaInstanciaRepository.save(metricaInstancia);
        }
    }

    public void registrarFinInstancia(String idInstancia, String estadoFinal) {
        MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia).orElse(null);
        if (metricaInstancia != null) {
            metricaInstancia.setEstadoActual(estadoFinal);
            metricaInstancia.setEstadoFinal(estadoFinal);
            if (metricaInstancia.getFechaFin() == null) {
                metricaInstancia.setFechaFin(LocalDateTime.now());
                if (metricaInstancia.getFechaInicio() != null) {
                    metricaInstancia.setDuracionTotal(Duration.between(metricaInstancia.getFechaInicio(), metricaInstancia.getFechaFin()).toMinutes());
                }
            }
            metricaInstanciaRepository.save(metricaInstancia);
        }
    }

    public Map<String, Object> obtenerHistorialInstancia(String idInstancia) {
        MetricaInstancia metricaInstancia = metricaInstanciaRepository.findByIdInstancia(idInstancia).orElse(null);
        List<MetricaNodo> nodos = metricaNodoRepository.findByIdInstancia(idInstancia);
        
        Map<String, Object> result = new HashMap<>();
        result.put("instancia", metricaInstancia);
        result.put("nodos", nodos);
        return result;
    }
    
    public Map<String, Object> obtenerMetricasPolitica(String idPolitica) {
        List<MetricaInstancia> instancias = metricaInstanciaRepository.findByIdPolitica(idPolitica);
        
        long totalInstancias = instancias.size();
        long instanciasCompletadas = instancias.stream().filter(i -> "FINALIZADA".equals(i.getEstadoFinal())).count();
        double tiempoPromedio = instancias.stream()
            .filter(i -> i.getDuracionTotal() != null)
            .mapToLong(MetricaInstancia::getDuracionTotal)
            .average().orElse(0.0);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalInstancias", totalInstancias);
        result.put("instanciasCompletadas", instanciasCompletadas);
        result.put("tiempoPromedioMinutos", tiempoPromedio);
        
        return result;
    }
    
    public Map<String, Object> obtenerMetricasGenerales() {
        long totalInstancias = metricaInstanciaRepository.count();
        long totalNodos = metricaNodoRepository.count();
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalInstanciasRegistradas", totalInstancias);
        result.put("totalEventosNodos", totalNodos);
        
        return result;
    }
}
