package com.leo.politicas_de_negocio.analiticas.controller;

import com.leo.politicas_de_negocio.analiticas.dto.response.AttentionTimesAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.DashboardSummaryResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.GeneralAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.PolicyImprovementAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.TaskAccumulationAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.TaskRedistributionAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.service.AnalyticsService;
import com.leo.politicas_de_negocio.analiticas.service.SystemAuditService;
import com.leo.politicas_de_negocio.analiticas.model.AuditoriaSistema;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SystemAuditService systemAuditService;

    @GetMapping("/general")
    public ResponseEntity<GeneralAnalyticsResponse> getGeneral(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getGeneralMetrics(resolveActorUserId(userId, adminUserId)));
    }

    @GetMapping("/system-audit")
    public ResponseEntity<List<AuditoriaSistema>> getSystemAudit(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        resolveActorUserId(userId, adminUserId);
        return ResponseEntity.ok(systemAuditService.obtenerTodosOrdenados());
    }

    @PostMapping("/system-audit")
    public ResponseEntity<AuditoriaSistema> logSystemAudit(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Desconocido") String userName,
            @RequestHeader(value = "X-User-Email", required = false, defaultValue = "sin@correo.com") String userEmail,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "USUARIO") String userRole,
            @RequestBody java.util.Map<String, String> body
    ) {
        String actorId = resolveActorUserId(userId, adminUserId);
        String accion = body.getOrDefault("accion", "DESCONOCIDA");
        String detalle = body.getOrDefault("detalle", "Sin detalle");
        
        AuditoriaSistema log = systemAuditService.log(actorId, userName, userEmail, userRole, accion, detalle);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/attention-times")
    public ResponseEntity<AttentionTimesAnalyticsResponse> getAttentionTimes(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getAttentionTimes(resolveActorUserId(userId, adminUserId)));
    }

    @GetMapping("/task-accumulation")
    public ResponseEntity<TaskAccumulationAnalyticsResponse> getTaskAccumulation(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getTaskAccumulation(resolveActorUserId(userId, adminUserId)));
    }

    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getDashboardSummary(resolveActorUserId(userId, adminUserId)));
    }

    @GetMapping("/bottlenecks")
    public ResponseEntity<BottlenecksAnalyticsResponse> getBottlenecks(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getBottlenecks(resolveActorUserId(userId, adminUserId)));
    }

        @GetMapping({"/task-redistribution", "/recommendations/task-redistribution"})
    public ResponseEntity<TaskRedistributionAnalyticsResponse> getTaskRedistribution(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getTaskRedistribution(resolveActorUserId(userId, adminUserId)));
    }

        @GetMapping({"/policy-improvement", "/recommendations/policy-improvement"})
    public ResponseEntity<PolicyImprovementAnalyticsResponse> getPolicyImprovement(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        return ResponseEntity.ok(analyticsService.getPolicyImprovement(resolveActorUserId(userId, adminUserId)));
    }

    private String resolveActorUserId(String userId, String adminUserId) {
        String normalizedUser = normalize(userId);
        if (normalizedUser != null) {
            return normalizedUser;
        }

        String normalizedAdmin = normalize(adminUserId);
        if (normalizedAdmin != null) {
            return normalizedAdmin;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
