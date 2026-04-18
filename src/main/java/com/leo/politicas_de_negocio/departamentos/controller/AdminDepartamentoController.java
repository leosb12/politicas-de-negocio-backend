package com.leo.politicas_de_negocio.departamentos.controller;

import com.leo.politicas_de_negocio.departamentos.dto.CreateDepartamentoRequest;
import com.leo.politicas_de_negocio.departamentos.dto.DepartamentoResponse;
import com.leo.politicas_de_negocio.departamentos.dto.ReasignarDepartamentoRequest;
import com.leo.politicas_de_negocio.departamentos.dto.UpdateDepartamentoRequest;
import com.leo.politicas_de_negocio.usuarios.dto.UserResponse;
import com.leo.politicas_de_negocio.departamentos.service.AdminDepartamentoService;
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
@RequestMapping("/api/admin/departamentos")
public class AdminDepartamentoController {

    private final AdminDepartamentoService adminDepartamentoService;

    public AdminDepartamentoController(AdminDepartamentoService adminDepartamentoService) {
        this.adminDepartamentoService = adminDepartamentoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartamentoResponse crearDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @RequestBody CreateDepartamentoRequest request
    ) {
        return adminDepartamentoService.crearDepartamento(adminUserId, request);
    }

    @GetMapping
    public List<DepartamentoResponse> listarDepartamentos(
            @RequestHeader("X-Admin-User-Id") String adminUserId
    ) {
        return adminDepartamentoService.listarDepartamentos(adminUserId);
    }

    @GetMapping("/{departamentoId}")
    public DepartamentoResponse obtenerDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId
    ) {
        return adminDepartamentoService.obtenerDepartamento(adminUserId, departamentoId);
    }

    @PutMapping("/{departamentoId}")
    public DepartamentoResponse actualizarDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId,
            @RequestBody UpdateDepartamentoRequest request
    ) {
        return adminDepartamentoService.actualizarDepartamento(adminUserId, departamentoId, request);
    }

    @PatchMapping("/{departamentoId}/activar")
    public DepartamentoResponse activarDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId
    ) {
        return adminDepartamentoService.activarDepartamento(adminUserId, departamentoId);
    }

    @PatchMapping("/{departamentoId}/desactivar")
    public DepartamentoResponse desactivarDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId
    ) {
        return adminDepartamentoService.desactivarDepartamento(adminUserId, departamentoId);
    }

    @DeleteMapping("/{departamentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId
    ) {
        adminDepartamentoService.eliminarDepartamento(adminUserId, departamentoId);
    }

    @GetMapping("/{departamentoId}/usuarios")
    public List<UserResponse> listarUsuariosPorDepartamento(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId
    ) {
        return adminDepartamentoService.listarUsuariosPorDepartamento(adminUserId, departamentoId);
    }

    @PostMapping("/{departamentoId}/reasignar-usuarios")
    public DepartamentoResponse reasignarUsuarios(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String departamentoId,
            @RequestBody ReasignarDepartamentoRequest request
    ) {
        return adminDepartamentoService.reasignarUsuarios(adminUserId, departamentoId, request);
    }
}