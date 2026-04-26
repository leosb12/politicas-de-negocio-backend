# Modulo Pagos

## Descripcion
Este modulo resuelve el cobro previo al inicio de politicas pagadas.

## Responsabilidades
- Registrar pagos Stripe y PayPal.
- Crear sesiones reales de Stripe Checkout con monto dinamico tomado desde la politica.
- Generar links reales de PayPal con monto y descripcion dinamicos.
- Confirmar pagos Stripe y disparar el inicio de la instancia solo cuando el pago fue aprobado.
- Mantener estados claros para flujos manuales de PayPal.

## Modelos
- `Pago`: registro auditable del intento/cobro.
- `ProveedorPago`: `STRIPE`, `PAYPAL`.
- `EstadoPago`: `PENDIENTE`, `APROBADO`, `CANCELADO`, `FALLIDO`, `PENDIENTE_CONFIRMACION_PAYPAL`, `APROBADO_MANUALMENTE`.

## Endpoints
### POST /api/pagos/stripe/crear-checkout
Recibe `politicaId`, `usuarioId`, `monto`, `descripcion` y tambien puede recibir `codigoTramite`, `datosContexto`, `successUrl`, `cancelUrl`.
Reglas:
- El backend no confia en `monto` ni `descripcion`; siempre valida contra la politica.
- Crea la sesion real de Stripe Checkout y devuelve `stripeCheckoutUrl`.

### GET /api/pagos/stripe/verificar?sessionId=...
Verifica la sesion contra Stripe.
Reglas:
- Si `payment_status=paid`, marca el pago aprobado e inicia automaticamente la instancia.
- Si el pago ya fue aplicado antes, reutiliza el resultado para evitar duplicados basicos.

### POST /api/pagos/paypal/crear-link
Genera el link real de PayPal y registra un `Pago` con estado `PENDIENTE_CONFIRMACION_PAYPAL`.
Reglas:
- No inicia automaticamente la instancia.
- Devuelve `paypalUrl` para redireccion del frontend.

### POST /api/pagos/paypal/{pagoId}/confirmar-manual
Disponible para modo demo/admin.
Reglas:
- Requiere `X-Admin-User-Id`.
- Marca el pago como `APROBADO_MANUALMENTE` e inicia la instancia.

### GET /api/pagos/{pagoId}
Consulta estado del pago para refrescar UI.

## Limitacion importante
El link clasico de PayPal no confirma el pago automaticamente en backend. Sin IPN o webhook no se debe fingir aprobacion automatica.
