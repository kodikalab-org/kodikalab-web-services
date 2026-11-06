# Navegación autorizada de equipos, competencias y problemas

## Alcance

Se implementan dos consultas nuevas, que junto con `GET /teams/me` permiten seleccionar el contexto de US-11 a US-14 sin introducir IDs manualmente. No se modifican entidades, tablas ni las operaciones de ranking, reporte, resultados oficiales o registro manual existentes.

## Contratos implementados

Base /api, autenticación por la sesión existente. No se acepta userId del cliente para resolver la identidad.

### GET /teams/me

Ya implementado por US-04 a US-06; su contrato vigente está en [03-api-contracts.md](03-api-contracts.md) (`GET /api/teams/me`) y no cambia con este documento. Las consultas de competencias y problemas de abajo se apoyan en él para que el cliente elija un equipo sin escribir IDs.

### GET /competitions/teams/{teamId}

Usuario activo y coach propietario o practicante con membresía ACTIVO. Devuelve competitionId, teamId, eventName, status, startsAt y endsAt, ordenados por inicio descendente e ID descendente. No expone accessKey ni otra credencial. Incluye los estados existentes; la validación de confirmación de resultados conserva la regla FINALIZADA.

DTO: competitions/competition/dto/TeamCompetitionResponse.

### GET /competitions/teams/{teamId}/problems

Misma autorización. Devuelve asignaciones con competitionProblemId, competitionId, teamId, problemId, title, eventName, letter y status. Orden por inicio de competencia descendente, ID de competencia descendente, letra e ID de asignación. El mismo problema en competencias diferentes tiene competitionProblemId diferente.

DTO: competitions/competitionproblem/dto/AssignedProblemResponse. Se conserva TeamAssignedProblem y su consulta usada por US12.

## Errores

Formato de `CompetitionExceptionHandler` (`message` y `errors`): 401 sin sesión, 403 para cuenta inactiva o equipo ajeno/membresía no activa, 404 si el equipo no existe y 400 para identificadores inválidos. Un equipo autorizado sin competencias/asignaciones devuelve 200 con []. Los mensajes y operaciones de resultados oficiales permanecen sin cambios.

`StudyGroupService.requireAccessibleTeam` centraliza el acceso de los dos listados: resuelve `CurrentUserResolver` y comprueba propietario o membresía `ACTIVO` antes de consultar las competencias/asignaciones. No se inyecta analytics en teams ni se crea dependencia circular.

## Integración Angular

TeamFrame reutiliza TeamsApi.loadMine y ofrece equipos por nombre. Si hay uno solo y no existe contexto previo, lo selecciona. Las competencias y problemas asignados se consultan mediante TeamApi en el equipo seleccionado. ResultsLive conserva historial, pendientes y confirmados; ProgressLive utiliza el ID de asignación devuelto por el listado.

Al cambiar de equipo se eliminan los parámetros de competencia/problema anteriores, se cancelan lecturas en curso y se limpian las selecciones. No se publican listas fallidas como vacías. El guard de rol, las cookies, MANUAL_PROVISIONAL y ambos temas se conservan. Los accesos de Mi entrenamiento permiten iniciar los cuatro flujos desde el dashboard sin construir URLs.

## Pruebas

- StudyGroupAccessTests: `requireAccessibleTeam` (propiedad, membresía activa, cuenta suspendida, sin sesión, equipo inexistente e id inválido).
- StudyGroupControllerTests: `/teams/me` no toma la identidad de parámetros de la consulta.
- TeamNavigationServiceTests: autorización previa a las consultas y respuesta de competencias.
- TeamNavigationIntegrationTests: PostgreSQL real, sesión de login, separación de roles, equipos ajenos, revocación, vacíos y asignaciones del mismo problema independientes. Solo acepta NAVIGATION_TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/kodikalab_us11_14_test. Crea un esquema navigation_test_<uuid> y lo elimina al terminar; nunca usa el esquema public para sus fixtures.
- RuntimeBoundaryTests: se añaden al inventario los dos endpoints nuevos.
