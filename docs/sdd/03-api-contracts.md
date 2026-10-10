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

Respuesta pública, sin contraseña, hash ni JWT. Incluye el `recoveryCode` de la cuenta, que el titular debe guardar (ver "Recuperación de acceso sin correo"):

```json
{
  "message": "Registro exitoso",
  "email": "test@gmail.com",
  "role": "PRACTICANTE",
  "recoveryCode": "ABCD-EFGH-IJKL-MNOP-QRST-UVWX"
}
```

### Contrato implementado: claves JSON conservadas, enums en español

- `firstName` y `lastName` siguen obligatorios, con el límite individual existente de 80 caracteres. Su unión normalizada, separada por un espacio, **no puede superar 150 caracteres**, porque se persiste como `usuario.nombre_completo`; no se crean columnas separadas en la nueva tabla.
- `email` es obligatorio, válido, único y de **hasta 100 caracteres**, conforme a `usuario.correo`. Se normaliza a minúsculas y sin espacios externos.
- `password` mantiene la política existente: mínimo 8 caracteres, una mayúscula y un número; máximo técnico BCrypt de 72 bytes UTF-8, sin truncado.
- `role` acepta únicamente `PRACTICANTE` o `COACH`, idénticos en Java/HTTP/SQL y sin ordinales numéricos. **`PRACTITIONER` y `ADMIN` se rechazan con `400`**, con mensaje `El rol debe ser PRACTICANTE o COACH`.
- La cuenta nueva utiliza `ACTIVO` en Java y SQL (`estado_cuenta`).

Los clientes deben enviar `PRACTICANTE` en lugar de `PRACTITIONER` y utilizar la autoridad `ROLE_PRACTICANTE`. Descartar las sesiones y tokens previos al desplegar este cambio. La persistencia SQL ya usaba los valores españoles y no requiere traducir filas por este ajuste.

La conservación de las claves JSON es una decisión de compatibilidad del SDD, no una afirmación de que el ERD contenga columnas `first_name`/`last_name`. Un cambio futuro a un campo HTTP `fullName` debe coordinarse expresamente con el cliente.

Errores con cuerpo `{ "message": "...", "errors": {} }`:

- `400`: datos inválidos; `errors` puede contener mensajes por campo.
- `400`, contraseña débil: `La contraseña debe contener al menos 8 caracteres, una mayúscula y un número`.
- `409`, correo duplicado: `El correo institucional ya está vinculado a una cuenta existente`.

No se incluyen contraseñas, valores rechazados ni detalles SQL en errores. El ERD no define una lista de dominios institucionales permitidos ni exige generar perfiles con datos académicos ficticios. Crear la cuenta base no equivale a completar `coach`/`practicante`; su contrato pertenece al flujo de perfiles. La autorización de coach y las reglas de incorporación siguen siendo decisiones de seguridad posteriores.

El registro no inicia sesión ni entrega token. La colección `tests/US01-register.postman_collection.json` y `tests/README.md` están actualizadas al modelo de `usuario`; incluyen rechazo de `ADMIN` y límites de nombre/correo.

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
  "role": "PRACTICANTE",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

El rol se obtiene de la cuenta, nunca de un valor suministrado por el cliente. `token` es un JWT firmado; `tokenType` es siempre `Bearer` y `expiresIn` la vigencia en segundos (`JWT_EXPIRATION`). No se crea sesión ni cookie, y el JSON no incluye contraseña ni hash. Cada login emite un token nuevo; los anteriores siguen válidos hasta su vencimiento.

- `400`: correo ausente/mal formado/superior a 100 caracteres, contraseña ausente/vacía o JSON inválido.
- `401`: correo no registrado, contraseña incorrecta, contraseña superior a 72 bytes UTF-8 o cuenta `SUSPENDIDO` (enum Java `SUSPENDIDO`). Todos usan `{ "message": "Credenciales inválidas", "errors": {} }` sin revelar existencia ni estado.
- Los estados anteriores `INACTIVE`/`BLOCKED` requieren una decisión de migración; no siguen siendo valores oficiales.
- El correo se normaliza; la contraseña no se recorta ni se valida de nuevo contra la política de fortaleza de registro.
- Un intento fallido no emite token ni invalida uno vigente; puede reintentarse.

### Autenticación y autorización (Spring Security + JWT)

La API es **sin estado**: no hay sesión HTTP ni cookies. Tras `POST /api/auth/login` el cliente envía
`Authorization: Bearer <token>` en cada petición.

