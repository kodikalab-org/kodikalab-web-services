# Navegación autorizada de equipos, competencias y problemas

## Alcance

Se implementan tres consultas para seleccionar el contexto de US-11 a US-14 sin introducir IDs manualmente. No se modifican entidades, tablas ni las operaciones de ranking, reporte, resultados oficiales o registro manual existentes.

## Contratos implementados

Base /api, autenticación por la sesión existente. No se acepta userId del cliente para resolver la identidad.

### GET /teams/me

Devuelve un array ordenado por groupId. Coach activo: equipos propios. Practicante activo: equipos con membresía ACTIVO. Cuenta sin equipos: 200 con [].

Campos comunes: groupId, name, description, expectedLevel, maxCapacity, sessionSchedule, status y visibility. Para coach incluye invitationCode y activeMembers calculado sobre membresías ACTIVO. Para practicante incluye membership con membershipId, status, teamRole, joinedAt y leftAt. Los campos específicos del otro rol se omiten.

No se devuelve pendingRequests: el modelo local de MembershipStatus solo contiene ACTIVO, RETIRADO y EXPULSADO y no existe una entidad de solicitudes. El cliente conserva ese campo como opcional para mantener compatibilidad con otras versiones del contrato de US04-US06. No se inventa un recuento ni se amplía el flujo de admisión.

DTO: teams/studygroup/dto/MyTeamResponse. Se conserva StudyGroupSummary sin cambios porque lo utilizan las otras historias.

### GET /competitions/teams/{teamId}

Usuario activo y coach propietario o practicante con membresía ACTIVO. Devuelve competitionId, teamId, eventName, status, startsAt y endsAt, ordenados por inicio descendente e ID descendente. No expone accessKey ni otra credencial. Incluye los estados existentes; la validación de confirmación de resultados conserva la regla FINALIZADA.

DTO: competitions/competition/dto/TeamCompetitionResponse.

### GET /competitions/teams/{teamId}/problems

Misma autorización. Devuelve asignaciones con competitionProblemId, competitionId, teamId, problemId, title, eventName, letter y status. Orden por inicio de competencia descendente, ID de competencia descendente, letra e ID de asignación. El mismo problema en competencias diferentes tiene competitionProblemId diferente.

DTO: competitions/competitionproblem/dto/AssignedProblemResponse. Se conserva TeamAssignedProblem y su consulta usada por US12.

## Errores

401 sin sesión, 403 para cuenta inactiva o equipo ajeno/membresía no activa, 404 si el equipo no existe, 400 para identificadores inválidos y 503 ante indisponibilidad de persistencia. Un equipo autorizado sin competencias/asignaciones devuelve 200 con []. Los listados de competitions reutilizan CompetitionExceptionHandler; teams usa TeamExceptionHandler siguiendo el patrón del proyecto. Los mensajes y operaciones de resultados oficiales permanecen sin cambios.

StudyGroupService.requireAccessibleTeam centraliza el acceso de los dos listados de competitions. Se resuelve CurrentUserResolver y se comprueba propietario o membresía activa, antes de consultar las competencias/asignaciones. No se inyecta analytics en teams ni se crea dependencia circular.

## Integración Angular

TeamFrame reutiliza TeamsApi.loadMine y ofrece equipos por nombre. Si hay uno solo y no existe contexto previo, lo selecciona. Las competencias y problemas asignados se consultan mediante TeamApi en el equipo seleccionado. ResultsLive conserva historial, pendientes y confirmados; ProgressLive utiliza el ID de asignación devuelto por el listado.

Al cambiar de equipo se eliminan los parámetros de competencia/problema anteriores, se cancelan lecturas en curso y se limpian las selecciones. No se publican listas fallidas como vacías. El guard de rol, las cookies, MANUAL_PROVISIONAL y ambos temas se conservan. Los accesos de Mi entrenamiento permiten iniciar los cuatro flujos desde el dashboard sin construir URLs.

## Pruebas

- StudyGroupServiceTests: identidad, propiedad, membresía activa, cuenta suspendida, sin sesión, equipo inexistente y listas vacías.
- StudyGroupControllerTests: contrato /teams/me y errores sin detalles internos.
- TeamNavigationServiceTests: autorización previa a las consultas y respuesta de competencias.
- TeamNavigationIntegrationTests: PostgreSQL real, sesión de login, separación de roles, equipos ajenos, revocación, vacíos y asignaciones del mismo problema independientes. Solo acepta NAVIGATION_TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/kodikalab_us11_14_test. Crea un esquema navigation_test_<uuid> y lo elimina al terminar; nunca usa el esquema public para sus fixtures.
- RuntimeBoundaryTests: se actualiza únicamente el inventario de los tres endpoints nuevos.

En esta ejecución todas las URLs opt-in de integración se dirigieron a kodikalab_us11_14_test. No se ejecutó ninguna prueba contra la base original. Resultado Maven: 487 pruebas, cero fallos, cero errores y cero omitidas. El empaquetado JAR también terminó correctamente; Maven resolvió componentes del plugin de empaquetado ya definido, sin cambiar pom.xml ni añadir dependencias de aplicación.
