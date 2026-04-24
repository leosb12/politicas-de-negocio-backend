package com.leo.politicas_de_negocio.simulation.controller;

import com.leo.politicas_de_negocio.simulation.dto.PolicyComparisonResponse;
import com.leo.politicas_de_negocio.simulation.dto.SimulationComparisonRequest;
import com.leo.politicas_de_negocio.simulation.dto.SimulationRunRequest;
import com.leo.politicas_de_negocio.simulation.dto.SimulationRunResponse;
import com.leo.politicas_de_negocio.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/policies/{policyId}/run")
    public ResponseEntity<SimulationRunResponse> runSimulation(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String policyId,
            @RequestBody(required = false) SimulationRunRequest request
    ) {
        return new ResponseEntity<>(simulationService.runSimulation(adminUserId, policyId, request), HttpStatus.CREATED);
    }

    @GetMapping("/{simulationId}")
    public ResponseEntity<SimulationRunResponse> getSimulationById(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String simulationId
    ) {
        return ResponseEntity.ok(simulationService.getSimulationById(adminUserId, simulationId));
    }

    @GetMapping("/policies/{policyId}")
    public ResponseEntity<List<SimulationRunResponse>> getSimulationsByPolicy(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String policyId
    ) {
        return ResponseEntity.ok(simulationService.getSimulationsByPolicy(adminUserId, policyId));
    }

    @PostMapping("/compare")
    public ResponseEntity<PolicyComparisonResponse> comparePolicies(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @RequestBody SimulationComparisonRequest request
    ) {
        return ResponseEntity.ok(simulationService.comparePolicies(adminUserId, request));
    }
}