| Acceso | Endpoints |
| --- | --- |
| Público (sin token) | `POST /auth/register`, `POST /auth/login`, `POST /auth/recovery`, `/swagger-ui.html`, `/v3/api-docs` |
| Solo `COACH` | `POST /teams`, `GET /teams/{id}/memberships`, `PATCH /teams/{id}/memberships/{memberId}`, `POST /problems`, `POST /problems/assign`, `POST /competitions`, `POST`/`PUT`/`GET /competitions/{competitionId}/official-result`, `GET /competitions/teams/{teamId}/official-results`, `GET /analytics/teams/{teamId}/weaknesses` |
| Solo `PRACTICANTE` | `POST /teams/{id}/join`, `POST /competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions`, `GET /analytics/teams/{teamId}/progress/me` |
| Cualquier cuenta autenticada | El resto: `POST /auth/recovery-code`, `GET`/`PUT /users/me`, `GET /teams`, `GET /problems`, `GET /problems/assigned` y `/{id}`, `GET /analytics/teams/{teamId}/standings` |

Las reglas por rol de la tabla se declaran en `config.SecurityConfig`. Los servicios conservan las verificaciones que
dependen de datos (coach responsable del equipo, membresía activa, cuenta `ACTIVO`).

Errores de seguridad, con el cuerpo `{ "message": "...", "errors": {} }`:

- `401`, token ausente: `Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)`.
- `401`, token inválido, manipulado, vencido, firmado con otra clave o de una cuenta inexistente:
  `El token es inválido o expiró: inicie sesión nuevamente`. Ambos incluyen `WWW-Authenticate: Bearer`.
- `403`, rol sin permiso: `No tiene permisos para realizar esta acción`.
- `403`, cuenta que dejó de estar `ACTIVO` después de iniciar sesión: `La cuenta no está habilitada`.

El token es un JWT firmado con HMAC-SHA256 que contiene `sub` (correo), `uid`, `role`, `pwd` (huella del hash de la contraseña), `iat`, `exp`, `jti` e
`iss=kodikalab`; nunca contraseñas ni hashes. El rol efectivo se lee siempre de la base de datos, no del token.
Configuración: `JWT_SECRET` (obligatorio, mínimo 32 caracteres), `JWT_EXPIRATION` (milisegundos, por defecto 24 h) y
`CORS_ALLOWED_ORIGINS`. Detalle y decisiones: `06-security-strategy.md`.

### Recuperación de acceso sin correo (US-02, escenario alternativo)

El sistema no envía correos: el titular verifica su identidad con el **código de recuperación** de su cuenta.

- **Código:** 24 caracteres Base32 en 6 grupos, por ejemplo `ABCD-EFGH-IJKL-MNOP-QRST-UVWX`. Se entrega en la respuesta del registro y en la de cada recuperación; el cliente debe mostrarlo al titular para que lo guarde. **No se guarda en la base de datos**: es un HMAC-SHA256 del correo y del hash vigente de la contraseña, con una clave derivada de `JWT_SECRET`. Es de un solo uso por construcción (al cambiar la contraseña cambia el código) y existe también para las cuentas anteriores a esta función.

`POST /api/auth/recovery` (público):

```json
{
  "email": "test@gmail.com",
  "recoveryCode": "ABCD-EFGH-IJKL-MNOP-QRST-UVWX",
  "newPassword": "Nueva1234"
}
```

`200 OK`:

```json
{
  "message": "Contraseña actualizada. Inicie sesión con la nueva contraseña y guarde su nuevo código de recuperación: el anterior ya no sirve.",
  "recoveryCode": "WXYZ-2345-6723-ABCD-EFGH-IJKL"
}
```

- `400`: campo ausente, correo inválido, contraseña nueva que no cumple la política del registro (8 caracteres, una mayúscula y un número) o de más de 72 bytes UTF-8.
- `401` `Datos de recuperación inválidos`: código incorrecto, correo no registrado, cuenta `SUSPENDIDO` o código ya usado. Todos responden igual, sin revelar si la cuenta existe.
- La contraseña nueva se guarda con BCrypt, los tokens emitidos antes del cambio dejan de valer (`401`) y se habilita un nuevo inicio de sesión. Dos solicitudes simultáneas con el mismo código: solo una gana.

`POST /api/auth/recovery-code` (requiere token): recibe `{ "password": "<contraseña actual>" }` y devuelve `200` con `{ "message": "...", "recoveryCode": "..." }`, el código vigente. Sirve para cuentas creadas antes de esta función o si el titular perdió el código. `401 Credenciales inválidas` si la contraseña no coincide; `400` si falta.

Límites conocidos: rotar `JWT_SECRET` cambia todos los códigos (cada titular puede consultar el nuevo con su contraseña) y todavía no hay limitación de intentos; con 120 bits de entropía, adivinar un código no es factible.

La colección `tests/US02-login.postman_collection.json`, su fixture SQL y `tests/US02-login.md` usan el modelo oficial; un `401` con un correo ausente no prueba una cuenta suspendida existente. Diseño: `10-us02-login.md`. Secuencia de alineación: `11-erd-oficial-alignment.md`.

