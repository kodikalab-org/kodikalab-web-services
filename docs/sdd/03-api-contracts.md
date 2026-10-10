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

## Analytics — US-11

`GET /api/analytics/teams/{teamId}/standings` consulta resoluciones persistidas del equipo. Requiere sesión HTTP y cuenta `ACTIVO`: se autoriza al coach responsable o al practicante con membresía `ACTIVO`. La identidad se obtiene desde la sesión; el cliente no elige el usuario solicitante.

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

Errores con cuerpo `{ "message": "...", "errors": {} }`: `400` por ID inválido, `401` sin sesión, `403` sin autorización/cuenta suspendida, `404` por equipo inexistente, `409` por datos inconsistentes (campos o índices en `errors`), `503` por fallo de persistencia/transacción y `500` por error inesperado. Nunca se devuelven posiciones parciales del cálculo fallido.

En `409` o `503` se añade opcionalmente `lastValidRanking: { "calculatedAt": "...", "ranking": { ... } }`, con el último resultado completo guardado en PostgreSQL para ese equipo. Se conserva el código de error: ese ranking corresponde a la fecha indicada y no se presenta como un cálculo actualizado. Solo se recupera después de verificar nuevamente los permisos actuales. Si no existe, no se puede leer o no se puede verificar la autorización, se omite. Una caída total de PostgreSQL impide recuperarlo durante la caída; el registro permanece almacenado. Cada cálculo válido reemplaza una sola fila por equipo, sin historial.

Detalle de reglas y verificación: [US-11](13-us11-ranking-interno.md).

## Analytics — US-12

`GET /api/analytics/teams/{teamId}/weaknesses` consulta la cobertura por tema. Requiere sesión HTTP, cuenta `ACTIVO`, rol `COACH` y ser el coach responsable del equipo. Un integrante no puede consultar este reporte, aunque pueda acceder al ranking. La identidad se resuelve desde la sesión; los parámetros del cliente no conceden permisos.

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

Errores `{ "message": "...", "errors": {} }`: `400` por ID inválido, `401` sin sesión, `403` sin autorización/cuenta suspendida, `404` por equipo inexistente, `409` por información insuficiente o inconsistente (identifica campos, índices o problemas sin clasificación), `503` por información no disponible debido a persistencia/transacción y `500` por error inesperado. Los errores no incluyen conclusiones parciales. Puede repetirse el mismo GET tras corregir los datos o recuperar su disponibilidad. US-12 no almacena reportes ni modifica el último ranking de US-11.

Detalle y verificación: [US-12](14-us12-temas-menor-resolucion.md).

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

## Rutas pendientes: no implementadas ni publicadas

Las siguientes rutas son propuestas para historias futuras: **no aparecen en Swagger y actualmente devuelven `404`**. Los controllers plantilla de `teams` y `problems` no declaran endpoints. US-13 implementa únicamente las rutas de resultados oficiales descritas arriba, dentro de `competitions`.

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
