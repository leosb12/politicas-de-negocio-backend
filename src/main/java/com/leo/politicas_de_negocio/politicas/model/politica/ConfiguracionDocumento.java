package com.leo.politicas_de_negocio.politicas.model.politica;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
