# US-07 y US-08 — Asignación de problemas y vista de problemas asignados

## Alcance implementado

| Ruta | Historia | Quién |
| --- | --- | --- |
| `POST /api/problems` | Catálogo (necesario para US-07: sin problemas no hay qué asignar) | Coach activo |
| `GET /api/problems` | Catálogo: búsqueda paginada para elegir qué asignar | Cuenta activa |
| `POST /api/problems/assign` | US-07 | Coach responsable del equipo de la competencia |
| `GET /api/problems/assigned?teamId=` | US-08 | Practicante con membresía `ACTIVO` o coach responsable |
| `GET /api/problems/assigned/{competitionProblemId}` | US-08 (detalle y continuar la práctica) | Igual que el listado |

El contrato con ejemplos está en [03-api-contracts.md](03-api-contracts.md). No se agregan tablas ni columnas: el
catálogo usa `problema`, `tema` y `problema_tema`; una asignación es una fila de `competencia_problema`.

## Decisiones de modelo

- **La competencia es el objetivo de entrenamiento.** Un problema se asigna a una competencia del equipo
  (`competencia_problema`); el nombre y la descripción de la competencia expresan el objetivo (tarea «vincular
  problemas a objetivos de entrenamiento»). Las competencias se crean con `POST /api/competitions`.
- **La asignación es para todo el equipo.** El ERD no registra destinatarios, así que el escenario alternativo de US-07
  («asignar solo a parte del equipo, informar exclusiones») **no está implementado**. Cubrirlo exigiría una tabla de
  destinatarios y, por tanto, una extensión del ERD que el equipo debe aprobar.
- **Las notificaciones a los integrantes no están implementadas** (US-07 las menciona, y la notificación al practicante
  de US-06 tampoco). La configuración de correo existe, pero ninguna historia la usa todavía.
- **El estado personal se deriva, no se guarda.** Por problema asignado y practicante, según sus intentos
  (`resolucion_problema`): `RESUELTO` (algún `ACCEPTED`) > `PENDIENTE` (hay un intento por verificar) > `EN_PROGRESO`
  (solo intentos fallidos) > `SIN_INTENTOS`. Un `ACCEPTED` declarado manualmente en US-14 cuenta como resuelto
  (ver la limitación de ese flujo en [16-us14-avance-independiente.md](16-us14-avance-independiente.md)).
- **Módulo `assignments`.** Sin tablas propias; coordina por servicios públicos, igual que `analytics`:
  `competitions` (asignaciones, competencias, intentos), `problems` (catálogo, temas) y `teams` (coach y membresías).

## Catálogo de problemas

- Registrar: `title` (obligatorio, hasta 150), `url` (obligatoria, http/https sin credenciales, hasta 300),
  `sourcePlatform` (`CODEFORCES` por defecto), `sourceCode` (hasta 50), `difficultyRating` (texto hasta 30),
  `timeLimitMs` y `memoryLimitMb` (por defecto 1000 y 256, mínimo 1) y `topics` (hasta 20 nombres de hasta 80).
- Los temas se buscan **sin distinguir mayúsculas**: se reutiliza el existente (conserva su nombre) y se crean los que
  faltan. Los repetidos en la solicitud se unifican. El reporte de US-12 exige que cada problema asignado tenga al
  menos un tema; por eso se aceptan al registrar el problema.
- Duplicados (`409` con el campo): misma `url` (sin distinguir mayúsculas) o mismo `sourceCode` en la misma plataforma.
  El ERD no define restricciones únicas para esto; la regla se verifica en el servicio, por lo que dos registros
  idénticos simultáneos podrían crearse ambos.
- Buscar: `q` (título o código, sin distinguir mayúsculas; `%` y `_` se buscan como texto), `topicId`, `difficulty`
  (igualdad exacta), `platform`, `page` (desde 0) y `size` (1 a 100, por defecto 20). Orden por título y luego id.

## US-07 — Asignar problemas

`POST /api/problems/assign` recibe `competitionId` y una lista de 1 a 50 `problems` con `problemId` y, opcionalmente,
`letter`, `score` y `balloonColor`.

- **Todo o nada.** Si algún problema no existe, ya está asignado, o su letra está en uso, no se registra ninguno: las
  asignaciones existentes se mantienen y `errors` indica la causa por campo (`problems[1].problemId`).
- **Letras.** Sin `letter` se asigna la primera libre de la secuencia `A`…`Z`, `AA`…; las letras explícitas (1 a 5
  letras, se guardan en mayúsculas) se reservan primero. `score` por defecto 1 y `balloonColor` por defecto `#FF0000`,
  los valores por defecto del ERD.
- **Autorización.** Solo el coach responsable del equipo de la competencia, con cuenta activa. Las validaciones de
  forma se informan juntas (`400`); los conflictos con lo ya asignado, también juntos (`409`).
