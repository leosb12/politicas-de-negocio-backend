package com.leo.politicas_de_negocio.shared.config;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.usuarios.model.Rol;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.usuarios.repository.RolRepository;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.PoliticaAuditoria;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

import com.leo.politicas_de_negocio.politicas.model.PoliticaAuditoria;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaAuditoriaRepository;
import com.leo.politicas_de_negocio.analiticas.service.SystemAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

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
            RolRepository rolRepository,
            PoliticaNegocioRepository politicaRepository,
            PoliticaAuditoriaRepository politicaAuditoriaRepository,
            SystemAuditService systemAuditService
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

                migrateAndSeedExistingPoliciesAudits(
                        usuarioRepository,
                        politicaRepository,
                        politicaAuditoriaRepository
                );

                systemAuditService.seedSystemAudits();
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
                if (usuarioRepository.existsByCorreo(correo)) {
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
                                .sistema(sistema)
                                .build()
                ));
    }

    private void migrateAndSeedExistingPoliciesAudits(
            UsuarioRepository usuarioRepository,
            PoliticaNegocioRepository politicaRepository,
            PoliticaAuditoriaRepository politicaAuditoriaRepository
    ) {
        Usuario admin = usuarioRepository.findByCorreo("admin@demo.com").orElse(null);
        if (admin == null) {
            log.warn("No se pudo migrar/auditar políticas existentes: admin@demo.com no existe.");
            return;
        }

        List<PoliticaNegocio> politicas = politicaRepository.findAll();
        for (PoliticaNegocio politica : politicas) {
            boolean changed = false;
            if (politica.getCreadoPor() == null || politica.getCreadoPor().isBlank()) {
                politica.setCreadoPor(admin.getId());
                politica.setCreadoPorNombre(admin.getNombre());
                changed = true;
            }
            if (changed) {
                politicaRepository.save(politica);
            }

            List<PoliticaAuditoria> audits = politicaAuditoriaRepository.findByPoliticaIdOrderByFechaDesc(politica.getId());
            if (audits.isEmpty()) {
                LocalDateTime refDate = politica.getFechaCreacion() != null ? politica.getFechaCreacion() : LocalDateTime.now().minusDays(5);
                
                politicaAuditoriaRepository.save(PoliticaAuditoria.builder()
                        .politicaId(politica.getId())
                        .fecha(refDate)
                        .tipoAccion("CREACION")
                        .usuarioId(admin.getId())
                        .usuarioNombre(admin.getNombre())
                        .detalle("Creación de la política '" + politica.getNombre() + "' en estado BORRADOR (migrada).")
                        .build());

                politicaAuditoriaRepository.save(PoliticaAuditoria.builder()
                        .politicaId(politica.getId())
                        .fecha(refDate.plusHours(1))
                        .tipoAccion("EDICION_FLUJO")
                        .usuarioId(admin.getId())
                        .usuarioNombre(admin.getNombre())
                        .detalle("Diseño inicial del flujo canvas colaborativo (" + (politica.getNodos() != null ? politica.getNodos().size() : 0) + " nodos).")
                        .build());

                politicaAuditoriaRepository.save(PoliticaAuditoria.builder()
                        .politicaId(politica.getId())
                        .fecha(refDate.plusHours(2))
                        .tipoAccion("EDICION_METADATOS")
                        .usuarioId(admin.getId())
                        .usuarioNombre(admin.getNombre())
                        .detalle("Modificación de las propiedades y metadatos generales de la política.")
                        .build());

                if (politica.getEstado() != null && politica.getEstado() != com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica.BORRADOR) {
                    politicaAuditoriaRepository.save(PoliticaAuditoria.builder()
                            .politicaId(politica.getId())
                            .fecha(refDate.plusHours(3))
                            .tipoAccion("CAMBIO_ESTADO")
                            .usuarioId(admin.getId())
                            .usuarioNombre(admin.getNombre())
                            .detalle("Activación/Publicación de la política a estado: " + politica.getEstado().name())
                            .build());
                }
            }
        }
        log.info("Migración y seeding de auditorías de políticas completado con éxito.");
    }
}