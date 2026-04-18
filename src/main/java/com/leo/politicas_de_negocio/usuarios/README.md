# Modulo Usuarios

## Descripcion del modulo
Este modulo concentra la gestion administrativa de usuarios y roles.
Resuelve el problema de administrar altas, cambios de estado, asignacion de roles y mantenimiento del catalogo de roles del sistema.

## Responsabilidades
- Crear, listar, editar y activar/desactivar usuarios.
- Asignar o quitar roles a usuarios.
- Crear, listar, editar, activar/desactivar y eliminar roles.
- Validar que solo un ADMIN activo pueda ejecutar estas acciones.

## Clases principales
### Controllers
- AdminManagementController: expone toda la API administrativa de usuarios y roles.

### Services
- AdminManagementService: implementa reglas de negocio, validaciones y operaciones de persistencia para usuarios y roles.

### Models
- Usuario: representa una cuenta de usuario del sistema.
- Rol: representa un rol asignable a usuarios.

## Endpoints
### POST /api/admin/usuarios
Se usa para crear un usuario nuevo.
Recibe:
- Header X-Admin-User-Id.
- Body CreateUserRequest (nombre, correo, password, rol, departamentoId, activo).
Devuelve:
- UserResponse con los datos del usuario creado.

### GET /api/admin/usuarios
Se usa para listar usuarios.
Recibe:
- Header X-Admin-User-Id.
Devuelve:
- Lista de UserResponse.

### GET /api/admin/usuarios/{usuarioId}
Se usa para consultar el detalle de un usuario.
Recibe:
- Header X-Admin-User-Id.
- Path usuarioId.
Devuelve:
- UserResponse.

### PUT /api/admin/usuarios/{usuarioId}
Se usa para actualizar datos de un usuario.
Recibe:
- Header X-Admin-User-Id.
- Path usuarioId.
- Body UpdateUserRequest.
Devuelve:
- UserResponse actualizado.

### PATCH /api/admin/usuarios/{usuarioId}/activar
Se usa para activar una cuenta.
Recibe:
- Header X-Admin-User-Id.
- Path usuarioId.
Devuelve:
- UserResponse actualizado.

### PATCH /api/admin/usuarios/{usuarioId}/desactivar
Se usa para desactivar una cuenta.
Recibe:
- Header X-Admin-User-Id.
- Path usuarioId.
Devuelve:
- UserResponse actualizado.

### PATCH /api/admin/usuarios/{usuarioId}/rol
Se usa para cambiar el rol de un usuario.
Recibe:
- Header X-Admin-User-Id.
- Path usuarioId.
- Body UpdateUserRoleRequest.
Devuelve:
- UserResponse actualizado.

### PATCH /api/admin/usuarios/{usuarioId}/rol/quitar
Se usa para quitar el rol actual de un usuario y dejarlo con rol base.
Recibe:
- Header X-Admin-User-Id.
- Path usuarioId.
Devuelve:
- UserResponse actualizado.

### POST /api/admin/roles
Se usa para crear un rol nuevo.
Recibe:
- Header X-Admin-User-Id.
- Body CreateRoleRequest.
Devuelve:
- RoleResponse del rol creado.

### GET /api/admin/roles
Se usa para listar roles.
Recibe:
- Header X-Admin-User-Id.
Devuelve:
- Lista de RoleResponse.

### GET /api/admin/roles/{rolId}
Se usa para consultar un rol por id.
Recibe:
- Header X-Admin-User-Id.
- Path rolId.
Devuelve:
- RoleResponse.

### PUT /api/admin/roles/{rolId}
Se usa para actualizar un rol.
Recibe:
- Header X-Admin-User-Id.
- Path rolId.
- Body UpdateRoleRequest.
Devuelve:
- RoleResponse actualizado.

### PATCH /api/admin/roles/{rolId}/activar
Se usa para activar un rol.
Recibe:
- Header X-Admin-User-Id.
- Path rolId.
Devuelve:
- RoleResponse actualizado.

### PATCH /api/admin/roles/{rolId}/desactivar
Se usa para desactivar un rol.
Recibe:
- Header X-Admin-User-Id.
- Path rolId.
Devuelve:
- RoleResponse actualizado.

### DELETE /api/admin/roles/{rolId}
Se usa para eliminar un rol.
Recibe:
- Header X-Admin-User-Id.
- Path rolId.
Devuelve:
- Sin contenido (204 No Content).

## Relacion con otros modulos
- departamentos: valida la existencia de departamentoId al crear o editar usuarios.
- shared: usa ApiException y manejo global de errores.
- auth: este modulo es la fuente de datos que usa el login.