## Users/Profile

`GET /api/users/me` y `PUT /api/users/me` operan sobre el perfil del usuario autenticado y **dependen del rol de la cuenta autenticada**: `PRACTICANTE` gestiona la tabla `practicante` y `COACH` gestiona la tabla `coach`. Requieren el token Bearer obtenido con `POST /api/auth/login`. El cliente no envía `usuarioId` ni rol; ambos se resuelven desde el token. Un body con los campos del otro rol se rechaza con `400` (faltan los campos obligatorios del rol propio) y nunca escribe en la tabla del otro rol.

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

Los endpoints exigen el token Bearer obtenido con `POST /api/auth/login`.

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

### GET /api/teams/me — Mis equipos

Cualquier cuenta autenticada y `ACTIVO`. Devuelve `200 OK` con un arreglo ordenado por `groupId`, vacío si no hay
nada que mostrar. La forma depende del rol del token y los campos del otro rol no se incluyen.

**Coach:** los grupos que creó (de cualquier estado), con el código de invitación y los contadores. Es el único lugar
donde el coach puede volver a consultar el código de un grupo.

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
    "visibility": "PROTEGIDO",
    "invitationCode": "A1B2C3D4E5F6",
    "activeMembers": 3,
    "pendingRequests": 1
  }
]
```

**Practicante:** los grupos donde tiene o tuvo una membresía, con el estado de esa membresía. Así sabe si lo aceptaron
(`ACTIVO`) o lo rechazaron (`RECHAZADO`) sin necesidad de una notificación.

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
    "visibility": "PROTEGIDO",
    "membership": {
      "membershipId": 2,
      "status": "PENDIENTE",
      "teamRole": "MIEMBRO",
      "joinedAt": "2026-10-09T19:00:00-05:00",
      "leftAt": null
    }
  }
]
```

