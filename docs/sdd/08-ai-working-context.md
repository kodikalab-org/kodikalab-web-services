# 08 - AI Working Context

## Reglas obligatorias

- Respetar `com.kodika.kodikalab`, Java 21 y Spring Boot 3.5.6.
- Mantener un **monolito modular simple** y migrar únicamente el dominio de la tarea.
- Leer `assets/oficial.erd`, `04-database-model.md`, `05-architecture.md` y `03-api-contracts.md` antes de adaptar auth/users.
- El nuevo ERD prevalece sobre los ejemplos del modelo anterior. No asumir que una entidad actual está alineada porque compile.
- No modificar `.env`, exponer secretos, ejecutar commits/push ni cambiar ramas sin autorización.
- No incluir datos personales en documentación, colecciones o fixtures: usar nombres genéricos como `Usuario Prueba` y correos de ejemplo como `test@gmail.com`.
- No cambiar `/api`, activar JWT ni cerrar endpoints de desarrollo sin solicitud explícita.
- El scaffolding raíz `controller`/`dto`/`entity`/`repository`/`service` se retiró tras revisión y autorización; no restaurar clases vacías ni endpoints ficticios. Revisar referencias y avisar antes de nuevas eliminaciones; `auth/dto` sí es funcional y se conserva.
- No ejecutar DDL/migraciones sobre la base local ni usar `ddl-auto=update` como sustituto de una migración aprobada.
- Después de cambios Java, ejecutar `./mvnw clean compile` y las pruebas correspondientes.

## Artefactos y fase actual

Fuente vigente: `docs/sdd/assets/oficial.erd`. No hay un snapshot SQL vigente en assets. Los archivos retirados no se restauran automáticamente.

`auth`/`users`, fixtures y Postman están alineados al ERD oficial para la cuenta base. Los demás módulos siguen como diseño pendiente; su scaffolding sin lógica fue retirado. Los datos existentes no se han migrado ni eliminado. Mantener esta distinción al extender el proyecto.

El ERD tiene inconsistencias de listas/metadatos en solicitudes, resoluciones y categoría; ver `04-database-model.md`. No editar el archivo oficial para ocultarlas ni generar su SQL completo sin revisarlas.

## Implementación vigente de auth/users

- `users.User` se mapea a `usuario`, con ID coherente con `SERIAL`/`INT`.
- Nombre persistido: `nombre_completo`, hasta 150 caracteres; correo: `correo`, hasta 100.
- Conservar nombres de clases, paquetes, rutas y claves JSON; los **valores de los enums de cuenta están en español**, igual que en el ERD.
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
