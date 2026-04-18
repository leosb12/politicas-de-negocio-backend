# Modulo Colaboracion

## Descripcion del modulo
Este modulo maneja la colaboracion en tiempo real sobre las politicas.
Resuelve el problema de permitir edicion concurrente, sincronizacion de estado, presencia de usuarios y bloqueo logico de nodos para evitar conflictos.

## Responsabilidades
- Procesar eventos colaborativos sobre el flujo de una politica.
- Mantener idempotencia y secuencia de eventos aplicados.
- Exponer estado actual, historial y bloqueos por API REST.
- Gestionar presencia de usuarios conectados por WebSocket.
- Publicar actualizaciones a clientes por STOMP.

## Clases principales
### Controllers
- PoliticaColaboracionController: endpoints REST para estado, historial, presencia y bloqueos.
- PoliticaColaboracionWsController: canales STOMP para eventos en tiempo real.

### Services
- PoliticaColaboracionService: logica central de aplicacion de eventos, snapshots y sincronizacion de flujo.
- PoliticaPresenciaService: seguimiento de sesiones activas, presencia y colisiones de edicion.

### Models
- EventoColaboracionAplicado: registro de eventos aplicados sobre una politica.
- SnapshotColaboracionPolitica: snapshot del flujo en una secuencia concreta.
- TipoEventoColaboracion: tipo de operacion colaborativa (crear nodo, mover nodo, etc).

## Endpoints
### GET /api/politicas/{politicaId}/colaboracion/estado
Se usa para obtener el estado colaborativo actual de una politica.
Recibe:
- Header X-Admin-User-Id.
- Path politicaId.
Devuelve:
- ColaboracionEstadoResponse.

### GET /api/politicas/{politicaId}/colaboracion/historial
Se usa para consultar eventos colaborativos recientes.
Recibe:
- Header X-Admin-User-Id.
- Path politicaId.
Devuelve:
- Lista de EventoColaboracionAplicado.

### GET /api/politicas/{politicaId}/colaboracion/presencia
Se usa para ver usuarios conectados en la politica.
Recibe:
- Header X-Admin-User-Id.
- Path politicaId.
Devuelve:
- PresenciaPoliticaResponse.

### GET /api/politicas/{politicaId}/colaboracion/nodos-bloqueados
Se usa para consultar nodos en edicion activa.
Recibe:
- Header X-Admin-User-Id.
- Path politicaId.
Devuelve:
- Lista de NodoBloqueoResponse.

### WS SEND /app/politicas/{politicaId}/eventos
Se usa para enviar un evento colaborativo.
Recibe:
- Payload ColaboracionEventoRequest.
Devuelve:
- Publica ColaboracionEventoResponse en /topic/politicas/{politicaId}/eventos.
- Si hay error, publica ColaboracionErrorResponse en /topic/politicas/{politicaId}/errores.

### WS SEND /app/politicas/{politicaId}/sync
Se usa para pedir sincronizacion del estado completo.
Recibe:
- Payload con actorUserId.
Devuelve:
- Publica ColaboracionEstadoResponse en /topic/politicas/{politicaId}/estado.

### WS SEND /app/politicas/{politicaId}/presencia/join
Se usa para registrar una sesion conectada.
Recibe:
- Payload PresenciaJoinRequest.
Devuelve:
- Publica PresenciaPoliticaResponse en /topic/politicas/{politicaId}/presencia.

### WS SEND /app/politicas/{politicaId}/presencia/leave
Se usa para sacar una sesion de presencia.
Recibe:
- Contexto de sesion WebSocket.
Devuelve:
- Publica presencia actualizada y bloqueos actualizados.

### WS SEND /app/politicas/{politicaId}/nodos/edicion
Se usa para marcar inicio o fin de edicion de un nodo.
Recibe:
- Payload NodoEdicionRequest.
Devuelve:
- Publica NodoBloqueoResponse en /topic/politicas/{politicaId}/nodos-bloqueados.

### WS SEND /app/politicas/{politicaId}/nodos/edicion/sync
Se usa para sincronizar bloqueos de nodos.
Recibe:
- Payload con actorUserId.
Devuelve:
- Publica bloqueos activos por nodo en /topic/politicas/{politicaId}/nodos-bloqueados.

## Relacion con otros modulos
- politicas: usa PoliticaNegocioService y PoliticaNegocioRepository para leer y actualizar el flujo.
- usuarios: valida que el actor tenga permisos de ADMIN.
- shared: usa ApiException y depende del manejo global de errores.
