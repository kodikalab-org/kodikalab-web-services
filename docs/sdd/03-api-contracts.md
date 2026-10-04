# 03 - API Contracts

## Convenciones generales

URL base local:

```txt
http://localhost:8080/api
```

Todos los contratos son estructurales durante el scaffolding. Los DTOs pueden cambiar cuando se implemente cada historia.

## Auth

```txt
POST /auth/register
POST /auth/login
```

### US-01 — Registro de cuenta

`POST /api/auth/register` devuelve `201 Created`.

```json
{
  "firstName": "Matias",
  "lastName": "Del Castillo",
  "email": "matias@upc.edu.pe",
  "password": "Password123",
  "role": "PRACTITIONER"
}
```

Respuesta pública, sin contraseña, hash ni JWT:

```json
{
  "message": "Registro exitoso",
  "email": "matias@upc.edu.pe",
  "role": "PRACTITIONER"
}
```

Reglas: nombres obligatorios de hasta 80 caracteres; correo obligatorio, válido, de hasta 255 caracteres y único; contraseña de al menos 8 caracteres con mayúscula y número; rol obligatorio entre `PRACTITIONER`, `COACH` y `ADMIN` (nombre textual, no ordinal numérico). BCrypt admite como máximo 72 bytes UTF-8; no se truncan contraseñas. Nombre, apellido y correo se guardan sin espacios externos, y el correo en minúsculas.

Errores con cuerpo `{ "message": "...", "errors": {} }`:

- `400`: datos inválidos; `errors` puede contener mensajes por campo.
- `400`, contraseña débil: `La contraseña debe contener al menos 8 caracteres, una mayúscula y un número`.
- `409`, correo duplicado: `El correo institucional ya está vinculado a una cuenta existente`.

No se incluyen contraseñas, valores rechazados ni detalles SQL en errores. No se define todavía una lista de dominios institucionales permitidos. Los roles disponibles y el estado `ACTIVE` siguen `tarea.md`; la autorización de roles privilegiados es una política futura.

Login conserva su scaffolding y no se implementa en US-01. Colección de validación: `tests/US01-register.postman_collection.json`; instrucciones en `tests/README.md`.

## Users/Profile

```txt
GET  /users/me
PUT  /users/me
POST /users/me/handles
```

## Teams

```txt
POST  /teams
POST  /teams/{id}/join
PATCH /teams/{id}/memberships/{memberId}
GET   /teams/{id}/members
```

## Problems

```txt
POST /problems/assign
GET  /problems/assigned
POST /problems/{id}/submit
GET  /problems/{id}/resources
```

## Analytics

```txt
GET  /analytics/teams/{teamId}/topics
GET  /analytics/teams/{teamId}/standings
GET  /analytics/teams/{teamId}/weaknesses
POST /analytics/teams/{teamId}/competitions
GET  /analytics/users/me/independent-progress
```

## AI Assistant

```txt
POST /assistant/query
```

## Validación manual

Usar Swagger UI para revisar y probar endpoints:

```txt
http://localhost:8080/api/swagger-ui.html
```
