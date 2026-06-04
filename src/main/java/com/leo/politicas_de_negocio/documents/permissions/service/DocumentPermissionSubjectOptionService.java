package com.leo.politicas_de_negocio.documents.permissions.service;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentSubjectOptionResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.RolRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentPermissionSubjectOptionService {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final InstanciaPoliticaRepository instanciaPoliticaRepository;

    public List<DocumentSubjectOptionResponse> listarOpciones(DocumentSubjectType tipoSujeto) {
        if (tipoSujeto == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar tipoSujeto");
        }

        return switch (tipoSujeto) {
            case ROL -> listarRoles();
            case USUARIO -> listarUsuarios();
            case DEPARTAMENTO -> listarDepartamentos();
            case CLIENTE -> listarClientes();
            case TRAMITE -> listarTramites();
        };
    }

    private List<DocumentSubjectOptionResponse> listarRoles() {
        return rolRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(rol -> opcion(DocumentSubjectType.ROL, rol.getNombre(), rol.getNombre(), rol.getDescripcion()))
                .toList();
    }

    private List<DocumentSubjectOptionResponse> listarUsuarios() {
        return usuarioRepository.findAllByActivoOrderByNombreAsc(true).stream()
                .map(usuario -> opcion(DocumentSubjectType.USUARIO, usuario.getId(), usuario.getNombre(), usuario.getCorreo()))
                .toList();
    }

    private List<DocumentSubjectOptionResponse> listarDepartamentos() {
        return departamentoRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(this::opcionDepartamento)
                .toList();
    }

    private List<DocumentSubjectOptionResponse> listarClientes() {
        LinkedHashMap<String, DocumentSubjectOptionResponse> clientes = new LinkedHashMap<>();
        usuarioRepository.findAllByActivoOrderByNombreAsc(true).stream()
                .filter(usuario -> esRolCliente(usuario.getRol()))
                .map(usuario -> opcion(DocumentSubjectType.CLIENTE, usuario.getId(), usuario.getNombre(), usuario.getCorreo()))
                .forEach(option -> clientes.putIfAbsent(option.getId(), option));

        return clientes.values().stream()
                .sorted(Comparator.comparing(option -> safeLower(option.getNombre())))
                .toList();
    }

    private List<DocumentSubjectOptionResponse> listarTramites() {
        return instanciaPoliticaRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(this::opcionTramite)
                .toList();
    }

    private DocumentSubjectOptionResponse opcionDepartamento(Departamento departamento) {
        return opcion(
                DocumentSubjectType.DEPARTAMENTO,
                departamento.getId(),
                departamento.getNombre(),
                departamento.getDescripcion()
        );
    }

    private DocumentSubjectOptionResponse opcionTramite(InstanciaPolitica instancia) {
        String nombre = normalizar(instancia.getCodigoTramite()) != null
                ? instancia.getCodigoTramite()
                : instancia.getId();
        String detalle = instancia.getEstadoInstancia() != null ? instancia.getEstadoInstancia().name() : null;
        return opcion(DocumentSubjectType.TRAMITE, instancia.getId(), nombre, detalle);
    }

    private DocumentSubjectOptionResponse opcion(
            DocumentSubjectType tipoSujeto,
            String id,
            String nombre,
            String detalle
    ) {
        return DocumentSubjectOptionResponse.builder()
                .tipoSujeto(tipoSujeto)
                .id(id)
                .nombre(normalizar(nombre) != null ? nombre.trim() : id)
                .detalle(normalizar(detalle))
                .build();
    }

    private boolean esRolCliente(String rol) {
        String normalized = normalizar(rol);
        if (normalized == null) {
            return false;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return "CLIENTE".equals(normalized) || "USUARIO".equals(normalized);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizar(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
