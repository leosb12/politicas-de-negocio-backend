# CU-35 Clasificar solicitud mediante agente inteligente

Endpoint movil:

```http
POST /api/movil/ia/clasificar-solicitud
```

Spring Boot valida el texto, consulta politicas `ACTIVA` reales desde MongoDB y envia sus campos semanticos a `ia-service`. El resultado se valida contra las politicas activas antes de responder al movil.

Este flujo no inicia tramites automaticamente. La respuesta siempre incluye `requiereConfirmacion: true` para que el usuario confirme antes de continuar.

Configuracion:

```properties
IA_SERVICE_URL=http://localhost:8000
```

`ia-service` orquesta y `ia-deep-learning-service` calcula embeddings semanticos para soportar politicas futuras sin `codigoClasificacion` ni reentrenamiento.
