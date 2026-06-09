package com.leo.politicas_de_negocio.politicas.model.politica;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionDocumento {
    private String tipoDocumento;
    private String modoColaboracion;
    private PermisosSeccion permisosEdicion;
    private PermisosLecturaSeccion permisosLectura;
    private PermisosSeccion permisosDescarga;
    @JsonAlias({
            "permisosImprimir",
            "permisosPrint",
            "permisosDeImpresion",
            "permisosDeImpresión",
            "permisosImpresionArchivo",
            "permisosImpresión",
            "impresion",
            "impresión"
    })
    private PermisosSeccion permisosImpresion;
    @JsonAlias({"permisosComentario", "permisosComentar"})
    private PermisosSeccion permisosComentarios;
    @JsonAlias({"permisosReemplazar"})
    private PermisosSeccion permisosReemplazo;
    @JsonAlias({"permisosEliminar"})
    private PermisosSeccion permisosEliminacion;
    @JsonAlias({"permisosCompartir"})
    private PermisosSeccion permisosCompartirInternamente;
    private PermisosAdicionalesDocumento permisosAdicionales;
    private Boolean auditarCambios;
    @Builder.Default
    private Boolean controlVersionesHabilitado = false;
    private DocumentoPlantilla documentoPlantilla;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentoPlantilla {
        private String nombreOriginal;
        private String extension;
        private String mimeType;
        private String url;
        private String storageKey;
        private String fechaSubida;
    }

    @JsonAnySetter
    public void capturarPermisosImpresionCompatibles(String fieldName, Object value) {
        String normalized = normalizarClave(fieldName);
        if (normalized == null || !normalized.contains("impres")) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            PermisosSeccion permisos = asegurarPermisosImpresion();
            List<String> departamentos = listaTexto(primerValor(map, "departamentos", "departments", "departmentIds"));
            List<String> roles = listaTexto(primerValor(map, "roles", "roleIds"));
            List<String> usuarios = listaTexto(primerValor(map, "usuarios", "users", "userIds"));
            if (departamentos != null) {
                permisos.setDepartamentos(departamentos);
            }
            if (roles != null) {
                permisos.setRoles(roles);
            }
            if (usuarios != null) {
                permisos.setUsuarios(usuarios);
            }
            return;
        }

        List<String> values = listaTexto(value);
        if (values == null) {
            return;
        }

        PermisosSeccion permisos = asegurarPermisosImpresion();
        if (normalized.contains("depart")) {
            permisos.setDepartamentos(values);
        } else if (normalized.contains("rol")) {
            permisos.setRoles(values);
        } else if (normalized.contains("usu") || normalized.contains("user")) {
            permisos.setUsuarios(values);
        }
    }

    private PermisosSeccion asegurarPermisosImpresion() {
        if (permisosImpresion == null) {
            permisosImpresion = new PermisosSeccion();
        }
        return permisosImpresion;
    }

    private Object primerValor(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private List<String> listaTexto(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !value.toString().trim().isEmpty()) {
                result.add(value.toString().trim());
            }
        }
        return result;
    }

    private String normalizarClave(String value) {
        if (value == null) {
            return null;
        }
        return value.toLowerCase()
                .replace("ó", "o")
                .replace("í", "i")
                .replaceAll("[^a-z0-9]", "");
    }
}