Reglas:
- `invitationCode`, `activeMembers` y `pendingRequests` solo aparecen para el coach; `membership` solo para el practicante.
- `joinedAt` es la fecha de la solicitud mientras la membresía está `PENDIENTE` y la de ingreso cuando pasa a `ACTIVO`.
- Los contadores del coach cuentan solo membresías `ACTIVO` y `PENDIENTE`.
- `401` sin token válido; `403` cuenta que no está `ACTIVO`.

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
  "message": "Ingreso al grupo registrado correctamente",
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
    "requestedAt": "2026-10-09T19:00:00-05:00",
    "practitioner": {
      "fullName": "Ana Prueba",
      "studentCode": "U2020001",
      "career": "Ingeniería de Software",
      "academicCycle": 5,
      "competitiveLevel": "INTERMEDIO",
      "codeforcesHandle": "tourist",
      "codeforcesRating": 3800,
      "atcoderHandle": null,
      "vjudgeHandle": null
    }
  }
]
```

Reglas:
- Solamente el coach responsable puede consultar las solicitudes.
- El parámetro `status` debe ser `PENDIENTE`.
- Si no existen solicitudes pendientes, se devuelve una lista vacía.
- `membershipId` identifica un registro real de `practicante_grupo`.
- `practitioner` trae el perfil académico y competitivo del postulante para que el coach decida con información. No incluye el correo.
- Las solicitudes salen de la más antigua a la más reciente.

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

## Analytics — US-10

`GET /api/analytics/teams/{teamId}/progress/me/topics` devuelve el progreso por tema del practicante autenticado en
ese equipo. Requiere token Bearer, cuenta `ACTIVO`, rol `PRACTICANTE` y membresía `ACTIVO` propia; el coach dispone del
reporte del equipo (US-12). El avance de cada equipo es independiente. Respuesta `200 OK`:

```json
{
  "teamId": 1,
  "membershipId": 7,
  "userId": 10,
  "assignedProblems": 3,
  "solvedProblems": 1,
  "unclassifiedProblems": 0,
  "reinforcementCriterion": "Se marcan para reforzar los temas con la menor cobertura (todos los empatados) mientras esa cobertura sea menor que 100%. ...",
  "topics": [
    {
      "topicId": 3, "topicName": "Programación dinámica", "assignedProblems": 1, "solvedProblems": 0,
      "unsolvedProblems": 1, "pendingProblems": 0, "coveragePercentage": 0.00,
      "status": "SIN_ACTIVIDAD", "needsReinforcement": true
    },
    {
      "topicId": 1, "topicName": "Grafos", "assignedProblems": 2, "solvedProblems": 1,
      "unsolvedProblems": 1, "pendingProblems": 0, "coveragePercentage": 50.00,
      "status": "EN_PROGRESO", "needsReinforcement": false
    }
  ]
}
```

- Se cuentan los problemas asignados al equipo, sea cual sea el estado de la competencia. Un problema cuenta una sola vez por
  tema aunque se asigne en varias competencias, y está resuelto si el practicante tiene al menos un intento `ACCEPTED`.
  `solvedProblems` total coincide con `acceptedProblems` de `progress/me`.
- `status`: `COMPLETADO` si todos los problemas del tema están resueltos, `SIN_ACTIVIDAD` si no hay ningún intento en ellos y
  `EN_PROGRESO` en los demás casos. Los temas aún sin actividad aparecen igualmente, con su estado inicial.
- `needsReinforcement` marca los temas con la menor cobertura (todos los empatados) mientras sea menor que 100%. La lista sale
  de menor a mayor cobertura y, con la misma, por nombre.
- Un problema sin tema no hace fallar la consulta: se cuenta en `unclassifiedProblems` y no aparece en ningún tema. Un equipo
  sin problemas asignados devuelve `topics: []`.
- Errores `{ "message": "...", "errors": {} }`: `400` identificador inválido, `401` sin token válido, `403` rol distinto de
  `PRACTICANTE`, cuenta no activa o sin membresía `ACTIVO` en el equipo, `404` equipo inexistente, `409` datos faltantes o
  inconsistentes (`errors` nombra el dato afectado y no se devuelve ningún indicador), `503` información no disponible (el
  mensaje invita a reintentar). Detalle y decisiones: [US-10](18-us10-progreso-por-tema.md).

## Analytics — US-11

`GET /api/analytics/teams/{teamId}/standings` consulta resoluciones persistidas del equipo. Requiere token Bearer y cuenta `ACTIVO`: se autoriza al coach responsable o al practicante con membresía `ACTIVO`. La identidad se obtiene desde la sesión; el cliente no elige el usuario solicitante.

Respuesta `200 OK`:

```json
{
  "teamId": 1,
  "status": "CALCULATED",
  "orderingCriterion": "DISTINCT_ACCEPTED_PROBLEMS_DESC",
  "tieCriterion": "SHARED_POSITION_1_1_3",
  "members": [
    { "membershipId": 1, "userId": 10, "fullName": "Usuario Prueba", "acceptedProblems": 2, "position": 1 }
  ]
}
```

Cada problema de catálogo aceptado cuenta una vez por membresía y equipo, incluso entre competencias. Solo se muestran integrantes activos, con orden descendente y empates `1, 1, 3`; el ID de membresía estabiliza el orden de presentación. Sin resoluciones, `status` es `NO_ACTIVITY` y `members` es `[]`. Con intentos válidos sin aceptaciones, se muestran puntuaciones cero compartidas.

Errores con cuerpo `{ "message": "...", "errors": {} }`: `400` por ID inválido, `401` sin token válido, `403` sin autorización/cuenta suspendida, `404` por equipo inexistente, `409` por datos inconsistentes (campos o índices en `errors`), `503` por fallo de persistencia/transacción y `500` por error inesperado. Nunca se devuelven posiciones parciales del cálculo fallido.

En `409` o `503` se añade opcionalmente `lastValidRanking: { "calculatedAt": "...", "ranking": { ... } }`, con el último resultado completo guardado en PostgreSQL para ese equipo. Se conserva el código de error: ese ranking corresponde a la fecha indicada y no se presenta como un cálculo actualizado. Solo se recupera después de verificar nuevamente los permisos actuales. Si no existe, no se puede leer o no se puede verificar la autorización, se omite. Una caída total de PostgreSQL impide recuperarlo durante la caída; el registro permanece almacenado. Cada cálculo válido reemplaza una sola fila por equipo, sin historial.

Detalle de reglas y verificación: [US-11](13-us11-ranking-interno.md).

## Analytics — US-12

`GET /api/analytics/teams/{teamId}/weaknesses` consulta la cobertura por tema. Requiere token Bearer, cuenta `ACTIVO`, rol `COACH` y ser el coach responsable del equipo. Un integrante no puede consultar este reporte, aunque pueda acceder al ranking. La identidad se resuelve desde la sesión; los parámetros del cliente no conceden permisos.

Se consideran competencias `FINALIZADA`, integrantes actualmente `ACTIVO` y problemas distintos del catálogo. Por tema, la cobertura es `100 × problemas aceptados / problemas asignados`. Una aceptación de cualquier integrante incluido resuelve el problema para el equipo. Repeticiones entre intentos o competencias no aumentan el indicador. Un problema con varios temas se cuenta una vez en cada tema.

Respuesta `200 OK`:

```json
{
  "teamId": 1,
  "metric": "DISTINCT_SOLVED_PROBLEMS_OVER_ASSIGNED_PROBLEMS",
  "comparisonCriterion": "EXACT_PROPORTION_MINIMUM_ALL_TIES",
  "comparisonExplanation": "Se comparan proporciones exactas de problemas distintos aceptados sobre asignados; todos los temas con la proporción mínima comparten menor cobertura. El porcentaje se redondea solo para mostrarlo.",
  "activeMembers": 2,
  "pendingResolutions": 1,
  "topics": [
    {
      "topicId": 10,
      "topicName": "Grafos",
      "assignedProblems": 3,
      "solvedProblems": 1,
      "unsolvedProblems": 2,
      "solvingMembers": 1,
      "pendingResolutions": 1,
      "coveragePercentage": 33.33,
      "lowestCoverage": true
    }
  ]
}
```

Se devuelven todos los temas del universo analizado, ordenados por proporción ascendente; `lowestCoverage` identifica **todos** los empatados en el mínimo exacto, antes del redondeo. Dentro del empate, el ID de tema estabiliza la presentación sin establecer prioridades. Los recuentos de problemas sin aceptación y de integrantes con aceptaciones permiten al coach decidir el refuerzo.

`PENDIENTE` no suma al numerador y se informa como recuento de resoluciones únicas, global y por tema; no invalida automáticamente el reporte. Sin ninguna resolución definitiva de integrantes activos en competencias finalizadas, hay información insuficiente y se indica el número de pendientes en el error. Un tema sin aceptaciones permanece en el reporte cuando el conjunto sí tiene información suficiente.

Errores `{ "message": "...", "errors": {} }`: `400` por ID inválido, `401` sin token válido, `403` sin autorización/cuenta suspendida, `404` por equipo inexistente, `409` por información insuficiente o inconsistente (identifica campos, índices o problemas sin clasificación), `503` por información no disponible debido a persistencia/transacción y `500` por error inesperado. Los errores no incluyen conclusiones parciales. Puede repetirse el mismo GET tras corregir los datos o recuperar su disponibilidad. US-12 no almacena reportes ni modifica el último ranking de US-11.

Detalle y verificación: [US-12](14-us12-temas-menor-resolucion.md).

## US-13 — Crear competencia

`POST /api/competitions` crea una competencia (`competencia`) para un equipo y devuelve `201`. Solo puede usarla el
coach responsable de ese equipo, con cuenta `ACTIVO` y token Bearer; el coach se obtiene del token y el equipo
de `teamId`. Es el paso previo para registrar su resultado oficial y para asignarle problemas.

```json
{
  "teamId": 1,
  "eventName": "ICPC Regional 2026",
  "description": "Fase regional",
  "accessType": "PUBLICO_GRUPO",
  "penaltyRule": "ICPC_20_MIN",
  "scoreboardFreezeMinutes": 60,
  "status": "PROGRAMADA",
  "startsAt": "2026-10-20T14:00:00-05:00",
  "endsAt": "2026-10-20T19:00:00-05:00"
}
```

| Campo | Regla |
| --- | --- |
| `teamId` | Obligatorio, entero positivo; el coach debe ser el responsable de ese equipo. |
| `eventName` | Obligatorio, hasta 150 caracteres (se recortan los espacios externos). |
| `description` | Opcional, hasta 500 caracteres; vacío se guarda como `null`. |
| `accessType` | `PUBLICO_GRUPO` (por defecto) o `PRIVADO_PASS`. |
| `accessKey` | Obligatoria con `PRIVADO_PASS` (máximo 72 bytes UTF-8); no se admite con `PUBLICO_GRUPO`. Se guarda solo como hash BCrypt y nunca se devuelve. |
| `penaltyRule` | `ICPC_20_MIN` (por defecto) o `IOI_POINTS`. |
| `scoreboardFreezeMinutes` | Opcional, entre 0 y la duración. Por defecto 60, o la duración si esta es menor. |
| `status` | `PROGRAMADA` (por defecto), `EN_CURSO` o `FINALIZADA`. Se respeta el valor enviado; no se infiere de las fechas. Permite registrar eventos pasados. Lo habitual es crearla `PROGRAMADA` y que el coach la inicie y la finalice (ver "Ciclo de vida de la competencia"). |
| `startsAt`, `endsAt` | Obligatorias, ISO-8601 con zona; `endsAt` al menos un minuto posterior a `startsAt`. |

`durationMinutes` no se envía: se calcula como los minutos entre `startsAt` y `endsAt`. La respuesta devuelve
`id`, `teamId`, `eventName`, `description`, `accessType`, `penaltyRule`, `durationMinutes`,
`scoreboardFreezeMinutes`, `status`, `startsAt` y `endsAt`. No se acepta un coach ni un rol enviados en el body.

La lectura es estricta: un campo con tipo incorrecto (por ejemplo un número como cadena) se rechaza, y los errores
de todos los campos se informan juntos. Errores con cuerpo `{ "message": "...", "errors": {} }`: `400` datos
inválidos o JSON inválido (`errors` indica los campos), `401` sin token válido, `403` cuenta no coach, suspendida o
equipo de otro coach, `404` equipo inexistente, `409` ya existe una competencia del mismo equipo con el mismo
nombre (sin distinguir mayúsculas) y la misma fecha de inicio, `503` persistencia no disponible. Una solicitud
rechazada no crea ningún registro. El ERD no define una restricción única para el duplicado: la regla se verifica
en el servicio, por lo que dos solicitudes simultáneas idénticas podrían crear ambas.

## US-13 — Ciclo de vida de la competencia

`PATCH /api/competitions/{competitionId}/status`. Solo el coach responsable del equipo, con cuenta `ACTIVO` y token Bearer.

```json
{ "status": "EN_CURSO" }
```

Devuelve `200` con la competencia (la misma forma que `POST /api/competitions`, sin la clave de acceso). El estado solo
avanza un paso: `PROGRAMADA`, `EN_CURSO`, `FINALIZADA`. No retrocede ni se salta un paso, y `FINALIZADA` es definitivo.
`startsAt` y `endsAt` son informativos: el estado lo cambia el coach, no el reloj.

| Estado | Qué permite |
| --- | --- |
| `PROGRAMADA` | Asignar problemas. No se registran resoluciones (`409`: el coach debe iniciar la competencia). |
| `EN_CURSO` | Asignar problemas y registrar resoluciones. |
| `FINALIZADA` | Confirmar el resultado oficial y generar el reporte de temas (US-12). Los practicantes todavía pueden registrar resoluciones (repaso); ya no se asignan problemas (`409`). |

Errores `{ "message": "...", "errors": {} }`: `400` cuerpo inválido o `status` ausente o desconocido (`errors.status`) e
identificador no numérico; `401` sin token válido; `403` cuenta no coach o coach de otro equipo; `404` competencia
inexistente; `409` transición no permitida (el mensaje nombra los dos estados); `503` persistencia no disponible. Dos
solicitudes simultáneas se serializan: una cambia el estado y la otra recibe `409`.

## Competencias de un equipo

`GET /api/competitions?teamId=1`. El coach responsable o un integrante con membresía `ACTIVO`.

```json
{
  "teamId": 1,
  "total": 1,
  "items": [
    {
      "id": 5,
      "eventName": "Simulacro 1",
      "description": null,
      "accessType": "PUBLICO_GRUPO",
      "penaltyRule": "ICPC_20_MIN",
      "durationMinutes": 300,
      "scoreboardFreezeMinutes": 60,
      "status": "EN_CURSO",
      "startsAt": "2026-10-20T14:00:00-05:00",
      "endsAt": "2026-10-20T19:00:00-05:00",
      "problemsCount": 3
    }
  ]
}
```

La de inicio más reciente primero y, con el mismo inicio, la creada después; `items` queda vacío si el equipo no tiene
competencias. Nunca incluye la clave de acceso. Errores: `400` sin `teamId` o no numérico (`errors.teamId`), `401` sin token
válido, `403` cuenta no activa, practicante sin membresía `ACTIVO` o coach de otro equipo, `404` equipo inexistente.

## US-13 — Resultados oficiales de competencias

Solo el coach responsable con cuenta activa y sesión puede utilizar estas rutas:

```txt
POST /competitions/{competitionId}/official-result
PUT  /competitions/{competitionId}/official-result
GET  /competitions/{competitionId}/official-result
GET  /competitions/teams/{teamId}/official-results
```

POST devuelve `201`; PUT y GET devuelven `200`. Body de POST/PUT: `finalPosition` (entero positivo),
`solvedProblems` (entero no negativo) y `confirm` (booleano, omitido equivale a `false`). Ambos números pueden
faltar en un pendiente; confirmar exige ambos y una competencia `FINALIZADA`. PUT reemplaza todos los
campos editables de un pendiente; un confirmado es inmutable. Solo se permite un registro por competencia.
El equipo se deriva de la competencia, nunca de un `teamId` enviado en el body.

El detalle devuelve `id`, `competitionId`, `teamId`, `eventName`, `competitionEndsAt`, `finalPosition`,
`solvedProblems`, `status` (`PENDIENTE`/`CONFIRMADO`), `registeredAt` y `confirmedAt` (null en pendientes).
El historial devuelve un array de estos DTOs, exclusivamente confirmados, por fecha de fin descendente e ID
de competencia descendente en igualdad. Sin confirmados devuelve `[]`.

Errores `{ "message": "...", "errors": {} }`: `400` identifica campos inválidos o faltantes al confirmar;
`401` sesión ausente; `403` coach no autorizado; `404` equipo, competencia o resultado inexistente;
`409` duplicado o modificación de confirmado; `503` datos/transacción no disponibles; `500` error inesperado.
Ninguna operación rechazada reemplaza información válida. Ejemplos, tabla y límite de duplicados entre IDs
distintos: [US-13](15-us13-resultados-oficiales.md).

## US-14 — Avance personal por equipo

```txt
POST /competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions
GET  /analytics/teams/{teamId}/progress/me
```

Ambas rutas requieren sesión, cuenta `ACTIVO`, rol `PRACTICANTE` y membresía `ACTIVO` propia. La identidad
se obtiene del contexto autenticado. El problema asignado y la membresía deben pertenecer al equipo de la ruta.
`competitionProblemId` es el ID de `competencia_problema`, no el del catálogo.

POST recibe `language` (texto obligatorio, máximo 30) y `evidenceUrl` (URL HTTP/HTTPS opcional, máximo 500).
Registra un `ACCEPTED` manual provisional y devuelve `201` con `resolution` (DTO `TeamResolutionData`),
`registrationMethod: "MANUAL_PROVISIONAL"` y `progress`. No constituye verificación automática: este `ACCEPTED`
declarado cuenta igual en el ranking (US-11) y en el reporte de temas (US-12) hasta que US-09 lo sustituya por un
registro verificado. Rechaza otro
`ACCEPTED` para la misma membresía/asignación sin modificar los intentos anteriores. Otros equipos o
competencias conservan registros independientes. Mientras la competencia está `PROGRAMADA` responde `409` (el coach
debe iniciarla con `PATCH /competitions/{competitionId}/status`); durante `EN_CURSO` y después de `FINALIZADA` se permite.

GET devuelve `200` con `teamId`, `membershipId`, `userId` y `acceptedProblems`: problemas distintos aceptados
del practicante en ese equipo, según US-11. Sin actividad, el conteo es cero. POST recalcula ese avance antes
de completar la transacción; si hay un error no se conserva una escritura parcial. No se combinan equipos.

Errores `{ "message": "...", "errors": {} }`: `400` campos/contexto/asignación cruzada inválidos;
`401` sesión ausente; `403` rol/cuenta/membresía no autorizados; `404` equipo/asignación inexistente;
`409` duplicado, competencia no iniciada o datos inconsistentes; `503` información/transacción no disponible; `500` error inesperado.
Detalles y limitaciones: [US-14](16-us14-avance-independiente.md).

## Problems — catálogo

`POST /api/problems` registra un problema en el catálogo (`problema`) y devuelve `201`. Solo un coach con cuenta
`ACTIVO` y sesión. Los temas se crean si no existen; se comparan sin distinguir mayúsculas.

```json
{
  "title": "Theatre Square",
  "url": "https://codeforces.com/problemset/problem/1/A",
  "sourcePlatform": "CODEFORCES",
  "sourceCode": "CF-1A",
  "difficultyRating": "1000",
  "timeLimitMs": 1000,
  "memoryLimitMb": 256,
  "topics": ["Matemática", "Implementación"]
}
```

Obligatorios: `title` (hasta 150) y `url` (http/https sin credenciales, hasta 300). Opcionales: `sourcePlatform`
(`CODEFORCES` por defecto, `ATCODER`, `CSES`), `sourceCode` (hasta 50), `difficultyRating` (texto, hasta 30),
`timeLimitMs` (1000 por defecto) y `memoryLimitMb` (256 por defecto), ambos de al menos 1, y `topics` (hasta 20
nombres de hasta 80). La respuesta devuelve `id`, esos campos y `topics: [{ "id", "name" }]`.

`GET /api/problems` busca en el catálogo (cualquier cuenta activa). Parámetros opcionales: `q` (título o código de
origen, sin distinguir mayúsculas; `%` y `_` se buscan como texto), `topicId`, `difficulty` (igualdad exacta),
`platform`, `page` (desde 0) y `size` (1 a 100, por defecto 20). Orden por título y luego `id`.

```json
{ "items": [ { "id": 7, "title": "...", "topics": [ { "id": 3, "name": "Matemática" } ] } ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1 }
```

Errores con cuerpo `{ "message": "...", "errors": {} }`: `400` datos o criterios inválidos (`errors` por campo) o tipo
incorrecto en el JSON, `401` sin token válido, `403` cuenta no coach (al registrar) o suspendida, `409` la `url` o el
`sourceCode` de la plataforma ya existen (`errors` indica cuál) y `503`. Detalle: [US-07/US-08](17-us07-us08-asignacion-problemas.md).

## US-07 — Asignar problemas a una competencia

`POST /api/problems/assign` asigna uno o más problemas del catálogo a una competencia y devuelve `201`. Solo el coach
responsable del equipo de la competencia, con cuenta `ACTIVO` y sesión. La asignación es para todo el equipo.

```json
{
  "competitionId": 5,
  "problems": [
    { "problemId": 12, "letter": "A", "score": 100, "balloonColor": "#00FF00" },
    { "problemId": 13 }
  ]
}
```

`competitionId` y de 1 a 50 `problems` son obligatorios. Por problema, `problemId` es obligatorio; `letter` (1 a 5 letras A-Z)
se asigna sola si falta: la primera libre de `A`, `B`, ..., `Z`, `AA`...; `score` (al menos 1) vale 1 por defecto y
`balloonColor` (`#RRGGBB`) vale `#FF0000`. Respuesta:

```json
{
  "competitionId": 5,
  "teamId": 1,
  "assigned": [
    { "competitionProblemId": 40, "problemId": 12, "title": "Theatre Square", "letter": "A", "score": 100,
      "balloonColor": "#00FF00", "assignedAt": "2026-10-20T14:00:00Z" }
  ]
}
```

Es todo o nada. Errores con cuerpo `{ "message": "...", "errors": {} }` y rutas de campo como
`problems[1].problemId`: `400` datos inválidos o problema inexistente en el catálogo, `401` sin token válido, `403` no es
un coach activo o no es el responsable del equipo, `404` competencia inexistente, `409` la competencia ya
`FINALIZADA`, un problema ya asignado o una letra ya usada (las asignaciones existentes se mantienen) y `503`. El
escenario de asignar solo a parte del equipo no está soportado por el ERD; las notificaciones tampoco están
implementadas.

## US-08 — Problemas asignados

`GET /api/problems/assigned?teamId=1` lista los problemas asignados al equipo. Lo consulta un practicante con
membresía `ACTIVO` en el equipo (con su estado personal) o el coach responsable (sin estado personal). Filtros y orden
opcionales: `q` (título o código), `difficulty`, `competitionId`, `competitionStatus` (`PROGRAMADA`, `EN_CURSO`,
`FINALIZADA`), `status` (`SIN_INTENTOS`, `EN_PROGRESO`, `PENDIENTE`, `RESUELTO`; solo practicantes), `sort` (`letter`,
`title`, `difficulty`, `assignedAt`, `status`) y `order` (`asc`/`desc`). Solo lee.

```json
{
  "teamId": 1,
  "total": 1,
  "items": [
    {
      "competitionProblemId": 40, "letter": "A", "score": 100, "balloonColor": "#00FF00",
      "assignedAt": "2026-10-20T14:00:00Z",
      "competition": { "id": 5, "name": "Simulacro 1", "status": "PROGRAMADA", "startsAt": "...", "endsAt": "...",
                       "durationMinutes": 300, "penaltyRule": "ICPC_20_MIN", "scoreboardFreezeMinutes": 60,
                       "accessType": "PUBLICO_GRUPO" },
      "problem": { "id": 12, "title": "Theatre Square", "url": "https://codeforces.com/...", "sourcePlatform": "CODEFORCES",
                   "sourceCode": "CF-1A", "difficultyRating": "1000", "timeLimitMs": 1000, "memoryLimitMb": 256,
                   "topics": ["Matemática"] },
      "status": "RESUELTO", "attemptCount": 2,
      "lastAttempt": { "resolutionId": 9, "verdict": "ACCEPTED", "language": "Java", "submittedAt": "...",
                       "evidenceUrl": "https://..." }
    }
  ]
}
```

El estado se deriva de los intentos del practicante: `RESUELTO` (algún `ACCEPTED`), `PENDIENTE` (hay un intento por
verificar), `EN_PROGRESO` (solo intentos fallidos) o `SIN_INTENTOS`. Nunca se devuelve la clave de acceso de la
competencia. Sin `sort` el orden es competencia más reciente primero y por letra; las asignaciones sin dificultad
quedan al final al ordenar por ella.

`GET /api/problems/assigned/{competitionProblemId}` devuelve `{ "assignment": { ...igual que un elemento... },
"attempts": [ ... ] }` con el historial de intentos propios, el más reciente primero (vacío para el coach).

Errores con cuerpo `{ "message": "...", "errors": {} }`: `400` `teamId` ausente o criterios inválidos, `401` sin token válido,
`403` cuenta suspendida, practicante que no pertenece al equipo (o con membresía retirada) o coach que no es el
responsable, `404` equipo o asignación inexistente y `503` si no se pudo recuperar la información (el mensaje invita
a reintentar). Detalle y decisiones: [US-07/US-08](17-us07-us08-asignacion-problemas.md).

## Rutas pendientes: no implementadas ni publicadas

Las siguientes rutas son propuestas para historias futuras: **no aparecen en Swagger y actualmente devuelven `404`**. `problems` publica únicamente el catálogo y las rutas de US-07 y US-08 descritas arriba (`/problems/assign` y `/problems/assigned`). `teams` publica únicamente las rutas de US-04 a US-06 descritas arriba y `competitions`, las de US-13 descritas arriba.

## Teams

```txt
GET   /teams/{id}/members
```

## Problems

```txt
POST /problems/{id}/submit
GET  /problems/{id}/resources
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
