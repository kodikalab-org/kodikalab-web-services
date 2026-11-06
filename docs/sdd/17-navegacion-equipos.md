# Navegación autorizada de equipos, competencias y problemas

## Alcance

La navegación de US-11 a US-14 no introduce endpoints propios: el cliente selecciona equipo, competencia y problema con consultas ya existentes, sin escribir IDs manualmente. No se modifican entidades, tablas ni las operaciones de ranking, reporte, resultados oficiales o registro manual.

## Consultas utilizadas

Base /api, autenticación por la sesión existente. No se acepta userId del cliente para resolver la identidad.

| Paso | Endpoint | Contrato |
| --- | --- | --- |
| Equipos del usuario | `GET /teams/me` | [03-api-contracts.md](03-api-contracts.md) (`GET /api/teams/me`, US-04 a US-06) |
| Competencias del equipo | `GET /competitions?teamId=` | [03-api-contracts.md](03-api-contracts.md) (listado de competencias del equipo, `CompetitionServiceImpl.listByTeam`) |
| Problemas asignados | `GET /problems/assigned?teamId=` | [03-api-contracts.md](03-api-contracts.md) (problemas asignados, `AssignedProblemsServiceImpl`) |

Ambos listados exigen sesión activa y que el usuario sea el coach responsable del equipo o un integrante con membresía `ACTIVO`; cada uno aplica su propia comprobación y sus propios mensajes, que no cambian con este documento.

## Integración Angular

TeamFrame reutiliza TeamsApi.loadMine y ofrece equipos por nombre. Si hay uno solo y no existe contexto previo, lo selecciona. Las competencias y los problemas asignados se consultan en el equipo seleccionado con `GET /competitions?teamId=` y `GET /problems/assigned?teamId=`. ResultsLive conserva historial, pendientes y confirmados; ProgressLive utiliza el ID de asignación (`competitionProblemId`) devuelto por el listado de problemas asignados.

Al cambiar de equipo se eliminan los parámetros de competencia/problema anteriores, se cancelan lecturas en curso y se limpian las selecciones. No se publican listas fallidas como vacías. El guard de rol, las cookies, MANUAL_PROVISIONAL y ambos temas se conservan. Los accesos de Mi entrenamiento permiten iniciar los cuatro flujos desde el dashboard sin construir URLs.

## Pruebas

- StudyGroupAccessTests: `requireAccessibleTeam` (propiedad, membresía activa, cuenta suspendida, sin sesión, equipo inexistente e id inválido).
- StudyGroupControllerTests: `/teams/me` no toma la identidad de parámetros de la consulta.
- TeamNavigationIntegrationTests: PostgreSQL real y sesión de login; `/teams/me` separa los contratos de coach y practicante, y `/competitions?teamId=` y `/problems/assigned?teamId=` rechazan equipos ajenos, membresías revocadas y solicitudes anónimas. Requiere PROFILE_TEST_DB_URL, crea un esquema navigation_test_<uuid> y lo elimina al terminar; nunca usa el esquema public para sus fixtures.
