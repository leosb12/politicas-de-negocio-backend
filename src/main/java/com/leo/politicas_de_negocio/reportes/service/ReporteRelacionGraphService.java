package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.model.EntidadReportable;
import com.leo.politicas_de_negocio.reportes.model.RelacionReportable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteRelacionGraphService {

    private final ReporteCatalogoService catalogoService;
    private Map<String, List<RelationshipStep>> adjList = new HashMap<>();

    @Data
    @AllArgsConstructor
    public static class RelationshipStep {
        private String source;
        private String target;
        private RelacionReportable relation;
        private boolean reverse;

        public String getFromCollection() {
            return reverse ? relation.getEntidadOrigen() : relation.getEntidadDestino();
        }

        public String getLocalField() {
            return reverse ? relation.getCampoDestino() : relation.getCampoLocal();
        }

        public String getForeignField() {
            return reverse ? relation.getCampoLocal() : relation.getCampoDestino();
        }
    }

    @PostConstruct
    public void init() {
        buildAdjacencyList();
    }

    /**
     * Busca la ruta más corta (lookups) de una entidad origen a una destino usando BFS.
     */
    public List<RelationshipStep> buscarRuta(String origen, String destino) {
        return buscarRutaPriorizada(origen, destino, null);
    }

    /**
     * Busca la mejor ruta entre origen y destino, priorizando aquellas que contengan
     * pasos cuyo campo local o nombre de relación comience con el prefijo deseado.
     */
    public List<RelationshipStep> buscarRutaPriorizada(String origen, String destino, String preferedPrefix) {
        if (origen == null || destino == null) {
            return Collections.emptyList();
        }
        
        origen = origen.trim();
        destino = destino.trim();
        
        if (origen.equals(destino)) {
            return Collections.emptyList();
        }

        if (adjList.isEmpty()) {
            buildAdjacencyList();
        }

        List<List<RelationshipStep>> todosLosCaminos = encontrarTodosLosCaminos(origen, destino);
        if (todosLosCaminos.isEmpty()) {
            log.warn("No se encontró ruta de relaciones entre {} y {}", origen, destino);
            return Collections.emptyList();
        }

        // Ordenar caminos:
        // 1. Caminos que coinciden con el prefijo prioritario
        // 2. Longitud del camino
        todosLosCaminos.sort((p1, p2) -> {
            boolean p1Matches = pathContainsPrefix(p1, preferedPrefix);
            boolean p2Matches = pathContainsPrefix(p2, preferedPrefix);
            if (p1Matches && !p2Matches) return -1;
            if (!p1Matches && p2Matches) return 1;
            return Integer.compare(p1.size(), p2.size());
        });

        return todosLosCaminos.get(0);
    }

    public List<List<RelationshipStep>> encontrarTodosLosCaminos(String origen, String destino) {
        List<List<RelationshipStep>> caminos = new ArrayList<>();
        encontrarCaminosHelper(origen, destino, new HashSet<>(), new ArrayList<>(), caminos);
        return caminos;
    }

    private void encontrarCaminosHelper(String current, String destino, Set<String> visited, List<RelationshipStep> caminoActual, List<List<RelationshipStep>> caminos) {
        if (current.equals(destino)) {
            caminos.add(new ArrayList<>(caminoActual));
            return;
        }
        visited.add(current);
        List<RelationshipStep> neighbors = adjList.getOrDefault(current, Collections.emptyList());
        for (RelationshipStep neighbor : neighbors) {
            String target = neighbor.getTarget();
            if (!visited.contains(target)) {
                caminoActual.add(neighbor);
                encontrarCaminosHelper(target, destino, visited, caminoActual, caminos);
                caminoActual.remove(caminoActual.size() - 1);
            }
        }
        visited.remove(current);
    }

    private boolean pathContainsPrefix(List<RelationshipStep> path, String prefix) {
        if (prefix == null || prefix.isEmpty()) return false;
        String lowerPrefix = prefix.toLowerCase();
        for (RelationshipStep step : path) {
            String localField = step.getLocalField().toLowerCase();
            String relName = step.getRelation().getNombre() != null ? step.getRelation().getNombre().toLowerCase() : "";
            if (localField.startsWith(lowerPrefix) || relName.startsWith(lowerPrefix)) {
                return true;
            }
        }
        return false;
    }

    private synchronized void buildAdjacencyList() {
        adjList.clear();
        Map<String, EntidadReportable> catalogo = catalogoService.getCatalogoCompleto();

        for (EntidadReportable entidad : catalogo.values()) {
            if (!entidad.isReportable() || entidad.getRelaciones() == null) {
                continue;
            }

            for (RelacionReportable rel : entidad.getRelaciones()) {
                String u = rel.getEntidadOrigen();
                String v = rel.getEntidadDestino();

                // Forward step
                RelationshipStep forwardStep = new RelationshipStep(u, v, rel, false);
                adjList.computeIfAbsent(u, k -> new ArrayList<>()).add(forwardStep);

                // Reverse step
                RelationshipStep reverseStep = new RelationshipStep(v, u, rel, true);
                adjList.computeIfAbsent(v, k -> new ArrayList<>()).add(reverseStep);
            }
        }
    }
}
