# 06 - Security Strategy

## Fuente y etapa de actualización

Modelo vigente: `docs/sdd/assets/oficial.erd`. `auth`/`users` implementan la cuenta oficial; los enums y tablas anteriores no forman parte del modelo activo de autenticación.

La API usa **Spring Security + JWT sin estado** (requisito de autenticación y autorización del Sprint 1). Reemplaza a la sesión HTTP de US-02 y al `permitAll()` de desarrollo.

## Estado actual

- **Autenticación:** `POST /api/auth/login` valida la contraseña con BCrypt y devuelve un JWT. El cliente lo envía en cada petición como `Authorization: Bearer <token>`. No hay sesión HTTP, `JSESSIONID` ni cookies.
- **Autorización:** las reglas por rol (`COACH` / `PRACTICANTE`) se declaran en un solo lugar, `config.SecurityConfig`. Los servicios conservan las verificaciones que dependen de datos (coach responsable del equipo, membresía activa, cuenta `ACTIVO`).
- **Errores:** `401` y `403` en JSON `{ "message": "...", "errors": {} }`, igual que el resto de la API.
- **Documentación:** Swagger UI (`/api/swagger-ui.html`) y OpenAPI (`/api/v3/api-docs`) son públicos y declaran el esquema `bearerAuth`; el botón **Authorize** acepta el token.

## Flujo

```text
POST /api/auth/login
  → auth.AuthController → auth.AuthService.login
      → users.UserService.findByEmail → PasswordEncoder.matches (BCrypt)
      → security.JwtService.issue  → { token, tokenType: "Bearer", expiresIn }

Peticiones posteriores: Authorization: Bearer <token>
  → security.JwtAuthenticationFilter   valida firma, emisor y vigencia; carga la cuenta
  → config.SecurityConfig              reglas por rol
  → controller → service               verificaciones de pertenencia / propiedad
```

## Token

JWT firmado con HMAC-SHA256 (`jjwt`). Claims: `iss=kodikalab`, `sub` (correo), `uid`, `role`, `pwd` (huella del hash de la contraseña), `iat`, `exp` y `jti` (identificador único). Nunca contiene contraseñas, hashes ni datos de negocio.

- **El rol del token es informativo para el cliente.** En cada petición el filtro vuelve a leer la cuenta y deriva la autoridad (`ROLE_COACH` / `ROLE_PRACTICANTE`) del rol guardado en `usuario.rol`. Un token firmado con un rol distinto del almacenado no eleva privilegios.
- **Cambio de contraseña:** el filtro compara `pwd` con la huella del hash vigente. Al recuperar el acceso (o cambiar la contraseña) los tokens emitidos antes dejan de valer (`401`), lo que cubre la revocación en ese caso.
- **Cuenta suspendida:** si la cuenta deja de estar `ACTIVO` después de iniciar sesión, cualquier petición con su token recibe `403 La cuenta no está habilitada`, sin esperar a que el token venza.
- **Cuenta inexistente:** un token válido de una cuenta eliminada se trata como inválido (`401`).
- **Rechazos:** token mal formado, manipulado, sin firma (`alg=none`), firmado con otra clave, de otro emisor o vencido. Las causas no se distinguen hacia afuera.
- Los endpoints públicos (registro, login, documentación) ignoran cualquier token que el cliente adjunte.

## Reglas de acceso

