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

Esto implica una sola aplicación Spring Boot y una sola base de datos, pero con el código organizado por módulos de dominio como `auth`, `users`, `profiles`, `teams`, `competitions`, `problems`, `analytics` y `ai`.

El código implementado se organiza por dominio. El scaffolding inicial por capas fue retirado tras revisar sus dependencias y confirmar que carecía de lógica funcional.

## Fuente de datos vigente

El diseño oficial está en `docs/sdd/assets/oficial.erd` y en los diagramas de `Diagrama_C4_Kodikalab.drawio` (ERD, clases y componentes). **Precedencia:** para datos (tablas, columnas, tipos, relaciones) manda **`oficial.erd`**, el ERD oficial. La página "Base de Datos" del drawio es su representación visual y debe coincidir con él. El **diagrama de clases** (página "CODIGO") define los **nombres de las clases** y su comportamiento (métodos). Las diferencias de atributos entre ambos diagramas se registran en `04-database-model.md` para que el equipo corrija el diagrama de clases; no cambian el modelo de datos. El diseño de datos está en `oficial.erd`. Sus tablas físicas usan los nombres allí definidos, por ejemplo `usuario`, `coach`, `practicante` y `grupo_estudio`; las clases, rutas y claves JSON conservan sus nombres ingleses, mientras los valores de enums de cuenta usan el español del ERD.

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

- `auth`, `users`, `security`, `common` y `config`: registro (US-01) y login con token JWT (US-02) sobre `usuario`.
- `profiles`: perfiles `practicante` y `coach` (US-03) en `GET/PUT /api/users/me`, según el rol de la sesión.
- `teams`, `competitions` y `problems`: **plantilla del ERD** con una carpeta por entidad (entidad, enums,
  repositorio y servicio) y un controller por módulo, sin lógica ni endpoints. Ver `05-architecture.md`.
- `assignments` (US-07 y US-08) y `analytics` (US-11 a US-14): implementados sin tablas propias, coordinando por servicios públicos. `ai`: diseño objetivo, sin código.

Todas las tablas del ERD tienen entidad JPA y Hibernate las crea con `ddl-auto: update` en desarrollo. No hay
migración de datos ni del esquema anterior. La API exige token JWT y autoriza por rol con Spring Security (`06-security-strategy.md`).
El scaffolding inicial por capas se retiró (ver `12-source-cleanup.md`).

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
