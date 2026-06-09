package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.CatalogoResumidoResponse;
import com.leo.politicas_de_negocio.reportes.dto.CoberturaResponse;
import com.leo.politicas_de_negocio.reportes.model.CampoReportable;
import com.leo.politicas_de_negocio.reportes.model.EntidadReportable;
import com.leo.politicas_de_negocio.reportes.model.RelacionReportable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReporteCatalogoService {

    private final Map<String, EntidadReportable> catalogoMaestro = new LinkedHashMap<>();
    
    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void init() {
        // 1. Instancias de política / trámites
        catalogoMaestro.put("instancias_politica", EntidadReportable.builder()
                .nombreLogico("instancias_politica")
                .coleccionMongo("instancias_politica")
                .claseJava("InstanciaPolitica")
                .descripcion("Ejecución real de una política/workflow iniciada por un usuario.")
                .aliases(Arrays.asList("tramite", "tramites", "solicitud", "solicitudes", "instancia", "instancias", "caso", "expediente", "gestion", "proceso iniciado", "trámite iniciado", "trámites iniciados", "solicitud iniciada", "solicitudes iniciadas", "política iniciada", "workflow iniciado", "flujo iniciado"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList("id", "identificador"), true, true, true, false, false, false, null),
                        crearCampo("codigoTramite", "codigoTramite", "codigoTramite", "string", "Código del trámite", Arrays.asList("codigo"), true, true, false, false, false, false, null),
                        crearCampo("estadoInstancia", "estadoInstancia", "estadoInstancia", "string", "Estado", Arrays.asList("estado", "situacion", "etapa"), true, true, true, false, false, false, Arrays.asList("EN_CURSO", "FINALIZADA", "RECHAZADA", "PENDIENTE_PAGO")),
                        crearCampo("fechaCreacion", "fechaCreacion", "fechaCreacion", "date", "Fecha de creación", Arrays.asList("fecha", "fecha de inicio", "creado", "iniciado"), true, true, true, true, false, false, null),
                        crearCampo("fechaFinalizacion", "fechaFinalizacion", "fechaFinalizacion", "date", "Fecha finalización", Arrays.asList("fecha fin", "terminado"), true, true, true, true, false, false, null),
                        crearCampo("creadaPor", "creadaPor", "creadaPor", "string", "Usuario creador", Arrays.asList("creador", "usuario", "cliente", "solicitante", "iniciador", "quien inicio", "quien inició", "nombre de quien inicio", "nombre de quien inició", "usuario que inicio", "usuario que inició", "cliente que inicio", "cliente que inició", "nombre del usuario", "nombre del cliente", "nombre solicitante"), true, true, true, false, false, true, null),
                        crearCampo("politicaId", "politicaId", "politicaId", "string", "Política de negocio", Arrays.asList("politica", "flujo", "tipo de tramite", "nombre de la politica", "nombre politica"), true, true, true, false, false, true, null),
                        crearCampo("departamentoId", "departamentoId", "departamentoId", "string", "Departamento asignado", Arrays.asList("departamento", "area", "oficina"), true, true, true, false, false, true, null),
                        crearCampo("funcionarioAsignado", "funcionarioAsignado", "funcionarioAsignado", "string", "Funcionario", Arrays.asList("funcionario", "responsable", "asignado", "nombre del funcionario", "funcionario nombre"), true, true, true, false, false, true, null),
                        crearCampo("requierePago", "requierePago", "requierePago", "boolean", "Requiere Pago", Arrays.asList("pagable"), true, true, true, false, false, false, null),
                        crearCampo("prioridad", "prioridad", "prioridad", "string", "Prioridad", Arrays.asList("urgencia"), true, true, true, false, false, false, null),
                        // Campos Anidados (Arrays con requiereUnwind)
                        crearCampoArray("requisitosInicialesDefinicion.nombre", "requisitosInicialesDefinicion.nombre", "requisitosInicialesDefinicion.nombre", "string", "Nombre Requisito Inicial", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("requisitosInicialesDefinicion.tipo", "requisitosInicialesDefinicion.tipo", "requisitosInicialesDefinicion.tipo", "string", "Tipo Requisito Inicial", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("requisitosInicialesDefinicion.obligatorio", "requisitosInicialesDefinicion.obligatorio", "requisitosInicialesDefinicion.obligatorio", "boolean", "Requisito Obligatorio", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("formularios.campos.nombre", "formularios.campos.nombre", "formularios.campos.nombre", "string", "Nombre Campo Formulario", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("formularios.campos.tipo", "formularios.campos.tipo", "formularios.campos.tipo", "string", "Tipo Campo Formulario", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("respuestasFormulario.campo", "respuestasFormulario.campo", "respuestasFormulario.campo", "string", "Campo Respuesta", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("respuestasFormulario.valor", "respuestasFormulario.valor", "respuestasFormulario.valor", "string", "Valor Respuesta", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("respuestasRequisitosIniciales", "respuestasRequisitosIniciales", "respuestasRequisitosIniciales", "object", "Respuestas Requisitos", Arrays.asList(), true, false, false, false, false, false, null)
                ))

                .relaciones(Arrays.asList(
                        crearRelacion("usuario", "instancias_politica", "usuarios", "creadaPor", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "correo")),
                        crearRelacion("politica", "instancias_politica", "politicas_negocio", "politicaId", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "categoria")),
                        crearRelacion("departamento", "instancias_politica", "departamentos", "departamentoId", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre")),
                        crearRelacion("funcionario", "instancias_politica", "usuarios", "funcionarioAsignado", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "correo"))
                ))
                .build());

        // 2. Políticas de negocio
        catalogoMaestro.put("politicas_negocio", EntidadReportable.builder()
                .nombreLogico("politicas_negocio")
                .coleccionMongo("politicas_negocio")
                .claseJava("PoliticaNegocio")
                .descripcion("Definición completa de un workflow creado por el administrador. Contiene nodos o tareas internas del flujo.")
                .aliases(Arrays.asList("politica", "politicas", "flujo", "flujos", "procesos", "proceso", "tipo de tramite", "workflow", "workflows", "trámite configurado", "procedimiento"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("nombre", "nombre", "nombre", "string", "Nombre", Arrays.asList("titulo", "politica"), true, true, true, true, false, false, null),
                        crearCampo("categoria", "categoria", "categoria", "string", "Categoría", Arrays.asList("tipo"), true, true, true, false, false, false, null),
                        crearCampo("estado", "estado", "estado", "string", "Estado", Arrays.asList("activo"), true, true, true, false, false, false, Arrays.asList("ACTIVA", "INACTIVA")),
                        crearCampo("requierePago", "requierePago", "requierePago", "boolean", "Requiere pago", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("fechaCreacion", "fechaCreacion", "fechaCreacion", "date", "Fecha creación", Arrays.asList(), true, true, true, true, false, false, null),
                        crearCampoArray("requisitosIniciales.nombre", "requisitosIniciales.nombre", "requisitosIniciales.nombre", "string", "Nombre Requisito", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("requisitosIniciales.tipo", "requisitosIniciales.tipo", "requisitosIniciales.tipo", "string", "Tipo Requisito", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("requisitosIniciales.obligatorio", "requisitosIniciales.obligatorio", "requisitosIniciales.obligatorio", "boolean", "Requisito Obligatorio", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("formularios", "formularios", "formularios", "object", "Formularios", Arrays.asList(), true, false, false, false, false, false, null),
                        crearCampo("pagos", "pagos", "pagos", "object", "Pagos", Arrays.asList(), true, false, false, false, false, false, null),
                        crearCampo("notificaciones", "notificaciones", "notificaciones", "object", "Notificaciones", Arrays.asList(), true, false, false, false, false, false, null),
                        crearCampoArray("nodos.id", "nodos.id", "nodos.id", "string", "ID Nodo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("nodos.nombre", "nodos.nombre", "nodos.nombre", "string", "Nombre Nodo", Arrays.asList("nodo", "nodos", "tarea", "tareas", "paso", "pasos", "etapa", "etapas", "actividad", "actividades", "bloque", "elemento del flujo"), true, true, true, false, false, false, null),
                        crearCampoArray("nodos.tipo", "nodos.tipo", "nodos.tipo", "string", "Tipo Nodo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("nodos.rol", "nodos.responsableTipo", "nodos.responsableTipo", "string", "Rol Nodo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("nodos.departamento", "nodos.departamento", "nodos.departamento", "string", "Departamento Nodo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("nodos.posicion", "nodos.posicion", "nodos.posicion", "number", "Posición Nodo", Arrays.asList(), true, true, true, true, false, false, null),
                        crearCampoArray("nodos.configuracion", "nodos.configuracion", "nodos.configuracion", "object", "Configuración Nodo", Arrays.asList(), false, false, false, false, false, false, null),
                        crearCampoArray("nodos.esInicial", "nodos.esInicial", "nodos.esInicial", "boolean", "Es Nodo Inicial", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoArray("nodos.esFinal", "nodos.esFinal", "nodos.esFinal", "boolean", "Es Nodo Final", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("nodos", "nodos", "nodos", "object", "Nodos", Arrays.asList(), true, false, false, false, false, false, null)
                ))
                .build());

        // 3. Usuarios
        catalogoMaestro.put("usuarios", EntidadReportable.builder()
                .nombreLogico("usuarios")
                .coleccionMongo("usuarios")
                .claseJava("Usuario")
                .descripcion("Usuarios del sistema")
                .aliases(Arrays.asList("usuario", "usuarios", "cliente", "clientes", "solicitante", "funcionario", "persona"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("nombre", "nombre", "nombre", "string", "Nombre", Arrays.asList("usuario", "cliente"), true, true, true, true, false, false, null),
                        crearCampo("correo", "correo", "correo", "string", "Correo", Arrays.asList("email"), true, true, true, true, false, false, null),
                        crearCampo("rol", "rol", "rol", "string", "Rol", Arrays.asList("perfil"), true, true, true, false, false, false, null),
                        crearCampo("departamentoId", "departamentoId", "departamentoId", "string", "Departamento", Arrays.asList("area"), true, true, true, false, false, true, null),
                        crearCampo("activo", "activo", "activo", "boolean", "Activo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampoSensible("password", "Contraseña", "Contiene hashes de seguridad")
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("departamento", "usuarios", "departamentos", "departamentoId", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre"))
                ))
                .build());

        // 4. Departamentos
        catalogoMaestro.put("departamentos", EntidadReportable.builder()
                .nombreLogico("departamentos")
                .coleccionMongo("departamentos")
                .claseJava("Departamento")
                .descripcion("Departamentos o áreas de la empresa")
                .aliases(Arrays.asList("departamento", "departamentos", "area", "areas", "unidad", "seccion", "oficina"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("nombre", "nombre", "nombre", "string", "Nombre", Arrays.asList("departamento", "area"), true, true, true, true, false, false, null),
                        crearCampo("activo", "activo", "activo", "boolean", "Activo", Arrays.asList(), true, true, true, false, false, false, null)
                ))
                .build());

        // 5. Tareas
        catalogoMaestro.put("tareas_actividad", EntidadReportable.builder()
                .nombreLogico("tareas_actividad")
                .coleccionMongo("tareas_actividad")
                .claseJava("TareaActividad")
                .descripcion("Tareas reales generadas durante la ejecución de una instancia y asignadas a funcionarios o usuarios.")
                .aliases(Arrays.asList("tarea", "tareas", "actividad", "actividades", "tarea asignada", "tareas asignadas", "actividad asignada", "trabajo pendiente", "tarea de funcionario", "tarea real", "actividad real", "pendiente del funcionario"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("instanciaId", "instanciaId", "instanciaId", "string", "Trámite", Arrays.asList("tramite"), true, true, true, false, false, true, null),
                        crearCampo("responsableId", "responsableId", "responsableId", "string", "Responsable", Arrays.asList("funcionario", "responsable", "nombre del responsable", "funcionario nombre"), true, true, true, false, false, true, null),
                        crearCampo("estado", "estadoTarea", "estadoTarea", "string", "Estado", Arrays.asList(), true, true, true, false, false, false, Arrays.asList("PENDIENTE", "COMPLETADA", "EN_CURSO")),
                        crearCampo("actividadNombre", "nombreNodo", "nombreNodo", "string", "Nombre actividad", Arrays.asList("nombre"), true, true, true, false, false, false, null),
                        crearCampo("nodoId", "nodoId", "nodoId", "string", "ID Nodo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("nodoNombre", "nombreNodo", "nombreNodo", "string", "Nombre Nodo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("tipo", "tipo", "tipo", "string", "Tipo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("fechaLimite", "fechaFin", "fechaFin", "date", "Fecha Límite", Arrays.asList(), true, true, true, true, false, false, null),
                        crearCampo("fechaCompletado", "fechaFin", "fechaFin", "date", "Fecha Completado", Arrays.asList(), true, true, true, true, false, false, null),
                        crearCampo("fechaCreacion", "fechaCreacion", "fechaCreacion", "date", "Fecha Creación", Arrays.asList(), true, true, true, true, false, false, null)
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("instancia", "tareas_actividad", "instancias_politica", "instanciaId", "_id", "MANY_TO_ONE", true, Arrays.asList("codigoTramite", "estadoInstancia")),
                        crearRelacion("responsable", "tareas_actividad", "usuarios", "responsableId", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "correo"))
                ))
                .build());

        // 6. Pagos
        catalogoMaestro.put("pagos", EntidadReportable.builder()
                .nombreLogico("pagos")
                .coleccionMongo("pagos")
                .claseJava("Pago")
                .descripcion("Pagos registrados en los trámites")
                .aliases(Arrays.asList("pago", "pagos", "cobro", "monto", "recaudacion"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("instanciaPoliticaId", "instanciaId", "instanciaId", "string", "Trámite", Arrays.asList(), true, true, true, false, false, true, null),
                        crearCampo("monto", "monto", "monto", "number", "Monto", Arrays.asList("importe", "dinero"), true, true, true, true, true, false, null),
                        crearCampo("estado", "estado", "estado", "string", "Estado", Arrays.asList("situacion"), true, true, true, false, false, false, Arrays.asList("PENDIENTE", "PAGADO", "CANCELADO")),
                        crearCampo("fechaCreacion", "fechaCreacion", "fechaCreacion", "date", "Fecha", Arrays.asList(), true, true, true, true, false, false, null)
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("instancia", "pagos", "instancias_politica", "instanciaId", "_id", "MANY_TO_ONE", true, Arrays.asList("codigoTramite"))
                ))
                .build());
                
        // 7. Documentos (Archivos Adjuntos)
        catalogoMaestro.put("archivos_adjuntos", EntidadReportable.builder()
                .nombreLogico("archivos_adjuntos")
                .coleccionMongo("archivos_adjuntos")
                .claseJava("ArchivoAdjunto")
                .descripcion("Documentos y archivos subidos")
                .aliases(Arrays.asList("documento", "documentos", "archivo", "archivos", "adjunto"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("instanciaId", "instanciaId", "instanciaId", "string", "Trámite", Arrays.asList(), true, true, true, false, false, true, null),
                        crearCampo("subidoPor", "subidoPor", "subidoPor", "string", "Subido por", Arrays.asList("usuario", "subido por", "quien subio", "cargado por", "usuario que subio", "autor", "nombre de quien subio", "usuario nombre"), true, true, true, false, false, true, null),
                        crearCampo("nombreArchivo", "nombreOriginal", "nombreOriginal", "string", "Nombre archivo", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("extension", "extension", "extension", "string", "Tipo de archivo", Arrays.asList("tipo", "formato"), true, true, true, false, false, false, null),
                        crearCampo("fechaSubida", "fechaSubida", "fechaSubida", "date", "Fecha subida", Arrays.asList(), true, true, true, true, false, false, null),
                        crearCampoSensible("s3Key", "Llave S3", "Ruta privada")
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("instancia", "archivos_adjuntos", "instancias_politica", "instanciaId", "_id", "MANY_TO_ONE", true, Arrays.asList("codigoTramite")),
                        crearRelacion("usuario", "archivos_adjuntos", "usuarios", "subidoPor", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "correo"))
                ))
                .build());

        // 8. Trazabilidad
        catalogoMaestro.put("historial_instancia", EntidadReportable.builder()
                .nombreLogico("historial_instancia")
                .coleccionMongo("historial_instancia")
                .claseJava("HistorialInstancia")
                .descripcion("Trazabilidad y cambios en trámites")
                .aliases(Arrays.asList("trazabilidad", "historial", "movimientos", "eventos"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("instanciaId", "instanciaId", "instanciaId", "string", "Trámite", Arrays.asList(), true, true, true, false, false, true, null),
                        crearCampo("accion", "accion", "accion", "string", "Acción", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("usuarioId", "usuarioId", "usuarioId", "string", "Usuario", Arrays.asList("usuario", "nombre de usuario", "usuario nombre"), true, true, true, false, false, true, null),
                        crearCampo("fecha", "fecha", "fecha", "date", "Fecha", Arrays.asList(), true, true, true, true, false, false, null)
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("instancia", "historial_instancia", "instancias_politica", "instanciaId", "_id", "MANY_TO_ONE", true, Arrays.asList("codigoTramite")),
                        crearRelacion("usuario", "historial_instancia", "usuarios", "usuarioId", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "correo"))
                ))
                .build());

        // 9. Predicciones IA
        catalogoMaestro.put("predicciones_ia", EntidadReportable.builder()
                .nombreLogico("predicciones_ia")
                .coleccionMongo("predicciones_ia") // asumiendo o usando otra similar
                .claseJava("PrediccionIA")
                .descripcion("Predicciones de riesgo y anomalías")
                .aliases(Arrays.asList("prediccion", "predicciones", "riesgo", "anomalia", "cuello de botella"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("instanciaId", "instanciaId", "instanciaId", "string", "Trámite", Arrays.asList(), true, true, true, false, false, true, null),
                        crearCampo("riesgoDemora", "riesgoDemora", "riesgoDemora", "string", "Riesgo", Arrays.asList("riesgo", "riesgo alto", "riesgo medio", "riesgo bajo", "demora", "riesgo de demora"), true, true, true, false, false, false, Arrays.asList("ALTO", "MEDIO", "BAJO")),
                        crearCampo("prioridadRecomendada", "prioridadRecomendada", "prioridadRecomendada", "string", "Prioridad recomendada", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("cuelloBotella", "cuelloBotella", "cuelloBotella", "boolean", "Cuello de botella", Arrays.asList(), true, true, true, false, false, false, null)
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("instancia", "predicciones_ia", "instancias_politica", "instanciaId", "_id", "MANY_TO_ONE", true, Arrays.asList("codigoTramite"))
                ))
                .build());

        // 10. Notificaciones (DeviceTokens o Notificaciones genéricas)
        catalogoMaestro.put("notificaciones", EntidadReportable.builder()
                .nombreLogico("notificaciones")
                .coleccionMongo("notificaciones") // Si no existe en BD, el normalizer lo manejará
                .claseJava("Notificacion")
                .descripcion("Notificaciones enviadas")
                .aliases(Arrays.asList("notificacion", "notificaciones", "alertas"))
                .reportable(true)
                .fuenteDatos("MONGO")
                .campos(Arrays.asList(
                        crearCampo("id", "_id", "_id", "string", "ID", Arrays.asList(), true, true, true, false, false, false, null),
                        crearCampo("usuarioId", "usuarioId", "usuarioId", "string", "Usuario", Arrays.asList(), true, true, true, false, false, true, null),
                        crearCampo("leida", "leida", "leida", "boolean", "Leída", Arrays.asList("leida", "leido", "sin leer", "no leida", "pendiente de lectura"), true, true, true, false, false, false, null),
                        crearCampo("fechaCreacion", "fechaCreacion", "fechaCreacion", "date", "Fecha", Arrays.asList(), true, true, true, true, false, false, null)
                ))
                .relaciones(Arrays.asList(
                        crearRelacion("usuario", "notificaciones", "usuarios", "usuarioId", "_id", "MANY_TO_ONE", true, Arrays.asList("nombre", "correo"))
                ))
                .build());

        // Otras no reportables explícitas
        catalogoMaestro.put("password_reset_token", EntidadReportable.builder()
                .nombreLogico("password_reset_token")
                .coleccionMongo("password_reset_token")
                .claseJava("PasswordResetToken")
                .reportable(false)
                .motivoNoReportable("Contiene datos sensibles y de seguridad interna")
                .build());
    }

    private CampoReportable crearCampo(String logico, String mongo, String ruta, String tipo, String desc, List<String> aliases, boolean mostrable, boolean filtrable, boolean agrupable, boolean ordenable, boolean metrico, boolean requiereLookup, List<String> valoresPermitidos) {
        return CampoReportable.builder()
                .nombreLogico(logico)
                .campoMongo(mongo)
                .rutaMongo(ruta)
                .tipoDato(tipo)
                .descripcion(desc)
                .aliases(aliases != null ? aliases : new ArrayList<>())
                .mostrable(mostrable)
                .filtrable(filtrable)
                .agrupable(agrupable)
                .ordenable(ordenable)
                .metrico(metrico)
                .sensible(false)
                .reportable(true)
                .requiereLookup(requiereLookup)
                .esArray(false)
                .requiereUnwind(false)
                .valoresPermitidos(valoresPermitidos != null ? valoresPermitidos : new ArrayList<>())
                .operadoresPermitidos(Arrays.asList("=", "!=", ">", "<", "in", "contains", "mes_actual", "anio_actual", "ultimos_dias", "ultimos_meses", "semana_actual", "mes_pasado", "anio_pasado", "rango_fechas", "hoy", "ayer"))
                .build();
    }

    private CampoReportable crearCampoArray(String logico, String mongo, String ruta, String tipo, String desc, List<String> aliases, boolean mostrable, boolean filtrable, boolean agrupable, boolean ordenable, boolean metrico, boolean requiereLookup, List<String> valoresPermitidos) {
        return CampoReportable.builder()
                .nombreLogico(logico)
                .campoMongo(mongo)
                .rutaMongo(ruta)
                .tipoDato(tipo)
                .descripcion(desc)
                .aliases(aliases != null ? aliases : new ArrayList<>())
                .mostrable(mostrable)
                .filtrable(filtrable)
                .agrupable(agrupable)
                .ordenable(ordenable)
                .metrico(metrico)
                .sensible(false)
                .reportable(true)
                .requiereLookup(requiereLookup)
                .esArray(true)
                .requiereUnwind(true)
                .valoresPermitidos(valoresPermitidos != null ? valoresPermitidos : new ArrayList<>())
                .operadoresPermitidos(Arrays.asList("=", "!=", "in", "contains"))
                .build();
    }


    private CampoReportable crearCampoSensible(String mongo, String desc, String motivo) {
        return CampoReportable.builder()
                .nombreLogico(mongo)
                .campoMongo(mongo)
                .descripcion(desc)
                .sensible(true)
                .reportable(false)
                .motivoNoReportable(motivo)
                .build();
    }

    private RelacionReportable crearRelacion(String nombre, String origen, String destino, String campoLocal, String campoDest, String tipo, boolean requiereObjectId, List<String> camposEnriquecidos) {
        return RelacionReportable.builder()
                .nombre(nombre)
                .entidadOrigen(origen)
                .entidadDestino(destino)
                .campoLocal(campoLocal)
                .campoDestino(campoDest)
                .tipoRelacion(tipo)
                .requiereObjectIdConversion(requiereObjectId)
                .camposEnriquecidos(camposEnriquecidos != null ? camposEnriquecidos : new ArrayList<>())
                .build();
    }

    public Map<String, EntidadReportable> getCatalogoCompleto() {
        return catalogoMaestro;
    }

    public List<Object> obtenerValoresDistintos(String entidad, String campo) {
        EntidadReportable ent = obtenerEntidadPorNombreOAlias(entidad);
        if (ent == null) return Collections.emptyList();
        
        CampoReportable c = obtenerCampoDeEntidad(ent, campo);
        String campoMongo = (c != null) ? c.getCampoMongo() : campo;
        if (campoMongo.equals("id")) campoMongo = "_id";

        try {
            return (List<Object>) (List<?>) mongoTemplate.findDistinct(
                    new org.springframework.data.mongodb.core.query.Query(),
                    campoMongo,
                    ent.getColeccionMongo(),
                    Object.class
            );
        } catch (Exception e) {
            System.err.println("Error al obtener valores distintos de " + ent.getColeccionMongo() + "." + campoMongo + ": " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    // Antiguo método para compatibilidad (usado en Controller viejo)
    public Map<String, List<String>> getCatalogo() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (EntidadReportable ent : catalogoMaestro.values()) {
            if (ent.isReportable()) {
                List<String> campos = ent.getCampos().stream()
                        .filter(CampoReportable::isReportable)
                        .map(CampoReportable::getNombreLogico)
                        .collect(Collectors.toList());
                map.put(ent.getNombreLogico(), campos);
            }
        }
        return map;
    }

    public EntidadReportable obtenerEntidadPorNombreOAlias(String termino) {
        if (termino == null) return null;
        String t = termino.toLowerCase().trim();
        for (EntidadReportable ent : catalogoMaestro.values()) {
            if (ent.getNombreLogico().equalsIgnoreCase(t)) return ent;
            if (ent.getAliases() != null && ent.getAliases().contains(t)) return ent;
        }
        return null;
    }

    public CampoReportable obtenerCampoDeEntidad(EntidadReportable entidad, String campoAlias) {
        if (entidad == null || campoAlias == null || entidad.getCampos() == null) return null;
        String c = campoAlias.toLowerCase().trim();
        for (CampoReportable campo : entidad.getCampos()) {
            if (campo.getNombreLogico().equalsIgnoreCase(c)) return campo;
            if (campo.getAliases() != null && campo.getAliases().contains(c)) return campo;
        }
        return null;
    }

    public boolean esEntidadPermitida(String entidad) {
        EntidadReportable ent = obtenerEntidadPorNombreOAlias(entidad);
        return ent != null && ent.isReportable();
    }

    public boolean esCampoPermitido(String entidad, String campo) {
        EntidadReportable ent = obtenerEntidadPorNombreOAlias(entidad);
        if (ent == null || !ent.isReportable()) return false;
        if (campo == null || campo.isEmpty()) return true;
        if (campo.equals("id") || campo.equals("_id") || campo.equals("monto")) return true;
        
        // Check exact match first
        CampoReportable c = obtenerCampoDeEntidad(ent, campo);
        if (c != null && c.isReportable()) return true;
        
        // Check if it's an enriched field (e.g. creadaPorNombre -> basada en creadaPor)
        if (ent.getRelaciones() != null) {
            for (RelacionReportable rel : ent.getRelaciones()) {
                if (rel.getCamposEnriquecidos() != null) {
                    for (String enr : rel.getCamposEnriquecidos()) {
                        String enrichedName = rel.getCampoLocal() + enr.substring(0, 1).toUpperCase() + enr.substring(1);
                        if (campo.equalsIgnoreCase(enrichedName)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }

    public List<String> validarCampos(String entidad, List<String> campos) {
        if (campos == null || campos.isEmpty()) return Collections.emptyList();
        List<String> invalidos = new ArrayList<>();
        for (String campo : campos) {
            if (!esCampoPermitido(entidad, campo)) {
                invalidos.add(campo);
            }
        }
        return invalidos;
    }

    public CatalogoResumidoResponse getCatalogoResumido() {
        CatalogoResumidoResponse res = new CatalogoResumidoResponse();
        res.setVersion("1.0");
        res.setOrigen("SPRING_BOOT");
        res.setOperacionesPermitidas(Arrays.asList("count", "sum", "avg", "min", "max", "listado", "ranking"));
        res.setOperadoresPermitidos(Arrays.asList("=", "!=", ">", ">=", "<", "<=", "between", "in", "contains", "mes_actual", "anio_actual", "ultimos_dias", "ultimos_meses", "semana_actual", "mes_pasado", "anio_pasado", "rango_fechas", "hoy", "ayer"));
        
        List<CatalogoResumidoResponse.EntidadResumida> ents = new ArrayList<>();
        for (EntidadReportable e : catalogoMaestro.values()) {
            if (!e.isReportable()) continue;
            
            CatalogoResumidoResponse.EntidadResumida er = new CatalogoResumidoResponse.EntidadResumida();
            er.setNombreLogico(e.getNombreLogico());
            er.setAliases(e.getAliases());
            er.setDescripcion(e.getDescripcion());
            
            List<CatalogoResumidoResponse.CampoResumido> crs = new ArrayList<>();
            if (e.getCampos() != null) {
                for (CampoReportable c : e.getCampos()) {
                    if (!c.isReportable() || c.isSensible()) continue;
                    CatalogoResumidoResponse.CampoResumido cr = new CatalogoResumidoResponse.CampoResumido();
                    cr.setNombreLogico(c.getNombreLogico());
                    cr.setAliases(c.getAliases());
                    cr.setTipoDato(c.getTipoDato());
                    cr.setFiltrable(c.isFiltrable());
                    cr.setAgrupable(c.isAgrupable());
                    cr.setOrdenable(c.isOrdenable());
                    cr.setValoresPermitidos(c.getValoresPermitidos());
                    crs.add(cr);
                }
            }
            er.setCampos(crs);
            ents.add(er);
        }
        res.setEntidades(ents);
        return res;
    }

    public CoberturaResponse getCobertura() {
        CoberturaResponse res = new CoberturaResponse();
        int detectadas = catalogoMaestro.size();
        int reportables = 0;
        int noReportables = 0;
        int camposDet = 0;
        int camposRep = 0;
        int camposSensibles = 0;
        int aliases = 0;
        int rels = 0;

        for (EntidadReportable e : catalogoMaestro.values()) {
            if (e.isReportable()) reportables++;
            else noReportables++;

            if (e.getAliases() != null) aliases += e.getAliases().size();
            if (e.getRelaciones() != null) rels += e.getRelaciones().size();

            if (e.getCampos() != null) {
                camposDet += e.getCampos().size();
                for (CampoReportable c : e.getCampos()) {
                    if (c.isReportable()) camposRep++;
                    if (c.isSensible()) camposSensibles++;
                    if (c.getAliases() != null) aliases += c.getAliases().size();
                }
            }
        }

        res.setColeccionesDetectadas(detectadas);
        res.setColeccionesReportables(reportables);
        res.setColeccionesNoReportables(noReportables);
        res.setCamposDetectados(camposDet);
        res.setCamposReportables(camposRep);
        res.setCamposSensiblesExcluidos(camposSensibles);
        res.setAliasesConfigurados(aliases);
        res.setRelacionesConfiguradas(rels);
        res.setCamposAnidados(0); // Aproximado para demo
        res.setCamposArray(0); // Aproximado para demo
        res.setAdvertencias(new ArrayList<>());
        res.setEntidadesSinRepository(new ArrayList<>());
        res.setEntidadesSinRelaciones(new ArrayList<>());

        return res;
    }
}
