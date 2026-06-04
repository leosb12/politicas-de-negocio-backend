# Documentos colaborativos CU-33

OnlyOffice Document Server se consume como servicio externo. Para desarrollo local:

```bash
docker rm -f onlyoffice-document-server
docker run -i -t -d -p 8082:80 -e JWT_ENABLED=false -e ALLOW_PRIVATE_IP_ADDRESS=true -e ALLOW_META_IP_ADDRESS=true --name onlyoffice-document-server onlyoffice/documentserver
```

Configuracion esperada:

```properties
onlyoffice.document-server-url=${ONLYOFFICE_DOCUMENT_SERVER_URL:http://localhost:8082}
onlyoffice.callback-base-url=${ONLYOFFICE_CALLBACK_BASE_URL:http://host.docker.internal:8080}
onlyoffice.source-public-access-enabled=${ONLYOFFICE_SOURCE_PUBLIC_ACCESS_ENABLED:true}
onlyoffice.jwt-enabled=${ONLYOFFICE_JWT_ENABLED:false}
onlyoffice.jwt-secret=${ONLYOFFICE_JWT_SECRET:}
```

Si OnlyOffice corre dentro de Docker, el backend Spring Boot del host suele ser accesible para el contenedor como:

```properties
ONLYOFFICE_CALLBACK_BASE_URL=http://host.docker.internal:8080
```

OnlyOffice descarga el archivo desde `/api/documentos-colaborativos/{documentoId}/source` y guarda cambios mediante `/api/documentos-colaborativos/onlyoffice/callback/{documentoId}`. La edicion colaborativa en vivo la resuelve OnlyOffice Document Server; la aplicacion no implementa WebSocket propio para CU-33.

El contenedor local debe permitir IPs privadas porque `host.docker.internal` resuelve contra la red privada de Docker Desktop. Si `ALLOW_PRIVATE_IP_ADDRESS=true` no esta activo, OnlyOffice puede abrir la interfaz pero falla al descargar el archivo con `Download failed`.

Con `onlyoffice.jwt-enabled=false`, el backend no incluye el campo `token` en la configuracion enviada a OnlyOffice. Con `onlyoffice.jwt-enabled=true`, el backend firma la configuracion con `onlyoffice.jwt-secret`; si el secreto esta vacio, falla con un error claro de configuracion.

Para desarrollo local, `onlyoffice.source-public-access-enabled=true` permite que OnlyOffice descargue `/source` sin headers del usuario. En ambientes productivos, desactivar ese flag y usar la URL firmada con `accessToken`.
