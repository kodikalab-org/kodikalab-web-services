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

## US-03 — Perfil de practicante y coach

`GET/PUT /api/users/me` gestiona `practicante` o `coach` según el rol de la sesión. Contrato: `docs/sdd/03-api-contracts.md`.

### Postman

Con el backend arrancado y el cookie jar habilitado, ejecutar en orden:

```bash
npx --yes newman run tests/US03-profile.postman_collection.json -e tests/local.postman_environment.json
npx --yes newman run tests/US03-coach.postman_collection.json -e tests/local.postman_environment.json
```

- `US03-profile` (16 solicitudes): crear/consultar/actualizar practicante, handle inválido conserva datos, LeetCode ignorado, COACH con body de practicante rechazado, Codeforces inexistente no se persiste, código de estudiante duplicado `409`. El caso 04 consulta la **API real de Codeforces** (`tourist`): sin internet el mensaje cambia a la advertencia de Codeforces no confirmado y las aserciones del handle fallan.
- `US03-coach` (10 solicitudes): crear/consultar/actualizar coach, error conserva datos previos, opcionales en blanco → `null`, body de practicante rechazado.

**Efecto sobre datos:** `US03-profile` crea por corrida dos practicantes (con sus filas en `practicante`) y un coach sin perfil; `US03-coach` crea un coach con su fila en `coach`. Todos con correos `test.us03.*@gmail.com`; no se eliminan ni modifican otros datos. Consulta de solo lectura:

```sql
SELECT u.correo, u.rol, p.codigo_estudiante, p.codeforces_handle, p.codeforces_rating
FROM usuario u JOIN practicante p ON p.usuario_id = u.id
WHERE u.correo LIKE 'test.us03.%' ORDER BY u.id DESC;

SELECT u.correo, c.especialidad_principal, c.organizacion_club, c.anios_experiencia
FROM usuario u JOIN coach c ON c.usuario_id = u.id
WHERE u.correo LIKE 'test.us03.coach.%' ORDER BY u.id DESC;
```

### Pruebas Java

Sin PostgreSQL ni internet (Codeforces se simula con un servidor HTTP local):

```bash
./mvnw -Dtest='Profile*Tests,CurrentUserResolverTests,*ProfileServiceTests,CodeforcesApiClientTests,ConstraintViolationsTests' test
```

`ProfileIntegrationTests` es opt-in, con el mismo patrón que login/registro: schema aleatorio `profile_test_<uuid>` en una **base exclusiva de pruebas** (por ejemplo `kodikalab_test`), HTTP en puerto aleatorio, cookie de sesión real y stub de Codeforces. Elimina el schema al terminar.

```bash
export PROFILE_TEST_DB_URL='jdbc:postgresql://localhost:5432/kodikalab_test'
export PROFILE_TEST_DB_USER=postgres
# Configurar PROFILE_TEST_DB_PASSWORD si se requiere.
./mvnw -Dtest=ProfileIntegrationTests test
```

Verifica los flujos completos de ambos roles, que un error no reemplace datos previos, el escenario alternativo de Codeforces, `409` por código duplicado (también con solicitudes concurrentes), que el body no elija usuario ni rol, que cada rol escriba solo su tabla, `401` sin sesión y el esquema físico de `practicante`/`coach` contra el ERD.

## Esquema del ERD

`ErdSchemaIntegrationTests` (opt-in) genera el esquema completo desde las entidades JPA en un schema aleatorio
`erd_test_<uuid>` y lo compara con las tablas visibles de `oficial.erd`: columnas, tipos, longitudes, nulos,
defaults, PKs, FKs y unicidades. Elimina el schema al terminar.

```bash
export ERD_TEST_DB_URL='jdbc:postgresql://localhost:5432/kodikalab_test'
export ERD_TEST_DB_USER=postgres
# Configurar ERD_TEST_DB_PASSWORD si se requiere.
./mvnw -Dtest=ErdSchemaIntegrationTests test
```

