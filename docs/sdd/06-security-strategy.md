# 06 - Security Strategy

## Fuente y etapa de actualización

Modelo vigente: `docs/sdd/assets/oficial.erd`. `auth`/`users` implementan la cuenta oficial; los enums y tablas anteriores no forman parte del modelo activo de autenticación.

## Estado actual

Durante el desarrollo inicial, Spring Security está configurado para permitir todos los endpoints:

```java
.anyRequest().permitAll()
```

Esto permite que los integrantes prueben sus endpoints sin bloquearse por autenticación JWT.

## Objetivo posterior

Cuando los endpoints base estén implementados, se debe reemplazar la configuración permisiva por seguridad basada en JWT.

## Endpoints públicos esperados

Cuando se active JWT, normalmente quedarán públicos:

```txt
POST /auth/register
POST /auth/login
GET  /swagger-ui/**
GET  /v3/api-docs/**
```

## Endpoints protegidos

Todos los demás endpoints deberían requerir token:

```txt
Authorization: Bearer <token>
```

## Implementado en US-01

- Bean `PasswordEncoder` con BCrypt en `SecurityConfig`.
- Registro con contraseña hasheada; nunca se devuelve contraseña/hash en la respuesta.
- Se mantiene `permitAll()` sin JWT, según el alcance de desarrollo actual.

## Implementado en US-02

- Login usa `PasswordEncoder.matches`, mensaje genérico y validación de estado sobre `usuario`.
- Se permite SQL `ACTIVO` y se deniega `SUSPENDIDO` con el mismo `401 Credenciales inválidas` que un usuario ausente o una contraseña incorrecta. `UserStatus` usa los mismos valores `ACTIVO`/`SUSPENDIDO`, persistidos directamente con `@Enumerated(EnumType.STRING)`.
- Sesión HTTP persistida mediante `HttpSessionSecurityContextRepository`; almacena identidad y autoridad del rol, nunca credenciales ni entidades de negocio.
- Renovación del ID al autenticar una sesión existente, cookie `HttpOnly`/`SameSite=Lax` y expiración por inactividad (30 minutos).
- `SESSION_COOKIE_SECURE=true` en HTTPS; en HTTP local el valor por defecto es `false`.
- Se conserva `permitAll()` y CSRF temporalmente desactivado; no se activa JWT ni se migra globalmente la configuración.

Este flujo de login **no deja la autorización lista para producción**. Antes de proteger operaciones mediante cookies, activar CSRF y definir logout, revocación, permisos y limitación de intentos. El escenario de recuperación de acceso requiere un contrato posterior y no se considera implementado.

## Roles del modelo oficial

- Persistencia: `COACH` y `PRACTICANTE`; no hay `ADMIN` en `oficial.erd`.
- Java/HTTP implementado: `COACH` y `PRACTICANTE`, iguales a SQL y sin conversores; las autoridades son `ROLE_COACH`/`ROLE_PRACTICANTE`.
- Las autoridades de sesión deben derivarse del rol validado y almacenado, no de un rol suministrado al login.
- Las cuentas `ADMIN` existentes requieren tratamiento aprobado; no reclasificarlas ni mantener privilegios silenciosamente. Invalidar sesiones previas al desplegar el cambio de nombres de roles/autoridades.
- El ERD no define una verificación adicional del coach ni datos suficientes para crear su perfil durante el registro base; no inventar esa política en la adaptación de auth.

## Pendientes de seguridad

- Implementar generación de JWT en login.
- Implementar validación de JWT por request.
- Implementar filtro JWT.
- Definir roles y permisos.
- Proteger endpoints por rol si la historia lo requiere.
