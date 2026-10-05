# US-02 — Validación de login

## Alcance implementado

`POST /api/auth/login` consulta `usuario`, valida BCrypt y permite solo `ACTIVO`; `SUSPENDIDO` deniega el acceso. Java y SQL usan los mismos enums en español con `@Enumerated(EnumType.STRING)` sin converters. Devuelve el rol almacenado (`PRACTICANTE`/`COACH`) y crea una sesión HTTP, sin JWT ni cambios a los endpoints públicos.

El frontend decide la navegación según `role`; el backend no inventa pantallas. Recuperación de acceso, logout, revocación, CSRF y limitación de intentos quedan pendientes de contrato/política antes de producción.

## Postman

1. Arrancar PostgreSQL y backend con Java 21 en una **base exclusiva de pruebas**.
2. Ejecutar allí `tests/fixtures/US02-login-statuses.sql` y comprobar la fila `test.us02.suspended@gmail.com`, con `SUSPENDIDO` y `PRACTICANTE`.
3. Importar `US02-login.postman_collection.json` y `local.postman_environment.json`.
4. Elegir el entorno local (`baseUrl = http://localhost:8080/api`).
5. Mantener habilitado el cookie jar y ejecutar las **22 solicitudes en orden**.

Crea dos cuentas activas de prueba, prueba ambos roles y rechaza el registro `ADMIN`. Comprueba error genérico, reintento, normalización, datos inválidos, correo superior a 100, límite BCrypt, rol de login ignorado, cuenta suspendida y renovación de ID. No borra ni sobrescribe cuentas existentes.

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
  "role": "PRACTICANTE"
}
```

Cookie `JSESSIONID` en `Set-Cookie`, no en JSON. Cuenta ausente/suspendida, contraseña incorrecta o superior a 72 bytes UTF-8: `401`, `message: "Credenciales inválidas"`, `errors: {}`. Correo ausente, mal formado, superior a 100 o contraseña ausente/vacía: `400`.

Se normaliza el correo, no la contraseña. No se exige de nuevo la política de fortaleza del registro ni se acepta un rol suministrado por el cliente como autoridad.

## Sesiones

- Identidad y `ROLE_PRACTICANTE`/`ROLE_COACH`; no contraseña ni entidad JPA. Reiniciar/invalidar las sesiones antiguas que usen nombres ingleses al desplegar este cambio.
- Renovación de ID en reautenticación; el ID anterior deja de autenticar.
- Cookie `HttpOnly`/`SameSite=Lax`, timeout de 30 minutos de inactividad.
- `SESSION_COOKIE_SECURE=true` en HTTPS; `false` solo en HTTP local.
- Sesiones en memoria: reiniciar invalida sesiones.
- Un intento fallido no crea sesión ni reemplaza la identidad existente.
- `permitAll()` y CSRF temporalmente desactivado: **no es autorización lista para producción**.

## Pruebas Java sin PostgreSQL

```bash
./mvnw clean compile
./mvnw -Dtest=LoginServiceTests,LoginControllerTests,LoginSessionServiceTests,UserServiceTests,ErdEnumsTests test
```

## Integración HTTP + PostgreSQL

`LoginIntegrationTests` es opt-in. Usa una base exclusiva, schema aleatorio `login_test_<uuid>` y puerto HTTP aleatorio. Limita el DDL al schema y lo elimina al terminar; no modifica `public`. Una interrupción puede dejar su schema temporal.

Ejemplo en PowerShell:

```powershell
$env:JAVA_HOME = '<RUTA_JDK_21>'
$env:LOGIN_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/kodikalab_login_test'
$env:LOGIN_TEST_DB_USER = 'postgres'
# Configurar LOGIN_TEST_DB_PASSWORD sin versionar secretos.
.\mvnw.cmd '-Dtest=LoginIntegrationTests' test
```

Sin `LOGIN_TEST_DB_URL` se omite. En IntelliJ definir JDK 21 y variables `LOGIN_TEST_DB_*`; no forzar suites deshabilitadas sin sus variables. Se leen del entorno del proceso, no automáticamente de `.env`. No necesita un backend previamente iniciado.

Para la suite completa también configurar `REGISTRATION_TEST_DB_*` y `DB_*` contra la base exclusiva; las pruebas de contexto (`KodikalabApplicationTests` y `RuntimeBoundaryTests`) usan `DB_*` con `ddl-auto=update`. Las suites de registro/login aíslan sus schemas por separado.

La verificación de esta adaptación ejecutó **106 pruebas sin fallos ni omitidas** sobre PostgreSQL temporal aislado. Ese servidor ya está detenido; ninguna URL temporal configura la base local.

## Bases existentes

El código usa `usuario`, no `users`. Las fixtures anteriores `INACTIVE`/`BLOCKED` ya no son válidas. Renombrar anotaciones o usar `ddl-auto=update` no migra filas/roles/estados ni actualiza todas las restricciones. Se requiere una migración explícita aprobada; no se ejecutaron cambios sobre la base de desarrollo.
