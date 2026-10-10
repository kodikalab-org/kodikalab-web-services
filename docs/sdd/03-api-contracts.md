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

## Users/Profile

`GET /api/users/me` y `PUT /api/users/me` operan sobre el perfil del usuario autenticado y **dependen del rol de la sesión**: `PRACTICANTE` gestiona la tabla `practicante` y `COACH` gestiona la tabla `coach`. Requieren sesión HTTP iniciada por `POST /api/auth/login`. El cliente no envía `usuarioId` ni rol; ambos se resuelven desde la sesión. Un body con los campos del otro rol se rechaza con `400` (faltan los campos obligatorios del rol propio) y nunca escribe en la tabla del otro rol.

### US-03 — Perfil competitivo del practicante

`GET /api/users/me` devuelve el perfil `practicante` del usuario autenticado con rol `PRACTICANTE`.

`PUT /api/users/me` crea o actualiza el perfil `practicante` asociado al `usuario.id` autenticado.

Request:

```json
{
  "codigoEstudiante": "20240001",
  "carrera": "Ingeniería de Software",
  "cicloAcademico": 5,
  "nivelCompetitivo": "INTERMEDIO",
  "codeforcesHandle": "tourist",
  "atcoderHandle": "tourist_atcoder",
  "vjudgeHandle": "usuario_vjudge"
}
```

Respuesta:

```json
{
  "message": "Perfil actualizado correctamente",
  "profile": {
    "email": "test@gmail.com",
    "role": "PRACTICANTE",
    "codigoEstudiante": "20240001",
    "carrera": "Ingeniería de Software",
    "cicloAcademico": 5,
    "nivelCompetitivo": "INTERMEDIO",
    "codeforcesHandle": "tourist",
    "codeforcesRating": 0,
    "atcoderHandle": "tourist_atcoder",
    "vjudgeHandle": "usuario_vjudge"
  }
}
```

Contrato alineado al ERD:

- Tabla física `practicante`; PK/FK compartida `usuario_id -> usuario.id`.
- `codigoEstudiante` es obligatorio, máximo 20 caracteres y único (comparación exacta, igual que la restricción `uq_practicante_codigo_estudiante`).
- `carrera` es obligatoria, máximo 100 caracteres.
- `cicloAcademico` es obligatorio y mayor o igual a 1.
- `nivelCompetitivo` acepta únicamente `PRINCIPIANTE`, `INTERMEDIO` o `AVANZADO`.
- Los handles de plataformas son opcionales, máximo 50 caracteres y formato seguro `[A-Za-z0-9._-]`.
- Las plataformas persistidas son las del ERD: Codeforces, AtCoder y VJudge. **LeetCode no está implementado** porque no existe en `oficial.erd`.
- `codeforcesRating` no se acepta como dato confiable desde el cliente; el backend lo obtiene desde la API pública de Codeforces cuando `codeforcesHandle` se valida correctamente.
- Si Codeforces no confirma el usuario (`FAILED`, `404`, timeout o error no confirmable), el sistema actualiza los demás datos válidos pero no persiste el nuevo `codeforcesHandle` ni `codeforcesRating`; conserva valores previos de Codeforces si existían y, si no, deja ambos en `null`. En ese caso la respuesta es `200` con `message` = "Perfil actualizado correctamente, pero Codeforces no confirmó el identificador informado; se conservaron los datos previos de Codeforces". La consulta a Codeforces se realiza antes de abrir la transacción de base de datos.
- No se persisten estados de vinculación externa (`PENDIENTE`, `VERIFICADO`, etc.) porque el ERD no define columnas para ello.

Errores con cuerpo `{ "message": "...", "errors": {} }`:

- `400`: datos inválidos o JSON inválido; `errors` puede contener mensajes por campo.
- `401`: no existe sesión autenticada válida.
- `404`: el perfil todavía no existe al consultar `GET /users/me`.
- `409`: `codigoEstudiante` ya está vinculado a otro practicante.

### US-03 — Perfil del coach

`GET /api/users/me` devuelve el perfil `coach` del usuario autenticado con rol `COACH`.

`PUT /api/users/me` crea o actualiza el perfil `coach` asociado al `usuario.id` autenticado. Es prerrequisito de US-04: `grupo_estudio.coach_id` referencia `coach.usuario_id`.

Request:

```json
{
  "especialidadPrincipal": "Grafos y Programación Dinámica",
  "organizacionClub": "Club de Programación Competitiva",
  "aniosExperiencia": 4,
  "presentacion": "Entrenador de maratones ICPC."
}
```

Respuesta:

```json
{
  "message": "Perfil actualizado correctamente",
  "profile": {
    "email": "test@gmail.com",
    "role": "COACH",
    "especialidadPrincipal": "Grafos y Programación Dinámica",
    "organizacionClub": "Club de Programación Competitiva",
    "aniosExperiencia": 4,
    "presentacion": "Entrenador de maratones ICPC."
  }
}
```

