package com.leo.politicas_de_negocio.documents.model;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

@DynamoDbBean
public class DocumentoColaborativoMetadata {

    private String pk; // CLIENTE#{clienteId}
    private String sk; // TRAMITE#{tramiteId}#DOC_COLAB#{documentoId}

    private String documentoId;
    private String clienteId;
    private String tramiteId;
    private String campoFormularioId;
    private String nombreDocumento;
    private String descripcion;
    private String tipoDocumento;
    private String estado;
    private String s3Key;
    private String creadoPor;
    private String modificadoPor;
    private String fechaCreacion;
    private String fechaUltimaModificacion;
    private String ultimoEventoOnlyOffice;
    
    private ConfiguracionOrigen configuracionOrigen;
    private PermisosEdicion permisosEdicion;
    private PermisosLectura permisosLectura;
    private PermisosAccion permisosDescarga;
    private PermisosAccion permisosComentarios;
    private PermisosAccion permisosReemplazo;
    private PermisosAccion permisosEliminacion;
    private PermisosAccion permisosCompartirInternamente;
    private PermisosAccion permisosImpresion;
    private PermisosAdicionales permisosAdicionales;
    private DocumentoColaborativoPermisosDto permisosUsuario;

    private String tareaId;
    private String nodoId;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("clientId")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("repositoryId")
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getDocumentoId() {
        return documentoId;
    }

