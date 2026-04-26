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
Intenta iniciar una nueva instancia de politica.
Reglas:
- Si la politica es gratuita, crea la instancia y devuelve `201 Created`.
- Si la politica requiere pago, no crea la instancia y devuelve `200 OK` con `requierePago=true`, monto, moneda y descripcion para que el frontend abra el modal/pantalla de pago.

### GET /api/instancias
Lista instancias (filtro opcional por estado).

### GET /api/instancias/{id}
Obtiene detalle de instancia.

### GET /api/instancias/{id}/seguimiento
Devuelve una vista de solo lectura para pintar el diagrama del tramite en movil: nodos, conexiones, carriles, tareas de la instancia, nodos actuales y departamentos actuales.

### GET /api/instancias/{id}/historial
Obtiene trazabilidad completa de la instancia.

## Relacion con pagos
- El inicio real de instancias pagadas ocurre solo despues de confirmar el pago.
- Stripe inicia automaticamente la instancia al verificar `payment_status=paid`.
- PayPal por link queda en `PENDIENTE_CONFIRMACION_PAYPAL` hasta aprobacion manual/demo o una integracion futura con IPN/webhook.
