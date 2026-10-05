# 00 - Project Context

## Proyecto

**KodikaLab - Backend Web Services** es el backend del proyecto académico desarrollado para el curso de **Ingeniería de Software (UPC 2026-2)**, en el contexto de la startup **ProgramaJuntos**.

## Propósito

KodikaLab busca apoyar a estudiantes que practican programación competitiva mediante gestión de usuarios, perfiles, equipos, problemas, recursos, progreso, rankings y asistencia inteligente.

## Stack base

- Java 21.
- Spring Boot 3.5.6.
- Maven.
- PostgreSQL.
- Spring Data JPA.
- Spring Security.
- SpringDoc OpenAPI / Swagger UI.

## Enfoque arquitectónico

El backend se desarrollará como un **monolito modular simple**.

Esto implica una sola aplicación Spring Boot y una sola base de datos, pero con el código organizado por módulos de dominio como `auth`, `users`, `profiles`, `teams`, `problems`, `analytics` y `ai`.

El código implementado se organiza por dominio. El scaffolding inicial por capas fue retirado tras revisar sus dependencias y confirmar que carecía de lógica funcional.

## Fuente de datos vigente

El diseño oficial está en `docs/sdd/assets/oficial.erd`. Sus tablas físicas usan los nombres allí definidos, por ejemplo `usuario`, `coach`, `practicante` y `grupo_estudio`; las clases, rutas y claves JSON conservan sus nombres ingleses, mientras los valores de enums de cuenta usan el español del ERD.

No hay un snapshot SQL vigente en la carpeta de assets. Los campos, relaciones y diferencias frente al código anterior están documentados en `04-database-model.md`.

## URL base local

El backend usa `server.servlet.context-path` con valor `/api`.

```txt
http://localhost:8080/api
```

Swagger UI:

```txt
http://localhost:8080/api/swagger-ui.html
```

## Estado actual

La estructura actual conserva únicamente código funcional:

- `auth`, incluido `auth/dto`.
- `users`.
- `security`.
- `common/exception`.
- `config`.
- `KodikalabApplication`.

Se retiraron 55 archivos de los paquetes raíz `controller`, `dto`, `entity`, `repository` y `service`; ver `12-source-cleanup.md`.

Registro y login en `auth`/`users` ya persisten en `usuario` según el ERD oficial, con ID entero, nombre completo y enums de rol/estado en español mediante `@Enumerated(EnumType.STRING)`, sin conversores. El login prepara una sesión HTTP en `security`.

La adaptación de cuentas tiene pruebas Java/HTTP/PostgreSQL y fixtures actualizadas; no es una migración de datos ni una alineación global del esquema. Los demás módulos permanecen como diseño pendiente, sin endpoints ficticios ni entidades legacy en runtime. La seguridad continúa temporalmente abierta con `permitAll()`, sin JWT.

## Dirección de evolución

Las nuevas funcionalidades deben tender a organizarse por módulo de negocio:

- `auth`
- `users`
- `profiles`
- `teams`
- `problems`
- `analytics`
- `ai`
- `config`
- `security`
- `common`
