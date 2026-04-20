package com.leo.politicas_de_negocio.shared.config;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.usuarios.model.Rol;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.usuarios.repository.RolRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class DataLoader {

        private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    @Bean
    CommandLineRunner initData(
            DepartamentoRepository departamentoRepository,
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository
    ) {
        return args -> {
            try {

                Rol rolAdmin = ensureRole(
                        rolRepository,
                        "ADMIN",
                        "Administrador con acceso completo al sistema",
                        true
                );

                Rol rolFuncionario = ensureRole(
                        rolRepository,
                        "FUNCIONARIO",
                        "Funcionario que ejecuta actividades del flujo",
                        true
                );

                Rol rolUsuario = ensureRole(
                        rolRepository,
                        "USUARIO",
                        "Usuario final o cliente",
                        true
                );

                Departamento administracion = departamentoRepository.findByNombreIgnoreCase("Administración")
                        .orElseGet(() -> departamentoRepository.save(
                                Departamento.builder()
                                        .nombre("Administración")
                                        .descripcion("Área administrativa")
                                        .activo(true)
                                        .build()
                        ));

                Departamento atencion = departamentoRepository.findByNombreIgnoreCase("Atención al Cliente")
                        .orElseGet(() -> departamentoRepository.save(
                                Departamento.builder()
                                        .nombre("Atención al Cliente")
                                        .descripcion("Área de atención")
                                        .activo(true)
                                        .build()
                        ));

                ensureUser(
                        usuarioRepository,
                        "Administrador General",
                        "admin@demo.com",
                        "123456",
                        rolAdmin.getNombre(),
                        administracion.getId()
                );

                ensureUser(
                        usuarioRepository,
                        "Funcionario Uno",
                        "funcionario@demo.com",
                        "123456",
                        rolFuncionario.getNombre(),
                        atencion.getId()
                );

                ensureUser(
                        usuarioRepository,
                        "Usuario Final",
                        "usuario@demo.com",
                        "123456",
                        rolUsuario.getNombre(),
                        null
                );

                ensureUser(
                        usuarioRepository,
                        "Usuario Movil Demo",
                        "movil@demo.com",
                        "123456",
                        rolUsuario.getNombre(),
                        null
                );
            } catch (Exception ex) {
                log.warn("No se pudo inicializar datos semilla en MongoDB. La app continuará iniciando. Causa: {}", ex.getMessage());
            }
        };
    }

    private void ensureUser(
            UsuarioRepository usuarioRepository,
            String nombre,
            String correo,
            String password,
            String rol,
            String departamentoId
    ) {
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            return;
        }

        usuarioRepository.save(
                Usuario.builder()
                        .nombre(nombre)
                        .correo(correo)
                        .password(password)
                        .rol(rol)
                        .departamentoId(departamentoId)
                        .activo(true)
                        .fechaCreacion(LocalDateTime.now())
                        .build()
        );
    }

    private Rol ensureRole(RolRepository rolRepository, String nombre, String descripcion, boolean sistema) {
        return rolRepository.findByNombreIgnoreCase(nombre)
                .map(existing -> {
                    boolean changed = false;

                    if (!Boolean.TRUE.equals(existing.getActivo())) {
                        existing.setActivo(true);
                        changed = true;
                    }

                    if (existing.getSistema() == null || existing.getSistema() != sistema) {
                        existing.setSistema(sistema);
                        changed = true;
                    }

                    if (existing.getDescripcion() == null || existing.getDescripcion().isBlank()) {
                        existing.setDescripcion(descripcion);
                        changed = true;
                    }

                    if (changed) {
                        return rolRepository.save(existing);
                    }

                    return existing;
                })
                .orElseGet(() -> rolRepository.save(
                        Rol.builder()
                                .nombre(nombre)
                                .descripcion(descripcion)
                                .activo(true)
                                .sistema(sistema)
                                .build()
                ));
    }
}