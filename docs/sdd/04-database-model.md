# 04 - Database Model

## Estrategia oficial

KodikaLab usará un enfoque **code-first guiado por el ERD del SDD**.

Esto significa:

- El ERD define el diseño funcional y relacional esperado.
- Las entidades JPA implementan técnicamente ese diseño en Java.
- El script SQL funciona como snapshot de referencia del esquema.
- Si hay diferencia entre código y ERD, debe revisarse y corregirse conscientemente, no ignorarse.

Artefactos de referencia:

```txt
docs/sdd/assets/diagrama_entidad_relacion.erd
docs/sdd/assets/init_schema.sql
```

## Fuente de verdad durante implementación

Durante la implementación, la fuente técnica inmediata está en las entidades JPA, pero estas deben alinearse con el ERD.

Flujo esperado:

```txt
ERD del SDD -> Entidades JPA -> PostgreSQL -> script SQL / documentación actualizada
```

Si una historia requiere cambiar el modelo:

1. Revisar el ERD.
2. Ajustar la entidad JPA correspondiente.
3. Actualizar el ERD si el cambio modifica el diseño.
4. Actualizar el script SQL si corresponde.
5. Compilar y validar la aplicación.

## Relación con el monolito modular

El modelo de datos también guía la separación de módulos del monolito modular.

Cada grupo de tablas tiene un módulo dueño principal:

| Grupo de tablas | Módulo dueño |
|---|---|
| `users`, `user_availabilities` | `users` |
| `competitive_profiles`, `external_accounts` | `profiles` |
| `teams`, `team_memberships`, `join_requests`, `team_schedules` | `teams` |
| `problems`, `topics`, `academic_resources`, tablas puente de recursos y temas | `problems` |
| `assignments`, `assignment_details`, `assignment_recipients`, `submissions` | `assignments` |
| `competitions`, `competition_results` | `competitions` |
| `ai_conversations`, `ai_messages`, `agent_actions` | `ai` |

Las relaciones entre tablas pueden cruzar módulos porque sigue siendo una sola aplicación y una sola base de datos.

## Configuración de Hibernate

Para desarrollo local se usa:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

Con esta configuración, Hibernate puede crear o actualizar tablas, columnas, claves foráneas e índices derivados de las entidades JPA.

## Rol del ERD

El ERD no es decorativo. Es la referencia funcional del diseño de datos.

Debe usarse para:

- Entender las entidades principales.
- Identificar relaciones entre módulos.
- Decidir dónde pertenece una entidad dentro del monolito modular.
- Revisar que las entidades JPA no se alejen del modelo esperado.

## Rol del script SQL

El script ubicado en:

```txt
docs/sdd/assets/init_schema.sql
```

se conserva como snapshot/export de referencia del esquema, útil para revisión, documentación o reconstrucción manual si fuera necesario.

No es obligatorio ejecutarlo para levantar el backend local mientras `ddl-auto=update` esté activo.

## Convención de idioma y nombres

- Código Java: inglés.
- Entidades: inglés, singular y PascalCase.
- Tablas: inglés, plural y snake_case.
- Columnas: inglés y snake_case.
- Documentación funcional, informes y diagramas visuales: español.

Ejemplos:

| Java | Base de datos |
|---|---|
| `User` | `users` |
| `Team` | `teams` |
| `TeamMembership` | `team_memberships` |
| `Problem` | `problems` |
| `Submission` | `submissions` |

## Reglas para modificar el modelo

- Revisar primero el ERD del SDD.
- Modificar la entidad JPA correspondiente.
- Mantener `@Table(name = "...")` explícito en entidades.
- Mantener `@Column(name = "...")` cuando el nombre Java y el nombre SQL difieran.
- Mantener relaciones JPA explícitas cuando se implementen asociaciones reales.
- Si se cambia una entidad, actualizar documentación, ERD y script SQL cuando corresponda.
- Evitar nombres reservados de PostgreSQL como `user`; usar `users`.

## US-01 — Registro y propiedad de usuarios

La entidad activa es `com.kodika.kodikalab.users.User`, alineada con las columnas y longitudes del ERD y del snapshot `assets/init_schema.sql`. No se agregan tablas ni columnas nuevas en esta historia.

- `users` es dueño de `User`, `Role`, `UserStatus` y `UserRepository`.
- El servicio público `users.UserService` crea cuentas; `auth` no usa su repositorio directamente.
- El correo se normaliza a minúsculas sin espacios externos y tiene una restricción única en PostgreSQL. La consulta previa ignora mayúsculas para detectar también cuentas legacy.
- `password_hash` contiene BCrypt, nunca la contraseña original.
- `role` y `status` se almacenan como texto; `tarea.md` exige crear la cuenta en `ACTIVE`.
- `created_at` se asigna con zona UTC.
- `entity.User` y `repository.UserRepository` se conservan como scaffolding inactivo, no como una segunda entidad/bean de persistencia.

Si la base ya contiene datos, revisar nulos, correos duplicados (también diferencias de mayúsculas), longitudes y valores de rol/estado antes de aplicar restricciones. `ddl-auto=update` no sustituye una migración ni garantiza corregir datos incompatibles. No se borran ni normalizan cuentas existentes automáticamente.

El ERD usa etiquetas funcionales en español (`PRACTICANTE` corresponde a `PRACTITIONER` en Java). La verificación futura del coach no se implementa en US-01; esta tarea exige `ACTIVE` para las cuentas creadas.

## US-04 — Grupos y horarios

La implementación de T2/T3 se concentra en `com.kodika.kodikalab.teams`:

- `Team` conserva la tabla `teams` y su relación con `users` mediante `coach_id`.
- `max_members` / `maxMembers`: entero obligatorio positivo, representa la capacidad del grupo. No representa el número actual de miembros.
- La combinación `(coach_id, name)` es única, conforme al índice del ERD.
- `TeamSchedule` implementa `team_schedules`, con relación obligatoria a `Team` mediante `team_id`.
- `week_day` almacena los nombres ingleses de `DayOfWeek`; las horas se modelan con `LocalTime`.
- Se conserva la unicidad `(team_id, week_day, start_time, end_time)` y se exige `end_time > start_time`.
- Grupo y horarios se guardan en una sola transacción; los horarios se persisten por cascada.

### Bases de desarrollo con datos existentes

El snapshot `assets/init_schema.sql` describe el esquema esperado; no es una migración incremental. `ddl-auto=update` no garantiza añadir todas las restricciones ni resolver filas existentes incompatibles.

Si `teams` ya contiene registros, antes de iniciar con el nuevo modelo se debe agregar `max_members` inicialmente nullable, asignar una capacidad positiva **acordada para cada grupo existente** y luego exigir `NOT NULL` y el `CHECK` positivo. No se asigna un valor ficticio por defecto ni se borran datos automáticamente.

También deben revisarse nombres duplicados por coach, coaches huérfanos y horarios inválidos antes de aplicar las claves foráneas, unicidad y restricciones correspondientes. Esta rama no modifica la base local automáticamente mediante scripts propios.

## Nota para entrega final

Antes de una entrega formal, el equipo puede cambiar temporalmente a:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

para verificar que el esquema generado y las entidades estén alineados sin que Hibernate modifique la base de datos.
