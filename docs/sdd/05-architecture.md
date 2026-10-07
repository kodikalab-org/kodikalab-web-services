# 05 - Architecture

## Decisión arquitectónica

KodikaLab mantiene un **monolito modular simple**: una aplicación Spring Boot, un proyecto Maven y una base PostgreSQL, con paquetes internos por dominio. No son microservicios ni se requiere una arquitectura hexagonal completa para cada módulo.

Fuente de diseño vigente:

```text
docs/sdd/assets/oficial.erd
docs/sdd/04-database-model.md
```

La adaptación de `auth`/`users` implementa `usuario`. El scaffolding de otros módulos fue retirado tras su revisión: no hay implementaciones ni entidades JPA adicionales; los demás módulos siguen como diseño objetivo.

## Módulos y propiedad del modelo oficial

| Módulo | Tablas / conceptos del ERD | Responsabilidad |
| --- | --- | --- |
| `auth` | Usa la cuenta `usuario`, sin ser dueño de su persistencia | Registro, login, respuesta de identidad y autenticación futura |
| `users` | `usuario` | Cuenta base, correo, hash, rol, estado y fecha de registro |
| `profiles` | `coach`, `practicante` | Perfiles de rol, datos académicos/competitivos y handles definidos por el ERD |
| `teams` | `grupo_estudio`, `practicante_grupo`, `solicitud_grupo` | Coach responsable, grupos, cupos, horario descriptivo, membresías y solicitudes |
| `problems` | `problema`, `tema`, `problema_tema`, `material` | Catálogo, clasificación y biblioteca/recursos |
| `competitions` | `competencia`, `competencia_problema`, `resolucion_problema` | Evento del grupo, problemas del evento, resoluciones y datos de scoreboard |
| `assignments` | Capacidad funcional; sin tablas genéricas propias en este ERD | Coordinación de asignación/resolución según los agregados actuales; no recrear tablas retiradas sin diseño aprobado |
| `analytics` | Lecturas derivadas de grupos, competencias y resoluciones | Progreso, rankings y métricas; no dueño de entidades transaccionales ajenas |
| `ai` | Capacidad funcional; sin tablas propias en este ERD | Asistencia inteligente; cualquier persistencia adicional requiere definición |
| `security` | Sesión, contexto de seguridad, permisos | Componentes transversales; JWT sigue pendiente |
| `config` | Beans Spring | Configuración general, sin lógica de negocio |
| `common` | Excepciones y tipos transversales | Componentes compartidos, sin dependencia de módulos de negocio |

`Categoria` necesita aclarar nombres, claves y relaciones antes de asignar un módulo dueño. Las incidencias de metadatos del ERD están registradas en `04-database-model.md`.

## Nombres y estructura

Las clases, paquetes, rutas y claves JSON conservan sus nombres ingleses. **Los valores de enums de cuenta están en español en Java, HTTP y SQL** y se persisten con `@Enumerated(EnumType.STRING)`, sin conversores. Los identificadores físicos se mapean **exactamente como aparecen en el ERD**. Por ejemplo, `users.User` corresponde a `usuario`, no a una tabla inventada `users`.

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

Las rutas pendientes se conservan únicamente como contratos propuestos. Sus controllers de scaffolding se eliminaron y ya no aparecen en Swagger; sus solicitudes devuelven `404`, no una respuesta ficticia de éxito.

## Migración por alcance

1. Actualizar primero SDD con el archivo oficial y marcar diferencias frente al código.
2. Adaptar únicamente `auth`/`users`, con pruebas y Postman correspondientes.
3. No modificar código de equipos, competencias, catálogo o perfiles sin la tarea correspondiente.
4. El scaffolding legacy sin lógica fue retirado por autorización explícita; no restaurarlo para simular funcionalidades. Revisar dependencias y avisar antes de nuevas eliminaciones.
5. No ejecutar migraciones sobre datos reales ni confiar en `ddl-auto=update` para renombrar tablas o convertir valores.
6. Validar en PostgreSQL aislado y ejecutar `./mvnw clean compile` tras cambios Java.

La persistencia implementada se limita a `usuario`; las demás tablas oficiales todavía no tienen entidades JPA. Quitar clases no elimina tablas ni migra datos existentes. La limpieza está documentada en `12-source-cleanup.md`.