- **Competencias finalizadas.** No admiten nuevos problemas (`409`); `PROGRAMADA` y `EN_CURSO` sí.
- **Concurrencia.** La competencia se bloquea (`SELECT … FOR UPDATE`) durante la asignación: dos solicitudes
  simultáneas no eligen la misma letra ni asignan dos veces el mismo problema (la segunda recibe `409`). Las
  restricciones únicas del ERD (`uq_competencia_problema`, `uq_competencia_orden_letra`) son la última defensa.

## US-08 — Problemas asignados

- **Listado.** `teamId` es obligatorio. Un practicante necesita membresía `ACTIVO` en ese equipo; el coach responsable
  ve las asignaciones del equipo sin estado personal (`status`, `attemptCount` y `lastAttempt` son `null`). Un
  equipo inexistente da `404`; cualquier otro acceso, `403` sin revelar datos; sin sesión, `401`.
- **Información y condiciones de realización.** Cada elemento trae letra, puntaje, color, fecha de asignación, los
  datos del problema (título, URL, plataforma, código, dificultad, límites de tiempo y memoria, temas) y de su
  competencia (nombre, estado, inicio, fin, duración, regla de penalización, congelamiento del marcador y tipo de
  acceso). **Nunca se devuelve la clave de acceso** de una competencia privada.
- **Buscar, filtrar y ordenar** (escenario alternativo): `q` (título o código), `difficulty`, `competitionId`,
  `competitionStatus`, `status` (solo practicantes), `sort` (`letter`, `title`, `difficulty`, `assignedAt`, `status`) y
  `order` (`asc`/`desc`). Sin `sort` se conserva el orden por defecto (competencia más reciente primero y por
  letra). El orden es estable y las asignaciones sin dificultad quedan siempre al final. Solo lee: no modifica
  asignaciones ni intentos. El listado no se pagina porque una competencia tiene un número reducido de problemas.
- **Detalle y continuar la práctica.** `GET /api/problems/assigned/{competitionProblemId}` agrega el historial de
  intentos propios (más reciente primero). Para continuar, el elemento incluye la URL del problema y su último
  intento; registrar un nuevo intento sigue siendo `POST /api/competitions/teams/{teamId}/problems/{id}/resolutions`.
- **Errores recuperables.** Un fallo de persistencia devuelve `503` con un mensaje que invita a reintentar; no se
  publica información parcial.

## Errores

Todos con el cuerpo `{ "message": "...", "errors": {} }`: `400` datos o criterios inválidos (campo por campo), `401` sin
sesión, `403` sin permiso o cuenta suspendida, `404` equipo, competencia o asignación inexistente, `409` conflicto,
`503` persistencia no disponible y `500` error inesperado. El cuerpo de las solicitudes se lee de forma estricta: un
campo con tipo incorrecto (un número como cadena) se rechaza y no se aceptan coerciones.

## Trazabilidad con los commits de Sergio Saavedra

Esta entrega parte de los dos commits de `feature/AsignaciondeProblemasAlEquipo` y de `feature/listarProblemasAsignados`,
incorporados con su autoría:

- Se **conservan y usan** `findByCompetition_IdOrderByLetterAsc` (letras y problemas ya asignados al asignar) y
  `findDetailById` (detalle de una asignación con su problema y competencia).
- Se **reemplazan** por consultas con resultado tipado: `listProblemsByGroupNative` (que devolvía `Object[]`) por
  `findWithCompetitionByTeamId`, y las consultas sueltas del catálogo (`findByTitleContainingIgnoreCase`,
  `findBySourceCodeContainingIgnoreCase`, `findByDifficultyRating`, `findProblemWithTopics`, `listByTopicNative`,
  `findByTopicId`, `findProblemsByTopic` y `listProblemCatalogByTopicNative`) por una única búsqueda paginada con
  filtros combinables. Se retiran `existsByCompetition_IdAndProblem_Id` (redundante con la lista de asignaciones) y
  `updateAssignment`, que declaraba `@Transactional` en el repositorio y no tenía uso.
- Se **retiran** los cuatro procedimientos almacenados de `tests/fixtures/`: la base no los aplicaba y su lógica
  (listar, detalle, estado, continuar) ahora está en los servicios, con autorización por sesión y pruebas.
- No se incorporan los cambios de `application.yaml` (puerto 8081 y contraseña por defecto de la base): las credenciales
  van en variables de entorno y el puerto del proyecto es 8080.

## Pruebas

- Sin PostgreSQL: `AssignmentServiceTests`, `AssignedProblemsServiceTests`, `AssignmentControllerTests`,
  `ProblemServiceTests`, `TopicServiceTests` y `ProblemControllerTests`.
- Integración HTTP + PostgreSQL (opt-in, ver `tests/README.md`): `AssignmentsIntegrationTests`. Recorre el flujo
  completo por la API (catálogo, competencia, asignación, vista, intento y estado `RESUELTO`), el rechazo atómico,
  los permisos, la protección de datos entre equipos, la independencia del avance de cada integrante y la
  concurrencia de asignaciones.

## Pendientes

Asignación a parte del equipo (requiere extensión del ERD), notificaciones, edición o retiro de una asignación, y
importación del catálogo desde las plataformas (hoy se registra problema por problema).
