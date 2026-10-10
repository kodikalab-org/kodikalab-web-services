# Alineación con `oficial.erd` — auth/users

## Alcance y estado

Fuente: `docs/sdd/assets/oficial.erd`. El SDD y el código de **`auth`/`users` están alineados para la cuenta base**. Se conservan los cambios previos de login. El scaffolding legacy sin funcionalidad fue retirado tras revisión y autorización; ver `12-source-cleanup.md`.

No se implementan perfiles, equipos, competencias, catálogo ni IA. **No se migraron datos existentes** y el ERD oficial permanece intacto; sus incidencias de metadatos siguen documentadas en `04-database-model.md`.

## Mapeo implementado

| ERD físico | Java / HTTP |
| --- | --- |
| `usuario` | `users.User`, `@Table` explícito |
| `id SERIAL`, referencias `INT` | `Integer`, identidad generada; repository con ID `Integer` |
| `nombre_completo VARCHAR(150)` | `fullName`; HTTP conserva `firstName`/`lastName`, unidos con un espacio |
| `correo VARCHAR(100)`, único | `email`, normalizado a minúsculas sin espacios externos |
| `password_hash VARCHAR(255)` | `passwordHash`, BCrypt, excluido de respuestas |
| `rol VARCHAR(20)` | `Role`: `PRACTICANTE` / `COACH`, `@Enumerated(EnumType.STRING)` |
| `estado_cuenta VARCHAR(20)` | `UserStatus`: `ACTIVO` / `SUSPENDIDO`, `@Enumerated(EnumType.STRING)` |
| `fecha_registro TIMESTAMP WITH TIME ZONE` | `createdAt: OffsetDateTime`, creación UTC |

Los defaults SQL de estado/fecha están declarados en JPA. Los enums usan los mismos valores en Java/HTTP/SQL sin converters. `PRACTITIONER`, `ADMIN` y ordinales numéricos se rechazan en registro; no se reclasifican roles ni estados legacy. La restricción de correo se identifica como `uq_usuario_correo` o `usuario_correo_key`, sin convertir otras violaciones SQL en falsos duplicados.

## Contrato y seguridad conservados

- `/api/auth/register` y `/api/auth/login`; claves JSON existentes conservadas, valores de roles/estados en español.
- El cliente debe enviar/interpretar `PRACTICANTE`, no `PRACTITIONER`; las autoridades son `ROLE_PRACTICANTE`/`ROLE_COACH`. Reiniciar o invalidar sesiones anteriores al desplegar. Este ajuste no traduce filas: los valores SQL ya estaban en español.
- Nombre/apellido obligatorios, hasta 80 cada uno; la unión normalizada no supera 150.
- Correo hasta 100 en registro y login; no se impone un dominio institucional exclusivo.
- BCrypt, límite de 72 bytes UTF-8 sin truncado y mensajes públicos de aceptación.
- Login solo para cuentas activas; mismo `401 Credenciales inválidas` para credenciales incorrectas, cuentas ausentes o suspendidas.
- Autenticación con token JWT (Bearer) sin sesión HTTP; el rol efectivo se lee de `usuario.rol` en cada petición.
- Autorización por rol en `SecurityConfig`; ver `06-security-strategy.md` para lo que sigue pendiente antes de producción.
- No se fabrican perfiles `coach`/`practicante` sin sus datos obligatorios.
- Documentación, fixtures y colecciones usan nombres genéricos y correos de prueba, sin datos personales.

## Verificación ejecutada

- `./mvnw clean compile`: correcto con Java 21.
- `./mvnw test`: **106 pruebas, 0 fallos, 0 errores, 0 omitidas**, sobre PostgreSQL 17 temporal aislado de la base local; clúster detenido y eliminado al finalizar.
- Se verificaron columnas físicas, ID entero, longitudes, defaults, nombre completo, valores SQL españoles persistidos directamente como enums, BCrypt, duplicados/concurrencia y rechazo sin persistencia (incluido el rol inglés anterior).
- Regresión de sesión: autoridades de los dos roles, continuidad entre solicitudes, renovación/invalidez del ID anterior, reintento y prevención de elevación de rol.
- Limpieza: una sola entidad/repository de cuenta, solo registro/login publicados y `404` para rutas ficticias retiradas; ver `12-source-cleanup.md`.
- Newman sobre backend empaquetado y PostgreSQL temporal: **48 solicitudes, 261 aserciones, 0 fallos** entre ambas colecciones; fixture `SUSPENDIDO` ejecutada y comprobada.
- Colecciones Postman y fixture SQL actualizadas; ver `tests/README.md` y `tests/US02-login.md`.

## Antes de migrar cuentas existentes

1. Respaldar la base y revisar IDs/FKs de todos los módulos.
2. Comprobar rango `BIGINT` → `INT`, nombres de hasta 150 y correos de hasta 100; no truncar datos.
3. Resolver duplicados después de normalizar correos.
4. Acordar el tratamiento de `ADMIN` y la equivalencia de estados anteriores; no reclasificar privilegios ni reactivar cuentas.
5. Conservar hashes BCrypt sin rehashearlos y definir perfiles con datos válidos.
6. Preparar y aprobar una migración explícita, validada primero en una copia aislada.
7. Invalidar sesiones anteriores al desplegar el nuevo modelo de roles.

Renombrar `@Table` o usar `ddl-auto=update` no copia filas de `users` a `usuario`. Retirar las clases de scaffolding no elimina sus tablas antiguas ni significa haber implementado todo el ERD.
