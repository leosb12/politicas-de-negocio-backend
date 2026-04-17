package com.leo.politicas_de_negocio.controller;

import com.leo.politicas_de_negocio.dto.admin.CreateRoleRequest;
import com.leo.politicas_de_negocio.dto.admin.CreateUserRequest;
import com.leo.politicas_de_negocio.dto.admin.RoleResponse;
import com.leo.politicas_de_negocio.dto.admin.UpdateRoleRequest;
import com.leo.politicas_de_negocio.dto.admin.UpdateUserRequest;
import com.leo.politicas_de_negocio.dto.admin.UpdateUserRoleRequest;
import com.leo.politicas_de_negocio.dto.admin.UserResponse;
import com.leo.politicas_de_negocio.service.AdminManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminManagementController {

    private final AdminManagementService adminManagementService;

    public AdminManagementController(AdminManagementService adminManagementService) {
        this.adminManagementService = adminManagementService;
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse crearUsuario(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @RequestBody CreateUserRequest request
    ) {
        return adminManagementService.crearUsuario(adminUserId, request);
    }

    @GetMapping("/usuarios")
    public List<UserResponse> listarUsuarios(@RequestHeader("X-Admin-User-Id") String adminUserId) {
        return adminManagementService.listarUsuarios(adminUserId);
    }

    @GetMapping("/usuarios/{usuarioId}")
    public UserResponse obtenerUsuario(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String usuarioId
    ) {
        return adminManagementService.obtenerUsuario(adminUserId, usuarioId);
    }

    @PutMapping("/usuarios/{usuarioId}")
    public UserResponse actualizarUsuario(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String usuarioId,
            @RequestBody UpdateUserRequest request
    ) {
        return adminManagementService.actualizarUsuario(adminUserId, usuarioId, request);
    }

    @PatchMapping("/usuarios/{usuarioId}/activar")
    public UserResponse activarUsuario(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String usuarioId
    ) {
        return adminManagementService.activarUsuario(adminUserId, usuarioId);
    }

    @PatchMapping("/usuarios/{usuarioId}/desactivar")
    public UserResponse desactivarUsuario(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String usuarioId
    ) {
        return adminManagementService.desactivarUsuario(adminUserId, usuarioId);
    }

    @PatchMapping("/usuarios/{usuarioId}/rol")
    public UserResponse asignarRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String usuarioId,
            @RequestBody UpdateUserRoleRequest request
    ) {
        return adminManagementService.asignarRol(adminUserId, usuarioId, request);
    }

    @PatchMapping("/usuarios/{usuarioId}/rol/quitar")
    public UserResponse quitarRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String usuarioId
    ) {
        return adminManagementService.quitarRol(adminUserId, usuarioId);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse crearRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @RequestBody CreateRoleRequest request
    ) {
        return adminManagementService.crearRol(adminUserId, request);
    }

    @GetMapping("/roles")
    public List<RoleResponse> listarRoles(@RequestHeader("X-Admin-User-Id") String adminUserId) {
        return adminManagementService.listarRoles(adminUserId);
    }

    @GetMapping("/roles/{rolId}")
    public RoleResponse obtenerRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String rolId
    ) {
        return adminManagementService.obtenerRol(adminUserId, rolId);
    }

    @PutMapping("/roles/{rolId}")
    public RoleResponse actualizarRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String rolId,
            @RequestBody UpdateRoleRequest request
    ) {
        return adminManagementService.actualizarRol(adminUserId, rolId, request);
    }

    @PatchMapping("/roles/{rolId}/activar")
    public RoleResponse activarRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String rolId
    ) {
        return adminManagementService.activarRol(adminUserId, rolId);
    }

    @PatchMapping("/roles/{rolId}/desactivar")
    public RoleResponse desactivarRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String rolId
    ) {
        return adminManagementService.desactivarRol(adminUserId, rolId);
    }

    @DeleteMapping("/roles/{rolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarRol(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String rolId
    ) {
        adminManagementService.eliminarRol(adminUserId, rolId);
    }
}