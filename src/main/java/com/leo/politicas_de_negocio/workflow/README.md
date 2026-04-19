# Modulo Workflow Engine

## Descripcion del modulo
Este modulo ejecuta el diagrama de politica como workflow real, creando tareas y avanzando estados.

## Responsabilidades
- Arrancar instancia desde nodo INICIO.
- Resolver transiciones por tipo de nodo: INICIO, ACTIVIDAD, DECISION, FORK, JOIN, FIN.
- Crear tareas para nodos ACTIVIDAD.
- Esperar todas las ramas en JOIN.
- Cerrar instancia en FIN cuando no hay tareas abiertas.

## Clase principal
- WorkflowEngineService: motor de ejecucion y transicion por diagrama.

## Reglas aplicadas
- Toda transicion sale de conexiones del diagrama.
- DECISION usa condiciones del nodo y contexto de instancia/respuesta.
- JOIN no libera transicion hasta recibir todas las ramas entrantes.
- FIN solo finaliza si no quedan tareas abiertas.
