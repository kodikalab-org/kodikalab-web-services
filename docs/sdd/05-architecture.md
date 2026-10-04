# 05 - Architecture

## Decisión arquitectónica

KodikaLab se desarrollará como un **monolito modular simple** sobre Spring Boot.

Esto significa:

- Una sola aplicación backend desplegable.
- Un solo proyecto Maven.
- Una sola base de datos PostgreSQL.
- Código organizado por módulos de negocio.
- Separación interna clara para que cada módulo sea mantenible.
- Modelo de datos compartido dentro del mismo monolito, documentado en el ERD del SDD.

Esta decisión **no convierte el proyecto en microservicios**. Los módulos son paquetes internos dentro de la misma aplicación.

## Fuente de verdad del diseño

La modularización debe respetar el diseño de dominio y datos documentado en:

```txt
docs/sdd/assets/diagrama_entidad_relacion.erd
docs/sdd/assets/init_schema.sql
docs/sdd/04-database-model.md
```

El ERD muestra los agregados principales del sistema y ayuda a definir los módulos. Por eso, la separación modular debe salir del dominio real y no solo de nombres de carpetas.

## Módulos oficiales

| Módulo | Tablas / conceptos principales | Responsabilidad |
|---|---|---|
| `auth` | `users` | Registro, login, autenticación y emisión futura de JWT. |
| `users` | `users`, `user_availabilities` | Cuenta de usuario, rol, estado y disponibilidad personal. |
| `profiles` | `competitive_profiles`, `external_accounts` | Perfil competitivo y cuentas externas como Codeforces o LeetCode. |
| `teams` | `teams`, `team_memberships`, `join_requests`, `team_schedules` | Equipos, coach, membresías, solicitudes de ingreso y horarios. |
| `problems` | `problems`, `topics`, `academic_resources`, tablas puente de problemas/temas/recursos | Catálogo de problemas, temas y recursos académicos. |
| `assignments` | `assignments`, `assignment_details`, `assignment_recipients`, `submissions` | Asignación de problemas, destinatarios y resoluciones/envíos. |
| `competitions` | `competitions`, `competition_results` | Competencias y resultados de equipos. |
| `analytics` | Lecturas derivadas de equipos, asignaciones, resoluciones y competencias | Progreso, rankings, debilidades y métricas. No debe ser dueño principal de entidades transaccionales. |
| `ai` | `ai_conversations`, `ai_messages`, `agent_actions` | Conversaciones IA, mensajes y acciones del agente. |
| `security` | Configuración y componentes de seguridad | Filtros, permisos, JWT y protección de endpoints. |
| `config` | Configuración Spring transversal | Beans y configuración general. |
| `common` | Componentes compartidos | Excepciones, respuestas comunes, utilidades y tipos transversales. |

## Nota sobre nombres de módulos

Los paquetes Java deben nombrarse en inglés y en minúsculas.

Ejemplos:

```txt
com.kodika.kodikalab.teams
com.kodika.kodikalab.problems
com.kodika.kodikalab.assignments
```

Los nombres funcionales en documentación pueden mantenerse en español.

## Estructura esperada de paquetes

El scaffolding inicial puede existir por capas:

```txt
controller/
service/
service.impl/
repository/
entity/
dto/
```

Pero la dirección oficial del proyecto es evolucionar a módulos de dominio:

```txt
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
├── config
├── security
└── common
```

## Estructura interna recomendada por módulo

Para módulos pequeños:

```txt
users/
├── User.java
├── Role.java
├── UserRepository.java
├── UserService.java
└── dto/
```

Para módulos con varios casos de uso:

```txt
teams/
├── TeamsController.java
├── TeamService.java
├── TeamServiceImpl.java
├── Team.java
├── TeamMembership.java
├── JoinRequest.java
├── TeamSchedule.java
├── TeamRepository.java
├── TeamMembershipRepository.java
├── JoinRequestRepository.java
├── TeamScheduleRepository.java
└── dto/
```

