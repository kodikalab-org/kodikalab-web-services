# US-14 — Avance independiente en múltiples equipos

## Alcance y reglas aprobadas

US-14 reutiliza `GroupMembership`, `Competition`, `CompetitionProblem` y `ProblemResolution` sin modificar entidades, relaciones ni tablas. La membresía única por equipo/practicante admite al mismo usuario en varios equipos. Cada resolución conserva su membresía y su problema de competencia; ambos deben corresponder al equipo seleccionado.

El informe `report_1ACC0236-20262.pdf`, página 39, exige identificar el equipo al registrar, rechazar contextos inválidos sin modificar el progreso y mantener independiente el mismo problema en distintos grupos. El registro de resoluciones de US-09 aún no existe; se autorizó únicamente la integración mínima necesaria para estos escenarios.

Decisiones aprobadas:

- El registro es una declaración manual provisional del practicante y se guarda como `ACCEPTED`. No es una verificación automática. La respuesta lo identifica con `registrationMethod: "MANUAL_PROVISIONAL"`.
- No se implementan juez, importación externa, ejecución de código, verificación de evidencia ni el flujo completo de US-09. El modelo existente no almacena un campo de procedencia/verificación; no se infiere que los registros históricos hayan sido verificados automáticamente.
- **Limitación conocida:** al no existir verificación, un `ACCEPTED` declarado por el practicante cuenta igual que cualquier otro en el ranking (US-11) y en el reporte de temas (US-12); puede inflar ambos. Debe sustituirse por el flujo verificado de US-09 (o marcar su procedencia en el modelo) antes de usar esos resultados como oficiales.
- Se rechaza otro `ACCEPTED` para la misma membresía y `competitionProblemId`. Los intentos `PENDIENTE`, rechazados y anteriores permanecen intactos.
- El mismo problema puede tener registros independientes en diferentes equipos o competencias. Se reutiliza la regla de US-11: cada problema de catálogo aceptado cuenta una vez por membresía y equipo, incluso si aparece en varias competencias del mismo grupo.

La identidad procede exclusivamente de `CurrentUserResolver` y la sesión autenticada. Solo una cuenta `ACTIVO`, rol `PRACTICANTE` y membresía `ACTIVO` propia puede registrar o consultar su avance. Los identificadores de usuario, membresía, equipo o veredicto en el body no seleccionan al solicitante ni alteran el contexto. El equipo y el problema asignado se toman de la ruta y se verifican contra la persistencia.

## Operaciones y validación

Registro mínimo sobre una asignación existente:

```txt
POST /api/competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions
```

`competitionProblemId` identifica `competencia_problema.id`, no `problema.id`. El problema debe estar vinculado a una competencia cuyo equipo coincida con el de la membresía. No se crean equipos, membresías, competencias ni asignaciones desde este endpoint; sus flujos pertenecen a otras historias. No se inventa una restricción temporal o por fase de competencia para el registro manual.

Body:

```json
{
  "language": "Java 21",
  "evidenceUrl": "https://example.com/submission/1"
}
```

`language` es obligatorio, texto no vacío de hasta 30 caracteres después de quitar espacios externos. `evidenceUrl` es opcional, máximo 500 caracteres y URL HTTP/HTTPS con host; vacío se guarda como null. Solo se valida su sintaxis, sin consultar la URL. Los campos admiten texto/null según corresponda; no se convierten números, booleanos u objetos a texto. La fecha de envío se asigna en UTC; tiempos de ejecución y memoria permanecen null, pues no se midieron.

El POST constituye la acción de registro manual; no admite elegir otro veredicto. Respuesta `201`:

```json
{
  "resolution": {
    "resolutionId": 60,
    "membershipId": 20,
    "membershipTeamId": 1,
    "competitionProblemId": 50,
    "competitionId": 30,
    "competitionTeamId": 1,
    "problemId": 40,
    "verdict": "ACCEPTED"
  },
  "registrationMethod": "MANUAL_PROVISIONAL",
  "progress": {
    "teamId": 1,
    "membershipId": 20,
    "userId": 10,
    "acceptedProblems": 1
  }
}
```

Consulta personal:

```txt
GET /api/analytics/teams/{teamId}/progress/me
```

Devuelve `200` con el objeto `progress` anterior, exclusivamente del practicante autenticado en ese equipo. Sin actividad devuelve el contexto de membresía y `acceptedProblems: 0`. No devuelve las estadísticas de otros integrantes ni combina grupos.

## Transacciones, concurrencia y métricas

`ProblemResolutionService.registerManualAccepted` valida y guarda mediante sus repositorios propietarios. `GroupMembershipService.findForUpdate` proporciona la membresía con `PESSIMISTIC_WRITE`, dentro de una transacción existente (`MANDATORY`); no expone repositorios de `teams` a otros módulos.

