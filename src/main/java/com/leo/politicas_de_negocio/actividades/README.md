# Modulo Actividades

## Descripcion del modulo
Este modulo esta reservado para manejar actividades ejecutables dentro de un flujo.
Apunta a resolver la parte operativa de cada paso de una politica cuando una instancia ya esta en curso.

## Responsabilidades
- Definir logica de ejecucion de actividades.
- Manejar estados de actividad y resultado.
- Coordinar entradas y salidas entre pasos del flujo.

## Clases principales
### Controllers
- Aun no hay controllers implementados.

### Services
- Aun no hay services implementados.

### Models
- Aun no hay modelos implementados.

## Endpoints
Este modulo no expone endpoints HTTP ni WS por ahora.

## Relacion con otros modulos
- politicas: tomaria la definicion del flujo y los nodos de tipo actividad.
- formularios: usaria formularios asociados a actividades.
- instancias: ejecutaria actividades dentro de cada tramite o instancia.
