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
| Público (sin token) | `POST /auth/register`, `POST /auth/login`, `/swagger-ui.html`, `/v3/api-docs` |
| Solo `COACH` | `POST /teams`, `GET /teams/{id}/memberships`, `PATCH /teams/{id}/memberships/{memberId}`, `POST /problems`, `POST /problems/assign`, `POST /competitions`, `POST`/`PUT`/`GET /competitions/{competitionId}/official-result`, `GET /competitions/teams/{teamId}/official-results`, `GET /analytics/teams/{teamId}/weaknesses` |
| Solo `PRACTICANTE` | `POST /teams/{id}/join`, `POST /competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions`, `GET /analytics/teams/{teamId}/progress/me` |
| Cualquier cuenta autenticada | El resto: `GET`/`PUT /users/me`, `GET /teams`, `GET /problems`, `GET /problems/assigned` y `/{id}`, `GET /analytics/teams/{teamId}/standings` |

Las reglas por rol de la tabla se declaran en `config.SecurityConfig`. Los servicios conservan las verificaciones que
dependen de datos (coach responsable del equipo, membresía activa, cuenta `ACTIVO`).

Errores de seguridad, con el cuerpo `{ "message": "...", "errors": {} }`:

- `401`, token ausente: `Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)`.
- `401`, token inválido, manipulado, vencido, firmado con otra clave o de una cuenta inexistente:
  `El token es inválido o expiró: inicie sesión nuevamente`. Ambos incluyen `WWW-Authenticate: Bearer`.
- `403`, rol sin permiso: `No tiene permisos para realizar esta acción`.
- `403`, cuenta que dejó de estar `ACTIVO` después de iniciar sesión: `La cuenta no está habilitada`.

El token es un JWT firmado con HMAC-SHA256 que contiene `sub` (correo), `uid`, `role`, `iat`, `exp`, `jti` e
`iss=kodikalab`; nunca contraseñas ni hashes. El rol efectivo se lee siempre de la base de datos, no del token.
Configuración: `JWT_SECRET` (obligatorio, mínimo 32 caracteres), `JWT_EXPIRATION` (milisegundos, por defecto 24 h) y
`CORS_ALLOWED_ORIGINS`. Detalle y decisiones: `06-security-strategy.md`.

La recuperación de acceso descrita en `tarea.md` queda pendiente: no se definió su contrato técnico ni se implementan
endpoints de recuperación.

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
| `status` | `PROGRAMADA` (por defecto), `EN_CURSO` o `FINALIZADA`. Se respeta el valor enviado; no se infiere de las fechas. Permite registrar eventos pasados. |
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
competencias conservan registros independientes.

GET devuelve `200` con `teamId`, `membershipId`, `userId` y `acceptedProblems`: problemas distintos aceptados
del practicante en ese equipo, según US-11. Sin actividad, el conteo es cero. POST recalcula ese avance antes
de completar la transacción; si hay un error no se conserva una escritura parcial. No se combinan equipos.

Errores `{ "message": "...", "errors": {} }`: `400` campos/contexto/asignación cruzada inválidos;
`401` sesión ausente; `403` rol/cuenta/membresía no autorizados; `404` equipo/asignación inexistente;
`409` duplicado o datos inconsistentes; `503` información/transacción no disponible; `500` error inesperado.
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

## Analytics

```txt
GET  /analytics/teams/{teamId}/topics
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