Contrato alineado al ERD:

- Tabla física `coach`; PK/FK compartida `usuario_id -> usuario.id`.
- `especialidadPrincipal` es obligatoria, texto libre de máximo 120 caracteres (el ERD no define un dominio cerrado).
- `organizacionClub` es opcional, máximo 150 caracteres; vacío se guarda como `null`.
- `aniosExperiencia` es obligatorio, entre 0 y 60.
- `presentacion` es opcional, máximo 500 caracteres; vacío se guarda como `null`.
- No se persisten estados de verificación del coach ni especialidades como catálogo, porque el ERD no los define.

Errores con cuerpo `{ "message": "...", "errors": {} }`:

- `400`: datos inválidos, JSON inválido o body de practicante; `errors` puede contener mensajes por campo.
- `401`: no existe sesión autenticada válida.
- `404`: el perfil de coach todavía no existe al consultar `GET /users/me`.


## Teams — US04, US05 y US06 implementadas

Las funcionalidades de creación de grupos, solicitud de ingreso y revisión de membresías están implementadas.

Los endpoints utilizan la sesión HTTP creada mediante `POST /api/auth/login`.

Las operaciones de Teams utilizan las tablas oficiales `grupo_estudio` y `practicante_grupo`. No se utiliza una tabla adicional `solicitud_grupo`.

### GET /api/teams — Listar grupos activos

Devuelve los grupos de estudio cuyo estado es `ACTIVO`.

Respuesta exitosa: `200 OK`.

```json
[
  {
    "groupId": 1,
    "name": "Entrenamiento de Grafos",
    "description": "Preparación para competencias ICPC",
    "expectedLevel": "Div3",
    "maxCapacity": 15,
    "sessionSchedule": "Lunes de 18:00 a 20:00",
    "status": "ACTIVO",
    "visibility": "PUBLICO"
  }
]
```

Reglas:
- Solamente se listan grupos con estado `ACTIVO`.
- El código de invitación no se incluye en el listado.
- Si no existen grupos activos, se devuelve una lista vacía.

### US04 — Crear grupo de estudio

`POST /api/teams`

Rol requerido: `COACH`, con perfil de coach registrado.

Request:

```json
{
  "name": "Entrenamiento de Grafos",
  "description": "Preparación para competencias ICPC",
  "expectedLevel": "Div3",
  "maxCapacity": 15,
  "sessionSchedule": "Lunes y miércoles de 18:00 a 20:00",
  "visibility": "PUBLICO"
}
```

Respuesta exitosa: `201 Created`.

```json
{
  "message": "Grupo creado correctamente",
  "groupId": 1,
  "name": "Entrenamiento de Grafos",
  "invitationCode": "CODIGO_GENERADO"
}
```

Reglas:
- Solamente un usuario `COACH` puede crear grupos.
- El coach debe tener su perfil registrado.
- El grupo se crea con estado `ACTIVO`.
- Se genera automáticamente un código único de invitación.
- La capacidad máxima debe estar entre 1 y 1000.
- La visibilidad `ARCHIVADO` no está permitida al crear grupos.
- El grupo se guarda en `grupo_estudio`.

### US05 — Ingresar o solicitar ingreso a un grupo

`POST /api/teams/{id}/join`

Rol requerido: `PRACTICANTE`, con perfil registrado.

Parámetros:
- `id`: identificador del grupo.
- `invitationCode`: parámetro de consulta opcional.

No requiere cuerpo JSON.

#### Caso 1: Grupo público

Un practicante que solicita ingresar a un grupo `PUBLICO` y tiene cupo disponible obtiene directamente una membresía `ACTIVO`.

Respuesta: `201 Created`.

```json
{
  "message": "Ingreso al grupo realizado correctamente",
  "membershipId": 1,
  "groupId": 1,
  "status": "ACTIVO"
}
```

#### Caso 2: Grupo protegido sin código

Si el grupo tiene visibilidad `PROTEGIDO` y el practicante no proporciona un código de invitación válido, se registra una solicitud con estado `PENDIENTE`.

Respuesta: `201 Created`.

```json
{
  "message": "Solicitud de ingreso registrada correctamente",
  "membershipId": 2,
  "groupId": 1,
  "status": "PENDIENTE"
}
```

#### Caso 3: Grupo protegido con código válido

El practicante puede ingresar directamente a un grupo `PROTEGIDO` si proporciona el código de invitación correcto.

Ejemplo:

`POST /api/teams/1/join?invitationCode=CODIGO_GENERADO`

Respuesta: `201 Created`, con estado `ACTIVO`.

