# SDD - Spec Driven Development

Esta carpeta contiene el contexto funcional y técnico de KodikaLab.

Debe ser usada como fuente de consulta por desarrolladores y asistentes IA antes de implementar cambios importantes.

## Decisión arquitectónica principal

KodikaLab se trabajará como un **monolito modular simple**: una sola aplicación Spring Boot y una sola base de datos PostgreSQL, organizada internamente por módulos de dominio.

La modularización debe respetar el diseño de dominio y datos documentado en el ERD:

```txt
docs/sdd/assets/diagrama_entidad_relacion.erd
```

Los módulos esperados son:

- `auth`
- `users`
- `profiles`
- `teams`
- `problems`
- `assignments`
- `competitions`
- `analytics`
- `ai`
- `config`
- `security`
- `common`

La estructura inicial por capas puede existir durante el scaffolding, pero las nuevas funcionalidades deben tender a la organización por dominio descrita en `05-architecture.md`.

## Índice

1. [`00-project-context.md`](00-project-context.md) - Contexto general del proyecto.
2. [`01-business-domain.md`](01-business-domain.md) - Glosario y dominio de negocio.
3. [`02-user-stories.md`](02-user-stories.md) - Historias de usuario.
4. [`03-api-contracts.md`](03-api-contracts.md) - Contratos y rutas de API.
5. [`04-database-model.md`](04-database-model.md) - Modelo de datos, ERD y estrategia de BD.
6. [`05-architecture.md`](05-architecture.md) - Arquitectura del backend y monolito modular.
7. [`06-security-strategy.md`](06-security-strategy.md) - Estrategia de seguridad.
8. [`07-development-guidelines.md`](07-development-guidelines.md) - Guías de desarrollo.
9. [`08-ai-working-context.md`](08-ai-working-context.md) - Reglas para trabajo asistido por IA.
10. [`09-us04-implementation.md`](09-us04-implementation.md) - Alcance, estructura modular y pruebas de T2/T3 de US-04.

## Assets

Los artefactos del modelo de datos deben colocarse en:

```txt
docs/sdd/assets/
```

Artefactos actuales:

- `diagrama_entidad_relacion.erd`
- `init_schema.sql`
- `script_inicio.txt`
- `script_inicio_es.txt`
