package com.leo.politicas_de_negocio.archivos.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ReemplazarArchivoRequest {
    private MultipartFile archivo;
}
