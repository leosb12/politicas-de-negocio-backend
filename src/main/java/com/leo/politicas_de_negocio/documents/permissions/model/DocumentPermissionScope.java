package com.leo.politicas_de_negocio.documents.permissions.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPermissionScope {

    private String clienteId;
    private String tramiteId;
    private String departamentoId;
}