    public void setDocumentoId(String documentoId) {
        this.documentoId = documentoId;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getTramiteId() {
        return tramiteId;
    }

    public void setTramiteId(String tramiteId) {
        this.tramiteId = tramiteId;
    }

    public String getCampoFormularioId() {
        return campoFormularioId;
    }

    public void setCampoFormularioId(String campoFormularioId) {
        this.campoFormularioId = campoFormularioId;
    }

    public String getNombreDocumento() {
        return nombreDocumento;
    }

    public void setNombreDocumento(String nombreDocumento) {
        this.nombreDocumento = nombreDocumento;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(String creadoPor) {
        this.creadoPor = creadoPor;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public void setModificadoPor(String modificadoPor) {
        this.modificadoPor = modificadoPor;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getFechaUltimaModificacion() {
        return fechaUltimaModificacion;
    }

    public void setFechaUltimaModificacion(String fechaUltimaModificacion) {
        this.fechaUltimaModificacion = fechaUltimaModificacion;
    }

    public String getUltimoEventoOnlyOffice() {
        return ultimoEventoOnlyOffice;
    }

    public void setUltimoEventoOnlyOffice(String ultimoEventoOnlyOffice) {
        this.ultimoEventoOnlyOffice = ultimoEventoOnlyOffice;
    }

    public ConfiguracionOrigen getConfiguracionOrigen() {
        return configuracionOrigen;
    }

    public void setConfiguracionOrigen(ConfiguracionOrigen configuracionOrigen) {
        this.configuracionOrigen = configuracionOrigen;
    }

    public PermisosEdicion getPermisosEdicion() {
        return permisosEdicion;
    }

    public void setPermisosEdicion(PermisosEdicion permisosEdicion) {
        this.permisosEdicion = permisosEdicion;
    }

    public PermisosLectura getPermisosLectura() {
        return permisosLectura;
    }

    public void setPermisosLectura(PermisosLectura permisosLectura) {
        this.permisosLectura = permisosLectura;
    }

    public PermisosAccion getPermisosDescarga() {
        return permisosDescarga;
    }

    public void setPermisosDescarga(PermisosAccion permisosDescarga) {
        this.permisosDescarga = permisosDescarga;
    }

    public PermisosAccion getPermisosComentarios() {
        return permisosComentarios;
    }

    public void setPermisosComentarios(PermisosAccion permisosComentarios) {
        this.permisosComentarios = permisosComentarios;
    }

    public PermisosAccion getPermisosReemplazo() {
        return permisosReemplazo;
    }

    public void setPermisosReemplazo(PermisosAccion permisosReemplazo) {
        this.permisosReemplazo = permisosReemplazo;
    }

    public PermisosAccion getPermisosEliminacion() {
        return permisosEliminacion;
    }

    public void setPermisosEliminacion(PermisosAccion permisosEliminacion) {
        this.permisosEliminacion = permisosEliminacion;
    }

    public PermisosAccion getPermisosCompartirInternamente() {
        return permisosCompartirInternamente;
    }

    public void setPermisosCompartirInternamente(PermisosAccion permisosCompartirInternamente) {
        this.permisosCompartirInternamente = permisosCompartirInternamente;
    }

    public PermisosAccion getPermisosImpresion() {
        return permisosImpresion;
    }

    public void setPermisosImpresion(PermisosAccion permisosImpresion) {
        this.permisosImpresion = permisosImpresion;
    }

    public PermisosAdicionales getPermisosAdicionales() {
        return permisosAdicionales;
    }

    public void setPermisosAdicionales(PermisosAdicionales permisosAdicionales) {
        this.permisosAdicionales = permisosAdicionales;
    }

    @DynamoDbIgnore
    public DocumentoColaborativoPermisosDto getPermisosUsuario() {
        return permisosUsuario;
    }

    public void setPermisosUsuario(DocumentoColaborativoPermisosDto permisosUsuario) {
        this.permisosUsuario = permisosUsuario;
    }

    @DynamoDbIgnore
    public String getTareaId() {
        return tareaId;
    }

    public void setTareaId(String tareaId) {
        this.tareaId = tareaId;
    }

    @DynamoDbIgnore
    public String getNodoId() {
        return nodoId;
    }

    public void setNodoId(String nodoId) {
        this.nodoId = nodoId;
    }

    @DynamoDbBean
    public static class ConfiguracionOrigen {
        private String modoColaboracion;
        private Boolean permitirDocumentoBlanco = true;
        private Boolean permitirPlantilla = false;
        private Boolean permitirSubidaBase = false;

        public String getModoColaboracion() {
            return modoColaboracion;
        }

        public void setModoColaboracion(String modoColaboracion) {
            this.modoColaboracion = modoColaboracion;
        }

        public Boolean getPermitirDocumentoBlanco() {
            return permitirDocumentoBlanco;
        }

        public void setPermitirDocumentoBlanco(Boolean permitirDocumentoBlanco) {
            this.permitirDocumentoBlanco = permitirDocumentoBlanco;
        }

        public Boolean getPermitirPlantilla() {
            return permitirPlantilla;
        }

        public void setPermitirPlantilla(Boolean permitirPlantilla) {
            this.permitirPlantilla = permitirPlantilla;
        }

        public Boolean getPermitirSubidaBase() {
            return permitirSubidaBase;
        }

        public void setPermitirSubidaBase(Boolean permitirSubidaBase) {
            this.permitirSubidaBase = permitirSubidaBase;
        }
    }

    @DynamoDbBean
    public static class PermisosEdicion {
        private List<String> departamentos;
        private List<String> roles;
        private List<String> usuarios;

        public List<String> getDepartamentos() {
            return departamentos;
        }

        public void setDepartamentos(List<String> departamentos) {
            this.departamentos = departamentos;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public List<String> getUsuarios() {
            return usuarios;
        }

        public void setUsuarios(List<String> usuarios) {
            this.usuarios = usuarios;
        }
    }

    @DynamoDbBean
    public static class PermisosLectura {
        private List<String> departamentos;
        private List<String> roles;
        private List<String> usuarios;
        private Boolean incluirClienteIniciador;

        public List<String> getDepartamentos() {
            return departamentos;
        }

        public void setDepartamentos(List<String> departamentos) {
            this.departamentos = departamentos;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public List<String> getUsuarios() {
            return usuarios;
        }

        public void setUsuarios(List<String> usuarios) {
            this.usuarios = usuarios;
        }

        public Boolean getIncluirClienteIniciador() {
            return incluirClienteIniciador;
        }

        public void setIncluirClienteIniciador(Boolean incluirClienteIniciador) {
            this.incluirClienteIniciador = incluirClienteIniciador;
        }
    }

    @DynamoDbBean
    public static class PermisosAccion {
        private List<String> departamentos;
        private List<String> roles;
        private List<String> usuarios;

        public List<String> getDepartamentos() {
            return departamentos;
        }

        public void setDepartamentos(List<String> departamentos) {
            this.departamentos = departamentos;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }

        public List<String> getUsuarios() {
            return usuarios;
        }

        public void setUsuarios(List<String> usuarios) {
            this.usuarios = usuarios;
        }
    }

    @DynamoDbBean
    public static class PermisosAdicionales {
        private Boolean puedeDescargar;
        private Boolean puedeImprimir;
        private Boolean puedeComentar;
        private Boolean puedeReemplazar;
        private Boolean puedeEliminar = false;
        private Boolean puedeCompartirInternamente = false;

        public Boolean getPuedeDescargar() {
            return puedeDescargar;
        }

        public void setPuedeDescargar(Boolean puedeDescargar) {
            this.puedeDescargar = puedeDescargar;
        }

        public Boolean getPuedeImprimir() {
            return puedeImprimir;
        }

        public void setPuedeImprimir(Boolean puedeImprimir) {
            this.puedeImprimir = puedeImprimir;
        }

        public Boolean getPuedeComentar() {
            return puedeComentar;
        }

        public void setPuedeComentar(Boolean puedeComentar) {
            this.puedeComentar = puedeComentar;
        }

        public Boolean getPuedeReemplazar() {
            return puedeReemplazar;
        }

        public void setPuedeReemplazar(Boolean puedeReemplazar) {
            this.puedeReemplazar = puedeReemplazar;
        }

        public Boolean getPuedeEliminar() {
            return puedeEliminar;
        }

        public void setPuedeEliminar(Boolean puedeEliminar) {
            this.puedeEliminar = puedeEliminar;
        }

        public Boolean getPuedeCompartirInternamente() {
            return puedeCompartirInternamente;
        }

        public void setPuedeCompartirInternamente(Boolean puedeCompartirInternamente) {
            this.puedeCompartirInternamente = puedeCompartirInternamente;
        }
    }
}
