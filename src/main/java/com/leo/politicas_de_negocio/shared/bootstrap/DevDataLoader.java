package com.leo.politicas_de_negocio.shared.bootstrap;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("default")
public class DevDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    private static final String DEMO_USER_EMAIL = "demo@local.test";
    private static final String DEMO_USER_PASSWORD = "123456";
    private static final String DEMO_USER_NAME = "Usuario Demo";
    private static final String DEMO_POLITICA_ID = "demo-tramite";

    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioRepository politicaRepository;

    public DevDataLoader(UsuarioRepository usuarioRepository, PoliticaNegocioRepository politicaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.politicaRepository = politicaRepository;
    }

    @Override
    public void run(String... args) {
        seedDemoUserIfNeeded();
        seedDemoPoliticaIfNeeded();
    }

    private void seedDemoUserIfNeeded() {
        if (usuarioRepository.existsByCorreo(DEMO_USER_EMAIL)) {
            return;
        }

        Usuario demoUser = Usuario.builder()
                .nombre(DEMO_USER_NAME)
                .correo(DEMO_USER_EMAIL)
                .password(DEMO_USER_PASSWORD)
                .rol("USUARIO")
                .departamentoId(null)
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();

        usuarioRepository.save(demoUser);
        log.info("Seeded demo mobile user: {} / {}", DEMO_USER_EMAIL, DEMO_USER_PASSWORD);
    }

    private void seedDemoPoliticaIfNeeded() {
        if (!politicaRepository.findByEstado(EstadoPolitica.ACTIVA).isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Nodo inicio = Nodo.builder()
                .id("inicio-demo")
                .tipo(TipoNodo.INICIO)
                .nombre("Inicio")
                .version(1L)
                .fechaActualizacion(now)
                .build();

        Nodo actividad = Nodo.builder()
                .id("actividad-demo")
                .tipo(TipoNodo.ACTIVIDAD)
                .nombre("Completar solicitud")
                .responsableTipo("USUARIO")
                .responsableId("__RESPONSABLE_INICIADOR_TRAMITE__")
                .version(1L)
                .fechaActualizacion(now)
                .build();

        Nodo fin = Nodo.builder()
                .id("fin-demo")
                .tipo(TipoNodo.FIN)
                .nombre("Fin")
                .version(1L)
                .fechaActualizacion(now)
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id(DEMO_POLITICA_ID)
                .nombre("Tramite demo")
                .descripcion("Tramite de ejemplo para desarrollo local")
                .estado(EstadoPolitica.ACTIVA)
                .fueActivada(true)
                .nodos(List.of(inicio, actividad, fin))
                .conexiones(List.of(
                        Conexion.builder().origen("inicio-demo").destino("actividad-demo").build(),
                        Conexion.builder().origen("actividad-demo").destino("fin-demo").build()
                ))
                .laneOrientation("VERTICAL")
                .laneWidth(320d)
                .laneHeight(220d)
                .secuenciaColaboracion(0L)
                .fechaUltimaColaboracion(now)
                .fechaCreacion(now)
                .fechaActualizacion(now)
                .build();

        politicaRepository.save(politica);
        log.info("Seeded demo active politica: {}", DEMO_POLITICA_ID);
    }
}