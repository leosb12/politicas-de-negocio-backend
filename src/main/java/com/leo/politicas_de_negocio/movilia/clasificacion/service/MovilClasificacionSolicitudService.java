package com.leo.politicas_de_negocio.movilia.clasificacion.service;

import com.leo.politicas_de_negocio.movilia.clasificacion.client.IaClasificacionClient;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.ClasificarSolicitudMovilRequest;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.ClasificarSolicitudMovilResponse;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.IaClasificacionRequest;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.IaClasificacionResponse;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.PoliticaClasificacionDto;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.RequisitoInicialDto;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.TopResultadoClasificacionDto;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovilClasificacionSolicitudService {

    private static final String CANAL_MOVIL = "MOVIL";

    private final PoliticaNegocioRepository politicaRepository;
    private final IaClasificacionClient iaClasificacionClient;

    public ClasificarSolicitudMovilResponse clasificar(String usuarioMovilId, ClasificarSolicitudMovilRequest request) {
        validarUsuario(usuarioMovilId);
        String texto = request != null ? request.getTexto() : null;
        String nombreDocumento = request != null ? request.getNombreDocumento() : null;

        if ((texto == null || texto.trim().isEmpty()) && (nombreDocumento == null || nombreDocumento.trim().isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe ingresar una descripción de su necesidad o subir un documento");
        }

        if (texto == null || texto.trim().isEmpty()) {
            texto = "Documento: " + nombreDocumento;
        } else {
            texto = texto.trim();
        }

        Boolean usarDeepSeek = request != null && Boolean.TRUE.equals(request.getUsarDeepSeek());

        List<PoliticaNegocio> politicasActivas = politicaRepository.findByEstado(EstadoPolitica.ACTIVA);
        if (politicasActivas.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No existen politicas activas para clasificar la solicitud");
        }

        IaClasificacionResponse iaResponse = iaClasificacionClient.clasificar(IaClasificacionRequest.builder()
                .texto(texto)
                .canal(CANAL_MOVIL)
                .politicas(politicasActivas.stream().map(this::mapearPolitica).toList())
                .usarDeepSeek(usarDeepSeek)
                .nombreDocumento(nombreDocumento)
                .build());

        if (iaResponse == null || iaResponse.getPoliticaId() == null || iaResponse.getPoliticaId().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IA_DEEP_LEARNING_NO_DISPONIBLE");
        }

        Map<String, PoliticaNegocio> politicasPorId = politicasActivas.stream()
                .filter(politica -> politica.getId() != null)
                .collect(Collectors.toMap(PoliticaNegocio::getId, Function.identity(), (a, b) -> a));

        PoliticaNegocio politicaSugerida = Optional.ofNullable(politicasPorId.get(iaResponse.getPoliticaId()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "La politica sugerida por IA no corresponde a una politica activa de MongoDB"
                ));

        String nombrePolitica = politicaSugerida.getNombre();
        return ClasificarSolicitudMovilResponse.builder()
                .politicaId(politicaSugerida.getId())
                .nombrePolitica(nombrePolitica)
                .descripcionPolitica(politicaSugerida.getDescripcion())
                .confianza(iaResponse.getConfianza())
                .origen(iaResponse.getOrigen())
                .metodoRecomendacion(iaResponse.getMetodoRecomendacion())
                .requiereMasInformacion(Boolean.TRUE.equals(iaResponse.getRequiereMasInformacion()))
                .requiereConfirmacion(true)
                .mensaje("Detectamos que tu solicitud corresponde a " + nombrePolitica + ". Confirma si deseas continuar.")
                .requisitosDetectados(iaResponse.getRequisitosDetectados())
                .requisitosCoincidentes(iaResponse.getRequisitosCoincidentes())
                .requisitosFaltantes(iaResponse.getRequisitosFaltantes())
                .topResultados(mapearTopResultados(iaResponse.getTopResultados()))
                .build();
    }

    private String normalizarTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El texto de la solicitud es obligatorio");
        }
        return texto.trim();
    }

    private void validarUsuario(String usuarioMovilId) {
        if (usuarioMovilId == null || usuarioMovilId.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id");
        }
    }

    private PoliticaClasificacionDto mapearPolitica(PoliticaNegocio politica) {
        List<RequisitoInicialDto> reqs = politica.getRequisitosIniciales() != null
                ? politica.getRequisitosIniciales().stream()
                .map(req -> RequisitoInicialDto.builder()
                        .nombre(req.getCampo())
                        .label(req.getEtiqueta())
                        .tipo(req.getTipoRaw())
                        .obligatorio(Boolean.TRUE.equals(req.getRequerido()))
                        .build())
                .toList()
                : Collections.emptyList();

        return PoliticaClasificacionDto.builder()
                .id(politica.getId())
                .nombre(politica.getNombre())
                .descripcion(politica.getDescripcion())
                .categoria(politica.getCategoria())
                .descripcionClasificacion(politica.getDescripcionClasificacion())
                .palabrasClave(listaSegura(politica.getPalabrasClave()))
                .intencionesEjemplo(listaSegura(politica.getIntencionesEjemplo()))
                .requisitosSugeridos(listaSegura(politica.getRequisitosSugeridos()))
                .requisitosIniciales(reqs)
                .build();
    }

    private List<TopResultadoClasificacionDto> mapearTopResultados(List<TopResultadoClasificacionDto> topResultados) {
        if (topResultados == null) {
            return Collections.emptyList();
        }
        return topResultados;
    }

    private List<String> listaSegura(List<String> valores) {
        return valores != null ? valores : Collections.emptyList();
    }
}
