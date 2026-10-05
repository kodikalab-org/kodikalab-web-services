# Validación de auth/users — ERD oficial

Las cuentas se persisten en `usuario`. Java/HTTP/SQL usa `PRACTICANTE`/`COACH` y estados `ACTIVO`/`SUSPENDIDO`, con persistencia directa `@Enumerated(EnumType.STRING)` sin converters. No hay registro `ADMIN`.

Para login consultar [US02-login.md](US02-login.md).

## Postman — US-01

Importar `US01-register.postman_collection.json` y `local.postman_environment.json`. Seleccionar el entorno local (`baseUrl = http://localhost:8080/api`) y arrancar el backend con Java 21 y una **base exclusiva de pruebas**.

Ejecutar las **26 solicitudes en orden**. El primer caso genera correos únicos; los duplicados dependen de él. La colección comprueba:

- Registro de los dos roles permitidos; rechazo de `PRACTITIONER`, `ADMIN`, rol desconocido, nulo y numérico.
- Correo duplicado y normalización de mayúsculas/espacios.
- Campos obligatorios, formato del correo y JSON mal formado.
- Fortaleza de contraseña y límite BCrypt de 72 bytes ASCII/UTF-8.
- Nombre completo de 150 admitido / 151 rechazado y correo de 100 admitido / 101 rechazado.
- Mensajes públicos y ausencia de contraseñas, hashes o JWT.

**Efecto sobre datos:** crea cuatro cuentas de prueba por corrida; no elimina ni modifica otras. Los correos son ejemplos `test.us01.*@gmail.com` o un subdominio de prueba usado para el límite de 100. No enviar correos reales ni ejecutar en producción.

Regeneración reproducible de ambas colecciones (Node.js, sin dependencias):

```bash
node tests/generate-auth-collections.mjs
```

Newman opcional:

```bash
npx --yes newman run tests/US01-register.postman_collection.json \
  -e tests/local.postman_environment.json
```

Para otro puerto: `--env-var baseUrl=http://localhost:PUERTO/api`.

## Request de referencia

`POST /api/auth/register`:

```json
{
  "firstName": "Usuario",
  "lastName": "Prueba",
  "email": "test@gmail.com",
  "password": "Password123",
  "role": "PRACTICANTE"
}
```

Respuesta `201 Created`:

```json
{
  "message": "Registro exitoso",
  "email": "test@gmail.com",
  "role": "PRACTICANTE"
}
```

Errores: `400` para entradas inválidas; `409` para correo registrado. El cuerpo contiene `message` y `errors`, sin valores rechazados ni detalles SQL. Registro no inicia sesión.

## Comprobar persistencia

Consulta de solo lectura en la base de pruebas:

```sql
SELECT id, nombre_completo, correo, rol, estado_cuenta, fecha_registro,
       password_hash LIKE '$2%' AS bcrypt_hash
FROM usuario
WHERE correo LIKE 'test.us01.%'
ORDER BY id DESC;
```

Cuatro filas nuevas por corrida, `ACTIVO`, rol `PRACTICANTE`/`COACH` y `bcrypt_hash = true`. No mostrar hashes completos ni contraseñas en evidencias públicas. La integración Java también comprueba `PasswordEncoder.matches`.

## Pruebas Java sin PostgreSQL

```bash
./mvnw clean compile
./mvnw -Dtest=AuthServiceTests,AuthControllerTests,UserServiceTests,ErdEnumsTests,LoginServiceTests,LoginControllerTests,LoginSessionServiceTests test
```

## Integración HTTP + PostgreSQL

`RegistrationIntegrationTests` es opt-in. Proporcionar una **base exclusiva de pruebas**, nunca credenciales de desarrollo:

```bash
export REGISTRATION_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/kodikalab_erd_test'
export REGISTRATION_TEST_DB_USER=postgres
# Configurar REGISTRATION_TEST_DB_PASSWORD si se requiere.
./mvnw -Dtest=RegistrationIntegrationTests test
```

La URL es un ejemplo: requiere un servidor activo. La suite crea un schema aleatorio `registration_test_<uuid>`, limita DDL/search path a él, usa HTTP en puerto aleatorio y elimina el schema al terminar. Sin la variable JDBC se omite. Una interrupción puede requerir limpieza manual del schema.

Verifica esquema físico, límites exactos, defaults, BCrypt, rechazo de `ADMIN`, duplicados/concurrencia y registro único de entidad/rutas de autenticación.

Para `./mvnw test` completo, configurar además `LOGIN_TEST_DB_*` y `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` contra la base exclusiva: `KodikalabApplicationTests` y `RuntimeBoundaryTests` usan `DB_*` con `ddl-auto=update`. No modificar `.env` para estas pruebas.

## Alcance y datos existentes

- Sin JWT ni cambios a `/api`; los endpoints de desarrollo siguen públicos.
- No se inventan dominios institucionales autorizados ni perfiles incompletos.
- Scaffolding legacy sin lógica retirado; se conserva `auth/dto` y toda la funcionalidad de registro/login.
- `RuntimeBoundaryTests` verifica una sola entidad/repository de cuenta, solo rutas de negocio implementadas y `404` para las antiguas rutas ficticias. Actualizar esos límites al implementar un nuevo módulo real.
- Cambiar el mapeo JPA no migra cuentas ni otros módulos. Ver `docs/sdd/11-erd-oficial-alignment.md`.