## US-11 — Ranking interno

Endpoint: `GET /api/analytics/teams/{teamId}/standings`, con sesión del coach responsable o de un integrante activo. Contrato y resultados de verificación: [US-11](../docs/sdd/13-us11-ranking-interno.md).

Pruebas sin PostgreSQL (cálculo, autorización, adaptación de datos y HTTP):

```powershell
.\mvnw.cmd '-Dtest=RankingServiceTests,TeamRankingServiceTests,AnalyticsControllerTests,StoredTeamRankingServiceTests,RankingSnapshotServiceTests' test
```

`RankingIntegrationTests` requiere una base exclusiva de pruebas y permisos para crear/eliminar schemas:

```powershell
$env:RANKING_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/kodikalab_test'
$env:RANKING_TEST_DB_USER = 'postgres'
# Configurar RANKING_TEST_DB_PASSWORD si se requiere.
.\mvnw.cmd '-Dtest=RankingIntegrationTests' test
```

La suite crea `ranking_test_<uuid>`, carga fixtures únicamente en ese schema y utiliza login HTTP real antes de consultar el ranking. Verifica consolidación entre competencias, empates, ceros, ausencia de actividad, aislamiento por equipo, membresías inactivas, referencias cruzadas y permisos. También comprueba la recuperación del resultado guardado, su reemplazo tras corregir datos, una sola fila por equipo, rechazo de escrituras anteriores y que un miembro revocado no vea el resultado guardado. Elimina su schema al terminar; una interrupción puede requerir limpieza manual. Sin `RANKING_TEST_DB_URL`, se omite. No ha sido ejecutada contra PostgreSQL en esta entrega.

## US-12 — Temas con menor resolución

Endpoint: `GET /api/analytics/teams/{teamId}/weaknesses`, solo para el coach responsable con cuenta activa y sesión HTTP. Contrato, decisiones aprobadas y resultados: [US-12](../docs/sdd/14-us12-temas-menor-resolucion.md).

Pruebas sin PostgreSQL:

```powershell
.\mvnw.cmd '-Dtest=TopicCoverageCalculatorTests,TeamTopicReportServiceTests,TopicReportControllerTests' test
```

Integración con una base exclusiva de pruebas y permisos para crear/eliminar schemas:

```powershell
$env:TOPIC_REPORT_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/kodikalab_test'
$env:TOPIC_REPORT_TEST_DB_USER = 'postgres'
# Configurar TOPIC_REPORT_TEST_DB_PASSWORD si se requiere.
.\mvnw.cmd '-Dtest=TopicReportIntegrationTests' test
```

La suite crea `topic_report_test_<uuid>`, utiliza login HTTP real, carga fixtures solo en ese schema y lo elimina al terminar. Comprueba consultas, temas sin aceptaciones, deduplicación entre competencias, problemas con múltiples temas, empates, intentos pendientes, aislamiento por equipo, autorización y reintento tras corregir datos. Sin `TOPIC_REPORT_TEST_DB_URL` se omite. Una interrupción puede requerir limpieza manual del schema. No ha sido ejecutada contra PostgreSQL en esta entrega.

## Alcance y datos existentes

- Sin JWT ni cambios a `/api`; perfiles y ranking comprueban la sesión en sus servicios sin modificar el `permitAll()` global.
- No se inventan dominios institucionales autorizados ni perfiles incompletos.
- Scaffolding legacy sin lógica retirado; se conserva `auth/dto` y toda la funcionalidad de registro/login.
- `RuntimeBoundaryTests` verifica una sola entidad/repository de cuenta, solo rutas de negocio implementadas y `404` para las antiguas rutas ficticias. Actualizar esos límites al implementar un nuevo módulo real.
- Cambiar el mapeo JPA no migra cuentas ni otros módulos. Ver `docs/sdd/11-erd-oficial-alignment.md`.
