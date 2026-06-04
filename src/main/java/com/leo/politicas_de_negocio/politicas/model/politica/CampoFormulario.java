package com.leo.politicas_de_negocio.politicas.model.politica;

import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampoFormulario {
    private String campo;
    private String tipo;
    private String etiqueta;
    private Boolean requerido;
    private String placeholder;
    private String ayuda;
    private Integer orden;
    private List<String> opciones;
    private Map<String, Object> validaciones;
    private ConfiguracionDocumento configuracionDocumento;

    public TipoCampo getTipo() {
        return TipoCampo.fromString(this.tipo);
    }

    public String getTipoRaw() {
        return this.tipo;
    }

    public void setTipo(TipoCampo tipo) {
        this.tipo = tipo != null ? tipo.name() : null;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public static class CampoFormularioBuilder {
        private String tipo;

        public CampoFormularioBuilder tipo(TipoCampo tipo) {
            this.tipo = tipo != null ? tipo.name() : null;
            return this;
        }

        public CampoFormularioBuilder tipo(String tipo) {
            this.tipo = tipo;
            return this;
        }
    }
}