La escritura utiliza `READ_COMMITTED`: después de adquirir el bloqueo de la membresía se consulta la existencia de un `ACCEPTED` y se realiza el INSERT. El bloqueo se conserva hasta terminar la transacción. Una segunda petición concurrente para esa membresía espera y después ve el registro confirmado por la primera, devolviendo `409` en lugar de duplicarlo. Membresías de equipos distintos son filas independientes.

No se añade una restricción única a `resolucion_problema`, porque el modelo permite múltiples intentos y conserva su historial. **Los futuros escritores de US-09 deben reutilizar este servicio y el mismo contrato de bloqueo/aislamiento**; escrituras SQL directas o flujos que lo omitan no están protegidos por esta validación de negocio. Las FKs existentes tampoco comprueban por sí solas la coincidencia de equipo; se comprueba antes de guardar.

`IndependentProgressService` coordina el registro y el cálculo del avance en una sola transacción. Tras `saveAndFlush`, reutiliza `TeamRankingService.getRanking(teamId)` y extrae únicamente el conteo del practicante. La comprobación de duplicados y el cálculo propio ocurren manteniendo bloqueada su membresía. Esta invocación se incorpora a la transacción de escritura `READ_COMMITTED`; la consulta GET personal utiliza `REPEATABLE_READ`. US-11 conserva su aislamiento original cuando se consulta por su endpoint.

Si el guardado, cálculo o confirmación de la transacción falla, se revierte la resolución y no se devuelve un registro exitoso. Los registros anteriores permanecen intactos. Los errores de datos detectados por US-11 se propagan sin resultados parciales.

Solo se calcula el ranking del equipo seleccionado para obtener el avance actualizado. US-12 continúa calculando su cobertura al consultar su endpoint, con su autorización de coach y su universo de competencias finalizadas. No se ejecuta el reporte del coach con la identidad del practicante, ni se recalculan otros equipos. US-13 permanece separado y no recibe escrituras.

No se modifica ni actualiza directamente `ranking_equipo_actual` desde US-14. La conservación del último ranking válido sigue perteneciendo al endpoint y a los servicios existentes de US-11. Así, un fallo posterior al INSERT provisional no puede dejar un snapshot publicado por una transacción independiente.

`IndependentProgressController` publica las dos rutas de la historia y delega en el coordinador de `analytics`, que usa el servicio público propietario de las resoluciones. Su handler tiene alcance exclusivo; no cambia los mensajes ni contratos de US-11/US-12/US-13. No hay nueva configuración de seguridad ni dependencias.

## Errores

Formato `{ "message": "...", "errors": {} }`, sin resolución o avance parcial y sin detalles internos:

| HTTP | Situación |
| --- | --- |
| `400` | Contexto/identificador inválido, body inválido, campos incorrectos o asignación de otro equipo. |
| `401` | Sesión ausente. |
| `403` | Cuenta/rol no habilitado o ausencia de membresía activa propia. |
| `404` | Equipo o asignación inexistente. |
| `409` | `ACCEPTED` duplicado, relaciones incompletas o datos inconsistentes durante el cálculo. |
| `503` | Acceso a datos o transacción no disponibles; puede repetir la solicitud. |
| `500` | Error inesperado. |

En contextos inválidos de registro, el mensaje solicita seleccionar un equipo válido. `errors` identifica campos inválidos o los datos que impiden calcular el avance. No hay edición, eliminación ni transferencia de resoluciones entre equipos.

## Pruebas

- `ManualResolutionServiceTests`: identidad, membresía activa, relación equipo/asignación, duplicados, conservación de intentos, datos inválidos y delegación de la lectura existente.
- `IndependentProgressServiceTests`: conteo reutilizado por equipo, progreso cero, autorización, registro manual y propagación de errores de cálculo.
- `IndependentProgressControllerTests`: contrato, marcador provisional, tipos JSON, identidad no seleccionable y respuestas de error.
- `IndependentProgressIntegrationTests`: PostgreSQL con schema aleatorio y login HTTP real; múltiples membresías, mismo problema en varios equipos/competencias, permisos, concurrencia, rollback tras insertar y regresión conjunta de US-11/US-12/US-13.

La integración requiere `INDEPENDENT_PROGRESS_TEST_DB_URL`, `INDEPENDENT_PROGRESS_TEST_DB_USER` y, si corresponde, `INDEPENDENT_PROGRESS_TEST_DB_PASSWORD`, con permisos para crear/eliminar schemas en una base exclusiva de pruebas. Sin URL se omite. Comandos reproducibles: [tests/README.md](../../tests/README.md).

