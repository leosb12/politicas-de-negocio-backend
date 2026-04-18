# Modulo Shared

## Descripcion del modulo
Este modulo agrupa componentes transversales que usan varios dominios.
Resuelve el problema de no duplicar configuraciones y manejo de errores en cada modulo funcional.

## Responsabilidades
- Configurar CORS para toda la aplicacion.
- Cargar datos semilla de roles, usuarios y departamentos al iniciar.
- Centralizar excepciones de negocio y respuestas de error.

## Clases principales
### Controllers
- Este modulo no tiene controllers.

### Services
- Este modulo no tiene services de negocio.

### Models
- ApiErrorResponse: estructura comun para respuestas de error HTTP.
- ApiException: excepcion de negocio con HttpStatus.

### Configuracion y soporte
- CorsConfig: configura politicas CORS globales.
- DataLoader: inicializa datos base del sistema.
- GlobalExceptionHandler: convierte excepciones en respuestas HTTP consistentes.

## Endpoints
Este modulo no expone endpoints HTTP ni WS directamente.

## Relacion con otros modulos
- Todos los modulos funcionales: consumen excepciones y configuraciones comunes de este modulo.
- departamentos y usuarios: son usados por DataLoader para semilla inicial.
