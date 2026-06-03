package com.leo.politicas_de_negocio.documents.controller;

import com.leo.politicas_de_negocio.documents.model.DocumentRepository;
import com.leo.politicas_de_negocio.documents.service.DocumentRepositoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/document-repositories")
public class DocumentRepositoryController {

    private final DocumentRepositoryService repositoryService;

    public DocumentRepositoryController(DocumentRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping("/auto-create")
    public DocumentRepository createAutomatically(
            @RequestParam String clientId,
            @RequestParam String processInstanceId,
            @RequestParam(required = false) String policyId
    ) {
        return repositoryService.createAutomatically(clientId, processInstanceId, policyId);
    }
}
