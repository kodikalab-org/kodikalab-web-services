# US-02 — Login en el monolito modular

## Estado frente al ERD oficial

La persistencia está definida en `assets/oficial.erd`. La implementación y sus pruebas usan `usuario`, dos roles y los estados oficiales en español, persistidos directamente con `@Enumerated(EnumType.STRING)`. La adaptación de `auth`/`users` se detalla en `11-erd-oficial-alignment.md`.

No se modifican las garantías existentes de BCrypt, error genérico, sesión HTTP ni `/api` por el cambio del modelo de datos.

## Flujo

```text
POST /api/auth/login
  → auth.AuthController
    → auth.AuthService.login
      → users.UserService.findByEmail
        → users.UserRepository
      → PasswordEncoder.matches (BCrypt)
    → security.LoginSessionService
      → HttpSessionSecurityContextRepository
```

`users` sigue siendo dueño de la persistencia; `auth` no requiere un repositorio propio ni accede al de otro módulo directamente. La sesión es una preocupación técnica de `security`, no una entidad JPA nueva.

## Decisiones

- Reutilizar `AuthResponse(message, email, role)` sin cambiar el contrato de registro.
- Consultar el correo ignorando mayúsculas, normalizando espacios externos.
- No modificar la contraseña ingresada ni aplicar la política de fortaleza del registro al login.
- Usar el mismo `401 Credenciales inválidas` para usuario ausente, contraseña incorrecta, cuenta `SUSPENDIDO` o credenciales no utilizables. Solo `ACTIVO` puede iniciar sesión; no revelar existencia ni estado.
- Realizar una comparación BCrypt también cuando no hay cuenta, usando un hash ficticio no secreto; no prometer igualdad exacta de tiempos.
- Crear una sesión HTTP con identidad y autoridad derivada del rol almacenado; renovar el ID al autenticar una sesión existente.
- Mantener JWT desactivado y los demás endpoints abiertos para desarrollo. La autorización global no se considera resuelta por devolver un rol.
- Los archivos legacy de auth y demás scaffolding sin lógica se retiraron tras revisión y autorización. El único controller de autenticación es `auth.AuthController`; ver `12-source-cleanup.md`.
- Consultar la entidad `User` mapeada a `usuario`, con ID entero, correo de hasta 100 caracteres y enums de rol/estado en español, sin conversores.
- Java/HTTP/SQL utilizan `PRACTICANTE`/`COACH` y `ACTIVO`/`SUSPENDIDO`. El registro rechaza `PRACTITIONER`; `ADMIN`, `INACTIVE` y `BLOCKED` tampoco pertenecen al modelo vigente.
- No interpretar el cambio de tabla como una migración automática de cuentas. Registro sigue separado del completado de perfiles `coach`/`practicante`.

## Alcance pendiente

El escenario alternativo de recuperación de acceso de `tarea.md` no tiene contrato técnico definido. Falta acordar endpoints, verificación de titularidad, tokens con expiración/uso único y transporte seguro. No se implementan ni simulan esas operaciones en esta feature.

Logout, revocación, rate limiting, protección CSRF y reglas de acceso a los demás módulos requieren una política de seguridad posterior, antes de producción. El frontend decide su navegación a partir del rol; no hay pantallas ni URLs de frontend definidas en este repositorio.

## Verificación

- Pruebas de servicio y controller para credenciales, roles, estado, normalización y ausencia de datos sensibles.
- Pruebas de sesión para contexto almacenado y renovación de ID.
- Integración con PostgreSQL real y HTTP: verificar columnas de `usuario`, valores persistidos españoles, sesión conservada entre solicitudes, ID anterior invalidado, cuentas suspendidas, reintento y BCrypt.
- Fixtures y colecciones actualizadas a `usuario` y `SUSPENDIDO`; verificar la existencia de la cuenta suspendida antes de interpretar su `401`.
- Regresión completa de registro, incluida su colección Postman.

Contrato: `03-api-contracts.md`. Instrucciones y configuración de pruebas: `tests/US02-login.md`. Colección: `tests/US02-login.postman_collection.json`.
