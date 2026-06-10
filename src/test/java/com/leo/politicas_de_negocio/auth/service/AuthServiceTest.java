package com.leo.politicas_de_negocio.auth.service;

import com.leo.politicas_de_negocio.auth.dto.FuncionarioDepartamentoResponse;
import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.leo.politicas_de_negocio.analiticas.service.SystemAuditService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SystemAuditService systemAuditService;

    private AutoCloseable mocks;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        authService = new AuthService(usuarioRepository, departamentoRepository, passwordEncoder, systemAuditService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void getFuncionarioDepartment_debeRetornarNombreDelDepartamento() {
        Usuario funcionario = Usuario.builder()
                .id("func-1")
                .rol("FUNCIONARIO")
                .activo(true)
                .departamentoId("dep-1")
                .build();
        Departamento departamento = Departamento.builder()
                .id("dep-1")
                .nombre("Mesa de Entrada")
                .build();

        when(usuarioRepository.findByIdAndActivo("func-1", true)).thenReturn(Optional.of(funcionario));
        when(departamentoRepository.findById("dep-1")).thenReturn(Optional.of(departamento));

        FuncionarioDepartamentoResponse response = authService.getFuncionarioDepartment("func-1");

        assertEquals("dep-1", response.getId());
        assertEquals("Mesa de Entrada", response.getNombre());
    }

    @Test
    void getFuncionarioDepartment_debeRetornarVacioSiNoTieneDepartamentoAsignado() {
        Usuario funcionario = Usuario.builder()
                .id("func-1")
                .rol("FUNCIONARIO")
                .activo(true)
                .departamentoId(null)
                .build();

        when(usuarioRepository.findByIdAndActivo("func-1", true)).thenReturn(Optional.of(funcionario));

        FuncionarioDepartamentoResponse response = authService.getFuncionarioDepartment("func-1");

        assertNull(response.getId());
        assertNull(response.getNombre());
    }

    @Test
    void getFuncionarioDepartment_debeRechazarUsuariosNoFuncionario() {
        Usuario admin = Usuario.builder()
                .id("admin-1")
                .rol("ADMIN")
                .activo(true)
                .build();

        when(usuarioRepository.findByIdAndActivo("admin-1", true)).thenReturn(Optional.of(admin));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.getFuncionarioDepartment("admin-1")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }
}
