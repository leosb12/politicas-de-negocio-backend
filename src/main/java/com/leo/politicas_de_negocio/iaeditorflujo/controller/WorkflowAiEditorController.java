package com.leo.politicas_de_negocio.iaeditorflujo.controller;

import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditApplyRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditApplyResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditPreviewRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditPreviewResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.service.WorkflowAiEditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia/flujos/{policyId}/edicion")
@RequiredArgsConstructor
public class WorkflowAiEditorController {

    private final WorkflowAiEditorService workflowAiEditorService;

    @PostMapping("/previsualizar")
    public ResponseEntity<WorkflowAiEditPreviewResponse> previewEdition(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String policyId,
            @RequestBody WorkflowAiEditPreviewRequest request
    ) {
        return ResponseEntity.ok(workflowAiEditorService.previewEdition(adminUserId, policyId, request));
    }

    @PostMapping("/aplicar")
    public ResponseEntity<WorkflowAiEditApplyResponse> applyEdition(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String policyId,
            @RequestBody WorkflowAiEditApplyRequest request
    ) {
        return ResponseEntity.ok(workflowAiEditorService.applyEdition(adminUserId, policyId, request));
    }
}
