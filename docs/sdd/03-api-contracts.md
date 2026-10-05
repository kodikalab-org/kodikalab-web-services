# 03 - API Contracts

## Convenciones generales

URL base local:

```txt
http://localhost:8080/api
```

## Estado de la actualización

La fuente de datos vigente es `docs/sdd/assets/oficial.erd`. **`auth`/`users` ya implementan los límites, roles y persistencia aquí descritos.** La adaptación no migra cuentas antiguas ni implementa los demás módulos.

El ERD no define los nombres del JSON. Se conservan las claves existentes (`firstName`, `lastName`, `email`, `password`, `role`), pero **los valores de enums de cuenta están en español tanto en Java como en HTTP y SQL**, persistidos directamente con `@Enumerated(EnumType.STRING)` y sin converters. Los contratos de otros módulos son propuestas pendientes, sin implementación ni mappings HTTP activos.

## Auth

```txt
POST /auth/register
POST /auth/login
```

### US-01 — Registro de cuenta

`POST /api/auth/register` devuelve `201 Created`.

```json
{
  "firstName": "Usuario",
  "lastName": "Prueba",
  "email": "test@gmail.com",
  "password": "Password123",
  "role": "PRACTICANTE"
}
```

Respuesta pública, sin contraseña, hash ni JWT:

```json
{
  "message": "Registro exitoso",
  "email": "test@gmail.com",
  "role": "PRACTICANTE"
}
```

### Contrato implementado: claves JSON conservadas, enums en español

- `firstName` y `lastName` siguen obligatorios, con el límite individual existente de 80 caracteres. Su unión normalizada, separada por un espacio, **no puede superar 150 caracteres**, porque se persiste como `usuario.nombre_completo`; no se crean columnas separadas en la nueva tabla.
- `email` es obligatorio, válido, único y de **hasta 100 caracteres**, conforme a `usuario.correo`. Se normaliza a minúsculas y sin espacios externos.
- `password` mantiene la política existente: mínimo 8 caracteres, una mayúscula y un número; máximo técnico BCrypt de 72 bytes UTF-8, sin truncado.
- `role` acepta únicamente `PRACTICANTE` o `COACH`, idénticos en Java/HTTP/SQL y sin ordinales numéricos. **`PRACTITIONER` y `ADMIN` se rechazan con `400`**, con mensaje `El rol debe ser PRACTICANTE o COACH`.
- La cuenta nueva utiliza `ACTIVO` en Java y SQL (`estado_cuenta`).

Los clientes deben enviar `PRACTICANTE` en lugar de `PRACTITIONER` y utilizar la autoridad de sesión `ROLE_PRACTICANTE`. Reiniciar o invalidar sesiones previas al desplegar este cambio. La persistencia SQL ya usaba los valores españoles y no requiere traducir filas por este ajuste.

La conservación de las claves JSON es una decisión de compatibilidad del SDD, no una afirmación de que el ERD contenga columnas `first_name`/`last_name`. Un cambio futuro a un campo HTTP `fullName` debe coordinarse expresamente con el cliente.

Errores con cuerpo `{ "message": "...", "errors": {} }`:

- `400`: datos inválidos; `errors` puede contener mensajes por campo.
- `400`, contraseña débil: `La contraseña debe contener al menos 8 caracteres, una mayúscula y un número`.
- `409`, correo duplicado: `El correo institucional ya está vinculado a una cuenta existente`.

No se incluyen contraseñas, valores rechazados ni detalles SQL en errores. El ERD no define una lista de dominios institucionales permitidos ni exige generar perfiles con datos académicos ficticios. Crear la cuenta base no equivale a completar `coach`/`practicante`; su contrato pertenece al flujo de perfiles. La autorización de coach y las reglas de incorporación siguen siendo decisiones de seguridad posteriores.

El registro no emite una sesión de login. La colección `tests/US01-register.postman_collection.json` y `tests/README.md` están actualizadas al modelo de `usuario`; incluyen rechazo de `ADMIN` y límites de nombre/correo.

### US-02 — Inicio de sesión

`POST /api/auth/login` recibe:

```json
{
  "email": "test@gmail.com",
  "password": "Password123"
}
```

Una cuenta `usuario.estado_cuenta = ACTIVO` (enum Java `ACTIVO`) con credenciales correctas obtiene `200 OK`:

```json
{
  "message": "Inicio de sesión exitoso",
  "email": "test@gmail.com",
  "role": "PRACTICANTE"
}
```

El rol se obtiene de la cuenta, nunca de un valor suministrado por el cliente. La respuesta guarda una sesión HTTP mediante cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`, `Secure` configurable para HTTPS); no devuelve JWT, contraseña, hash ni ID de sesión en el JSON. Al autenticar una sesión existente, se renueva su ID. Expira tras 30 minutos de inactividad.

- `400`: correo ausente/mal formado/superior a 100 caracteres, contraseña ausente/vacía o JSON inválido.
- `401`: correo no registrado, contraseña incorrecta, contraseña superior a 72 bytes UTF-8 o cuenta `SUSPENDIDO` (enum Java `SUSPENDIDO`). Todos usan `{ "message": "Credenciales inválidas", "errors": {} }` sin revelar existencia ni estado.
- Los estados anteriores `INACTIVE`/`BLOCKED` requieren una decisión de migración; no siguen siendo valores oficiales.
- El correo se normaliza; la contraseña no se recorta ni se valida de nuevo contra la política de fortaleza de registro.
- Un intento fallido no crea sesión ni reemplaza una identidad ya autenticada; puede reintentarse.

Los demás endpoints siguen públicos (`permitAll()`) durante el desarrollo. Devolver el rol no equivale a protegerlos por rol. La recuperación de acceso descrita en `tarea.md` queda pendiente: no se definió su contrato técnico ni se implementan endpoints de recuperación.

La sesión y el formato de respuesta se conservan. La colección `tests/US02-login.postman_collection.json`, su fixture SQL y `tests/US02-login.md` usan el modelo oficial; un `401` con un correo ausente no prueba una cuenta suspendida existente. Diseño: `10-us02-login.md`. Secuencia de alineación: `11-erd-oficial-alignment.md`.

## Rutas pendientes: no implementadas ni publicadas

Las siguientes rutas son propuestas para historias futuras. Los controllers vacíos del scaffolding fueron retirados: **no aparecen en Swagger y actualmente devuelven `404`**. No deben considerarse funcionalidades disponibles.

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
