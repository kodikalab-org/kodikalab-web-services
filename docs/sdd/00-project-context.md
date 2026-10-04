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

La estructura inicial por capas puede existir durante el scaffolding, pero la dirección oficial del proyecto es evolucionar hacia paquetes por dominio.

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

El proyecto contiene scaffolding inicial por capas:

- `controller`
- `service`
- `service.impl`
- `repository`
- `entity`
- `dto`
- `config`

La seguridad está abierta temporalmente para desarrollo con `permitAll()`.

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
