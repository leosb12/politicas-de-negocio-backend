# Modulo Archivos

## Descripcion del modulo
Este modulo implementa la gestion de adjuntos genericos para el sistema de politicas/workflow.
Soporta cualquier tipo de archivo (pdf, imagen, docx, xlsx, txt, zip, etc.) y esta desacoplado del tipo de almacenamiento fisico.

## Objetivo de diseno
La API y la logica de negocio trabajan contra la interfaz `ArchivoStorageService`.
Por eso, para cambiar de almacenamiento solo necesitas modificar:
- `app.storage.type=local`
- `app.storage.type=s3`

Sin tocar controladores ni servicios de aplicacion.

## Arquitectura
### Capa API
- `ArchivoController`: expone endpoints REST para subir, consultar, descargar, eliminar y listar adjuntos.

### Capa aplicacion
- `ArchivoService`: valida actor, sanea datos, genera nombre unico, persiste metadata y delega operaciones fisicas al storage.

### Capa storage (abstraccion)
- `ArchivoStorageService`: contrato comun para almacenamiento.
- `LocalArchivoStorageService`: implementacion para desarrollo local en disco.
- `S3ArchivoStorageService`: implementacion para AWS S3 (AWS SDK v2).

### Capa persistencia
- `ArchivoAdjunto`: documento Mongo con metadatos del archivo.
- `ArchivoAdjuntoRepository`: consultas por id y por entidades asociadas.

## Metadatos guardados
El documento guarda, entre otros:
- id
- nombreOriginal
- nombreGuardado
- rutaOKey
- storageType
- contentType
- extension
- tamanoBytes
- fechaSubida
- subidoPor
- instanciaId
- actividadId
- tareaId
- usuarioId
- estado
- descripcion
- urlAcceso
- bucket

## Endpoints
### POST /api/archivos
Sube archivo multipart con campos opcionales de asociacion.
- Campo obligatorio: `archivo`
- Campos opcionales: `instanciaId`, `actividadId`, `tareaId`, `usuarioId`, `descripcion`
- Headers requeridos: `X-User-Id` o `X-Admin-User-Id`

### GET /api/archivos/{id}
Obtiene metadatos del archivo.

### GET /api/archivos/{id}/download
Descarga el archivo por backend.

### DELETE /api/archivos/{id}
Elimina el archivo fisico y marca metadata como `ELIMINADO`.

### GET /api/archivos/by-instancia/{instanciaId}
Lista adjuntos activos por instancia.

### GET /api/archivos/by-actividad/{actividadId}
Lista adjuntos activos por actividad.

## Configuracion
En `application.properties` ya queda preparado:

```properties
app.storage.type=${APP_STORAGE_TYPE:local}
app.storage.local.base-path=${APP_STORAGE_LOCAL_BASE_PATH:uploads}

app.storage.s3.bucket=${APP_STORAGE_S3_BUCKET:mi-bucket}
app.storage.s3.region=${APP_STORAGE_S3_REGION:us-east-1}
app.storage.s3.key-prefix=${APP_STORAGE_S3_KEY_PREFIX:adjuntos/}
```

## Modo local (desarrollo)
- Se activa con `app.storage.type=local`.
- Guarda archivos dentro de `app.storage.local.base-path`.
- Se crea el directorio automaticamente si no existe.
- `uploads/` esta agregado a `.gitignore`.

## Modo S3 (produccion AWS)
- Se activa con `app.storage.type=s3`.
- Usa `S3Client` del AWS SDK v2 y la cadena de credenciales por defecto de AWS.
- Operaciones implementadas: subir, descargar, eliminar y referencia `s3://bucket/key`.
- Queda listo para extender a presigned URLs en el futuro.

## Seguridad y robustez aplicadas
- Validacion de archivo no vacio.
- Sanitizacion de nombre original y nombres guardados.
- Nombre unico por UUID para evitar colisiones.
- Prevencion de path traversal en modo local.
- Manejo de errores de almacenamiento con excepciones especificas.
- Separacion de logica de negocio y acceso fisico al archivo.
