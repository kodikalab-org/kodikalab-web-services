# 05 - Architecture

## Decisión arquitectónica

KodikaLab mantiene un **monolito modular simple**: una aplicación Spring Boot, un proyecto Maven y una base PostgreSQL, con paquetes internos por dominio. No son microservicios ni se requiere una arquitectura hexagonal completa para cada módulo.

Fuente de diseño vigente:

```text
docs/sdd/assets/oficial.erd
docs/sdd/04-database-model.md
```

`auth`/`users` implementa `usuario` y `profiles` implementa `coach`/`practicante` (US-03). `teams`, `problems` y `competitions` mapean el resto de tablas visibles del ERD como plantilla sin lógica (ver "Estructura de los módulos plantilla"). `assignments`, `analytics` y `ai` siguen como diseño objetivo.

## Módulos y propiedad del modelo oficial

| Módulo | Tablas / conceptos del ERD | Responsabilidad |
| --- | --- | --- |
| `auth` | Usa la cuenta `usuario`, sin ser dueño de su persistencia | Registro, login, respuesta de identidad y autenticación futura |
| `users` | `usuario` | Cuenta base, correo, hash, rol, estado y fecha de registro |
| `profiles` | `coach`, `practicante` | Perfiles de rol, datos académicos/competitivos y handles definidos por el ERD |
| `teams` | `grupo_estudio`, `practicante_grupo` | Coach responsable, grupos, cupos, horario descriptivo, membresías y solicitudes |
| `problems` | `problema`, `tema`, `problema_tema`, `material` | Catálogo, clasificación y biblioteca/recursos |
| `competitions` | `competencia`, `competencia_problema`, `resolucion_problema`, `Categoria` | Evento del grupo, problemas del evento, resoluciones y datos de scoreboard |
| `assignments` | Capacidad funcional; sin tablas genéricas propias en este ERD | Coordinación de asignación/resolución según los agregados actuales; no recrear tablas retiradas sin diseño aprobado |
| `analytics` | Lecturas derivadas de grupos, competencias y resoluciones | Progreso, rankings y métricas; no dueño de entidades transaccionales ajenas |
| `ai` | Capacidad funcional; sin tablas propias en este ERD | Asistencia inteligente; cualquier persistencia adicional requiere definición |
| `security` | Sesión, contexto de seguridad, permisos | Componentes transversales; JWT sigue pendiente |
| `config` | Beans Spring | Configuración general, sin lógica de negocio |
| `common` | Excepciones y tipos transversales | Componentes compartidos, sin dependencia de módulos de negocio |

`Categoria` se mapea en `competitions` (como en `09-component-diagram.md`) y engloba únicamente al grupo de estudio: 1:1 con `grupo_estudio`, según `oficial.erd`. Las incidencias de metadatos del ERD están registradas en `04-database-model.md`.

## Nombres y estructura

Las clases adoptan los **nombres en español del diagrama de clases** (página "CODIGO" del drawio). **Los valores de enums están en español en Java, HTTP y SQL** y se persisten con `@Enumerated(EnumType.STRING)`, sin conversores.

| Diagrama de clases (objetivo) | Clase Java actual | Tabla |
| --- | --- | --- |
| `Usuario` | `users.User` | `usuario` |
| `Practicante` | `profiles.practitioner.PractitionerProfile` | `practicante` |
| `Coach` | `profiles.coach.CoachProfile` | `coach` |
| `GrupoEstudio` | `teams.studygroup.StudyGroup` | `grupo_estudio` |
| `PracticanteGrupo` | `teams.groupmembership.GroupMembership` | `practicante_grupo` |
| `Competencia` | `competitions.competition.Competition` | `competencia` |
| `CompetenciaProblema` | `competitions.competitionproblem.CompetitionProblem` | `competencia_problema` |
| `ResultadoCompetencia` | `competitions.problemresolution.ProblemResolution` | `resolucion_problema` |
| `Categoria` | `competitions.category.Category` | `categoria` |
| `Problema` | `problems.problem.Problem` | `problema` |
| `Tema` | `problems.topic.Topic` | `tema` |
| `ProblemaTema` | `problems.problemtopic.ProblemTopic` | `problema_tema` |
| `Material` | `problems.material.Material` | `material` |