Para módulos que crezcan demasiado, se permite refinar internamente:

```txt
teams/
├── api/
├── application/
├── domain/
├── infrastructure/
└── dto/
```

Esta separación estricta no es obligatoria al inicio. La prioridad es que los archivos de una misma capacidad de negocio estén juntos.

## Propiedad de entidades por módulo

Cada entidad JPA debe tener un módulo dueño principal.

Ejemplos:

| Entidad Java esperada | Tabla | Módulo dueño |
|---|---|---|
| `User` | `users` | `users` |
| `UserAvailability` | `user_availabilities` | `users` |
| `CompetitiveProfile` | `competitive_profiles` | `profiles` |
| `ExternalAccount` | `external_accounts` | `profiles` |
| `Team` | `teams` | `teams` |
| `TeamMembership` | `team_memberships` | `teams` |
| `JoinRequest` | `join_requests` | `teams` |
| `TeamSchedule` | `team_schedules` | `teams` |
| `Problem` | `problems` | `problems` |
| `Topic` | `topics` | `problems` |
| `AcademicResource` | `academic_resources` | `problems` |
| `Assignment` | `assignments` | `assignments` |
| `AssignmentDetail` | `assignment_details` | `assignments` |
| `Submission` | `submissions` | `assignments` |
| `Competition` | `competitions` | `competitions` |
| `CompetitionResult` | `competition_results` | `competitions` |
| `AiConversation` | `ai_conversations` | `ai` |
| `AiMessage` | `ai_messages` | `ai` |
| `AgentAction` | `agent_actions` | `ai` |

## Reglas de dependencia entre módulos

- Un controller solo debe delegar en servicios.
- Un controller no debe acceder directamente a repositories.
- Un servicio puede usar repositories de su propio módulo.
- Evitar que un módulo use repositories internos de otro módulo.
- Si un módulo necesita datos de otro, debe hacerlo mediante un servicio público del otro módulo o una abstracción clara.
- Las entidades JPA no deben depender de controllers, DTOs o services.
- Los DTOs se ubican preferentemente dentro del módulo que los usa.
- `common` no debe depender de módulos de negocio.
- `config` y `security` deben mantenerse transversales y sin lógica de negocio.

## Relaciones entre entidades de distintos módulos

Como es un monolito con una sola base de datos, las entidades pueden tener relaciones JPA entre módulos cuando el ERD lo justifique.

Ejemplos:

- `Team` puede referenciar a `User` como coach.
- `TeamMembership` puede referenciar a `User` y `Team`.
- `Assignment` puede referenciar a `Team` y a `User` como creador.
- `Submission` puede referenciar a `TeamMembership` y `AssignmentDetail`.

La regla importante es no convertir esas relaciones en acceso desordenado a repositories de otros módulos.

## API y módulos

Los endpoints definidos en `03-api-contracts.md` deben mapearse al módulo correspondiente:

| Ruta | Módulo |
|---|---|
| `/auth/**` | `auth` |
| `/users/**` | `users` / `profiles` según el caso |
| `/teams/**` | `teams` |
| `/problems/**` | `problems` / `assignments` según el caso |
| `/analytics/**` | `analytics` |
| `/assistant/**` | `ai` |

## Context path

El proyecto usa:

```yaml
server.servlet.context-path: /api
```

Por lo tanto, un controller con:

```java
@RequestMapping("/teams")
```

queda expuesto como:

```txt
/api/teams
```

## Criterio de migración

- No es necesario mover todo el proyecto en un solo cambio.
- Las nuevas historias deben preferir estructura por dominio.
- Cuando se toque una funcionalidad existente, se puede aprovechar para moverla a su módulo.
- La migración debe mantener compilación verde con `./mvnw clean compile`.
- Cualquier cambio de módulo que afecte entidades debe contrastarse con el ERD y `04-database-model.md`.
