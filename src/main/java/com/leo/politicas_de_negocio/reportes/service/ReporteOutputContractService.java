package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.MetricaDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteOutputContractService {

    public static class Requirement {
        private final List<String> synonyms;
        private final String errorMessage;

        public Requirement(List<String> synonyms, String errorMessage) {
            this.synonyms = synonyms;
            this.errorMessage = errorMessage;
        }

        public List<String> getSynonyms() {
            return synonyms;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getPreferredName() {
            return synonyms.isEmpty() ? null : synonyms.get(0);
        }
    }

    public static class OutputContract {
        private final List<Requirement> requirements = new ArrayList<>();

        public List<Requirement> getRequirements() {
            return requirements;
        }

        public List<String> getColumnasMinimas() {
            List<String> cols = new ArrayList<>();
            for (Requirement r : requirements) {
                cols.add(r.getPreferredName());
            }
            return cols;
        }
    }

    public OutputContract buildContract(String textoOriginal, ReporteResponseDto plan) {
        OutputContract contract = new OutputContract();
        if (textoOriginal == null || plan == null) {
            return contract;
        }

        String text = normalizeText(textoOriginal);
        String entidad = plan.getEntidadPrincipal();

        // 1. Correo / Email
        if (text.contains("correo") || text.contains("email") || text.contains("mail")) {
            if ("usuarios".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("correo"),
                        "No se pudo resolver el campo correo del usuario porque la relación usuarios no tiene campo correo reportable."));
            } else if ("tareas_actividad".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("responsableCorreo", "correo"),
                        "No se pudo resolver el campo correo del responsable porque la relación responsable no tiene campo correo reportable."));
            } else if ("instancias_politica".equalsIgnoreCase(entidad)) {
                if (text.contains("funcionario") || text.contains("responsable")) {
                    contract.requirements.add(new Requirement(
                            Arrays.asList("funcionarioCorreo", "correo"),
                            "No se pudo resolver el campo correo del funcionario porque la relación funcionario no tiene campo correo reportable."));
                } else {
                    contract.requirements.add(new Requirement(
                            Arrays.asList("creadaPorCorreo", "usuarioCorreo", "correo"),
                            "No se pudo resolver el campo correo del usuario porque la relación usuarios no tiene campo correo reportable."));
                }
            } else if ("archivos_adjuntos".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("usuarioCorreo", "correo"),
                        "No se pudo resolver el campo correo del usuario porque la relación usuarios no tiene campo correo reportable."));
            } else {
                contract.requirements.add(new Requirement(
                        Arrays.asList("creadaPorCorreo", "usuarioCorreo", "correo"),
                        "No se pudo resolver el campo correo del usuario porque la relación usuarios no tiene campo correo reportable."));
            }
        }

        // 2. Nombre de Usuario/Cliente/Solicitante
        if (text.contains("usuario") || text.contains("cliente") || text.contains("solicitante")
                || text.contains("quien inicio") || text.contains("quien inici")) {
            if ("instancias_politica".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("creadaPorNombre", "usuarioNombre", "nombre"),
                        "No se pudo resolver el campo de nombre del usuario solicitante."));
            } else if ("usuarios".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("nombre"),
                        "No se pudo resolver el campo de nombre del usuario."));
            } else {
                contract.requirements.add(new Requirement(
                        Arrays.asList("usuarioNombre", "nombre"),
                        "No se pudo resolver el campo de nombre del usuario."));
            }
        }

        // 3. Funcionario / Responsable
        if (text.contains("funcionario") || (text.contains("responsable") && !text.contains("rol responsable")
                && !text.contains("rol del nodo"))) {
            if ("tareas_actividad".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("responsableNombre", "nombre"),
                        "No se pudo resolver el campo de nombre del responsable de la tarea."));
            } else if ("instancias_politica".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("funcionarioNombre", "nombre"),
                        "No se pudo resolver el campo de nombre del funcionario asignado."));
            } else {
                contract.requirements.add(new Requirement(
                        Arrays.asList("responsableNombre", "funcionarioNombre", "nombre"),
                        "No se pudo resolver el campo de nombre del funcionario responsable."));
            }
        }

        // 4. Política
        if (text.contains("politica") || text.contains("flujo") || text.contains("workflow")) {
            if ("politicas_negocio".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("politicaNombre", "nombre"),
                        "No se pudo resolver el campo nombre de la política."));
            } else {
                contract.requirements.add(new Requirement(
                        Arrays.asList("politicaNombre", "nombre"),
                        "No se pudo resolver el campo nombre de la política relacionada."));
            }
        }

        // 5. Estado de la Política
        if (text.contains("politica activa") || text.contains("politicas activas") ||
                (text.contains("estado") && (text.contains("politica") || text.contains("workflow")))) {
            if ("politicas_negocio".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("estado", "politicaEstado"),
                        "No se pudo resolver el campo estado de la política."));
            } else {
                contract.requirements.add(new Requirement(
                        Arrays.asList("politicaEstado", "estado"),
                        "No se pudo resolver el campo estado de la política."));
            }
        }

        // 6. Estado de instancia / trámite
        if (text.contains("estado del tramite") || text.contains("estado de la solicitud")
                || text.contains("estado del proceso") || text.contains("estado de tramite")
                || text.contains("tramites en curso")) {
            if ("instancias_politica".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("estadoInstancia", "estado"),
                        "No se pudo resolver el campo estado del trámite."));
            }
        }

        // 7. Cantidad de Nodos
        if (text.contains("cantidad de nodos") || text.contains("numero de nodos")
                || text.contains("nodos configurados")) {
            contract.requirements.add(new Requirement(
                    Arrays.asList("cantidadNodos"),
                    "No se pudo resolver el campo cantidad de nodos."));
        }

        // 8. Nodos / Pasos / Tareas de la política (unwind)
        boolean pideDetalleNodos = (text.contains("tareas de la politica") || text.contains("pasos del flujo")
                || text.contains("pasos de la politica") || text.contains("tareas tiene cada politica")
                || text.contains("nodos tiene cada politica"))
                || (text.contains("nodos") && !(text.contains("cantidad de nodos") || text.contains("numero de nodos")
                        || text.contains("nodos configurados")));
        if (pideDetalleNodos) {
            if ("politicas_negocio".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("nodoNombre", "nodos.nombre"),
                        "No se pudo resolver el campo nombre de nodo."));
                contract.requirements.add(new Requirement(
                        Arrays.asList("nodoTipo", "nodos.tipo"),
                        "No se pudo resolver el campo tipo de nodo."));
                contract.requirements.add(new Requirement(
                        Arrays.asList("nodoRol", "nodos.rol"),
                        "No se pudo resolver el campo rol responsable de nodo."));
            }
        }

        // 9. Monto / Dinero
        if (text.contains("monto") || text.contains("plata") || text.contains("dinero") || text.contains("recaudado")
                || text.contains("generado")) {
            contract.requirements.add(new Requirement(
                    Arrays.asList("montoTotal", "monto"),
                    "No se pudo resolver el campo monto total."));
        }

        // 10. Cantidad de trámites / tareas
        if (text.contains("cantidad de tramites") || text.contains("cantidad de tareas")
                || text.contains("numero de tramites") || text.contains("mas tramites") || text.contains("mas tareas")
                || text.contains("con mas") || text.contains("mayor cantidad")) {
            if ("tareas_actividad".equalsIgnoreCase(entidad)) {
                contract.requirements.add(new Requirement(
                        Arrays.asList("cantidadTareas"),
                        "No se pudo resolver el campo cantidad de tareas."));
            } else {
                contract.requirements.add(new Requirement(
                        Arrays.asList("cantidadTramites"),
                        "No se pudo resolver el campo cantidad de trámites."));
            }
        }

        // 11. Fecha de creación / inicio
        // IMPORTANTE: Solo agregar fechaCreacion al contrato si el usuario pide VERLA explícitamente.
        // Expresiones temporales como "este mes", "este año", "últimos días" son filtros, NO columnas obligatorias.
        boolean pideFechaExplicita = text.contains("con fecha") || text.contains("mostrando fecha")
                || text.contains("ver fecha") || text.contains("incluyendo fecha")
                || text.contains("listar fecha") || text.contains("fecha de creacion") && (
                        text.contains("mostrando") || text.contains("incluyendo")
                        || text.contains(" con ") && !text.contains("este mes") && !text.contains("este ano")
                        && !text.contains("este año") && !text.contains("ultimo") && !text.contains("últimos"));
        if (pideFechaExplicita) {
            contract.requirements.add(new Requirement(
                    Arrays.asList("fechaCreacion"),
                    "No se pudo resolver el campo fecha de creación."));
        }

        // 12. Requiere pago
        // IMPORTANTE: Solo agregar si el usuario dice EXPLÍCITAMENTE "si requiere pago", "requiere pago" o "pagable".
        // NO activar cuando el usuario pregunta por montos, dinero generado, estado de pago o cantidad de pagos.
        boolean pideRequierePago = text.contains("si requiere pago") || text.contains("requiere pago")
                || text.contains("pagable") || text.contains("es pagable");
        if (pideRequierePago) {
            contract.requirements.add(new Requirement(
                    Arrays.asList("requierePago"),
                    "No se pudo resolver el campo si requiere pago."));
        }

        return contract;
    }

    public void repairPlan(ReporteResponseDto plan, OutputContract contract) {
        if (plan == null || contract == null)
            return;

        List<String> campos = plan.getCampos();
        if (campos == null) {
            campos = new ArrayList<>();
            plan.setCampos(campos);
        }

        List<String> agrupaciones = plan.getAgrupaciones();
        if (agrupaciones == null) {
            agrupaciones = new ArrayList<>();
            plan.setAgrupaciones(agrupaciones);
        }

        // 1. Reemplazar sinónimos no preferidos con sus nombres preferidos
        for (Requirement req : contract.getRequirements()) {
            String preferred = req.getPreferredName();
            if (preferred == null)
                continue;
            for (String synonym : req.getSynonyms()) {
                if (synonym.equalsIgnoreCase(preferred))
                    continue;

                // Reemplazar en campos
                for (int i = 0; i < campos.size(); i++) {
                    if (campos.get(i).equalsIgnoreCase(synonym)) {
                        campos.set(i, preferred);
                    }
                }

                // Reemplazar en agrupaciones
                for (int i = 0; i < agrupaciones.size(); i++) {
                    if (agrupaciones.get(i).equalsIgnoreCase(synonym)) {
                        agrupaciones.set(i, preferred);
                    }
                }
            }
        }

        // 2. Agregar requisitos faltantes
        for (Requirement req : contract.getRequirements()) {
            boolean present = false;

            for (String synonym : req.getSynonyms()) {
                if (campos.contains(synonym)) {
                    present = true;
                    break;
                }
                if (agrupaciones.contains(synonym)) {
                    present = true;
                    break;
                }
                if (plan.getMetricas() != null) {
                    for (MetricaDto m : plan.getMetricas()) {
                        if (synonym.equals(m.getAlias())) {
                            present = true;
                            break;
                        }
                    }
                }
                if (present)
                    break;
            }

            if (!present) {
                String col = req.getPreferredName();
                if (col.equals("montoTotal")) {
                    if (plan.getMetricas() == null) {
                        plan.setMetricas(new ArrayList<>());
                    }
                    boolean hasMonto = false;
                    for (MetricaDto m : plan.getMetricas()) {
                        if (m.getOperacion().equalsIgnoreCase("sum") && m.getCampo().equalsIgnoreCase("monto")) {
                            hasMonto = true;
                            break;
                        }
                    }
                    if (!hasMonto) {
                        MetricaDto m = new MetricaDto();
                        m.setOperacion("sum");
                        m.setCampo("monto");
                        m.setAlias("montoTotal");
                        plan.getMetricas().add(m);
                    }
                    if (!campos.contains("montoTotal")) {
                        campos.add("montoTotal");
                    }
                } else if (col.equals("cantidadNodos")) {
                    // cantidadNodos siempre va en campos[] — el builder lo calcula con $size(nodos).
                    // NUNCA va en agrupaciones porque eso provocaría un unwind del array.
                    if (!campos.contains("cantidadNodos")) {
                        campos.add("cantidadNodos");
                    }
                } else if (col.equals("cantidadTramites") || col.equals("cantidadTareas")) {
                    if (plan.getMetricas() == null) {
                        plan.setMetricas(new ArrayList<>());
                    }
                    boolean hasCount = false;
                    for (MetricaDto m : plan.getMetricas()) {
                        if (m.getOperacion().equalsIgnoreCase("count")) {
                            hasCount = true;
                            col = m.getAlias();
                            break;
                        }
                    }
                    if (!hasCount) {
                        MetricaDto m = new MetricaDto();
                        m.setOperacion("count");
                        m.setCampo("id");
                        m.setAlias(col);
                        plan.getMetricas().add(m);
                    }
                    if (!campos.contains(col)) {
                        campos.add(col);
                    }
                } else if (agrupaciones != null && !agrupaciones.isEmpty() && isDerivedField(col)) {
                    if (!agrupaciones.contains(col)) {
                        agrupaciones.add(col);
                    }
                } else {
                    campos.add(col);
                }
            }
        }

        // 3. Deduplicar campos y agrupaciones
        Set<String> uniqueCampos = new LinkedHashSet<>(campos);
        campos.clear();
        campos.addAll(uniqueCampos);

        Set<String> uniqueAgrupaciones = new LinkedHashSet<>(agrupaciones);
        agrupaciones.clear();
        agrupaciones.addAll(uniqueAgrupaciones);
    }

    public List<String> validate(List<Map<String, Object>> filas, OutputContract contract) {
        List<String> errors = new ArrayList<>();
        if (contract == null || contract.getRequirements().isEmpty()) {
            return errors;
        }

        if (filas == null || filas.isEmpty()) {
            return errors;
        }

        Set<String> columns = new HashSet<>();
        for (Map<String, Object> row : filas) {
            for (String k : row.keySet()) {
                columns.add(k.trim().toLowerCase());
            }
        }

        for (Requirement req : contract.getRequirements()) {
            boolean satisfied = false;
            String foundSynonym = null;
            for (String synonym : req.getSynonyms()) {
                if (columns.contains(synonym.toLowerCase())) {
                    satisfied = true;
                    foundSynonym = synonym;
                    break;
                }
            }

            // cantidadNodos es siempre calculado por el builder con $size(nodos).
            // No lanzar error si no aparece en columnas — puede estar como "cantidadNodos" o índice numérico.
            if (!satisfied && req.getSynonyms().contains("cantidadNodos")) {
                satisfied = true;
            }

            if (!satisfied) {
                errors.add(req.getErrorMessage());
            } else if (foundSynonym != null) {
                // Check contents for placeholders
                boolean hasOnlyPlaceholders = true;
                boolean hasRawId = false;
                for (Map<String, Object> row : filas) {
                    Object val = row.get(foundSynonym);
                    if (val == null) {
                        val = row.get(foundSynonym.toLowerCase());
                    }
                    if (val != null) {
                        String valStr = val.toString().trim();
                        if (!valStr.isEmpty() && !valStr.equals("-") && !valStr.equalsIgnoreCase("n/a")
                                && !valStr.equalsIgnoreCase("sin nombre")) {
                            hasOnlyPlaceholders = false;
                        }
                        if (foundSynonym.toLowerCase().contains("nombre") && isRawObjectId(val)) {
                            hasRawId = true;
                        }
                    }
                }

                if (hasOnlyPlaceholders) {
                    errors.add("El campo '" + foundSynonym + "' sólo contiene valores vacíos, '-' o 'Sin Nombre'.");
                }
                if (hasRawId) {
                    errors.add("El campo '" + foundSynonym + "' contiene IDs crudos en lugar de nombres legibles.");
                }
            }
        }

        return errors;
    }

    private boolean isDerivedField(String field) {
        String lc = field.toLowerCase();
        // "nodos" excluido: cantidadNodos va siempre en campos[], no en agrupaciones
        return lc.contains("nombre") || lc.contains("correo") || lc.contains("estado");
    }

    private boolean isRawObjectId(Object value) {
        if (value == null)
            return false;
        String str = value.toString().trim();
        return str.matches("^[0-9a-fA-F]{24}$");
    }

    private String normalizeText(String str) {
        if (str == null)
            return "";
        String normalized = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase().trim();
    }
}
