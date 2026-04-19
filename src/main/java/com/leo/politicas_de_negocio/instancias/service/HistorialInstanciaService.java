package com.leo.politicas_de_negocio.instancias.service;

import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.instancias.repository.HistorialInstanciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialInstanciaService {

    private final HistorialInstanciaRepository historialRepository;

    public HistorialInstancia registrar(
            String instanciaId,
            String tareaId,
            String accion,
            String usuario,
            String detalle
    ) {
        HistorialInstancia evento = HistorialInstancia.builder()
                .instanciaId(instanciaId)
                .tareaId(tareaId)
                .accion(accion)
                .usuario(usuario)
                .detalle(detalle)
                .fecha(LocalDateTime.now())
                .build();
        return historialRepository.save(evento);
    }

    public List<HistorialInstancia> listarPorInstancia(String instanciaId) {
        return historialRepository.findByInstanciaIdOrderByFechaAsc(instanciaId).stream()
                .sorted(Comparator.comparing(HistorialInstancia::getFecha, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
    }
}
