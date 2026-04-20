package com.leo.politicas_de_negocio.archivos.storage;

import com.leo.politicas_de_negocio.archivos.config.StorageProperties;
import com.leo.politicas_de_negocio.archivos.exception.ArchivoNoEncontradoException;
import com.leo.politicas_de_negocio.archivos.exception.ArchivoStorageException;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoContenido;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStorageRequest;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStoredObject;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalArchivoStorageService implements ArchivoStorageService {

    private final Path basePath;

    public LocalArchivoStorageService(StorageProperties storageProperties) {
        String configuredPath = storageProperties.getLocal() != null ? storageProperties.getLocal().getBasePath() : null;
        if (!StringUtils.hasText(configuredPath)) {
            configuredPath = "uploads";
        }
        this.basePath = Paths.get(configuredPath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(basePath);
        } catch (IOException ex) {
            throw new ArchivoStorageException("No se pudo crear el directorio base de archivos", ex);
        }
    }

    @Override
    public ArchivoStoredObject almacenar(ArchivoStorageRequest request) {
        validarContenido(request);

        String nombreGuardado = normalizarNombreGuardado(request.getNombreGuardado());
        String subdirectorio = normalizarSubdirectorio(request.getSubdirectorio());
        String rutaRelativa = construirRutaRelativa(subdirectorio, nombreGuardado);

        Path destino = resolverRutaSegura(rutaRelativa);

        try {
            if (destino.getParent() != null) {
                Files.createDirectories(destino.getParent());
            }
            Files.write(destino, request.getContenido(), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ex) {
            throw new ArchivoStorageException("Ya existe un archivo con la misma ruta en almacenamiento local", ex);
        } catch (IOException ex) {
            throw new ArchivoStorageException("No se pudo guardar el archivo en almacenamiento local", ex);
        }

        return ArchivoStoredObject.builder()
                .nombreGuardado(nombreGuardado)
                .rutaOKey(rutaRelativa)
                .storageType("local")
                .urlAcceso(construirReferenciaAcceso(rutaRelativa))
                .bucket(null)
                .build();
    }

    @Override
    public ArchivoContenido descargar(String rutaOKey) {
        String normalizedKey = normalizarRutaKey(rutaOKey);
        Path archivo = resolverRutaSegura(normalizedKey);
        if (!Files.exists(archivo)) {
            throw new ArchivoNoEncontradoException("Archivo no encontrado en almacenamiento local");
        }

        try {
            return ArchivoContenido.builder()
                    .contenido(Files.readAllBytes(archivo))
                    .contentType(Files.probeContentType(archivo))
                    .build();
        } catch (IOException ex) {
            throw new ArchivoStorageException("No se pudo leer el archivo en almacenamiento local", ex);
        }
    }

    @Override
    public void eliminar(String rutaOKey) {
        String normalizedKey = normalizarRutaKey(rutaOKey);
        Path archivo = resolverRutaSegura(normalizedKey);

        if (!Files.exists(archivo)) {
            throw new ArchivoNoEncontradoException("Archivo no encontrado en almacenamiento local");
        }

        try {
            Files.delete(archivo);
        } catch (IOException ex) {
            throw new ArchivoStorageException("No se pudo eliminar el archivo en almacenamiento local", ex);
        }
    }

    @Override
    public String construirReferenciaAcceso(String rutaOKey) {
        return "local://" + normalizarRutaKey(rutaOKey);
    }

    private void validarContenido(ArchivoStorageRequest request) {
        if (request == null || request.getContenido() == null || request.getContenido().length == 0) {
            throw new ArchivoStorageException("El contenido del archivo es obligatorio para almacenar");
        }
    }

    private Path resolverRutaSegura(String rutaRelativa) {
        Path resolved = basePath.resolve(rutaRelativa).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new ArchivoStorageException("Ruta invalida para almacenamiento local");
        }
        return resolved;
    }

    private String construirRutaRelativa(String subdirectorio, String nombreGuardado) {
        if (!StringUtils.hasText(subdirectorio)) {
            return nombreGuardado;
        }
        return subdirectorio + "/" + nombreGuardado;
    }

    private String normalizarRutaKey(String rutaOKey) {
        if (!StringUtils.hasText(rutaOKey)) {
            throw new ArchivoStorageException("La ruta del archivo es invalida");
        }

        String normalized = rutaOKey.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            throw new ArchivoStorageException("La ruta del archivo es invalida");
        }

        return normalized;
    }

    private String normalizarSubdirectorio(String subdirectorio) {
        if (!StringUtils.hasText(subdirectorio)) {
            return "";
        }

        String normalized = subdirectorio.trim().replace("\\", "/").replaceAll("/+", "/");
        String[] segmentos = normalized.split("/");
        List<String> limpios = new ArrayList<>();

        for (String segmento : segmentos) {
            if (!StringUtils.hasText(segmento)) {
                continue;
            }
            String cleaned = segmento.trim();
            if (".".equals(cleaned) || "..".equals(cleaned)) {
                continue;
            }
            cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (StringUtils.hasText(cleaned)) {
                limpios.add(cleaned);
            }
        }

        return String.join("/", limpios);
    }

    private String normalizarNombreGuardado(String nombreGuardado) {
        if (!StringUtils.hasText(nombreGuardado)) {
            throw new ArchivoStorageException("El nombre guardado del archivo es invalido");
        }

        String cleaned = nombreGuardado.trim().replace("\\", "/");
        int lastSlashIndex = cleaned.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            cleaned = cleaned.substring(lastSlashIndex + 1);
        }

        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (!StringUtils.hasText(cleaned) || cleaned.contains("..")) {
            throw new ArchivoStorageException("El nombre guardado del archivo es invalido");
        }

        return cleaned;
    }
}