Reglas:
- El grupo debe existir y encontrarse `ACTIVO`.
- No se permite ingresar a grupos con visibilidad `ARCHIVADO`.
- Solo un `PRACTICANTE` con perfil registrado puede ingresar.
- Se verifica el cupo disponible antes de registrar el ingreso o la solicitud.
- Únicamente las membresías `ACTIVO` cuentan para el cupo.
- No se permite crear una solicitud duplicada `PENDIENTE`.
- Un practicante que ya es miembro `ACTIVO` no puede ingresar nuevamente.
- Las membresías `RECHAZADO` y `RETIRADO` pueden regresar a `PENDIENTE` mediante una nueva solicitud.
- Una membresía `EXPULSADO` no puede volver a solicitar ingreso según la política implementada.
- Las solicitudes se almacenan en `practicante_grupo`, reutilizando el registro del practicante cuando corresponde.
- `fecha_ingreso` representa inicialmente la fecha de solicitud y se actualiza cuando la membresía pasa a `ACTIVO`.

### US06 — Consultar solicitudes pendientes

`GET /api/teams/{id}/memberships?status=PENDIENTE`

Rol requerido: `COACH` responsable del grupo.

Respuesta exitosa: `200 OK`.

```json
[
  {
    "membershipId": 2,
    "groupId": 1,
    "practitionerId": 5,
    "status": "PENDIENTE",
    "requestedAt": "2026-10-09T19:00:00-05:00"
  }
]
```

Reglas:
- Solamente el coach responsable puede consultar las solicitudes.
- El parámetro `status` debe ser `PENDIENTE`.
- Si no existen solicitudes pendientes, se devuelve una lista vacía.
- `membershipId` identifica un registro real de `practicante_grupo`.

### US06 — Aceptar o rechazar solicitudes

`PATCH /api/teams/{id}/memberships/{memberId}`

Rol requerido: `COACH` responsable del grupo.

Parámetros:
- `id`: identificador del grupo.
- `memberId`: identificador de la membresía en `practicante_grupo`.

Request para aceptar:

```json
{
  "decision": "ACEPTAR"
}
```

Request para rechazar:

```json
{
  "decision": "RECHAZAR"
}
```

#### Aceptación

Respuesta exitosa: `200 OK`.

```json
{
  "message": "Solicitud revisada correctamente",
  "membershipId": 2,
  "groupId": 1,
  "status": "ACTIVO"
}
```

Reglas:
- La membresía debe estar `PENDIENTE`.
- Se verifica que el coach sea responsable del grupo.
- Se comprueba que exista cupo disponible.
- El estado cambia de `PENDIENTE` a `ACTIVO`.
- `fecha_ingreso` se actualiza con la fecha de aceptación.
- El practicante conserva el rol de equipo `MIEMBRO`.
- No se crea una segunda membresía.

#### Rechazo

Respuesta exitosa: `200 OK`.

```json
{
  "message": "Solicitud revisada correctamente",
  "membershipId": 2,
  "groupId": 1,
  "status": "RECHAZADO"
}
```

Reglas:
- La membresía debe estar `PENDIENTE`.
- El estado cambia de `PENDIENTE` a `RECHAZADO`.
- No se crea una tabla ni un registro adicional de solicitud.
- El practicante puede solicitar ingreso nuevamente.

### Estados de membresía

La columna `practicante_grupo.estado` utiliza los siguientes valores:

- `PENDIENTE`: solicitud a la espera de respuesta.
- `ACTIVO`: practicante integrante del grupo.
- `RECHAZADO`: solicitud rechazada.
- `RETIRADO`: practicante que abandonó el grupo.
- `EXPULSADO`: practicante expulsado del grupo.

Los estados se representan con `MembershipStatus` y se persisten como texto mediante `EnumType.STRING`.

### Respuestas de error

Las respuestas de error de Teams siguen el formato:

```json
{
  "message": "Descripción del error",
  "errors": {}
}
```

Códigos HTTP:
- `400 Bad Request`: datos inválidos, decisión desconocida o parámetros incorrectos.
- `401 Unauthorized`: ausencia de sesión válida.
- `403 Forbidden`: rol no autorizado, coach ajeno o acceso prohibido.
- `404 Not Found`: grupo, membresía o perfil inexistente.
- `409 Conflict`: solicitud duplicada, membresía activa, cupo lleno o solicitud ya respondida.

No se exponen trazas internas ni detalles SQL en las respuestas.

### Persistencia y compatibilidad

US04 utiliza `grupo_estudio`.

US05 y US06 utilizan exclusivamente `practicante_grupo`.

La implementación no requiere la entidad ni la tabla adicional `solicitud_grupo`.

Las instalaciones existentes de PostgreSQL deben admitir los estados `PENDIENTE` y `RECHAZADO` en la restricción CHECK de `practicante_grupo.estado`. Este ajuste debe aplicarse mediante una migración reproducible antes del despliegue.

Las pruebas de integración verifican creación de grupos, solicitudes pendientes, aceptación, rechazo, autorización del coach, duplicados y capacidad.


## Rutas pendientes: no implementadas ni publicadas

Las siguientes rutas todavía no están implementadas ni publicadas en Swagger. Aunque algunos módulos ya tienen funcionalidades operativas, los endpoints enumerados a continuación permanecen pendientes de desarrollo.
## Teams

```txt
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