Repositorios, servicios y controllers siguen el mismo nombre (`GrupoEstudioRepository`, `GrupoEstudioService`,
`GrupoEstudioController`...), como en `09-component-diagram.md`. Los enums se nombran también en español
(`Rol`, `EstadoCuenta`, `NivelCompetitivo`...) y sus valores no cambian. **El renombrado del código está
pendiente de ejecutar**; hasta entonces el código conserva los nombres en inglés de la columna central.
El alcance para paquetes de módulo, rutas HTTP y claves JSON se define antes de ejecutar el renombrado.

Las tablas y columnas físicas siguen el ERD. Los identificadores físicos se mapean **exactamente como aparecen en el ERD**. Por ejemplo, `users.User` corresponde a `usuario`, no a una tabla inventada `users`.

```text
com.kodika.kodikalab
├── auth
├── users
├── profiles
├── teams
├── problems
├── assignments
├── competitions
├── analytics
├── ai
├── security
├── config
└── common
```

Para los módulos pequeños basta reunir clases relacionadas y un paquete `dto`. No crear capas adicionales por costumbre.

Estructura implementada de auth/users:

```text
users/
├── User.java                 # @Table(name = "usuario")
├── Role.java                 # PRACTICANTE / COACH
├── UserStatus.java           # ACTIVO / SUSPENDIDO
├── UserRepository.java       # Tipo de ID coherente con SERIAL/INT
└── UserService.java          # Frontera pública de persistencia

auth/
├── AuthController.java
├── AuthService.java
├── AuthServiceImpl.java
├── AuthExceptionHandler.java
└── dto/
    ├── RegisterRequest.java
    ├── LoginRequest.java
    └── AuthResponse.java
```

Los perfiles `Coach` / `Practitioner` pertenecen al ámbito `profiles`, con PK compartida `usuario_id`. La creación de perfiles con sus datos obligatorios requiere su propio contrato; no se agrega automáticamente a la adaptación de cuentas base.

Estructura implementada de profiles (US-03). Es **un solo módulo**: los subpaquetes `practitioner` y `coach` ordenan su interior, pero comparten el endpoint `/users/me` y sus piezas transversales en la raíz del módulo. No son módulos independientes ni deben depender entre sí.

```text
profiles/
├── ProfileController.java            # GET/PUT /users/me, despacha según el rol de la sesión
├── CurrentUserResolver.java          # Usuario de la sesión (401 si no hay)
├── ProfileRequestReader.java         # Convierte y valida el body según el rol
├── ProfileExceptionHandler.java
├── ProfileValidationException.java
├── practitioner/
│   ├── PractitionerProfile.java      # @Table(name = "practicante")
│   ├── PractitionerLevel.java
│   ├── PractitionerProfileRepository.java
│   ├── PractitionerProfileService.java
│   ├── PractitionerProfileServiceImpl.java
│   ├── dto/
│   └── integration/                  # Cliente de la API pública de Codeforces
└── coach/
    ├── CoachProfile.java             # @Table(name = "coach")
    ├── CoachProfileRepository.java
    ├── CoachProfileService.java
    ├── CoachProfileServiceImpl.java
    └── dto/
```

### Estructura de los módulos plantilla

Generados desde las tablas visibles de `oficial.erd`, con **una carpeta por entidad** dentro de cada módulo
(mismo patrón que `profiles/coach` y `profiles/practitioner`). Cada carpeta contiene la entidad, sus enums, su
repositorio y su servicio plantilla; el controller del módulo vive en la raíz. No tienen lógica: los servicios
solo reciben su repositorio y los controllers no declaran endpoints, por eso sus rutas siguen devolviendo `404`.

```text
teams/                         /api/teams
├── StudyGroupController
├── studygroup/                StudyGroup (grupo_estudio), GroupStatus, GroupVisibility,
│                              StudyGroupRepository, StudyGroupService, StudyGroupServiceImpl
└── groupmembership/           GroupMembership (practicante_grupo), MembershipStatus, TeamRole,
                               GroupMembershipRepository, GroupMembershipService, GroupMembershipServiceImpl

competitions/                  /api/competitions
├── CompetitionController
├── competition/               Competition (competencia), CompetitionAccessType, PenaltyRule,
│                              CompetitionStatus, CompetitionRepository, CompetitionService(Impl)
├── competitionproblem/        CompetitionProblem (competencia_problema), Repository, Service(Impl)
├── problemresolution/         ProblemResolution (resolucion_problema), Verdict, Repository, Service(Impl)
└── category/                  Category (Categoria), Repository, Service(Impl)

problems/                      /api/problems
├── ProblemController
├── problem/                   Problem (problema), SourcePlatform, Repository, Service(Impl)
├── topic/                     Topic (tema), Repository, Service(Impl)
├── problemtopic/              ProblemTopic + ProblemTopicId (problema_tema), Repository, Service(Impl)
└── material/                  Material (material), ResourceType, Repository, Service(Impl)
```

