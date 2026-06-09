package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.model.CampoReportable;
import com.leo.politicas_de_negocio.reportes.model.EntidadReportable;
import com.leo.politicas_de_negocio.reportes.model.RelacionReportable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteCampoResolver {

    private final ReporteCatalogoService catalogoService;
    private final ReporteRelacionGraphService graphService;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ResolvedField {
        private String originalFieldName;
        private String targetFieldName; // E.g., "nombre"
        private List<ReporteRelacionGraphService.RelationshipStep> path;
        private String resolvedMongoPath; // E.g., "creadaPor_lookup.nombre"
        private boolean derived;
    }

    @Data
    @AllArgsConstructor
    private static class UniversalDerivedMapping {
        private String targetEntity;
        private String targetField;
        private String preferedRelationPrefix;
    }

    private final Map<String, UniversalDerivedMapping> universalDerivedFields = new HashMap<>();

    @PostConstruct
    public void init() {
        universalDerivedFields.put("politicaNombre", new UniversalDerivedMapping("politicas_negocio", "nombre", "politica"));
        universalDerivedFields.put("usuarioNombre", new UniversalDerivedMapping("usuarios", "nombre", "usuario"));
        universalDerivedFields.put("usuarioCorreo", new UniversalDerivedMapping("usuarios", "correo", "usuario"));
        
        universalDerivedFields.put("creadaPorNombre", new UniversalDerivedMapping("usuarios", "nombre", "creadaPor"));
        universalDerivedFields.put("creadaPorCorreo", new UniversalDerivedMapping("usuarios", "correo", "creadaPor"));
        
        universalDerivedFields.put("responsableNombre", new UniversalDerivedMapping("usuarios", "nombre", "responsable"));
        universalDerivedFields.put("responsableCorreo", new UniversalDerivedMapping("usuarios", "correo", "responsable"));
        
        universalDerivedFields.put("funcionarioNombre", new UniversalDerivedMapping("usuarios", "nombre", "funcionario"));
        universalDerivedFields.put("funcionarioCorreo", new UniversalDerivedMapping("usuarios", "correo", "funcionario"));
        
        universalDerivedFields.put("departamentoNombre", new UniversalDerivedMapping("departamentos", "nombre", "responsable"));
        
        universalDerivedFields.put("estadoInstancia", new UniversalDerivedMapping("instancias_politica", "estadoInstancia", "instancia"));
        universalDerivedFields.put("codigoTramite", new UniversalDerivedMapping("instancias_politica", "codigoTramite", "instancia"));
        universalDerivedFields.put("fechaCreacionTramite", new UniversalDerivedMapping("instancias_politica", "fechaCreacion", "instancia"));
        universalDerivedFields.put("monto", new UniversalDerivedMapping("pagos", "monto", "pago"));
        universalDerivedFields.put("extension", new UniversalDerivedMapping("archivos_adjuntos", "extension", "archivo"));

        universalDerivedFields.put("politicaEstado", new UniversalDerivedMapping("politicas_negocio", "estado", "politica"));
        universalDerivedFields.put("cantidadNodos", new UniversalDerivedMapping("politicas_negocio", "cantidadNodos", "politica"));
        universalDerivedFields.put("nodoNombre", new UniversalDerivedMapping("politicas_negocio", "nodos.nombre", "politica"));
        universalDerivedFields.put("nodoTipo", new UniversalDerivedMapping("politicas_negocio", "nodos.tipo", "politica"));
        universalDerivedFields.put("nodoRol", new UniversalDerivedMapping("politicas_negocio", "nodos.responsableTipo", "politica"));
    }

    /**
     * Resuelve un nombre de campo (o alias) para una entidad base dada de forma completamente dinámica.
     */
    public ResolvedField resolverCampo(String entidadBase, String campoOriginal) {
        if (entidadBase == null || campoOriginal == null) {
            return null;
        }

        entidadBase = entidadBase.trim();
        campoOriginal = campoOriginal.trim();

        EntidadReportable entBase = catalogoService.obtenerEntidadPorNombreOAlias(entidadBase);
        if (entBase == null) {
            return null;
        }

        // 1. Verificar si el campo ya existe en la entidad base (o es alias directo)
        CampoReportable cLocal = catalogoService.obtenerCampoDeEntidad(entBase, campoOriginal);
        if (cLocal != null) {
            String targetMongoField = cLocal.getCampoMongo();
            if (targetMongoField.equals("id")) targetMongoField = "_id";
            return ResolvedField.builder()
                    .originalFieldName(campoOriginal)
                    .targetFieldName(targetMongoField)
                    .path(Collections.emptyList())
                    .resolvedMongoPath(targetMongoField)
                    .derived(false)
                    .build();
        }

        // 1.5. Si el campo contiene un punto (ej: relacionName.fieldName o pagos.estado)
        if (campoOriginal.contains(".")) {
            int dotIdx = campoOriginal.indexOf('.');
            String prefix = campoOriginal.substring(0, dotIdx).trim();
            String suffix = campoOriginal.substring(dotIdx + 1).trim();

            String targetEntity = null;
            String preferedRelation = null;
            
            // A) Verificar relaciones directas de la entidad base
            if (entBase.getRelaciones() != null) {
                for (RelacionReportable rel : entBase.getRelaciones()) {
                    String normRelName = rel.getNombre().toLowerCase();
                    String normLocalKey = rel.getCampoLocal().toLowerCase();
                    String normDest = rel.getEntidadDestino().toLowerCase();
                    String normPrefix = prefix.toLowerCase();
                    
                    if (normLocalKey.contains(normPrefix) || normPrefix.contains(normLocalKey) ||
                        normRelName.contains(normPrefix) || normPrefix.contains(normRelName) ||
                        normDest.contains(normPrefix) || normPrefix.contains(normDest)) {
                        targetEntity = rel.getEntidadDestino();
                        preferedRelation = rel.getCampoLocal();
                        break;
                    }
                }
            }

            // B) Si no se encuentra, verificar si el prefijo es un nombre de entidad en el catálogo
            if (targetEntity == null) {
                EntidadReportable candEnt = catalogoService.obtenerEntidadPorNombreOAlias(prefix);
                if (candEnt != null) {
                    targetEntity = candEnt.getNombreLogico();
                }
            }

            // C) Si aún no se encuentra, buscar en todas las relaciones de las demás entidades
            if (targetEntity == null) {
                for (EntidadReportable ent : catalogoService.getCatalogoCompleto().values()) {
                    if (ent.getRelaciones() != null) {
                        for (RelacionReportable rel : ent.getRelaciones()) {
                            String normRelName = rel.getNombre().toLowerCase();
                            String normLocalKey = rel.getCampoLocal().toLowerCase();
                            String normPrefix = prefix.toLowerCase();
                            if (normLocalKey.contains(normPrefix) || normPrefix.contains(normLocalKey) ||
                                normRelName.contains(normPrefix) || normPrefix.contains(normRelName)) {
                                targetEntity = ent.getNombreLogico();
                                break;
                            }
                        }
                    }
                    if (targetEntity != null) break;
                }
            }

            if (targetEntity != null) {
                ResolvedField rfTarget = resolverCampo(targetEntity, suffix);
                if (rfTarget != null) {
                    List<ReporteRelacionGraphService.RelationshipStep> basePath = graphService.buscarRutaPriorizada(
                            entBase.getNombreLogico(), targetEntity, preferedRelation != null ? preferedRelation : prefix
                    );
                    if (!basePath.isEmpty() || entBase.getNombreLogico().equalsIgnoreCase(targetEntity)) {
                        List<ReporteRelacionGraphService.RelationshipStep> fullPath = new ArrayList<>(basePath);
                        fullPath.addAll(rfTarget.getPath());
                        
                        String aliasPath = construirAliasPath(fullPath);
                        return ResolvedField.builder()
                                .originalFieldName(campoOriginal)
                                .targetFieldName(rfTarget.getTargetFieldName())
                                .path(fullPath)
                                .resolvedMongoPath(aliasPath.isEmpty() ? rfTarget.getTargetFieldName() : aliasPath + "." + rfTarget.getTargetFieldName())
                                .derived(true)
                                .build();
                    }
                }
            }
        }

        // 2. Verificar si es un campo derivado universal
        if (universalDerivedFields.containsKey(campoOriginal)) {
            UniversalDerivedMapping mapping = universalDerivedFields.get(campoOriginal);
            if (entBase.getNombreLogico().equalsIgnoreCase(mapping.getTargetEntity())) {
                return ResolvedField.builder()
                        .originalFieldName(campoOriginal)
                        .targetFieldName(mapping.getTargetField())
                        .path(Collections.emptyList())
                        .resolvedMongoPath(mapping.getTargetField())
                        .derived(true)
                        .build();
            }
            List<ReporteRelacionGraphService.RelationshipStep> path = graphService.buscarRutaPriorizada(
                    entBase.getNombreLogico(), mapping.getTargetEntity(), mapping.getPreferedRelationPrefix()
            );
            if (!path.isEmpty()) {
                String aliasPath = construirAliasPath(path);
                return ResolvedField.builder()
                        .originalFieldName(campoOriginal)
                        .targetFieldName(mapping.getTargetField())
                        .path(path)
                        .resolvedMongoPath(aliasPath + "." + mapping.getTargetField())
                        .derived(true)
                        .build();
            }
        }

        // 2.5. Verificar si es un campo enriquecido de una relación directa (ej. creadaPorNombre, politicaIdNombre)
        if (entBase.getRelaciones() != null) {
            for (RelacionReportable rel : entBase.getRelaciones()) {
                String localKey = rel.getCampoLocal();
                if (campoOriginal.startsWith(localKey) && campoOriginal.length() > localKey.length()) {
                    String suffix = campoOriginal.substring(localKey.length());
                    String targetField = suffix.substring(0, 1).toLowerCase() + suffix.substring(1);
                    if (rel.getCamposEnriquecidos().contains(targetField) || "nombre".equals(targetField) || "correo".equals(targetField) || "id".equals(targetField) || "_id".equals(targetField)) {
                        List<ReporteRelacionGraphService.RelationshipStep> path = graphService.buscarRutaPriorizada(
                                entBase.getNombreLogico(), rel.getEntidadDestino(), localKey
                        );
                        if (!path.isEmpty()) {
                            String aliasPath = construirAliasPath(path);
                            String targetMongo = "id".equals(targetField) ? "_id" : targetField;
                            return ResolvedField.builder()
                                    .originalFieldName(campoOriginal)
                                    .targetFieldName(targetMongo)
                                    .path(path)
                                    .resolvedMongoPath(aliasPath + "." + targetMongo)
                                    .derived(true)
                                    .build();
                        }
                    }
                }
            }
        }

        // 3. Fallback: Buscar el campo en el catálogo completo de otras entidades
        Map<String, EntidadReportable> catalogo = catalogoService.getCatalogoCompleto();
        List<String> candidatas = new ArrayList<>();
        Map<String, String> targetFieldInCandidata = new HashMap<>();

        for (EntidadReportable ent : catalogo.values()) {
            if (!ent.isReportable() || ent.getNombreLogico().equals(entBase.getNombreLogico())) {
                continue;
            }
            CampoReportable cDest = catalogoService.obtenerCampoDeEntidad(ent, campoOriginal);
            if (cDest != null) {
                candidatas.add(ent.getNombreLogico());
                String mongoF = cDest.getCampoMongo();
                if (mongoF.equals("id")) mongoF = "_id";
                targetFieldInCandidata.put(ent.getNombreLogico(), mongoF);
            }
        }

        // Encontrar la ruta más corta/priorizada entre las candidatas
        List<ReporteRelacionGraphService.RelationshipStep> shortestPath = null;
        String mejorCandidata = null;

        for (String cand : candidatas) {
            List<ReporteRelacionGraphService.RelationshipStep> path = graphService.buscarRuta(entBase.getNombreLogico(), cand);
            if (!path.isEmpty()) {
                if (shortestPath == null || path.size() < shortestPath.size()) {
                    shortestPath = path;
                    mejorCandidata = cand;
                }
            }
        }

        if (shortestPath != null) {
            String targetField = targetFieldInCandidata.get(mejorCandidata);
            String aliasPath = construirAliasPath(shortestPath);
            return ResolvedField.builder()
                    .originalFieldName(campoOriginal)
                    .targetFieldName(targetField)
                    .path(shortestPath)
                    .resolvedMongoPath(aliasPath + "." + targetField)
                    .derived(true)
                    .build();
        }

        return null;
    }

    public static String construirAliasPath(List<ReporteRelacionGraphService.RelationshipStep> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            ReporteRelacionGraphService.RelationshipStep step = path.get(i);
            if (i > 0) {
                sb.append("_");
            }
            sb.append(step.getLocalField());
        }
        if (path.isEmpty()) return "";
        return sb.toString() + "_lookup";
    }
}
