package com.leo.politicas_de_negocio.politicas.dto;

import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import lombok.Data;

import java.util.List;

@Data
public class UpdateFlujoRequest {
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
}
