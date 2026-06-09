package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class CatalogoResumidoResponse {
    private String version;
    private String origen;
    private List<EntidadResumida> entidades;
    private List<String> operacionesPermitidas;
    private List<String> operadoresPermitidos;

    @Data
    public static class EntidadResumida {
        private String nombreLogico;
        private List<String> aliases;
        private String descripcion;
        private List<CampoResumido> campos;
    }

    @Data
    public static class CampoResumido {
        private String nombreLogico;
        private List<String> aliases;
        private String tipoDato;
        private boolean filtrable;
        private boolean agrupable;
        private boolean ordenable;
        private List<String> valoresPermitidos;
    }
}
