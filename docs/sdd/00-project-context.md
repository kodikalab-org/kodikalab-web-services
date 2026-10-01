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
