# Validación de US-01 — Registro modular

## Postman

Importar estos archivos:

- `US01-register.postman_collection.json`
- `local.postman_environment.json`

Seleccionar el entorno **KodikaLab - Local US01**. La variable `baseUrl` vale:

```text
http://localhost:8080/api
```

Arrancar el backend con Java 21 y una base de desarrollo/pruebas configurada. Esta colección no configura PostgreSQL ni necesita JWT.

Ejecutar la colección completa **en orden** mediante Collection Runner. El caso `01` genera correos únicos por corrida. Los casos `02` y `03` dependen de la cuenta creada en `01`; para repetirlos individualmente, ejecutar primero `01`.

La colección contiene 20 solicitudes con aserciones de código HTTP, mensajes, roles, normalización y ausencia de contraseñas/tokens en las respuestas:

- Registro correcto de `PRACTITIONER`, `COACH` y `ADMIN`, conforme al enum solicitado.
- Correo duplicado, incluyendo mayúsculas y espacios externos.
- Contraseña débil, sin mayúscula o sin número.
- Correo mal formado, campos ausentes, nombre en blanco, rol inválido/nulo y JSON mal formado.
- Contraseña que excede el límite técnico de BCrypt (72 bytes UTF-8), con ASCII y caracteres multibyte.

**Efecto sobre datos:** cada corrida completa crea tres usuarios de prueba. No elimina usuarios ni modifica otras entidades. Los correos empiezan por `us01.` y terminan en `@upc.edu.pe`. Usar una base de pruebas; no ejecutar en producción.

### Newman (opcional)

Desde la raíz del repositorio:

```bash
npx --yes newman run tests/US01-register.postman_collection.json \
  -e tests/local.postman_environment.json
```

Para otro puerto, usar `--env-var baseUrl=http://localhost:PUERTO/api`.

### Request de referencia

```http
POST http://localhost:8080/api/auth/register
Content-Type: application/json
```

```json
{
  "firstName": "Matias",
  "lastName": "Del Castillo",
  "email": "matias@upc.edu.pe",
  "password": "Password123",
  "role": "PRACTITIONER"
}
```

Respuesta: `201 Created`.

```json
{
  "message": "Registro exitoso",
  "email": "matias@upc.edu.pe",
  "role": "PRACTITIONER"
}
```

Errores: `400 Bad Request` para entradas inválidas; `409 Conflict` para correo registrado. El cuerpo contiene `message` y `errors` (mapa por campo, vacío en conflictos). Nunca devuelve valores rechazados de contraseña.

No hay GET ni login implementado en esta historia. La ruta legacy de login conserva su scaffolding anterior.

## Comprobar persistencia manualmente

Postman valida la API; no puede confirmar directamente el hash almacenado. En la base usada para las pruebas ejecutar esta consulta de solo lectura:

```sql
SELECT id, first_name, last_name, email, role, status, created_at,
       password_hash LIKE '$2%' AS bcrypt_hash
FROM users
WHERE email LIKE 'us01.%@upc.edu.pe'
ORDER BY id DESC;
```

Debe haber tres filas nuevas por corrida, con `ACTIVE` y `bcrypt_hash = true`. No mostrar hashes completos ni contraseñas en evidencias públicas. La suite de integración también verifica el hash mediante `PasswordEncoder.matches`.

## Pruebas Java sin PostgreSQL

```bash
./mvnw clean compile
./mvnw -Dtest=AuthServiceTests,AuthControllerTests,UserServiceTests test
```

## Integración HTTP + PostgreSQL

`RegistrationIntegrationTests` requiere habilitación explícita. Usar una **base exclusiva de pruebas**, con credenciales proporcionadas por el entorno:

```bash
export REGISTRATION_TEST_DB_URL='jdbc:postgresql://127.0.0.1:55435/kodikalab_register_test'
export REGISTRATION_TEST_DB_USER=postgres
# Proporcionar REGISTRATION_TEST_DB_PASSWORD si es necesaria.
./mvnw -Dtest=RegistrationIntegrationTests test
```

La prueba crea un schema aleatorio `registration_test_<uuid>`, limita el DDL y el search path a ese schema, ejecuta HTTP en un puerto aleatorio y elimina el schema al terminar. Sin la variable JDBC, se omite explícitamente. Una interrupción forzada puede dejar un schema temporal que requiera limpieza manual.

Verifica registro, hash, estado/rol, duplicados, rechazo sin persistencia, solicitudes concurrentes y coexistencia del scaffolding sin entidades/rutas duplicadas.

Para ejecutar `./mvnw test` completo, también apuntar `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD` a la base exclusiva de pruebas: `KodikalabApplicationTests` usa esa configuración y `ddl-auto=update`. No apuntar la suite completa a una base compartida ni modificar `.env` para las pruebas automatizadas.

## Decisiones de alcance

- No se activa JWT ni se cambia `/api`.
- Se aceptan los tres roles del enum porque así lo define `tarea.md`; la autorización para roles privilegiados debe definirse antes de producción.
- Se valida formato de correo. La tarea no define una lista de dominios institucionales autorizados, por lo que no se inventa una restricción exclusiva a `upc.edu.pe`.
- Se conservan todos los archivos legacy. Solo se desactivan su mapping de registro y su entidad/repositorio de usuario duplicados; login permanece sin implementar.
