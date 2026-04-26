package com.leo.politicas_de_negocio.formulariointeligente.controller;

import com.leo.politicas_de_negocio.formulariointeligente.dto.FormularioInteligenteRequest;
import com.leo.politicas_de_negocio.formulariointeligente.dto.FormularioInteligenteResponse;
import com.leo.politicas_de_negocio.formulariointeligente.service.FormularioInteligenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia/forms")
@RequiredArgsConstructor
public class FormularioInteligenteController {

    private final FormularioInteligenteService formularioInteligenteService;

    @PostMapping("/fill")
    public ResponseEntity<FormularioInteligenteResponse> completarFormulario(
            @RequestBody FormularioInteligenteRequest request
    ) {
        return ResponseEntity.ok(formularioInteligenteService.completarFormulario(request));
    }
}
