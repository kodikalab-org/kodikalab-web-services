# KodikaLab - Backend Web Services

## Onboarding Guide del Equipo Backend

**KodikaLab - Backend Web Services** es el backend del proyecto académico desarrollado para el curso de **Ingeniería de Software (UPC 2026-2)**, en el contexto de la startup **ProgramaJuntos**.

El objetivo de este repositorio es centralizar los servicios web necesarios para gestionar usuarios, perfiles, equipos, problemas de programación, progreso, analítica, rankings y asistencia inteligente para estudiantes que practican programación competitiva.

### Stack base

- **Java 21** - Eclipse Adoptium Temurin.
- **Spring Boot 3**.
- **PostgreSQL 15+**.
- **Maven** mediante Maven Wrapper (`./mvnw`).
- **Spring Security** con JWT.
- **SpringDoc OpenAPI** para documentación Swagger.

---

## 1. Requisitos Previos

Antes de levantar el proyecto localmente, cada integrante debe tener instalado y configurado lo siguiente:

### JDK 21

Instalar **Java 21**, preferentemente la distribución:

```txt
Eclipse Adoptium Temurin 21
```

Verificar instalación:

```bash
java -version
```

Debe mostrar una versión similar a:

```txt
openjdk version "21.x.x"
```

También verificar que `JAVA_HOME` apunte al JDK 21.

### PostgreSQL 15+

Instalar y ejecutar PostgreSQL localmente.

Configuración esperada por defecto:

```txt
Host: localhost
Port: 5432
Database: kodikalab_db
```

### Git

Cada integrante debe configurar Git con su nombre y correo institucional:

```bash
git config --global user.name "Nombre Apellido"
git config --global user.email "correo@upc.edu.pe"
```

Verificar configuración:

```bash
git config --global --list
```

### IntelliJ IDEA

Se recomienda usar:

```txt
IntelliJ IDEA 2024+
```

Puede ser la edición Community o Ultimate.

---

## 2. Guía Paso a Paso de Instalación y Configuración

### Paso 1: Clonar el repositorio y cambiar a `develop`

Clonar el repositorio:

```bash
git clone <URL_DEL_REPOSITORIO>
cd kodikalab
```

Luego cambiar obligatoriamente a la rama de integración:

```bash
git checkout develop
```

Actualizar la rama local:

```bash
git pull origin develop
```

> Todo desarrollo debe partir desde `develop`, no desde `main`.

---

### Paso 2: Crear la base de datos local en PostgreSQL

Ingresar a PostgreSQL y crear la base de datos:

```sql
CREATE DATABASE kodikalab_db;
```

Si usan pgAdmin, pueden crearla visualmente con el nombre:

```txt
kodikalab_db
```

---

### Paso 3: Configurar el archivo de secretos `.env`

El repositorio incluye un archivo versionado llamado:

```txt
.env.example
```

Cada desarrollador debe duplicarlo y renombrar la copia como:

```txt
.env
```

En terminal:

```bash
cp .env.example .env
```

En Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

El archivo `.env` contiene secretos y configuración local. Por seguridad:

> `.env` está en `.gitignore` y **NUNCA debe commitearse**.

