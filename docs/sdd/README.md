# SDD - Spec Driven Development

Esta carpeta contiene el contexto funcional y técnico de KodikaLab. Debe revisarse antes de implementar cambios.

## Diseño oficial

El proyecto es un **monolito modular simple**: una aplicación Spring Boot, un proyecto Maven y una base PostgreSQL, con módulos internos de dominio.

Artefactos de diseño:

```text
docs/sdd/assets/oficial.erd          ERD en formato erd-editor (en el repositorio)
Diagrama_C4_Kodikalab.drawio       Diagramas del equipo (Drive): C4, componentes, clases ("CODIGO") y ERD ("Base de Datos")
```

**Precedencia:** para datos (tablas, columnas, tipos, relaciones) manda **`oficial.erd`**, el ERD oficial. La página "Base de Datos" del drawio es su representación visual y debe coincidir con él. El **diagrama de clases** (página "CODIGO") define los **nombres de las clases** y su comportamiento (métodos). Las diferencias de atributos entre ambos diagramas se registran en `04-database-model.md` para que el equipo corrija el diagrama de clases; no cambian el modelo de datos.

`Categoria` pertenece únicamente al grupo de estudio, como en `oficial.erd`. La página "Base de Datos" del drawio
aún muestra `competencia.categoria_id` y debe corregirse (ver `04-database-model.md`). No hay un snapshot SQL vigente en la carpeta de assets. No se deben restaurar scripts retirados ni inventar rutas de archivos inexistentes como fuente actual.

## Estado de esta actualización

1. **SDD** alineado con las tablas/campos del nuevo ERD.
2. **`auth`/`users` implementados** sobre `usuario`, con validaciones, enums en español persistidos directamente, pruebas y Postman actualizados.
3. Scaffolding sin lógica retirado; otros módulos se implementan solo en su historia correspondiente.

La documentación distingue modelo objetivo, código implementado y datos existentes. No se migraron cuentas anteriores ni el esquema global.

Cambios centrales: `usuario` con nombre completo y correo de 100 caracteres, roles SQL `COACH`/`PRACTICANTE`, estados `ACTIVO`/`SUSPENDIDO`, perfiles separados con PK compartida y tablas de grupos/competencias rediseñadas.

El JSON de `oficial.erd` tiene metadatos incompletos en resoluciones y categoría. Se documentan en `04-database-model.md`; el archivo oficial no se modifica silenciosamente.

## Módulos

`auth`, `users`, `profiles`, `teams`, `problems`, `assignments`, `competitions`, `analytics`, `ai`, `security`, `config` y `common`.

La propiedad de tablas y las capacidades sin tablas oficiales se detallan en `05-architecture.md`. Las clases adoptan los nombres en español del diagrama de clases (renombrado pendiente; ver `05-architecture.md`); los valores de enums están en español en Java, HTTP y SQL, sin converters. Los nombres físicos respetan el ERD.

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
13. [09-component-diagram.md](09-component-diagram.md) — Componentes del API y trazabilidad clase/tabla/US.

## Assets

En el repositorio: [oficial.erd](assets/oficial.erd). Los diagramas del equipo están en `Diagrama_C4_Kodikalab.drawio` (Google Drive).

Un nuevo snapshot SQL puede incorporarse después de revisar los metadatos y validar entidades/DDL. No es una migración de datos ni un requisito para arrancar una base vacía en modo code-first.
