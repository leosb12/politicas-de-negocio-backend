# Modulo Formularios

## Descripcion del modulo
Este modulo esta reservado para formularios dinamicos asociados al flujo.
Busca resolver la definicion de campos, captura de respuestas y vinculacion con actividades.

## Responsabilidades
- Definir estructura de formularios por tipo de actividad.
- Gestionar campos y validaciones de captura.
- Almacenar respuestas por instancia o tramite.

## Clases principales
### Controllers
- Aun no hay controllers implementados.

### Services
- Aun no hay services implementados.

### Models
- Aun no hay modelos implementados.
- Actualmente solo hay subpaquetes reservados: definicion, campos, respuestas y actividad.

## Endpoints
Este modulo no expone endpoints HTTP ni WS por ahora.

## Relacion con otros modulos
- politicas: los nodos podrian referenciar formularios para ejecucion.
- actividades: usaria formularios como entrada de cada actividad.
- instancias: almacenaria respuestas por tramite.