Cada historia declara su operación en el servicio de la entidad, la implementa en `XxxServiceImpl`, agrega el
endpoint al controller del módulo con su contrato en `03-api-contracts.md`, y crea sus DTOs en `<entidad>/dto`
y su `@RestControllerAdvice`, siguiendo el patrón de `profiles`. `ErdSchemaIntegrationTests` verifica que el
esquema generado coincida con el ERD.

Para módulos que crezcan se permite refinar capas internas, sin obligar a migrar todo el proyecto.

## Reglas de dependencia

- Controllers delegan en servicios y no acceden a repositories.
- Cada módulo controla sus repositories y sus entidades.
- `auth` obtiene cuentas mediante el servicio público `users.UserService`, no mediante `UserRepository`.
- Un módulo usa servicios públicos o abstracciones claras de otro; no atraviesa sus repositories internos.
- DTOs permanecen en el módulo que los usa.
- Entidades no dependen de controllers, servicios ni DTOs.
- `common` no depende de módulos de negocio.
- `security` y `config` implementan preocupaciones técnicas, no reglas de negocio.

Registro/login no requieren un `AuthRepository`: no existe una entidad persistente propia de autenticación en el ERD. La sesión HTTP actual vive en memoria mediante Spring Security.

## Relaciones justificadas por el ERD

En el monolito, las asociaciones JPA pueden cruzar módulos si respetan las relaciones oficiales:

- `Coach` y `Practitioner` referencian `User` con PK/FK compartida.
- `StudyGroup` referencia a `Coach`, no directamente a cualquier `User`.
- `GroupMembership` referencia a `StudyGroup` y `Practitioner`.
- `Competition` pertenece a un `StudyGroup`.
- `CompetitionProblem` relaciona `Competition` y `Problem`.
- `ProblemResolution` relaciona un problema de competencia y una membresía, sujeto a resolver las inconsistencias de metadatos del ERD.
- `Material` puede asociarse a un `Problem` o no tenerlo para representar biblioteca libre.

No deducir una asociación nueva solo de un nombre de columna ni introducir columnas discriminadoras/IDs adicionales no presentes en el diseño.

## API y módulos

Se conserva el prefijo `/api` y las rutas funcionales inglesas. El nombre físico de una tabla no impone renombrar el endpoint.

| Ruta | Módulo / estado |
| --- | --- |
| `/auth/**` | `auth`; registro/login persisten en `usuario` según el ERD nuevo |
| `/users/**` | Propuesta pendiente de `users` / `profiles`; no publicada |
| `/teams/**` | Propuesta pendiente de `teams`; no publicada |
| `/problems/**` | Propuesta pendiente de `problems` / asignación; no publicada |
| `/analytics/**` | Propuesta pendiente de `analytics`; no publicada |
| `/assistant/**` | Propuesta pendiente de `ai`; no publicada |

Las rutas pendientes se conservan únicamente como contratos propuestos. Los controllers plantilla de `teams`, `problems` y `competitions` no declaran endpoints, así que no aparecen en Swagger; sus solicitudes devuelven `404`, no una respuesta ficticia de éxito.

## Migración por alcance

1. Actualizar primero el SDD con los diagramas y marcar diferencias frente al código.
2. Implementar la lógica de cada módulo solo en su historia, con pruebas y Postman.
3. No modificar código de equipos, competencias, catálogo o perfiles fuera de la tarea correspondiente.
4. El scaffolding legacy sin lógica fue retirado por autorización explícita; no restaurarlo para simular funcionalidades. Las plantillas del ERD actual no exponen endpoints hasta su historia. Revisar dependencias y avisar antes de nuevas eliminaciones.
5. No ejecutar migraciones sobre datos reales ni confiar en `ddl-auto=update` para renombrar tablas o convertir valores.
6. Validar en PostgreSQL aislado y ejecutar `./mvnw clean compile` tras cambios Java.

Todas las tablas del ERD tienen entidad JPA; solo `usuario`, `coach` y `practicante` tienen lógica. Quitar clases no elimina tablas ni migra datos existentes. La limpieza está documentada en `12-source-cleanup.md`.
