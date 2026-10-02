# 06 - Security Strategy

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

## Pendientes de seguridad

- Implementar generación de JWT en login.
- Implementar validación de JWT por request.
- Implementar filtro JWT.
- Definir roles y permisos.
- Proteger endpoints por rol si la historia lo requiere.
