# Modulo Auth

## Descripcion del modulo
Este modulo se encarga de la autenticacion de usuarios para web y movil.
Resuelve el problema de validar credenciales activas y devolver una respuesta de login consistente para el frontend.

## Responsabilidades
- Validar correo y password enviados por el cliente.
- Verificar que el usuario exista y este activo.
- Aplicar la regla de negocio del parcial: el rol USUARIO no puede entrar por login web.
- Entregar la informacion basica del usuario autenticado.

## Clases principales
### Controllers
- AuthController: expone los endpoints de login web y login movil.

### Services
- AuthService: contiene la logica de autenticacion, validaciones y reglas de acceso por canal.

### Models
- Este modulo no define modelos propios.
- Usa Usuario desde el modulo usuarios.

## Endpoints
### POST /api/auth/web/login
Se usa para autenticar usuarios desde la web.
Recibe:
- Body LoginRequest con correo y password.
Devuelve:
- LoginResponse con id, nombre, correo, rol y departamentoId.

### POST /api/auth/movil/login
Se usa para autenticar usuarios desde la app movil.
Recibe:
- Body LoginRequest con correo y password.
Devuelve:
- LoginResponse con id, nombre, correo, rol y departamentoId.

## Relacion con otros modulos
- usuarios: consulta UsuarioRepository para buscar usuarios activos y validar credenciales.
- shared: usa ApiException para errores de autenticacion y se apoya en el manejo global de excepciones.
