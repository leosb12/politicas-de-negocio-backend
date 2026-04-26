package com.leo.politicas_de_negocio.iaflujo.controller;

import com.leo.politicas_de_negocio.iaflujo.dto.TextoAFlujoRequest;
import com.leo.politicas_de_negocio.iaflujo.dto.TextoAFlujoResponse;
import com.leo.politicas_de_negocio.iaflujo.service.TextoAFlujoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class TextoAFlujoController {

    private final TextoAFlujoService textoAFlujoService;

    @PostMapping("/texto-a-flujo")
    public ResponseEntity<TextoAFlujoResponse> generarFlujo(@RequestBody TextoAFlujoRequest request) {
        return ResponseEntity.ok(textoAFlujoService.generarFlujo(request));
    }
}
