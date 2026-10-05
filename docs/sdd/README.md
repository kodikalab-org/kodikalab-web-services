# SDD - Spec Driven Development

Esta carpeta contiene el contexto funcional y técnico de KodikaLab. Debe revisarse antes de implementar cambios.

## Diseño oficial

El proyecto es un **monolito modular simple**: una aplicación Spring Boot, un proyecto Maven y una base PostgreSQL, con módulos internos de dominio.

El artefacto vigente es:

```text
docs/sdd/assets/oficial.erd
```

Es el archivo encontrado en el repositorio, aunque se haya referido informalmente como `docs/assets`. No hay un snapshot SQL vigente en la carpeta de assets. No se deben restaurar scripts retirados ni inventar rutas de archivos inexistentes como fuente actual.

## Estado de esta actualización

1. **SDD** alineado con las tablas/campos del nuevo ERD.
2. **`auth`/`users` implementados** sobre `usuario`, con validaciones, enums en español persistidos directamente, pruebas y Postman actualizados.
3. Scaffolding sin lógica retirado; otros módulos se implementan solo en su historia correspondiente.

La documentación distingue modelo objetivo, código implementado y datos existentes. No se migraron cuentas anteriores ni el esquema global.

Cambios centrales: `usuario` con nombre completo y correo de 100 caracteres, roles SQL `COACH`/`PRACTICANTE`, estados `ACTIVO`/`SUSPENDIDO`, perfiles separados con PK compartida y tablas de grupos/competencias rediseñadas.

El JSON del ERD tiene metadatos incompletos en solicitudes, resoluciones y categoría. Se documentan en `04-database-model.md`; el archivo oficial no se modifica silenciosamente.

## Módulos

`auth`, `users`, `profiles`, `teams`, `problems`, `assignments`, `competitions`, `analytics`, `ai`, `security`, `config` y `common`.

La propiedad de tablas y las capacidades sin tablas oficiales se detallan en `05-architecture.md`. Los paquetes/claves JSON conservan sus nombres ingleses; los valores de enums de cuenta están en español en Java, HTTP y SQL, sin converters. Los nombres físicos respetan el ERD.

## Índice

1. [00-project-context.md](00-project-context.md) — Contexto y estado del proyecto.
2. [01-business-domain.md](01-business-domain.md) — Dominio y enums en español.
3. [02-user-stories.md](02-user-stories.md) — Historias funcionales y relación con el modelo.
4. [03-api-contracts.md](03-api-contracts.md) — Contratos HTTP y límites objetivo de auth.
5. [04-database-model.md](04-database-model.md) — Tablas oficiales, relaciones, diferencias y migración segura.
6. [05-architecture.md](05-architecture.md) — Monolito modular y propiedad de datos.
7. [06-security-strategy.md](06-security-strategy.md) — Autenticación, sesiones y seguridad pendiente.
8. [07-development-guidelines.md](07-development-guidelines.md) — Flujo de desarrollo y Git.
9. [08-ai-working-context.md](08-ai-working-context.md) — Reglas para asistentes.
10. [10-us02-login.md](10-us02-login.md) — Diseño del login alineado al ERD oficial.
11. [11-erd-oficial-alignment.md](11-erd-oficial-alignment.md) — Secuencia de adaptación de auth/users.
12. [12-source-cleanup.md](12-source-cleanup.md) — Revisión funcional y eliminación del scaffolding sin uso.

## Assets

Único artefacto actual: [oficial.erd](assets/oficial.erd).

Un nuevo snapshot SQL puede incorporarse después de revisar los metadatos y validar entidades/DDL. No es una migración de datos ni un requisito para arrancar una base vacía en modo code-first.
