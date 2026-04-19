# Modulo Tareas

## Descripcion del modulo
Este modulo representa actividades operativas reales generadas por el motor de workflow.

## Responsabilidades
- Listar tareas pendientes o en proceso para un actor.
- Tomar una tarea para ejecucion.
- Completar una tarea y activar el siguiente paso del flujo.

## Clases principales
### Controllers
- TareaActividadController: API para operar tareas.

### Services
- TareaActividadService: valida permisos de ejecucion y completa tareas.

### Models
- TareaActividad: trabajo puntual asignado a usuario o departamento.
- EstadoTarea: PENDIENTE, EN_PROCESO, COMPLETADA, RECHAZADA, CANCELADA.

## Endpoints
### GET /api/tareas/mis
Lista tareas abiertas del actor.

### GET /api/tareas/instancia/{instanciaId}
Lista tareas de una instancia.

### PATCH /api/tareas/{id}/tomar
Marca tarea como EN_PROCESO por el actor.

### PATCH /api/tareas/{id}/completar
Completa tarea, guarda respuesta y avanza workflow.