## Verificación — 2026-10-09

- Compilación `clean compile`: correcta, 140 archivos de producción con Java 21 y Maven 3.9.16.
- Suite completa: **465 casos contabilizados, 383 ejecutados correctamente, 0 fallos, 0 errores y 82 omitidos**.
- US-14: 25 casos de `ManualResolutionServiceTests`, 9 de `IndependentProgressServiceTests` y 21 de `IndependentProgressControllerTests`, todos aprobados.
- `IndependentProgressIntegrationTests`: 11 entradas omitidas por ausencia de `INDEPENDENT_PROGRESS_TEST_DB_URL`. No se ejecutaron inserciones SQL, bloqueo concurrente ni rollback real contra PostgreSQL. Las otras suites opt-in contabilizaron 71 omitidos.
- Las pruebas disponibles de US-11 (101), US-12 (44) y US-13 (58) pasaron. Sus cálculos, controllers, handlers y pruebas existentes permanecen intactos; se ampliaron únicamente los servicios compartidos de membresías/resoluciones y el listado de rutas de arquitectura.
- El contexto Spring arrancó y validó las consultas JPQL nuevas. Se desactivaron DDL, inicialización SQL y metadatos JDBC mediante parámetros, sin cambiar la configuración. Esto no demuestra ejecución contra PostgreSQL.
- El wrapper falló antes de iniciar Maven por acceso denegado al preparar su caché. Se utilizó directamente Maven 3.9.16 ya disponible, offline y con el repositorio local existente. Las pruebas se ejecutaron con acceso local al agente Mockito y a las pruebas HTTP locales existentes.
- No se realizaron cambios de esquema ni operaciones de escritura Git. La rama permanece `feature/US11-ranking-interno-equipos`.

Comandos reproducibles con wrapper operativo:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd '-Dtest=ManualResolutionServiceTests,IndependentProgressServiceTests,IndependentProgressControllerTests' test
.\mvnw.cmd '-Dspring.jpa.hibernate.ddl-auto=none' '-Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false' '-Dspring.sql.init.mode=never' test
```

## Archivos de la implementación

Creados:

- `src/main/java/com/kodika/kodikalab/analytics/IndependentProgressController.java`.
- `src/main/java/com/kodika/kodikalab/analytics/IndependentProgressExceptionHandler.java`.
- `src/main/java/com/kodika/kodikalab/analytics/IndependentProgressService.java`.
- `src/main/java/com/kodika/kodikalab/analytics/dto/TeamProgressResponse.java`, con el DTO de avance y su respuesta de registro anidada.
- `src/main/java/com/kodika/kodikalab/competitions/problemresolution/ResolutionValidationException.java`.
- `src/main/java/com/kodika/kodikalab/competitions/problemresolution/dto/ManualResolutionRequest.java`.
- `src/test/java/com/kodika/kodikalab/analytics/IndependentProgressControllerTests.java`.
- `src/test/java/com/kodika/kodikalab/analytics/IndependentProgressServiceTests.java`.
- `src/test/java/com/kodika/kodikalab/analytics/IndependentProgressIntegrationTests.java`.
- `src/test/java/com/kodika/kodikalab/competitions/problemresolution/ManualResolutionServiceTests.java`.
- `docs/sdd/16-us14-avance-independiente.md`.

Modificados:

- `src/main/java/com/kodika/kodikalab/teams/groupmembership/GroupMembershipRepository.java`: consulta con bloqueo por equipo y usuario.
- `src/main/java/com/kodika/kodikalab/teams/groupmembership/GroupMembershipService.java` y `GroupMembershipServiceImpl.java`: método público transaccional de membresía bloqueada, conservando la lectura existente.
- `src/main/java/com/kodika/kodikalab/competitions/problemresolution/ProblemResolutionRepository.java`: comprobación de duplicado `ACCEPTED` por membresía/asignación.
- `src/main/java/com/kodika/kodikalab/competitions/problemresolution/ProblemResolutionService.java` y `ProblemResolutionServiceImpl.java`: registro manual validado, conservando la consulta de US-11/US-12.
- `src/test/java/com/kodika/kodikalab/architecture/RuntimeBoundaryTests.java`: únicamente agrega las dos rutas implementadas; conserva entidades y repositories esperados.
- `docs/sdd/03-api-contracts.md`, `docs/sdd/05-architecture.md`, `docs/sdd/README.md` y `tests/README.md`: documentación de US-14.

Pendientes externos: validar la suite PostgreSQL aislada; disponer de equipos, membresías y asignaciones creados por sus módulos; integrar a futuro el flujo completo y la validación externa de US-09, sin atribuirlos a esta implementación.
