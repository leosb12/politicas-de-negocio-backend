# Modulo Departamentos

## Descripcion del modulo
Este modulo administra los departamentos de la organizacion y la asignacion de usuarios a cada area.
Resuelve el problema de mantener una estructura organizativa clara para luego usarla en roles, politicas y ejecucion de flujo.

## Responsabilidades
- Crear, listar, editar, activar/desactivar y eliminar departamentos.
- Listar usuarios por departamento.
- Reasignar usuarios de un departamento a otro.
- Validar que solo un ADMIN activo pueda hacer operaciones administrativas.

## Clases principales
### Controllers
- AdminDepartamentoController: expone la API de administracion de departamentos.

### Services
- AdminDepartamentoService: aplica reglas de negocio para departamentos, reasignaciones y validaciones de seguridad.

### Models
- Departamento: representa un departamento organizacional.

## Endpoints
### POST /api/admin/departamentos
Se usa para crear un departamento.
Recibe:
- Header X-Admin-User-Id.
- Body CreateDepartamentoRequest (nombre, descripcion).
Devuelve:
- DepartamentoResponse del departamento creado.

### GET /api/admin/departamentos
Se usa para listar departamentos.
Recibe:
- Header X-Admin-User-Id.
Devuelve:
- Lista de DepartamentoResponse.

### GET /api/admin/departamentos/{departamentoId}
Se usa para obtener un departamento por id.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId.
Devuelve:
- DepartamentoResponse.

### PUT /api/admin/departamentos/{departamentoId}
Se usa para actualizar datos de un departamento.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId.
- Body UpdateDepartamentoRequest.
Devuelve:
- DepartamentoResponse actualizado.

### PATCH /api/admin/departamentos/{departamentoId}/activar
Se usa para activar un departamento.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId.
Devuelve:
- DepartamentoResponse actualizado.

### PATCH /api/admin/departamentos/{departamentoId}/desactivar
Se usa para desactivar un departamento.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId.
Devuelve:
- DepartamentoResponse actualizado.

### DELETE /api/admin/departamentos/{departamentoId}
Se usa para eliminar un departamento.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId.
Devuelve:
- Sin contenido (204 No Content).

### GET /api/admin/departamentos/{departamentoId}/usuarios
Se usa para listar usuarios asignados a un departamento.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId.
Devuelve:
- Lista de UserResponse.

### POST /api/admin/departamentos/{departamentoId}/reasignar-usuarios
Se usa para mover usuarios de un departamento origen a un departamento destino.
Recibe:
- Header X-Admin-User-Id.
- Path departamentoId (origen).
- Body ReasignarDepartamentoRequest (departamentoDestinoId).
Devuelve:
- DepartamentoResponse del departamento origen ya sin esos usuarios.

## Relacion con otros modulos
- usuarios: valida administradores y reasigna usuarios entre departamentos.
- politicas: los departamentos se usan como carriles y responsables en nodos de flujo.
- shared: usa ApiException y manejo global de errores.
