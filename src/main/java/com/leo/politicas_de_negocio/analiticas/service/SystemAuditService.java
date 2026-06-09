package com.leo.politicas_de_negocio.analiticas.service;

import com.leo.politicas_de_negocio.analiticas.model.AuditoriaSistema;
import com.leo.politicas_de_negocio.analiticas.repository.AuditoriaSistemaRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemAuditService {

    private static final Logger log = LoggerFactory.getLogger(SystemAuditService.class);

    private final AuditoriaSistemaRepository repository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaSistema log(String usuarioId, String usuarioNombre, String usuarioCorreo, String rol, String accion, String detalle) {
        try {
            AuditoriaSistema logEntry = AuditoriaSistema.builder()
                    .usuarioId(usuarioId)
                    .usuarioNombre(usuarioNombre)
                    .usuarioCorreo(usuarioCorreo)
                    .rol(rol)
                    .accion(accion)
                    .fecha(LocalDateTime.now())
                    .detalle(detalle)
                    .ip("127.0.0.1")
                    .build();
            return repository.save(logEntry);
        } catch (Exception e) {
            log.error("Error al registrar auditoría de sistema: {}", e.getMessage());
            return null;
        }
    }

    public List<AuditoriaSistema> obtenerTodosOrdenados() {
        return repository.findAllByOrderByFechaDesc();
    }

    public void seedSystemAudits() {
        if (repository.count() > 0) {
            return;
        }

        log.info("Poblando colección de auditoría de sistema con datos iniciales...");

        Usuario admin = usuarioRepository.findByCorreo("admin@demo.com").orElse(null);
        Usuario funcionario = usuarioRepository.findByCorreo("funcionario@demo.com").orElse(null);
        Usuario cliente = usuarioRepository.findByCorreo("usuario@demo.com").orElse(null);

        String adminId = admin != null ? admin.getId() : "seed-admin-id";
        String adminNombre = admin != null ? admin.getNombre() : "Administrador General";
        String adminCorreo = admin != null ? admin.getCorreo() : "admin@demo.com";

        String funcId = funcionario != null ? funcionario.getId() : "seed-func-id";
        String funcNombre = funcionario != null ? funcionario.getNombre() : "Funcionario Uno";
        String funcCorreo = funcionario != null ? funcionario.getCorreo() : "funcionario@demo.com";

        String clienteId = cliente != null ? cliente.getId() : "seed-client-id";
        String clienteNombre = cliente != null ? cliente.getNombre() : "Usuario Final";
        String clienteCorreo = cliente != null ? cliente.getCorreo() : "usuario@demo.com";

        LocalDateTime baseTime = LocalDateTime.now().minusDays(3);

        // Seed entries
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "LOGIN_WEB", "Inicio de sesión en la plataforma web", baseTime);
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "CREAR_POLITICA", "Creación de la política 'Solicitud de Crédito Comercial'", baseTime.plusMinutes(20));
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "MODIFICAR_POLITICA", "Modificación del flujo en la política 'Solicitud de Crédito Comercial'", baseTime.plusHours(2));
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "CAMBIO_ESTADO_POLITICA", "Pase a producción (estado ACTIVA) de la política 'Solicitud de Crédito Comercial'", baseTime.plusHours(4));
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "LOGOUT", "Cierre de sesión de la plataforma", baseTime.plusHours(5));

        saveMockAudit(clienteId, clienteNombre, clienteCorreo, "USUARIO", "LOGIN_MOVIL", "Inicio de sesión desde la aplicación móvil", baseTime.plusDays(1).plusHours(2));
        saveMockAudit(clienteId, clienteNombre, clienteCorreo, "USUARIO", "REGISTRO_MOVIL", "Auto-registro exitoso de usuario móvil", baseTime.plusDays(1).plusHours(2).minusMinutes(5));
        saveMockAudit(clienteId, clienteNombre, clienteCorreo, "USUARIO", "LOGOUT", "Cierre de sesión desde móvil", baseTime.plusDays(1).plusHours(3));

        saveMockAudit(funcId, funcNombre, funcCorreo, "FUNCIONARIO", "LOGIN_WEB", "Inicio de sesión en la plataforma web", baseTime.plusDays(2).plusHours(1));
        saveMockAudit(funcId, funcNombre, funcCorreo, "FUNCIONARIO", "MODIFICAR_DOCUMENTO", "Edición del documento colaborativo de 'Requisitos Legales' para trámite #1034", baseTime.plusDays(2).plusHours(2));
        saveMockAudit(funcId, funcNombre, funcCorreo, "FUNCIONARIO", "LOGOUT", "Cierre de sesión de la plataforma", baseTime.plusDays(2).plusHours(4));

        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "LOGIN_WEB", "Inicio de sesión en la plataforma web", baseTime.plusDays(2).plusHours(5));
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "CREAR_POLITICA", "Creación de la política 'Acreditación de Proveedores'", baseTime.plusDays(2).plusHours(6));
        saveMockAudit(adminId, adminNombre, adminCorreo, "ADMIN", "CAMBIO_ESTADO_POLITICA", "Pase a producción (estado ACTIVA) de la política 'Acreditación de Proveedores'", baseTime.plusDays(2).plusHours(8));

        log.info("Auditoría de sistema poblada con éxito.");
    }

    private void saveMockAudit(String id, String nombre, String correo, String rol, String accion, String detalle, LocalDateTime fecha) {
        repository.save(AuditoriaSistema.builder()
                .usuarioId(id)
                .usuarioNombre(nombre)
                .usuarioCorreo(correo)
                .rol(rol)
                .accion(accion)
                .fecha(fecha)
                .detalle(detalle)
                .ip("127.0.0.1")
                .build());
    }
}
