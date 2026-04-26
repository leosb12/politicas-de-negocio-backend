# Modulo Politicas

## Descripcion del modulo
Este modulo gestiona las politicas de negocio y su flujo (nodos y conexiones).
Resuelve el problema de definir, guardar y controlar el ciclo de vida de cada politica dentro del sistema.

## Responsabilidades
- Crear politicas con estado inicial.
- Consultar politicas y detalle por id.
- Guardar y actualizar la estructura del flujo.
- Cambiar estado de la politica (por ejemplo BORRADOR, ACTIVA, PAUSADA, DESHABILITADA).
- Eliminar politicas de forma controlada solo cuando no tienen uso ni historial asociado.
- Validar reglas de negocio antes de activar o modificar flujos.
- Configurar si una politica es gratuita o pagada.

## Clases principales
### Controllers
- PoliticaNegocioController: expone la API para gestionar politicas y su flujo.

### Services
- PoliticaNegocioService: aplica validaciones de negocio, reglas de activacion y persistencia de politicas.

### Models
- PoliticaNegocio: entidad principal de la politica.
- Nodo y Conexion: estructura del flujo de trabajo.
- EstadoPolitica, TipoNodo, TipoCampo, ResponsableTipo: enums de comportamiento del flujo.
- Campos de pago en PoliticaNegocio:
  - requierePago
  - montoPago
  - monedaPago
  - descripcionPago

## Endpoints
### POST /api/politicas
Se usa para crear una politica nueva.
Recibe:
- Header X-Admin-User-Id.
- Body CreatePoliticaRequest (nombre, descripcion, tipoPolitica, departamentoInicioId, requierePago, montoPago, monedaPago, descripcionPago).
Devuelve:
- PoliticaNegocio creada.

### GET /api/politicas
Se usa para listar politicas.
Recibe:
- Header X-Admin-User-Id.
Devuelve:
- Lista de PoliticaNegocio.

### GET /api/politicas/movil/disponibles
Se usa para listar tramites disponibles para iniciar desde clientes moviles.
Recibe:
- Header X-User-Id o X-Admin-User-Id.
Reglas:
- Solo devuelve politicas en estado ACTIVA.
- El actor debe estar activo en el sistema.
Devuelve:
- Lista de TramiteDisponibleResponse (id, nombre, descripcion, requierePago, montoPago, monedaPago, descripcionPago).

### GET /api/politicas/{id}
Se usa para consultar una politica por id.
Recibe:
- Header X-Admin-User-Id.
- Path id.
Devuelve:
- PoliticaNegocio.

### PUT /api/politicas/{id}/flujo
Se usa para guardar o reemplazar el flujo de una politica.
Recibe:
- Header X-Admin-User-Id.
- Path id.
- Body UpdateFlujoRequest (nodos, conexiones).
Devuelve:
- PoliticaNegocio actualizada.

### PATCH /api/politicas/{id}/estado
Se usa para cambiar el estado de una politica.
Recibe:
- Header X-Admin-User-Id.
- Path id.
- Body con la clave estado.
Devuelve:
- PoliticaNegocio actualizada.

### PATCH /api/politicas/{id}
Permite actualizar metadatos generales y configuracion de pago.
Reglas:
- Si `requierePago=true`, `montoPago` debe ser mayor a 0.
- `monedaPago` usa `USD` por defecto si no se envia.

### DELETE /api/politicas/{id}
Se usa para eliminar una politica de forma permanente solo si es seguro.
Recibe:
- Header X-Admin-User-Id.
- Path id.
Reglas minimas:
- Solo permite eliminar en estado BORRADOR o DESHABILITADA.
- No permite eliminar si hay historial, uso previo o referencias en otras entidades.
- Si no se puede eliminar, devuelve error funcional y sugiere desactivacion.
Devuelve:
- 204 No Content cuando se elimina.

## Relacion con otros modulos
- usuarios: valida que quien opera sea ADMIN.
- departamentos: valida responsables y carriles por departamento dentro de nodos.
- colaboracion: este modulo es la base sobre la que se aplican eventos de edicion en tiempo real.
- shared: usa excepciones comunes y configuraciones transversales.
