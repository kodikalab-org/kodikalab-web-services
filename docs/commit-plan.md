# Commits de entrega — auth, ERD y limpieza

Rama: `feature/login`. El usuario autorizó completar los commits locales; **no se autoriza ni se realiza push automáticamente**. Abrir el PR hacia `develop` después de revisar y subir la rama.

## Commit de código ya existente

```text
973e6d1 feat(auth): alinear cuentas al ERD e implementar login con sesión
```

Este commit ya contiene el código funcional de `auth`, `users`, `security`, `common/exception/UnauthorizedException.java`, `config/SecurityConfig.java`, `application.yaml` y **las 55 eliminaciones** de scaffolding raíz. No duplicarlo, amendearlo ni volver a ejecutar los comandos anteriores de eliminación.

Inventario y justificación de lo retirado: [sdd/12-source-cleanup.md](sdd/12-source-cleanup.md). Se conserva `auth/dto`.

## Commits locales completados

- `cba4420`: pruebas Java de registro/login.
- `4e87f6e`: límites de runtime e informe de limpieza.
- `b1d0b1d`: Postman y fixtures.
- El commit final de SDD/documentación incluye esta guía; consultar su hash con `git log -1 --oneline` tras completarlo.

No se reescribió `973e6d1` ni se ejecutó push.

## Comandos de una sola línea

Son comandos de referencia para **Git Bash**, desde la raíz del repositorio, una línea por commit. `&&` detiene la cadena si un paso falla. Revisar previamente `git status` y `git diff`; no repetir un grupo si ya está commiteado. No usar `git add .` ni `git add -A` global.

### 1. Pruebas Java de registro/login

```text
test(auth): cubrir registro y login con enums en español
```

Archivos:

```text
src/test/java/com/kodika/kodikalab/auth/AuthControllerTests.java
src/test/java/com/kodika/kodikalab/auth/AuthServiceTests.java
src/test/java/com/kodika/kodikalab/auth/RegistrationIntegrationTests.java
src/test/java/com/kodika/kodikalab/auth/LoginControllerTests.java
src/test/java/com/kodika/kodikalab/auth/LoginIntegrationTests.java
src/test/java/com/kodika/kodikalab/auth/LoginServiceTests.java
src/test/java/com/kodika/kodikalab/users/UserServiceTests.java
src/test/java/com/kodika/kodikalab/users/ErdEnumsTests.java
src/test/java/com/kodika/kodikalab/security/LoginSessionServiceTests.java
```

```bash
git add -- src/test/java/com/kodika/kodikalab/auth/ src/test/java/com/kodika/kodikalab/users/ src/test/java/com/kodika/kodikalab/security/ && git diff --cached --check && git diff --cached --stat && git commit -m "test(auth): cubrir registro y login con enums en español"
```

### 2. Límites de runtime e informe de limpieza

```text
test(architecture): verificar límites tras retirar scaffolding
```

Archivos:

```text
src/test/java/com/kodika/kodikalab/architecture/RuntimeBoundaryTests.java
docs/sdd/12-source-cleanup.md
```

Verifica una sola entidad/repository de cuenta, solo registro/login como rutas de negocio y `404` en las antiguas rutas ficticias. Las eliminaciones ya pertenecen a `973e6d1`.

```bash
git add -- src/test/java/com/kodika/kodikalab/architecture/RuntimeBoundaryTests.java docs/sdd/12-source-cleanup.md && git diff --cached --check && git diff --cached --stat && git commit -m "test(architecture): verificar límites tras retirar scaffolding"
```

### 3. Postman, fixtures y documentación de pruebas

```text
test(auth): actualizar Postman y fixtures al modelo oficial
```

Archivos:

```text
tests/README.md
tests/US01-register.postman_collection.json
tests/US02-login.md
tests/US02-login.postman_collection.json
tests/fixtures/US02-login-statuses.sql
tests/generate-auth-collections.mjs
```

```bash
git add -- tests/README.md tests/US01-register.postman_collection.json tests/US02-login.md tests/US02-login.postman_collection.json tests/fixtures/US02-login-statuses.sql tests/generate-auth-collections.mjs && git diff --cached --check && git diff --cached --stat && git commit -m "test(auth): actualizar Postman y fixtures al modelo oficial"
```

`tests/local.postman_environment.json` no tiene cambios y no requiere un commit adicional.

### 4. SDD y documentación sin datos personales

```text
docs(sdd): actualizar ERD, contratos y estado del backend
```

Modificados:

```text
AGENT.md
README.md
tarea.md
docs/sdd/README.md
docs/sdd/00-project-context.md
docs/sdd/01-business-domain.md
docs/sdd/02-user-stories.md
docs/sdd/03-api-contracts.md
docs/sdd/04-database-model.md
docs/sdd/05-architecture.md
docs/sdd/06-security-strategy.md
docs/sdd/08-ai-working-context.md
```

Nuevos:

```text
docs/commit-plan.md
docs/sdd/10-us02-login.md
docs/sdd/11-erd-oficial-alignment.md
docs/sdd/assets/oficial.erd
```

Eliminaciones del reemplazo del diseño anterior:

```text
docs/sdd/assets/diagrama_entidad_relacion.erd
docs/sdd/assets/init_schema.sql
docs/sdd/assets/script_inicio.txt
docs/sdd/assets/script_inicio_es.txt
```

`12-source-cleanup.md` pertenece al grupo 2 y no vuelve a commitearse si no tiene diferencias nuevas.

```bash
git add -- AGENT.md README.md tarea.md docs/ && git diff --cached --check && git diff --cached --stat && git commit -m "docs(sdd): actualizar ERD, contratos y estado del backend"
```

## Verificación

- Maven Wrapper (`./mvnw` o `mvnw.cmd`), con Java 21; no se requiere Maven global.
- `./mvnw clean compile`: correcto.
- Suite completa contra PostgreSQL temporal: **106 pruebas, 0 fallos, 0 errores, 0 omitidas**.
- Newman sobre el estado funcional revisado: **48 solicitudes y 261 aserciones, sin fallos**.
- JAR/Swagger: solo registro y login como rutas de negocio.

Para repetir `./mvnw test`, configurar `DB_*`, `REGISTRATION_TEST_DB_*` y `LOGIN_TEST_DB_*` hacia una **base exclusiva de pruebas**; no apuntar la suite completa a una base compartida. Ver [../tests/README.md](../tests/README.md).

## Subida manual después de revisar

Una sola línea, únicamente si se desea subir la rama:

```bash
git status --short && git log -5 --oneline && git push -u origin feature/login
```

El `git status` debe estar limpio antes del push. Si cambió la rama desde la preparación de esta guía, ajustar el destino conscientemente. No ejecutar cambios de rama con trabajo pendiente ni subir a `main`/`develop` directamente.

## Excluidos

- `.env`, secretos, credenciales y datos personales.
- `.idea/`, `target/`, logs, bases/clústeres temporales y backups de scaffolding.
- `RoleConverter`/`UserStatusConverter`: se retiraron antes de versionarse y no requieren registrar una eliminación Git.
