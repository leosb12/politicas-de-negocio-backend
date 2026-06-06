package com.leo.politicas_de_negocio.shared.bootstrap;

import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.seed.politicas-wifi", name = "enabled", havingValue = "true")
public class WifiPoliticasSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WifiPoliticasSeedRunner.class);
    private static final int DEFINICIONES_ESPERADAS = 21;

    private static final List<WifiPoliticaDefinition> DEFINICIONES = List.of(
            new WifiPoliticaDefinition(
                    "Solicitar instalacion de internet WiFi",
                    "Permite al cliente solicitar la instalacion de un nuevo servicio de internet WiFi en su domicilio, oficina o negocio.",
                    "Contratacion de servicio",
                    List.of("instalar internet", "contratar wifi", "nuevo servicio", "instalacion", "internet hogar"),
                    List.of(
                            "Quiero contratar internet para mi casa",
                            "Necesito instalar WiFi",
                            "Quiero solicitar un nuevo servicio de internet"
                    ),
                    List.of("direccion", "zona", "telefono de contacto", "disponibilidad horaria")
            ),
            new WifiPoliticaDefinition(
                    "Cambiar plan de internet",
                    "Permite al cliente cambiar su plan actual de internet por uno de mayor o menor velocidad segun sus necesidades.",
                    "Gestion de plan",
                    List.of("cambiar plan", "subir plan", "bajar plan", "mejorar internet", "velocidad"),
                    List.of(
                            "Quiero cambiar mi plan de internet",
                            "Necesito mas velocidad",
                            "Quiero bajar mi plan porque pago mucho"
                    ),
                    List.of("plan actual", "plan deseado", "motivo")
            ),
            new WifiPoliticaDefinition(
                    "Mejorar velocidad de internet",
                    "Permite solicitar una mejora de velocidad cuando el cliente necesita mayor capacidad de navegacion, streaming, trabajo remoto o videojuegos.",
                    "Gestion de plan",
                    List.of("mejorar velocidad", "internet lento", "mas megas", "mayor velocidad", "upgrade"),
                    List.of(
                            "Mi internet esta lento",
                            "Quiero mas megas",
                            "Necesito mejorar la velocidad de mi WiFi"
                    ),
                    List.of("plan actual", "plan deseado", "motivo")
            ),
            new WifiPoliticaDefinition(
                    "Reportar internet caido",
                    "Permite reportar la falta total de conexion a internet en el domicilio o empresa del cliente.",
                    "Soporte tecnico",
                    List.of("sin internet", "internet caido", "no tengo conexion", "falla de servicio"),
                    List.of(
                            "No tengo internet",
                            "Se cayo mi conexion",
                            "Mi WiFi no funciona"
                    ),
                    List.of("descripcion del problema", "evidencia opcional", "horario disponible")
            ),
            new WifiPoliticaDefinition(
                    "Reportar internet lento",
                    "Permite reportar problemas de lentitud, baja velocidad o navegacion inestable en el servicio de internet.",
                    "Soporte tecnico",
                    List.of("internet lento", "baja velocidad", "conexion lenta", "lag", "se traba"),
                    List.of(
                            "Mi internet esta muy lento",
                            "La conexion se traba mucho",
                            "El WiFi anda lento"
                    ),
                    List.of("descripcion del problema", "evidencia opcional", "horario disponible")
            ),
            new WifiPoliticaDefinition(
                    "Cambiar contrasena del WiFi",
                    "Permite solicitar el cambio de contrasena de la red WiFi del cliente por seguridad o preferencia personal.",
                    "Configuracion de red",
                    List.of("contrasena wifi", "clave wifi", "cambiar contrasena", "password", "seguridad wifi"),
                    List.of(
                            "Quiero cambiar la contrasena del WiFi",
                            "Necesito cambiar mi clave",
                            "Alguien tiene mi contrasena de internet"
                    ),
                    List.of("descripcion del cambio solicitado", "horario disponible")
            ),
            new WifiPoliticaDefinition(
                    "Cambiar nombre de la red WiFi",
                    "Permite modificar el nombre visible de la red WiFi, tambien conocido como SSID.",
                    "Configuracion de red",
                    List.of("nombre wifi", "cambiar red", "SSID", "nombre de la red", "renombrar wifi"),
                    List.of(
                            "Quiero cambiar el nombre de mi WiFi",
                            "Necesito modificar el nombre de la red",
                            "Quiero ponerle otro nombre a mi internet"
                    ),
                    List.of("nombre actual de red", "nombre nuevo de red", "horario disponible")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar visita tecnica",
                    "Permite al cliente pedir que un tecnico revise presencialmente el modem, router, cableado o instalacion.",
                    "Soporte tecnico",
                    List.of("visita tecnica", "tecnico", "revision", "domicilio", "soporte presencial"),
                    List.of(
                            "Necesito que venga un tecnico",
                            "Quiero agendar una visita tecnica",
                            "Necesito que revisen mi instalacion"
                    ),
                    List.of("descripcion del problema", "evidencia opcional", "horario disponible")
            ),
            new WifiPoliticaDefinition(
                    "Reprogramar visita tecnica",
                    "Permite cambiar la fecha u horario de una visita tecnica previamente agendada.",
                    "Soporte tecnico",
                    List.of("reprogramar visita", "cambiar cita", "cambiar horario", "tecnico", "agenda"),
                    List.of(
                            "Quiero cambiar la fecha del tecnico",
                            "No puedo recibir al tecnico hoy",
                            "Necesito reprogramar mi visita tecnica"
                    ),
                    List.of("codigo o referencia de visita", "fecha solicitada", "horario disponible", "motivo")
            ),
            new WifiPoliticaDefinition(
                    "Consultar estado de instalacion",
                    "Permite consultar el avance de una solicitud de instalacion de internet o activacion de servicio.",
                    "Consulta de tramite",
                    List.of("estado instalacion", "avance", "seguimiento", "activacion", "instalacion pendiente"),
                    List.of(
                            "Quiero saber como va mi instalacion",
                            "Cuando van a instalar mi internet",
                            "Necesito consultar el estado de mi solicitud"
                    ),
                    List.of("numero de solicitud", "direccion", "telefono de contacto")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar traslado de servicio",
                    "Permite solicitar el traslado del servicio de internet a una nueva direccion por mudanza o cambio de domicilio.",
                    "Gestion de servicio",
                    List.of("mudanza", "traslado", "cambiar direccion", "nuevo domicilio", "mover servicio"),
                    List.of(
                            "Me estoy mudando y quiero llevar mi internet",
                            "Necesito cambiar la direccion del servicio",
                            "Quiero trasladar mi WiFi a otra casa"
                    ),
                    List.of("motivo", "fecha solicitada", "direccion nueva", "zona", "telefono de contacto")
            ),
            new WifiPoliticaDefinition(
                    "Actualizar datos del titular",
                    "Permite actualizar los datos personales, telefono, correo, documento o informacion de contacto del titular del servicio.",
                    "Gestion de cliente",
                    List.of("actualizar datos", "cambiar correo", "cambiar telefono", "titular", "informacion personal"),
                    List.of(
                            "Quiero actualizar mis datos",
                            "Cambie mi numero de telefono",
                            "Necesito modificar mi correo"
                    ),
                    List.of("datos actuales", "datos nuevos", "documento de identidad")
            ),
            new WifiPoliticaDefinition(
                    "Cambiar titular del servicio",
                    "Permite solicitar el cambio de titularidad del servicio de internet hacia otra persona autorizada.",
                    "Gestion de cliente",
                    List.of("cambio de titular", "titularidad", "transferir servicio", "nuevo titular"),
                    List.of(
                            "Quiero cambiar el titular del servicio",
                            "Necesito poner el internet a nombre de otra persona",
                            "Quiero transferir mi servicio"
                    ),
                    List.of("datos del titular actual", "datos del nuevo titular", "documento de identidad", "motivo")
            ),
            new WifiPoliticaDefinition(
                    "Consultar factura",
                    "Permite consultar el detalle de una factura emitida, monto a pagar, periodo facturado y fecha de vencimiento.",
                    "Facturacion",
                    List.of("factura", "boleta", "monto", "vencimiento", "detalle de pago"),
                    List.of(
                            "Quiero ver mi factura",
                            "Necesito saber cuanto debo pagar",
                            "Donde veo mi boleta"
                    ),
                    List.of("numero de factura", "periodo")
            ),
            new WifiPoliticaDefinition(
                    "Reclamar cobro incorrecto",
                    "Permite registrar un reclamo cuando el cliente considera que se le cobro de mas, se aplico un cargo incorrecto o no reconoce un importe.",
                    "Facturacion",
                    List.of("cobro incorrecto", "me cobraron de mas", "reclamo factura", "cargo desconocido"),
                    List.of(
                            "Me cobraron de mas",
                            "Mi factura esta mal",
                            "No reconozco este cobro"
                    ),
                    List.of("numero de factura", "periodo", "comprobante si corresponde", "descripcion del reclamo")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar duplicado de factura",
                    "Permite solicitar una copia o duplicado de una factura emitida anteriormente.",
                    "Facturacion",
                    List.of("duplicado factura", "copia factura", "descargar factura", "reenviar boleta"),
                    List.of(
                            "Necesito una copia de mi factura",
                            "Quiero descargar mi boleta",
                            "Pueden reenviarme la factura"
                    ),
                    List.of("numero de factura", "periodo", "correo de envio")
            ),
            new WifiPoliticaDefinition(
                    "Registrar comprobante de pago",
                    "Permite subir o registrar un comprobante de pago realizado para validar la cancelacion de una factura pendiente.",
                    "Pagos",
                    List.of("comprobante de pago", "subir pago", "registrar pago", "pague factura"),
                    List.of(
                            "Ya pague y quiero subir el comprobante",
                            "Necesito registrar mi pago",
                            "Quiero adjuntar el comprobante"
                    ),
                    List.of("comprobante", "fecha de pago", "medio de pago")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar reconexion de servicio",
                    "Permite solicitar la reconexion del servicio de internet luego de una suspension por deuda, corte tecnico o baja temporal.",
                    "Gestion de servicio",
                    List.of("reconexion", "reactivar internet", "servicio cortado", "suspension", "volver a conectar"),
                    List.of(
                            "Quiero que me reconecten el internet",
                            "Ya pague y sigo sin servicio",
                            "Necesito reactivar mi conexion"
                    ),
                    List.of("motivo", "fecha solicitada", "comprobante si corresponde", "telefono de contacto")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar baja del servicio",
                    "Permite solicitar la cancelacion definitiva del servicio de internet contratado.",
                    "Gestion de servicio",
                    List.of("baja de servicio", "cancelar internet", "dar de baja", "cancelar plan"),
                    List.of(
                            "Quiero cancelar mi internet",
                            "Necesito dar de baja el servicio",
                            "Ya no quiero continuar con el plan"
                    ),
                    List.of("motivo", "fecha solicitada")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar soporte por modem o router",
                    "Permite reportar problemas fisicos o de configuracion relacionados con el modem, router, luces del equipo, reinicios o fallas del dispositivo.",
                    "Soporte tecnico",
                    List.of("modem", "router", "luces", "equipo", "reinicio", "falla router"),
                    List.of(
                            "Mi router no prende",
                            "El modem tiene una luz roja",
                            "Mi equipo se reinicia solo"
                    ),
                    List.of("descripcion del problema", "evidencia opcional", "horario disponible")
            ),
            new WifiPoliticaDefinition(
                    "Solicitar cambio de equipo",
                    "Permite solicitar el reemplazo del modem o router cuando el equipo esta danado, es antiguo o no cumple con las necesidades del cliente.",
                    "Soporte tecnico",
                    List.of("cambiar modem", "cambiar router", "equipo danado", "reemplazo de equipo"),
                    List.of(
                            "Quiero cambiar mi modem",
                            "Mi router esta viejo",
                            "Necesito que me reemplacen el equipo"
                    ),
                    List.of("descripcion del problema", "evidencia opcional", "horario disponible")
            )
    );

    private final PoliticaNegocioRepository politicaRepository;

    public WifiPoliticasSeedRunner(PoliticaNegocioRepository politicaRepository) {
        this.politicaRepository = politicaRepository;
    }

    @Override
    public void run(String... args) {
        List<PoliticaNegocio> politicas = politicaRepository.findAll().stream()
                .sorted(stablePolicyComparator())
                .toList();

        log.info("Seed WiFi CU-35 habilitado. Politicas encontradas en MongoDB: {}", politicas.size());

        if (politicas.size() < DEFINICIONES_ESPERADAS) {
            log.warn("Seed WiFi CU-35 encontro menos de {} politicas. Se actualizaran solo {} politicas existentes.",
                    DEFINICIONES_ESPERADAS, politicas.size());
        } else if (politicas.size() > DEFINICIONES_ESPERADAS) {
            log.warn("Seed WiFi CU-35 encontro mas de {} politicas. Se actualizaran solo las primeras {} por orden estable.",
                    DEFINICIONES_ESPERADAS, DEFINICIONES_ESPERADAS);
        }

        int total = Math.min(politicas.size(), DEFINICIONES.size());
        for (int index = 0; index < total; index++) {
            actualizarPolitica(politicas.get(index), DEFINICIONES.get(index), index + 1);
        }

        log.info("Seed WiFi CU-35 finalizado. Politicas actualizadas: {}", total);
    }

    private void actualizarPolitica(PoliticaNegocio politica, WifiPoliticaDefinition definition, int posicion) {
        String nombreAnterior = politica.getNombre();
        boolean nombreAnteriorReal = pareceNombreReal(nombreAnterior);

        politica.setNombre(definition.nombre());
        politica.setDescripcion(definition.descripcion());
        politica.setCategoria(definition.categoria());
        politica.setDescripcionClasificacion(definition.descripcion());
        politica.setPalabrasClave(definition.palabrasClave());
        politica.setIntencionesEjemplo(definition.intencionesEjemplo());
        politica.setRequisitosSugeridos(definition.requisitosSugeridos());
        politica.setFechaActualizacion(LocalDateTime.now());

        politicaRepository.save(politica);

        if (nombreAnteriorReal) {
            log.warn("Seed WiFi CU-35 sobrescribio una politica con nombre aparentemente real. posicion={}, id={}, nombreAnterior='{}', nombreNuevo='{}'",
                    posicion, politica.getId(), nombreAnterior, definition.nombre());
        }

        log.info("Seed WiFi CU-35 actualizo politica. posicion={}, id={}, nombreAnterior='{}', nombreNuevo='{}', camposActualizados={}",
                posicion,
                politica.getId(),
                nombreAnterior,
                definition.nombre(),
                List.of("nombre", "descripcion", "categoria", "descripcionClasificacion", "palabrasClave",
                        "intencionesEjemplo", "requisitosSugeridos", "fechaActualizacion"));
    }

    private Comparator<PoliticaNegocio> stablePolicyComparator() {
        Comparator<LocalDateTime> nullSafeDate = Comparator.nullsLast(Comparator.naturalOrder());
        Comparator<String> nullSafeText = Comparator.nullsLast(Comparator.naturalOrder());

        return Comparator
                .comparing(PoliticaNegocio::getFechaCreacion, nullSafeDate)
                .thenComparing(PoliticaNegocio::getId, nullSafeText);
    }

    private boolean pareceNombreReal(String nombre) {
        String normalized = normalize(nombre);
        if (normalized == null) {
            return false;
        }

        return !(normalized.matches("politica\\s*\\d+")
                || normalized.matches("politica")
                || normalized.matches("test\\s*\\d*")
                || normalized.matches("demo\\s*\\d*")
                || normalized.matches("tramite\\s*demo")
                || normalized.matches("tramite\\s*con\\s*pago\\s*demo")
                || normalized.matches("prueba\\s*\\d*"));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private record WifiPoliticaDefinition(
            String nombre,
            String descripcion,
            String categoria,
            List<String> palabrasClave,
            List<String> intencionesEjemplo,
            List<String> requisitosSugeridos
    ) {
    }
}
