# AGENT.md - KodikaLab

Antes de modificar este proyecto, revisar:

- `README.md`
- `docs/sdd/README.md`
- `docs/sdd/00-project-context.md`
- `docs/sdd/04-database-model.md`
- `docs/sdd/05-architecture.md`
- `docs/sdd/08-ai-working-context.md`
- `docs/sdd/assets/oficial.erd`
- `docs/sdd/11-erd-oficial-alignment.md`

## Reglas para asistentes IA

- No ejecutar `git add`, `git commit`, `git push` ni cambios de rama sin autorización explícita.
- No modificar `.env` ni exponer secretos.
- No cambiar el context path `/api` sin aprobación.
- JWT y la autorización por rol están activos (`config.SecurityConfig`). Todo endpoint nuevo exige token Bearer; si es exclusivo de `COACH` o `PRACTICANTE`, declarar su regla en `SecurityConfig` y conservar en el servicio las verificaciones de pertenencia/propiedad. No usar sesión HTTP ni cookies.
- Mantener Java 21 y Spring Boot 3.5.6.
- Respetar la arquitectura de **monolito modular simple** definida en el SDD.
- Usar el ERD del SDD como referencia de diseño de datos y separación de módulos.
- Ejecutar `./mvnw clean compile` después de cambios relevantes en Java.

## Arquitectura objetivo

KodikaLab es una sola aplicación Spring Boot con una sola base de datos PostgreSQL, organizada internamente por módulos de dominio.

El scaffolding raíz sin funcionalidad fue retirado tras revisión y autorización. Conservar los módulos funcionales actuales (`auth`, `users`, `security`, `config`, `common`), incluido `auth/dto`. Los módulos `teams`, `problems` y `competitions` existen como **plantilla del ERD** (decisión del dueño del proyecto): entidades JPA, enums, repositorios, servicios y controllers sin lógica ni endpoints. Cada historia agrega sus métodos y endpoints con su contrato; no agregar respuestas ficticias ni endpoints sin historia. Los módulos sin tablas en el ERD (`assignments`, `analytics`, `ai`) se crean solo al implementar su historia:

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
├── security
├── config
└── common
```

## Módulos principales

| Módulo | Responsabilidad |
| --- | --- |
| `auth` | Registro, login y emisión del token JWT. |
| `users` | Cuenta base `usuario`, correo, hash, rol y estado. |
| `profiles` | Perfiles `coach`/`practicante`, datos académicos/competitivos y handles del ERD. |
| `teams` | Equipos, coach, membresías, solicitudes de ingreso y horarios. |
| `problems` | Catálogo de problemas, temas y recursos académicos. |
| `assignments` | Asignaciones, destinatarios, detalles y resoluciones/envíos. |
| `competitions` | Competencias y resultados. |
| `analytics` | Métricas, progreso, rankings y debilidades. |
| `ai` | Conversaciones, mensajes y acciones del asistente IA. |
| `security` | Filtros, permisos y seguridad transversal. |
| `config` | Configuración transversal de Spring. |
| `common` | Excepciones, respuestas comunes y utilidades compartidas. |

## Reglas de implementación

- Controllers delegan en services.
- Controllers no acceden directamente a repositories.
- Un módulo debe usar preferentemente sus propios repositories.
- Evitar acceder directamente a repositories internos de otros módulos.
- Si un módulo necesita datos de otro, usar un servicio público del otro módulo o una abstracción clara.
- Los DTOs deben vivir preferentemente dentro del módulo que los usa.
- Las entidades JPA no deben depender de controllers, DTOs o services.
- `common` no debe depender de módulos de negocio.
- `config` y `security` no deben contener lógica de negocio.

## Modelo de datos

El proyecto usa enfoque **code-first guiado por el ERD del SDD**.

Referencias:

```txt
docs/sdd/assets/oficial.erd
docs/sdd/04-database-model.md
docs/sdd/11-erd-oficial-alignment.md
```

Los nombres físicos se respetan como figuran en el ERD, aunque estén en español; las clases, rutas y claves JSON conservan sus nombres ingleses, pero los valores de enums de cuenta están en español (`PRACTICANTE`/`COACH`, `ACTIVO`/`SUSPENDIDO`) en Java, HTTP y SQL. Persistirlos directamente con `@Enumerated(EnumType.STRING)`, sin converters. No hay un snapshot SQL vigente en assets. `auth`/`users` ya están adaptados a `usuario`; otros módulos y datos existentes siguen sin migrar. Usar solo datos ficticios (`Usuario Prueba`, `test@gmail.com`) en documentación y ejemplos.

Antes de modificar entidades JPA o relaciones:

1. Revisar el ERD.
2. Revisar `docs/sdd/04-database-model.md`.
3. Aplicar el cambio en código.
4. Actualizar documentación, ERD o SQL si el diseño cambia.
