# Modulo Auth

## Descripcion del modulo
Este modulo se encarga de la autenticacion de usuarios para web y movil.
Resuelve el problema de validar credenciales activas y devolver una respuesta de login consistente para el frontend.

## Responsabilidades
- Validar correo y password enviados por el cliente.
- Verificar que el usuario exista y este activo.
- Registrar usuarios nuevos desde el canal movil con rol base USUARIO.
- Aplicar la regla de negocio del parcial: el rol USUARIO no puede entrar por login web.
- Entregar la informacion basica del usuario autenticado.

## Clases principales
### Controllers
- AuthController: expone los endpoints de login web, login movil y registro movil.

### Services
- AuthService: contiene la logica de autenticacion, registro movil, validaciones y reglas de acceso por canal.

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

### POST /api/auth/movil/register
Se usa para registrar una cuenta desde la app movil.
Recibe:
- Body RegisterMovilRequest con nombre, correo y password.
Reglas:
- El correo debe ser unico.
- El password debe tener al menos 6 caracteres.
- El usuario se crea activo con rol USUARIO.
Devuelve:
- LoginResponse con id, nombre, correo, rol y departamentoId.

## Relacion con otros modulos
- usuarios: consulta UsuarioRepository para buscar usuarios activos y validar credenciales.
- shared: usa ApiException para errores de autenticacion y se apoya en el manejo global de excepciones.
