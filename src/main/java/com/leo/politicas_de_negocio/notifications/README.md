# Notifications

Modulo de notificaciones push para la app Flutter usando Firebase Cloud Messaging.

## Flujo

1. Flutter obtiene el FCM registration token desde Firebase Messaging.
2. Flutter registra ese token en el backend con `POST /api/mobile/device-tokens`.
3. El backend guarda uno o mas tokens activos por usuario en MongoDB.
4. Un caso de uso del backend construye el mensaje con notification y data payload.
5. Firebase Admin SDK envia el push a FCM.
6. FCM entrega el mensaje a Android/iOS y Flutter procesa `data.type`, `data.tramiteId`, `data.tareaId` y `data.action`.

## Endpoints

### Registrar token

`POST /api/mobile/device-tokens`

Headers:

- `X-User-Id`: id del usuario autenticado en la app movil.

Body:

```json
{
  "token": "fcm-registration-token",
  "platform": "ANDROID",
  "deviceId": "pixel-8",
  "appVersion": "1.0.0"
}
```

### Enviar push de prueba al usuario autenticado

`POST /api/mobile/notifications/test`

Headers:

- `X-User-Id`: id del usuario autenticado en la app movil.

Body:

```json
{
  "title": "Tarea asignada",
  "body": "Tienes una nueva tarea pendiente",
  "type": "TAREA_ASIGNADA",
  "tramiteId": "instancia-123",
  "tareaId": "tarea-456",
  "action": "OPEN_TASK"
}
```

## Configuracion AWS/Firebase

El backend puede arrancar sin credenciales de Firebase. En ese modo, el sender `NoopPushNotificationSender` responde con `FIREBASE_NOT_CONFIGURED`.

Para produccion:

```properties
APP_NOTIFICATIONS_FIREBASE_ENABLED=true
FIREBASE_PROJECT_ID=mi-proyecto-firebase
FIREBASE_SERVICE_ACCOUNT_BASE64=<service-account-json-en-base64>
```

Tambien se puede usar `FIREBASE_SERVICE_ACCOUNT_PATH` o `GOOGLE_APPLICATION_CREDENTIALS`.
Para pruebas locales, `FIREBASE_CREDENTIALS_PATH` tambien apunta al JSON de service account.

## Eventos automaticos del workflow

El backend envia push automaticamente desde el workflow cuando:

- Se inicia una instancia de tramite: `TRAMITE_INICIADO`.
- Se crea una nueva tarea para un usuario: `TAREA_ASIGNADA`.
- Una instancia pasa a un departamento: `TRAMITE_CAMBIO_DEPARTAMENTO`.
- Se completa una tarea: `TAREA_COMPLETADA`.
- La instancia llega al nodo final: `TRAMITE_FINALIZADO`.

Los envios son best-effort: un fallo de Firebase o un usuario sin tokens activos no bloquea el avance del tramite.
