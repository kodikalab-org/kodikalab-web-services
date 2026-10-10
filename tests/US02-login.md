# US-02 — Validación de login

## Alcance implementado

`POST /api/auth/login` consulta `usuario`, valida BCrypt y permite solo `ACTIVO`; `SUSPENDIDO` deniega el acceso. Java y SQL usan los mismos enums en español con `@Enumerated(EnumType.STRING)` sin converters. Devuelve el rol almacenado (`PRACTICANTE`/`COACH`) y un token JWT (`token`, `tokenType`, `expiresIn`); no crea sesión ni cookie.

El frontend decide la navegación según `role`; el backend no inventa pantallas. Logout, revocación y limitación de intentos quedan pendientes de política antes de producción. La recuperación de acceso se hace sin correo, con el código de recuperación de la cuenta (`POST /api/auth/recovery`).

## Postman

1. Arrancar PostgreSQL y backend con Java 21 en una **base exclusiva de pruebas**.
2. Ejecutar allí `tests/fixtures/US02-login-statuses.sql` y comprobar la fila `test.us02.suspended@gmail.com`, con `SUSPENDIDO` y `PRACTICANTE`.
3. Importar `US02-login.postman_collection.json` y `local.postman_environment.json`.
4. Elegir el entorno local (`baseUrl = http://localhost:8080/api`).
5. Ejecutar las **35 solicitudes en orden**; cada login guarda el token en variables de colección.

Crea dos cuentas activas de prueba, prueba ambos roles y rechaza el registro `ADMIN`. Comprueba error genérico, reintento, normalización, datos inválidos, correo superior a 100, límite BCrypt, rol de login ignorado, cuenta suspendida y token nuevo en cada login. Las últimas 13 solicitudes prueban la recuperación de acceso sin correo (código inválido o de otra cuenta, contraseña débil, recuperación correcta, invalidación del token y de la contraseña anteriores, código de un solo uso y consulta del código vigente). No borra ni sobrescribe cuentas existentes.

**Un `401` aislado no demuestra suspensión:** un correo ausente produce el mismo error. Confirmar la fixture antes de ejecutar; si la fila ya existe, el SQL no cambia su estado ni hash.

```bash
npx --yes newman run tests/US02-login.postman_collection.json \
  -e tests/local.postman_environment.json
```

Para otro puerto: `--env-var baseUrl=http://localhost:PUERTO/api`.

## Request y respuesta

`POST /api/auth/login`:

```json
{
  "email": "test@gmail.com",
  "password": "Password123"
}
```

La cuenta debe existir con hash BCrypt. Una fixture con texto plano en `password_hash` no permite login.

Respuesta `200 OK`:

```json
{
  "message": "Inicio de sesión exitoso",
  "email": "test@gmail.com",
  "role": "PRACTICANTE",
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

El token viaja en el JSON y se envía después como `Authorization: Bearer <token>`; no se crea cookie. Cuenta ausente/suspendida, contraseña incorrecta o superior a 72 bytes UTF-8: `401`, `message: "Credenciales inválidas"`, `errors: {}`. Correo ausente, mal formado, superior a 100 o contraseña ausente/vacía: `400`.

Se normaliza el correo, no la contraseña. No se exige de nuevo la política de fortaleza del registro ni se acepta un rol suministrado por el cliente como autoridad.

## Tokens

- Contienen identidad (`sub`, `uid`) y rol informativo; no contraseña ni entidad JPA. El rol efectivo se lee de `usuario.rol` en cada petición.
- Firmados con HMAC-SHA256 con `JWT_SECRET` (obligatorio, mínimo 32 caracteres); vigencia `JWT_EXPIRATION` (por defecto 24 h).
- Cada login emite un token nuevo; los anteriores siguen válidos hasta su vencimiento (no hay revocación todavía).
- Una cuenta que pasa a `SUSPENDIDO` recibe `403` con su token vigente.
- Detalle y reglas de acceso por rol: `docs/sdd/06-security-strategy.md`. Colección de seguridad: `tests/SEC-jwt-roles.postman_collection.json`.
