package com.leo.politicas_de_negocio.workflow_metricas.controller;

import com.leo.politicas_de_negocio.workflow_metricas.service.WorkflowMetricasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workflow-metricas")
@RequiredArgsConstructor
public class WorkflowMetricasController {

    private final WorkflowMetricasService workflowMetricasService;

    @PostMapping("/instancias/{idInstancia}/nodos/{idNodo}/entrada")
    public ResponseEntity<Void> registrarEntrada(
            @PathVariable String idInstancia,
            @PathVariable String idNodo) {
        workflowMetricasService.registrarEntradaNodo(idInstancia, idNodo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instancias/{idInstancia}/nodos/{idNodo}/salida")
    public ResponseEntity<Void> registrarSalida(
            @PathVariable String idInstancia,
            @PathVariable String idNodo) {
        workflowMetricasService.registrarSalidaNodo(idInstancia, idNodo);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/instancias/{idInstancia}/historial")
    public ResponseEntity<Map<String, Object>> obtenerHistorialInstancia(
            @PathVariable String idInstancia) {
        return ResponseEntity.ok(workflowMetricasService.obtenerHistorialInstancia(idInstancia));
    }

    @GetMapping("/politicas/{idPolitica}/metricas")
    public ResponseEntity<Map<String, Object>> obtenerMetricasPolitica(
            @PathVariable String idPolitica) {
        return ResponseEntity.ok(workflowMetricasService.obtenerMetricasPolitica(idPolitica));
    }

    @GetMapping("/general")
    public ResponseEntity<Map<String, Object>> obtenerMetricasGenerales() {
        return ResponseEntity.ok(workflowMetricasService.obtenerMetricasGenerales());
    }
}
