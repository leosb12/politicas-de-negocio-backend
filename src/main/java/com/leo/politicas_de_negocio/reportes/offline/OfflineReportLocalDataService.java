package com.leo.politicas_de_negocio.reportes.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class OfflineReportLocalDataService {

    private final Path snapshotFilePath;
    private final ObjectMapper objectMapper;

    public OfflineReportLocalDataService(@Value("${offline.reports.cache.path:./offline-cache/reportes}") String cachePath) {
        this.snapshotFilePath = Paths.get(cachePath).resolve("data_snapshot.json").toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        log.info("OfflineReportLocalDataService inicializado. Ruta del snapshot: {}", this.snapshotFilePath);
    }

    public synchronized void saveSnapshot(Map<String, Object> snapshot) {
        try {
            File parentDir = snapshotFilePath.getParent().toFile();
            if (!parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                log.info("Directorio de cache creado: {} (Status: {})", parentDir, created);
            }
            objectMapper.writeValue(snapshotFilePath.toFile(), snapshot);
            log.info("Snapshot guardado exitosamente en: {} (Tamaño: {} bytes)", snapshotFilePath, Files.size(snapshotFilePath));
        } catch (IOException e) {
            log.error("Error al guardar el snapshot local offline: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo escribir el archivo de caché offline.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> loadSnapshot() {
        try {
            if (!Files.exists(snapshotFilePath)) {
                log.warn("El archivo de snapshot offline no existe en: {}", snapshotFilePath);
                return new HashMap<>();
            }
            return objectMapper.readValue(snapshotFilePath.toFile(), Map.class);
        } catch (IOException e) {
            log.error("Error al cargar el snapshot local offline de: {}", snapshotFilePath, e);
            return new HashMap<>();
        }
    }
}
