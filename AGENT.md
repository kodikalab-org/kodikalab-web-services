# AGENT.md - KodikaLab

Antes de modificar este proyecto, revisar:

- `README.md`
- `docs/sdd/README.md`
- `docs/sdd/00-project-context.md`
- `docs/sdd/04-database-model.md`
- `docs/sdd/05-architecture.md`
- `docs/sdd/08-ai-working-context.md`
- `docs/sdd/assets/diagrama_entidad_relacion.erd`

## Reglas para asistentes IA

- No ejecutar `git add`, `git commit`, `git push` ni cambios de rama sin autorización explícita.
- No modificar `.env` ni exponer secretos.
- No cambiar el context path `/api` sin aprobación.
- No activar seguridad JWT mientras el equipo esté desarrollando endpoints base, salvo solicitud explícita.
- Mantener Java 21 y Spring Boot 3.5.6.
- Respetar la arquitectura de **monolito modular simple** definida en el SDD.
- Usar el ERD del SDD como referencia de diseño de datos y separación de módulos.
- Ejecutar `./mvnw clean compile` después de cambios relevantes en Java.

## Arquitectura objetivo

KodikaLab es una sola aplicación Spring Boot con una sola base de datos PostgreSQL, organizada internamente por módulos de dominio.

La estructura inicial por capas puede existir durante el scaffolding, pero las nuevas funcionalidades deben tender a paquetes por dominio:

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
|---|---|
| `auth` | Registro, login, autenticación y JWT futuro. |
| `users` | Cuenta de usuario, rol, estado y disponibilidad. |
| `profiles` | Perfil competitivo y cuentas externas. |
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
docs/sdd/assets/diagrama_entidad_relacion.erd
docs/sdd/assets/init_schema.sql
docs/sdd/04-database-model.md
```

Antes de modificar entidades JPA o relaciones:

1. Revisar el ERD.
2. Revisar `docs/sdd/04-database-model.md`.
3. Aplicar el cambio en código.
4. Actualizar documentación, ERD o SQL si el diseño cambia.
