# US-02 — Login en el monolito modular

## Estado frente al ERD oficial

La persistencia está definida en `assets/oficial.erd`. La implementación y sus pruebas usan `usuario`, dos roles y los estados oficiales en español, persistidos directamente con `@Enumerated(EnumType.STRING)`. La adaptación de `auth`/`users` se detalla en `11-erd-oficial-alignment.md`.

No se modifican las garantías existentes de BCrypt, error genérico ni `/api` por el cambio del modelo de datos. La sesión HTTP fue reemplazada por un token JWT sin estado (ver `06-security-strategy.md`).

## Flujo

```text
POST /api/auth/login
  → auth.AuthController
    → auth.AuthService.login
      → users.UserService.findByEmail
        → users.UserRepository
      → PasswordEncoder.matches (BCrypt)
      → security.JwtService.issue (JWT firmado)

Peticiones posteriores: Authorization: Bearer <token>
  → security.JwtAuthenticationFilter (valida el token y carga la cuenta)
  → config.SecurityConfig (reglas por rol)
```

`users` sigue siendo dueño de la persistencia; `auth` no requiere un repositorio propio ni accede al de otro módulo directamente. El token es una preocupación técnica de `security`, no una entidad JPA nueva: no se persiste nada.

## Decisiones

- Reutilizar `AuthResponse`: el registro conserva `message`, `email` y `role`; el login agrega `token`, `tokenType` (`Bearer`) y `expiresIn` (segundos).
- Consultar el correo ignorando mayúsculas, normalizando espacios externos.
- No modificar la contraseña ingresada ni aplicar la política de fortaleza del registro al login.
- Usar el mismo `401 Credenciales inválidas` para usuario ausente, contraseña incorrecta, cuenta `SUSPENDIDO` o credenciales no utilizables. Solo `ACTIVO` puede iniciar sesión; no revelar existencia ni estado.
- Realizar una comparación BCrypt también cuando no hay cuenta, usando un hash ficticio no secreto; no prometer igualdad exacta de tiempos.
- Emitir un JWT con la identidad de la cuenta. La autoridad (`ROLE_*`) se deriva en cada petición del rol almacenado en `usuario`, nunca del contenido del token ni de un valor del cliente. Cada login emite un token nuevo.
- JWT activo y autorización por rol en `SecurityConfig` (`06-security-strategy.md`). Una cuenta que deja de estar `ACTIVO` pierde el acceso con su token vigente (`403`).
- Los archivos legacy de auth y demás scaffolding sin lógica se retiraron tras revisión y autorización. El único controller de autenticación es `auth.AuthController`; ver `12-source-cleanup.md`.
- Consultar la entidad `User` mapeada a `usuario`, con ID entero, correo de hasta 100 caracteres y enums de rol/estado en español, sin conversores.
- Java/HTTP/SQL utilizan `PRACTICANTE`/`COACH` y `ACTIVO`/`SUSPENDIDO`. El registro rechaza `PRACTITIONER`; `ADMIN`, `INACTIVE` y `BLOCKED` tampoco pertenecen al modelo vigente.
- No interpretar el cambio de tabla como una migración automática de cuentas. Registro sigue separado del completado de perfiles `coach`/`practicante`.

## Alcance pendiente

El escenario alternativo de recuperación de acceso se resuelve **sin correo electrónico**, con un código de recuperación derivado del secreto del servidor: no agrega tablas ni columnas. Contrato en `03-api-contracts.md` y decisiones en `06-security-strategy.md` ("Recuperación de acceso sin correo").

Logout, revocación de tokens y limitación de intentos requieren una política de seguridad posterior, antes de producción (`06-security-strategy.md`). CSRF no aplica: la autenticación no usa cookies. El frontend decide su navegación a partir del rol; no hay pantallas ni URLs de frontend definidas en este repositorio.

## Verificación

- Pruebas de servicio y controller para credenciales, roles, estado, normalización y ausencia de datos sensibles.
- Pruebas de token (`JwtServiceTests`, `JwtAuthenticationFilterTests`): emisión, vencimiento, manipulación, otra clave, `alg=none`, otro emisor y rol tomado de la base de datos.
- Integración con PostgreSQL real y HTTP: verificar columnas de `usuario`, valores persistidos españoles, token válido entre solicitudes, ausencia de cookie, cuentas suspendidas, reintento y BCrypt.
- Fixtures y colecciones actualizadas a `usuario` y `SUSPENDIDO`; verificar la existencia de la cuenta suspendida antes de interpretar su `401`.
- Regresión completa de registro, incluida su colección Postman.

Contrato: `03-api-contracts.md`. Instrucciones y configuración de pruebas: `tests/US02-login.md`. Colección: `tests/US02-login.postman_collection.json`.