| Acceso | Endpoints |
| --- | --- |
| Público | `POST /auth/register`, `POST /auth/login`, `POST /auth/recovery`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` |
| Solo `COACH` | `POST /teams`, `GET /teams/{id}/memberships`, `PATCH /teams/{id}/memberships/{memberId}`, `POST /problems`, `POST /problems/assign`, `POST /competitions`, `POST`/`PUT`/`GET /competitions/{competitionId}/official-result`, `GET /competitions/teams/{teamId}/official-results`, `GET /analytics/teams/{teamId}/weaknesses` |
| Solo `PRACTICANTE` | `POST /teams/{id}/join`, `POST /competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions`, `GET /analytics/teams/{teamId}/progress/me` |
| Cualquier cuenta autenticada | El resto: `POST /auth/recovery-code`, `GET`/`PUT /users/me`, `GET /teams`, `GET /problems`, `GET /problems/assigned` y `/{id}`, `GET /analytics/teams/{teamId}/standings` |

Todo endpoint nuevo queda protegido por defecto (`anyRequest().authenticated()`). Si es exclusivo de un rol, agregar su regla en `SecurityConfig` y una fila en esta tabla.

## Errores

| Código | Causa | `message` |
| --- | --- | --- |
| `401` | Sin token (o esquema distinto de Bearer) | `Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)` |
| `401` | Token inválido, manipulado, vencido o de una cuenta inexistente | `El token es inválido o expiró: inicie sesión nuevamente` |
| `403` | Rol sin permiso | `No tiene permisos para realizar esta acción` |
| `403` | Cuenta que ya no está `ACTIVO` | `La cuenta no está habilitada` |

Las respuestas `401` incluyen `WWW-Authenticate: Bearer` (con `error="invalid_token"` si se envió un token inválido).

## Configuración

| Variable | Descripción |
| --- | --- |
| `JWT_SECRET` | **Obligatoria**, sin valor por defecto. Mínimo 32 caracteres (`openssl rand -hex 32`). Sin ella, o con el valor de ejemplo `CHANGE_ME…`, la aplicación no arranca. |
| `JWT_EXPIRATION` | Vigencia en milisegundos; por defecto `86400000` (24 h). |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend permitidos, separados por comas. Sin credenciales ni cookies. |

CSRF está desactivado a propósito: sin cookies de sesión no hay petición de otro sitio que pueda reutilizar la autenticación del navegador.

## Roles del modelo oficial

- Persistencia: `COACH` y `PRACTICANTE`; no hay `ADMIN` en `oficial.erd`.
- Java/HTTP implementado: `COACH` y `PRACTICANTE`, iguales a SQL y sin conversores; las autoridades son `ROLE_COACH`/`ROLE_PRACTICANTE`.
- Las autoridades se derivan del rol validado y almacenado, no de un rol suministrado al login ni del contenido del token.
- Las cuentas `ADMIN` existentes requieren tratamiento aprobado; no reclasificarlas ni mantener privilegios silenciosamente.
- El ERD no define una verificación adicional del coach ni datos suficientes para crear su perfil durante el registro base; no inventar esa política en la adaptación de auth.

## Pruebas

- `JwtServiceTests`: emisión, vencimiento, manipulación, otra clave, otro emisor, `alg=none`, claims faltantes, secretos inválidos y que el secreto no aparezca en `toString`.
- `JwtAuthenticationFilterTests`: autenticación, rol tomado de la base de datos, token inválido, cuenta inexistente o suspendida y endpoints públicos.
- `SecurityIntegrationTests` (PostgreSQL en schema aislado, `SECURITY_TEST_DB_URL`): `401` en todos los endpoints protegidos sin token, tokens inválidos, `403` por rol en cada endpoint exclusivo, rol del token no confiable, cuenta suspendida o eliminada, ausencia de sesión/cookie, OpenAPI, Swagger y CORS.
- Los tests de integración de cada módulo inician sesión por HTTP real y usan el token.
- Postman: `tests/SEC-jwt-roles.postman_collection.json`. Ver `tests/README.md`.

## Recuperación de acceso sin correo

Escenario alternativo de US-02. El sistema no envía correos (no hay cliente de correo en el diagrama de componentes): el titular verifica la titularidad con un **código de recuperación** y define una contraseña nueva.

- **Código:** `ABCD-EFGH-IJKL-MNOP-QRST-UVWX` (120 bits). Se entrega en la respuesta del registro y de cada recuperación. No se guarda en ninguna tabla (el ERD no cambia): es `HMAC-SHA256(clave derivada de JWT_SECRET, correo + hash vigente de la contraseña)`.
- **Un solo uso:** al cambiar la contraseña cambia el hash y, con él, el código. Una actualización con comparación del hash anterior (`UPDATE ... WHERE password_hash = :actual`) hace que, con dos solicitudes simultáneas, solo una gane.
- **Cuentas anteriores:** `POST /auth/recovery-code` (token + contraseña actual) devuelve el código vigente de cualquier cuenta.
- **Sin filtrar información:** código incorrecto, correo inexistente, cuenta suspendida y código ya usado responden el mismo `401 Datos de recuperación inválidos`.
- **Efectos:** BCrypt para la contraseña nueva, tokens anteriores inválidos y nuevo intento de inicio de sesión habilitado.
- **Límites:** rotar `JWT_SECRET` cambia los códigos; falta limitación de intentos.

Contrato: `03-api-contracts.md`. Pruebas: `RecoveryCodeServiceTests`, `AccountRecoveryServiceTests`, `RecoveryControllerTests` y `RecoveryIntegrationTests`; Postman: bloque final de `US02-login`.

## Pendientes antes de producción

- **Logout y revocación:** el token vale hasta su vencimiento o hasta un cambio de contraseña; cerrar sesión consiste en descartarlo en el cliente. Una lista de revocación o tokens de corta vida con refresco requieren una política acordada.
- **Limitación de intentos** de login, registro y recuperación.
- **HTTPS** y cabeceras de seguridad en el despliegue; el secreto real solo en variables de entorno del servidor.