Campos principales del `.env`:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=kodikalab_db
DB_USER=postgres
DB_PASSWORD=postgres
JWT_SECRET=CHANGE_ME_SUPER_SECRET_KEY_FOR_LOCAL_DEVELOPMENT
JWT_EXPIRATION=86400000
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
```

Descripción:

| Variable | Descripción |
|---|---|
| `DB_HOST` | Host donde corre PostgreSQL. Normalmente `localhost`. |
| `DB_PORT` | Puerto local de PostgreSQL. Normalmente `5432`. |
| `DB_NAME` | Nombre de la base de datos local. Debe ser `kodikalab_db`. |
| `DB_USER` | Usuario local de PostgreSQL. |
| `DB_PASSWORD` | Contraseña local de PostgreSQL. |
| `JWT_SECRET` | Clave secreta usada para firmar tokens JWT. En desarrollo puede ser una clave dummy. |
| `JWT_EXPIRATION` | Tiempo de expiración del JWT en milisegundos. |
| `MAIL_HOST` | Host SMTP para correo. |
| `MAIL_PORT` | Puerto SMTP. |
| `MAIL_USERNAME` | Usuario del servicio de correo. |
| `MAIL_PASSWORD` | Password o app password del servicio de correo. |

---

### Paso 4: Enlazar el `.env` en IntelliJ IDEA

Para que Spring Boot lea las variables del archivo `.env` desde IntelliJ:

1. Abrir el proyecto en IntelliJ IDEA.
2. Ir al menú superior:

```txt
Run -> Edit Configurations...
```

3. Seleccionar la configuración:

```txt
KodikalabApplication
```

4. Buscar el campo:

```txt
Environment variables
```

5. Hacer clic en el ícono de carpeta **📁**.
6. Seleccionar el archivo `.env` ubicado en la raíz del proyecto.
7. Hacer clic en:

```txt
Apply -> OK
```

Con esto, al presionar **Play** en IntelliJ, la aplicación usará las variables definidas en `.env`.

---

### Paso 5: Compilar y ejecutar

Compilar el proyecto:

```bash
./mvnw clean compile
```

En Windows PowerShell también puede usarse:

```powershell
.\mvnw.cmd clean compile
```

Ejecutar desde terminal:

```bash
./mvnw spring-boot:run
```

O ejecutar desde IntelliJ presionando **Play** sobre:

```txt
KodikalabApplication
```

URL base de la API:

```txt
http://localhost:8080/api/v1
```

Swagger UI:

```txt
http://localhost:8080/api/v1/swagger-ui.html
```

> Nota: el prefijo `/api/v1` se configura mediante `server.servlet.context-path` en `src/main/resources/application.yaml`.

---

## 3. Flujo de Trabajo en Git

Este proyecto seguirá una estrategia basada en **GitFlow** y buenas prácticas de revisión de código.

### Reglas obligatorias

Está estrictamente prohibido commitear directamente sobre:

```txt
main
develop
```

La rama `main` representa entregables estables.

La rama `develop` representa la integración del Sprint.

Cada desarrollador debe trabajar en una rama propia creada desde `develop`.

---

### Crear una rama de trabajo

Primero actualizar `develop`:

```bash
git checkout develop
git pull origin develop
```

Crear rama nueva:

```bash
git checkout -b feature/US<id>-<nombre-corto>
```

Ejemplo:

```bash
git checkout -b feature/US01-registro-usuarios
```

Convención obligatoria:

```txt
feature/US<id>-<nombre-corto>
```

Ejemplos válidos:

```txt
feature/US01-registro-usuarios
feature/US04-creacion-grupo
feature/US07-asignacion-problemas
feature/US11-ranking-interno
```

---

### Conventional Commits

Todos los commits deben seguir el estándar **Conventional Commits**.

Tipos permitidos:

| Tipo | Uso |
|---|---|
| `feat:` | Nueva funcionalidad. |
| `fix:` | Corrección de errores. |
| `chore:` | Configuración, dependencias o tareas auxiliares. |
| `refactor:` | Reestructuración sin cambiar comportamiento. |
| `test:` | Pruebas unitarias o de integración. |

Ejemplos:

```bash
git commit -m "feat: implementar registro de usuarios"
git commit -m "fix: corregir validación de login"
git commit -m "chore: configurar variables de entorno"
git commit -m "refactor: separar lógica de equipos en servicio"
git commit -m "test: agregar pruebas para auth service"
```

---

### Procedimiento de entrega

Cuando una historia esté lista:

1. Verificar compilación local:

```bash
./mvnw clean compile
```

2. Revisar cambios:

```bash
git status
git diff
```

3. Subir la rama remota:

```bash
git push origin feature/US<id>-<nombre-corto>
```

4. Abrir un **Pull Request (PR)** hacia:

```txt
develop
```

5. Solicitar revisión de código a un compañero o al Tech Lead.

> Los Pull Requests son necesarios para evidenciar el flujo de trabajo y obtener capturas para la sección **4.2.4 del informe**.

---

## 4. Estructura General del Proyecto

Paquete raíz:

```txt
com.kodika.kodikalab
```

Estructura base:

```txt
src/main/java/com/kodika/kodikalab
├── config
├── controller
├── dto
├── entity
├── repository
└── service
    └── impl
```

Responsabilidad por capa:

| Capa | Responsabilidad |
|---|---|
| `controller` | Exponer endpoints REST y delegar a servicios. |
| `service` | Definir contratos de negocio. |
| `service/impl` | Implementar casos de uso. |
| `repository` | Acceso a datos mediante Spring Data JPA. |
| `entity` | Modelo persistente JPA. |
| `dto` | Objetos de entrada y salida de la API. |
| `config` | Configuración transversal: seguridad, Swagger, CORS, etc. |

---

## 6. Checklist antes de abrir Pull Request

Antes de abrir un PR hacia `develop`, verificar:

- [ ] Estoy trabajando en una rama `feature/US<id>-<nombre-corto>`.
- [ ] Mi rama fue creada desde `develop` actualizado.
- [ ] No modifiqué archivos de otros integrantes sin coordinar.
- [ ] No commiteé `.env` ni secretos.
- [ ] El proyecto compila con `./mvnw clean compile`.
- [ ] Los commits usan Conventional Commits.
- [ ] El PR apunta hacia `develop`.
- [ ] Incluí una descripción clara de lo implementado.

---

## 7. Comandos útiles

Compilar:

```bash
./mvnw clean compile
```

Ejecutar:

```bash
./mvnw spring-boot:run
```

Ver estado de Git:

```bash
git status
```

Actualizar `develop`:

```bash
git checkout develop
git pull origin develop
```

Crear rama de historia:

```bash
git checkout -b feature/US01-registro-usuarios
```

Subir rama:

```bash
git push origin feature/US01-registro-usuarios
```
