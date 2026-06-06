package com.leo.politicas_de_negocio.politicas.dto;

import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRequisitosInicialesRequest {
    private List<CampoFormulario> requisitosIniciales;
}
