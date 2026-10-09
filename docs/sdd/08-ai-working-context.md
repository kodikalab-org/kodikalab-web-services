# 08 - AI Working Context

## Reglas obligatorias

- Respetar `com.kodika.kodikalab`, Java 21 y Spring Boot 3.5.6.
- Mantener un **monolito modular simple** y migrar únicamente el dominio de la tarea.
- Leer `assets/oficial.erd`, `04-database-model.md`, `05-architecture.md` y `03-api-contracts.md` antes de cambiar el modelo o un módulo.
- Diagramas del equipo en `Diagrama_C4_Kodikalab.drawio`. **Precedencia:** para datos (tablas, columnas, tipos, relaciones) manda **`oficial.erd`**, el ERD oficial. La página "Base de Datos" del drawio es su representación visual y debe coincidir con él. El **diagrama de clases** (página "CODIGO") define los **nombres de las clases** y su comportamiento (métodos). Las diferencias de atributos entre ambos diagramas se registran en `04-database-model.md` para que el equipo corrija el diagrama de clases; no cambian el modelo de datos.
- El nuevo ERD prevalece sobre los ejemplos del modelo anterior. No asumir que una entidad actual está alineada porque compile.
- No modificar `.env`, exponer secretos, ejecutar commits/push ni cambiar ramas sin autorización.
- No incluir datos personales en documentación, colecciones o fixtures: usar nombres genéricos como `Usuario Prueba` y correos de ejemplo como `test@gmail.com`.
- No cambiar `/api`, activar JWT ni cerrar endpoints de desarrollo sin solicitud explícita.
- El scaffolding raíz `controller`/`dto`/`entity`/`repository`/`service` se retiró tras revisión y autorización; no restaurarlo. Los módulos `teams`, `problems` y `competitions` existen como **plantilla del ERD** (decisión del dueño del proyecto): entidades JPA, enums, repositorios, servicios y controllers sin lógica ni endpoints. Cada historia agrega sus métodos y endpoints con su contrato; no agregar respuestas ficticias ni endpoints sin historia. Revisar referencias y avisar antes de nuevas eliminaciones; `auth/dto` sí es funcional y se conserva.
- No ejecutar DDL/migraciones sobre la base local ni usar `ddl-auto=update` como sustituto de una migración aprobada.
- Después de cambios Java, ejecutar `./mvnw clean compile` y las pruebas correspondientes.

## Artefactos y fase actual

Fuente vigente: `docs/sdd/assets/oficial.erd`. No hay un snapshot SQL vigente en assets. Los archivos retirados no se restauran automáticamente.

`auth`/`users`, fixtures y Postman están alineados al ERD oficial para la cuenta base. `profiles` implementa los perfiles `practicante` y `coach` (US-03) en `GET/PUT /api/users/me`, que actúan según el rol de la sesión; colecciones `tests/US03-profile.postman_collection.json` y `tests/US03-coach.postman_collection.json`. Los demás módulos siguen como diseño pendiente; su scaffolding sin lógica fue retirado. Los datos existentes no se han migrado ni eliminado. Mantener esta distinción al extender el proyecto.

`oficial.erd` es el ERD oficial; conserva restos ocultos en resoluciones, categoría y solicitudes que no forman parte del modelo, y el drawio debe corregirse en `Categoria`. Ver `04-database-model.md`. No editar el archivo oficial para ocultarlas ni generar su SQL completo sin revisarlas.

## Implementación vigente de auth/users

- `users.User` se mapea a `usuario`, con ID coherente con `SERIAL`/`INT`.
- Nombre persistido: `nombre_completo`, hasta 150 caracteres; correo: `correo`, hasta 100.
- Nombres de clases: español del diagrama de clases (tabla de equivalencias en `05-architecture.md`; renombrado pendiente). No renombrar rutas ni claves JSON sin decisión explícita. Los **valores de los enums están en español**, igual que en el ERD.
- El registro puede conservar `firstName`/`lastName` en su DTO y unirlos explícitamente; validar el límite combinado de 150, sin crear columnas adicionales.
- `Role`: `PRACTICANTE`/`COACH` en Java, HTTP y SQL. El valor antiguo `PRACTITIONER` se rechaza; no mantener `ADMIN` como rol oficial.
- `UserStatus`: `ACTIVO`/`SUSPENDIDO`. Usar `@Enumerated(EnumType.STRING)` directamente, sin converters ni ordinales. No mantener automáticamente `INACTIVE`/`BLOCKED`.
- BCrypt, mensajes genéricos de login y sesión HTTP se conservan; no devolver ni registrar hashes/contraseñas.
- `auth` consulta por el servicio público de `users`, no por su repository directamente.
- No fabricar campos obligatorios de perfiles `coach`/`practicante` ni implementar perfiles en una tarea de cuentas base.
- Conservar las pruebas de rechazo de `ADMIN`, correo superior a 100, nombre combinado superior a 150 y cuentas suspendidas; no restaurar fixtures de `users`/`INACTIVE`/`BLOCKED`.
- Las cuentas existentes y sus sesiones requieren una estrategia de migración aprobada; no reclasificar roles ni reactivar cuentas silenciosamente.

## Organización modular

Módulos funcionales: `auth`, `users`, `profiles`, `teams`, `problems`, `assignments`, `competitions`, `analytics`, `ai`; transversales: `security`, `config`, `common`.

- Controllers delegan en servicios.
- Cada módulo controla sus repositories.
- DTOs viven en su módulo.
- Entidades no dependen de controllers/servicios/DTOs.
- `common` no depende de módulos de negocio.
- Relaciones JPA entre módulos solo cuando el ERD las justifica.
- Las capacidades sin tablas propias en este ERD no autorizan inventar nuevas tablas.

## Verificación y comunicación

Distinguir siempre: diseño objetivo, código adaptado, pruebas realmente ejecutadas y datos migrados. Las pruebas anteriores no demuestran cumplimiento del ERD nuevo.

Las pruebas PostgreSQL deben usar recursos aislados y variables de entorno de pruebas, no credenciales de desarrollo. En IntelliJ no forzar suites opt-in deshabilitadas sin configurar sus variables. La URL temporal de una ejecución previa no implica que ese servidor continúe activo.
