# 08 - AI Working Context

Este archivo define reglas para cualquier asistente IA o agente que trabaje en KodikaLab.

## Reglas obligatorias

- Respetar el paquete raíz `com.kodika.kodikalab`.
- Respetar la decisión arquitectónica de **monolito modular simple**.
- Preferir organización por módulos de dominio para nuevas funcionalidades.
- No modificar `.env` ni exponer secretos.
- No hacer commits, push o cambios de ramas sin autorización explícita.
- No cambiar `server.servlet.context-path: /api` sin aprobación.
- No reactivar JWT ni cerrar endpoints mientras el equipo esté en fase de desarrollo inicial, salvo pedido explícito.
- El proyecto usa enfoque code-first: las entidades JPA son la fuente de verdad del modelo de datos.
- Antes de modificar entidades, revisar `docs/sdd/04-database-model.md`.
- Antes de modificar endpoints, revisar `docs/sdd/03-api-contracts.md`.
- Antes de modificar paquetes o mover clases, revisar `docs/sdd/05-architecture.md`.
- Después de cambios relevantes en Java, ejecutar `./mvnw clean compile`.

## Contexto técnico

- Java 21.
- Spring Boot 3.5.6.
- Maven.
- PostgreSQL.
- Swagger UI disponible en `http://localhost:8080/api/swagger-ui.html`.
- Seguridad temporalmente abierta para desarrollo.

## Organización esperada

La estructura por capas inicial puede existir durante el scaffolding, pero las nuevas historias deben tender a paquetes por dominio:

```txt
com.kodika.kodikalab
├── auth
├── users
├── profiles
├── teams
├── problems
├── analytics
├── ai
├── config
├── security
└── common
```

Reglas principales:

- Controllers delegan en services.
- Controllers no usan repositories directamente.
- Un módulo debe usar preferentemente sus propios repositories.
- Evitar dependencias directas a repositories internos de otros módulos.
- Los DTOs deben vivir preferentemente dentro del módulo que los usa.
- `common` no debe depender de módulos de negocio.

## Criterio de implementación

Implementar únicamente la historia o tarea solicitada. No agregar lógica extra, librerías o cambios arquitectónicos sin confirmación del equipo.

Cuando se cree o modifique una funcionalidad, ubicarla en el módulo de dominio correspondiente siempre que sea razonable.
