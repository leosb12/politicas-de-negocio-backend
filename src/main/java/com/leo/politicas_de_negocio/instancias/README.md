# Modulo Instancias

## Descripcion del modulo
Este modulo gestiona la ejecucion real de una politica como caso (tramite) en curso.

## Responsabilidades
- Crear instancias desde una politica ACTIVA.
- Consultar instancias y su estado operativo.
- Mantener trazabilidad de eventos de instancia.

## Clases principales
### Controllers
- InstanciaPoliticaController: API para iniciar y consultar instancias.

### Services
- InstanciaPoliticaService: valida actor, politica activa y coordina inicio del workflow.
- HistorialInstanciaService: registra eventos de trazabilidad.

### Models
- InstanciaPolitica: caso real ejecutandose.
- HistorialInstancia: bitacora de eventos de la instancia.
- EstadoInstancia: EN_CURSO, PAUSADA, FINALIZADA, CANCELADA.

## Endpoints
### POST /api/instancias
Inicia una nueva instancia de politica.

### GET /api/instancias
Lista instancias (filtro opcional por estado).

### GET /api/instancias/{id}
Obtiene detalle de instancia.

### GET /api/instancias/{id}/seguimiento
Devuelve una vista de solo lectura para pintar el diagrama del tramite en movil: nodos, conexiones, carriles, tareas de la instancia, nodos actuales y departamentos actuales.

### GET /api/instancias/{id}/historial
Obtiene trazabilidad completa de la instancia.
