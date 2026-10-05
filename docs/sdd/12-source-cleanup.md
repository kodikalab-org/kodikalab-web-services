# 12 — Limpieza del código fuente sin funcionalidad

## Alcance autorizado

Se revisaron los archivos y referencias antes de retirar **55 archivos Java** de los paquetes raíz `controller`, `dto`, `entity`, `repository` y `service` (incluido `service/impl`). La limpieza fue solicitada expresamente; no se borraron archivos por su nombre de carpeta ni por una búsqueda de referencias vacía.

Se conservaron los módulos funcionales `auth`, `users`, `security`, `config` y `common`, incluidos los DTOs de `auth/dto`, la aplicación principal y todas las pruebas existentes. Quedan **19 archivos Java en `src/main`** y se agregaron pruebas de límites de runtime.

## Revisión funcional

| Grupo | Archivos | Evidencia y decisión |
| --- | ---: | --- |
| Controllers raíz | 6 | Auth legacy no tenía mappings de método. Los otros cinco publicaban 17 rutas de scaffolding; delegaban en servicios vacíos o devolvían un éxito sin operación. Se retiraron, no se confundieron con el controller activo de `auth`. |
| DTOs raíz | 17 | Todos eran `record` sin campos, sin validaciones ni lógica; solo referenciados desde el mismo scaffolding. Se conservaron los DTOs reales de `auth/dto`. |
| Services e implementaciones raíz | 12 | Seis interfaces y seis implementaciones. Sus 19 métodos se limitaban a 16 `return null` y tres excepciones `UnsupportedOperationException` con TODO. Ninguno utilizaba repositories ni realizaba persistencia/integraciones. |
| Repositories raíz | 9 | Ocho interfaces JPA sin métodos adicionales ni consumidores de negocio; el repository de usuario legacy estaba desactivado mediante `@NoRepositoryBean`. Se conserva `users.UserRepository`. |
| Entidades/enums raíz | 11 | Nueve entidades con atributos/getters/setters del ERD anterior, sin consumidores funcionales; sí eran detectadas por Hibernate. Usuario legacy no era entidad activa y el enum de rol era duplicado. Se conserva `users.User`. |

Se comprobó que no había imports, referencias plenamente calificadas ni configuración/reflexión desde el código conservado hacia esos cinco paquetes. La búsqueda estática se complementó con arranque real de Spring, metamodelo JPA, mappings MVC, OpenAPI y pruebas HTTP.

`SecurityConfig`, `StartupLogger` y `KodikalabApplication` se conservaron: Spring los registra dinámicamente aunque no tengan llamadas/imports explícitos. También se mantienen Maven Wrapper, recursos y configuración del proyecto.

## Efectos visibles y límites

- Solo están publicadas las rutas de negocio `POST /api/auth/register` y `POST /api/auth/login`.
- Las antiguas rutas ficticias de perfiles, equipos, problemas, analítica y asistente ya no aparecen en Swagger y devuelven `404`.
- Sus historias, contratos propuestos y tablas del ERD permanecen en el SDD como **pendientes**, no se presentan como implementadas.
- JPA solo administra `usuario`; ya no genera tablas legacy sin funcionalidad en una base nueva.
- Quitar clases **no elimina tablas ni filas de una base existente**. No se ejecutó ningún `DROP`, migración ni cambio sobre la base local.
- Los 55 archivos estaban versionados y sus versiones anteriores permanecen en Git. Antes de retirarlos también se guardó una copia temporal de sus versiones de trabajo fuera del repositorio; no es un artefacto a commitear.

## Inventario de eliminaciones

Todos los nombres siguientes son relativos a `src/main/java/com/kodika/kodikalab/`.

### `controller/` — 6

```text
AiAssistantController.java
AnalyticsController.java
AuthController.java
ProblemController.java
TeamsController.java
UserProfileController.java
```

### `dto/` — 17

```text
AiResponse.java
AssignProblemRequest.java
AuthResponse.java
CreateTeamRequest.java
JoinRequestDto.java
LinkHandleRequest.java
LoginRequest.java
ProblemResponse.java
ProfileResponse.java
ProgressResponse.java
PromptRequest.java
RegisterRequest.java
StandingDto.java
SubmitSolutionRequest.java
TeamResponse.java
UpdateProfileRequest.java
WeaknessReportDto.java
```

### `entity/` — 11

```text
CompetitionResult.java
ExternalHandle.java
Problem.java
ProblemAssignment.java
ProblemSubmission.java
Resource.java
Role.java
Team.java
TeamMembership.java
User.java
UserProfile.java
```

### `repository/` — 9

```text
CompetitionResultRepository.java
ProblemAssignmentRepository.java
ProblemRepository.java
ProblemSubmissionRepository.java
ResourceRepository.java
TeamMembershipRepository.java
TeamRepository.java
UserProfileRepository.java
UserRepository.java
```

### `service/` — 12

```text
AiAssistantService.java
AnalyticsService.java
AuthService.java
ProblemService.java
TeamService.java
UserProfileService.java
impl/AiAssistantServiceImpl.java
impl/AnalyticsServiceImpl.java
impl/AuthServiceImpl.java
impl/ProblemServiceImpl.java
impl/TeamServiceImpl.java
impl/UserProfileServiceImpl.java
```

## Verificación posterior a la limpieza

- `./mvnw clean compile` con Java 21: correcto, compila los 19 archivos principales y elimina clases obsoletas de `target`.
- `./mvnw test` sobre PostgreSQL temporal aislado: **106 pruebas, 0 fallos, 0 errores, 0 omitidas**.
- `RuntimeBoundaryTests`: una entidad JPA (`users.User`), un repository (`userRepository`), ausencia de beans legacy, solo dos rutas de negocio y `404` en muestras de los cinco módulos retirados.
- OpenAPI del JAR empaquetado: exactamente `/auth/register` y `/auth/login`.
- Newman: **48 solicitudes y 261 aserciones, sin fallos**; fixture de cuenta suspendida ejecutada y comprobada en la base temporal.
- PostgreSQL y backend temporales detenidos y eliminados; ERD oficial intacto, sin cambios de rama ni push. Los commits locales de entrega se completan con autorización explícita; ver `../commit-plan.md`.

Las pruebas de límites deben actualizarse deliberadamente cuando se implemente un nuevo módulo real. No reintroducir scaffolding vacío para hacerlas pasar.
