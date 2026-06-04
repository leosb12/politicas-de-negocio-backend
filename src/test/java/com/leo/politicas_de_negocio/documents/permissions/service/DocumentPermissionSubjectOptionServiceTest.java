package com.leo.politicas_de_negocio.documents.permissions.service;

import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentSubjectOptionResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.usuarios.model.Rol;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.RolRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentPermissionSubjectOptionServiceTest {

    private RolRepository rolRepository;
    private UsuarioRepository usuarioRepository;
    private DepartamentoRepository departamentoRepository;
    private InstanciaPoliticaRepository instanciaPoliticaRepository;
    private DocumentPermissionSubjectOptionService service;

    @BeforeEach
    void setUp() {
        rolRepository = mock(RolRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        departamentoRepository = mock(DepartamentoRepository.class);
        instanciaPoliticaRepository = mock(InstanciaPoliticaRepository.class);
        service = new DocumentPermissionSubjectOptionService(
                rolRepository,
                usuarioRepository,
                departamentoRepository,
                instanciaPoliticaRepository
        );
    }

    @Test
    void listarOpciones_debeUsarNombreDeRolComoIdParaReglasRol() {
        when(rolRepository.findAllByActivoTrueOrderByNombreAsc()).thenReturn(List.of(
                Rol.builder()
                        .id("mongo-role-1")
                        .nombre("ADMIN")
                        .descripcion("Administrador")
                        .activo(true)
                        .build()
        ));

        List<DocumentSubjectOptionResponse> options = service.listarOpciones(DocumentSubjectType.ROL);

        assertEquals(1, options.size());
        assertEquals("ADMIN", options.get(0).getId());
        assertEquals("ADMIN", options.get(0).getNombre());
        assertEquals("Administrador", options.get(0).getDetalle());
    }

    @Test
    void listarOpciones_debeFiltrarClientesPorRolUsuarioOCliente() {
        when(usuarioRepository.findAllByActivoOrderByNombreAsc(true)).thenReturn(List.of(
                Usuario.builder().id("admin-1").nombre("Admin").correo("admin@demo.com").rol("ADMIN").activo(true).build(),
                Usuario.builder().id("cliente-1").nombre("Cliente Uno").correo("cliente@demo.com").rol("USUARIO").activo(true).build(),
                Usuario.builder().id("cliente-2").nombre("Cliente Dos").correo("cliente2@demo.com").rol("CLIENTE").activo(true).build()
        ));

        List<DocumentSubjectOptionResponse> options = service.listarOpciones(DocumentSubjectType.CLIENTE);

        assertEquals(2, options.size());
        assertEquals("cliente-2", options.get(0).getId());
        assertEquals("cliente-1", options.get(1).getId());
    }

    @Test
    void listarOpciones_debeListarTramitesPorInstancia() {
        when(instanciaPoliticaRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of(
                InstanciaPolitica.builder()
                        .id("instancia-1")
                        .codigoTramite("TR-001")
                        .estadoInstancia(EstadoInstancia.EN_CURSO)
                        .build()
        ));

        List<DocumentSubjectOptionResponse> options = service.listarOpciones(DocumentSubjectType.TRAMITE);

        assertEquals(1, options.size());
        assertEquals("instancia-1", options.get(0).getId());
        assertEquals("TR-001", options.get(0).getNombre());
        assertEquals("EN_CURSO", options.get(0).getDetalle());
    }
}
