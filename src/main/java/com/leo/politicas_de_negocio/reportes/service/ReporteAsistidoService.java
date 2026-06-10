package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteAsistidoService {

    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final RestTemplate restTemplate;

    @Value("${app.ia.deep-learning-url:http://localhost:8010}")
    private String iaServiceUrl;

    // Cache to store previous simulated results for identical or similar requests
    private final Map<String, PreviewResponseDto> cacheSimulacion = new java.util.concurrent.ConcurrentHashMap<>();

    // Memory to maintain the same base records (rows) for an entity when columns
    // change
    private final Map<String, List<Map<String, Object>>> memoriaEntidades = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public PreviewResponseDto generarVistaAsistida(
            String textoOriginal,
            ReporteResponseDto interpretacion,
            String diagnostico,
            List<String> columnasEsperadas,
            List<Map<String, Object>> datosRealesParciales) {
        log.info("Iniciando generación de vista asistida (IA+) para consulta: '{}'", textoOriginal);

        String cacheKey = textoOriginal != null ? textoOriginal.trim().toLowerCase() : "";

        if (cacheSimulacion.containsKey(cacheKey)) {
            log.info("Devolviendo resultado IA+ desde caché temporal para la consulta exacta: {}", cacheKey);
            return cacheSimulacion.get(cacheKey);
        }

        String entidad = interpretacion != null && interpretacion.getEntidadPrincipal() != null
                ? interpretacion.getEntidadPrincipal().toLowerCase()
                : "desconocido";

        try {
            // 1. Obtener listas reales
            List<Usuario> todosUsuarios = usuarioRepository.findAll();
            List<String> usuariosReales = todosUsuarios.stream()
                    .filter(u -> "USUARIO".equalsIgnoreCase(u.getRol()))
                    .map(Usuario::getNombre)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<String> funcionariosReales = todosUsuarios.stream()
                    .filter(u -> "FUNCIONARIO".equalsIgnoreCase(u.getRol()))
                    .map(Usuario::getNombre)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<String> administradoresReales = todosUsuarios.stream()
                    .filter(u -> "ADMIN".equalsIgnoreCase(u.getRol()))
                    .map(Usuario::getNombre)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<PoliticaNegocio> todasPoliticas = politicaNegocioRepository.findAll();
            List<String> politicasReales = todasPoliticas.stream()
                    .map(PoliticaNegocio::getNombre)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            List<String> departamentosReales = departamentoRepository.findAll().stream()
                    .map(Departamento::getNombre)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            // Nodos
            List<String> nombresNodosReales = todasPoliticas.stream()
                    .filter(p -> p.getNodos() != null)
                    .flatMap(p -> p.getNodos().stream())
                    .map(Nodo::getNombre)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            // Estados (Instancia y Tarea)
            List<String> estadosReales = new ArrayList<>();
            for (EstadoInstancia e : EstadoInstancia.values()) {
                estadosReales.add(e.name());
            }
            for (EstadoTarea e : EstadoTarea.values()) {
                estadosReales.add(e.name());
            }
            estadosReales = estadosReales.stream().distinct().toList();

            // 2. Construir payload para el Python service
            Map<String, Object> payload = new HashMap<>();
            payload.put("preguntaOriginal", textoOriginal);
            payload.put("columnasEsperadas", columnasEsperadas != null ? columnasEsperadas
                    : (interpretacion.getCampos() != null ? interpretacion.getCampos() : Collections.emptyList()));
            payload.put("diagnosticoMotorReal",
                    diagnostico != null ? diagnostico : "No se encontraron resultados reales.");
            payload.put("datosRealesDisponibles",
                    datosRealesParciales != null ? datosRealesParciales : Collections.emptyList());
            payload.put("datosSimuladosPrevios", memoriaEntidades.getOrDefault(entidad, Collections.emptyList()));
            payload.put("usuariosReales", usuariosReales);
            payload.put("funcionariosReales", funcionariosReales);
            payload.put("administradoresReales", administradoresReales);
            payload.put("politicasReales", politicasReales);
            payload.put("departamentosReales", departamentosReales);
            payload.put("estadosReales", estadosReales);
            payload.put("nombresNodosReales", nombresNodosReales);

            // 3. Llamar al Python service
            String url = iaServiceUrl + "/api/ia/reportes/asistencia-extendida";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            log.info("Llamando a microservicio de asistencia extendida en url: {}", url);
            Map responseMap = restTemplate.postForObject(url, entity, Map.class);

            if (responseMap == null) {
                throw new RuntimeException("Respuesta vacía del servicio de asistencia extendida.");
            }

            // 4. Mapear respuesta
            PreviewResponseDto response = new PreviewResponseDto();
            response.setInterpretacion(interpretacion);
            response.setAsistido(true);

            List<String> columnas = (List<String>) responseMap.get("columnas");
            List<Map<String, Object>> filas = (List<Map<String, Object>>) responseMap.get("filas");

            response.setColumnas(columnas != null ? columnas : Collections.emptyList());
            response.setFilas(filas != null ? filas : Collections.emptyList());
            response.setTotal(filas != null ? filas.size() : 0);

            // Guardar en las cachés de memoria para preservar estado consistente
            cacheSimulacion.put(cacheKey, response);
            if (filas != null && !filas.isEmpty()) {
                memoriaEntidades.put(entidad, filas);
            }

            return response;
        } catch (Exception e) {
            log.error("Error al generar vista asistida (IA+): ", e);
            PreviewResponseDto fallback = new PreviewResponseDto();
            fallback.setInterpretacion(interpretacion);
            fallback.setAsistido(true);
            fallback.setError("FALLBACK_ASISTIDO_ERROR");
            fallback.setMensaje("Error al conectar con la asistencia extendida IA+.");
            fallback.setFilas(new ArrayList<>());
            fallback.setColumnas(columnasEsperadas != null ? columnasEsperadas : Collections.emptyList());
            fallback.setTotal(0);
            return fallback;
        }
    }
}
