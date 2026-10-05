# 04 - Database Model

## Fuente oficial y estado de alineación

El modelo vigente es **`docs/sdd/assets/oficial.erd`**, formato erd-editor 3.0.0, PostgreSQL, base `kodikalab_db`.

El archivo reemplaza al diseño anterior. Actualmente **no hay un snapshot SQL vigente** en `docs/sdd/assets/`; no se deben usar scripts retirados ni asumir que Hibernate ya reproduce el nuevo ERD.

`auth`/`users` ya están adaptados a `usuario`, nombre completo, dos roles y dos estados en español persistidos directamente con `@Enumerated(EnumType.STRING)`, sin conversores. Las pruebas y Postman usan el nuevo modelo. Las entidades legacy sin uso fueron retiradas y la única entidad JPA activa es `users.User`. **No se han migrado ni borrado datos existentes; las tablas de otros módulos siguen pendientes de implementar.**

Se mantiene el enfoque **code-first guiado por el ERD**:

```text
oficial.erd → entidades JPA alineadas → PostgreSQL de pruebas → snapshot SQL validado
```

El ERD define el diseño; JPA lo implementa. Una diferencia entre ambos exige revisión explícita.

## Convención de nombres

- Paquetes, clases, atributos Java, rutas y claves JSON: se conservan en inglés.
- **Valores de enums de cuenta: español tanto en Java como en HTTP y SQL**, según el ERD (`PRACTICANTE`/`COACH`, `ACTIVO`/`SUSPENDIDO`).
- **Tablas y columnas físicas: nombres exactos del ERD**, actualmente mayoritariamente en español y snake_case.
- No traducir automáticamente `usuario` a `users` ni imponer la antigua regla de tablas inglesas/plurales.
- Mantener `@Table` y `@Column` explícitos.
- Persistir los enums directamente con `@Enumerated(EnumType.STRING)`, no como ordinales. Sus constantes coinciden con el ERD: no se necesitan converters ni traducciones.
- `Categoria` contiene nombres y relaciones inconsistentes; no normalizarla ni generar su DDL sin aclaración del equipo.

## Cuenta base — `usuario`

Responsable de persistencia: módulo `users`. Registro y login: módulo `auth`, mediante el servicio público de `users`.

| Columna oficial | Tipo | Restricción / valor por defecto | Atributo Java objetivo |
| --- | --- | --- | --- |
| `id` | `SERIAL` (entero de 32 bits) | PK, generado | `id: Integer`, identidad |
| `nombre_completo` | `VARCHAR(150)` | Obligatorio | `fullName` |
| `correo` | `VARCHAR(100)` | Obligatorio, único | `email` |
| `password_hash` | `VARCHAR(255)` | Obligatorio | `passwordHash` |
| `rol` | `VARCHAR(20)` | Obligatorio; `COACH` / `PRACTICANTE` | `role: Role`, `@Enumerated(EnumType.STRING)` |
| `estado_cuenta` | `VARCHAR(20)` | Obligatorio; `ACTIVO` / `SUSPENDIDO`; default `ACTIVO` | `status: UserStatus`, `@Enumerated(EnumType.STRING)` |
| `fecha_registro` | `TIMESTAMP WITH TIME ZONE` | Obligatorio; default `CURRENT_TIMESTAMP` | `createdAt: OffsetDateTime` |

### Cambios implementados respecto del modelo anterior

| Modelo anterior | Implementado en auth/users |
| --- | --- |
| `users`, ID `Long` / `BIGINT` | `usuario`, ID entero / `SERIAL` |
| `first_name` y `last_name`, 80 caracteres cada uno | Un solo `nombre_completo`, máximo 150 |
| `email`, máximo 255 | `correo`, máximo 100 |
| `role` y `status`, máximo 30 | `rol` y `estado_cuenta`, máximo 20 |
| `created_at` | `fecha_registro` |
| `PRACTITIONER`, `COACH`, `ADMIN` | Persistir únicamente `PRACTICANTE`, `COACH` |
| `ACTIVE`, `INACTIVE`, `BLOCKED` | Persistir únicamente `ACTIVO`, `SUSPENDIDO` |

`Role` define `PRACTICANTE` y `COACH`; `UserStatus` define `ACTIVO` y `SUSPENDIDO`. Java, JSON y SQL utilizan los mismos valores, sin conversores. El antiguo valor HTTP `PRACTITIONER` ya no se admite; los clientes deben enviar `PRACTICANTE`. `ADMIN`, `INACTIVE` y `BLOCKED` tampoco pertenecen al modelo vigente.

El ERD no define un dominio institucional autorizado ni una política para transformar cuentas antiguas `ADMIN`; no inventar ninguna de esas reglas.

## Perfiles de rol — `coach` y `practicante`

Se documentan como perfiles del módulo `profiles`, separados de la cuenta base `users.User`. No son una nueva tabla de roles ni una segunda entidad de autenticación.

Ambos usan **PK compartida**: `usuario_id INT`, también FK a `usuario.id`. Una futura asociación JPA puede usar composición `@OneToOne` / `@MapsId`; no agregar un identificador autogenerado adicional ni una columna discriminadora que el ERD no contiene.

### `coach`

| Columna | Tipo | Restricción / default |
| --- | --- | --- |
| `usuario_id` | `INT` | PK y FK a `usuario.id` |
| `especialidad_principal` | `VARCHAR(120)` | Obligatorio |
| `organizacion_club` | `VARCHAR(150)` | Opcional |
| `anios_experiencia` | `INT` | Obligatorio; default `0` |
| `presentacion` | `VARCHAR(500)` | Opcional |

### `practicante`

| Columna | Tipo | Restricción / default |
| --- | --- | --- |
| `usuario_id` | `INT` | PK y FK a `usuario.id` |
| `codigo_estudiante` | `VARCHAR(20)` | Obligatorio, único |
| `carrera` | `VARCHAR(100)` | Obligatorio |
| `ciclo_academico` | `INT` | Obligatorio; default `1` |
| `nivel_competitivo` | `VARCHAR(30)` | Obligatorio; default `PRINCIPIANTE` |
| `codeforces_handle` | `VARCHAR(50)` | Opcional |
| `codeforces_rating` | `INT` | Opcional; default `0` |
| `atcoder_handle` | `VARCHAR(50)` | Opcional |
| `vjudge_handle` | `VARCHAR(50)` | Opcional |

Niveles documentados: `PRINCIPIANTE`, `INTERMEDIO`, `AVANZADO`. Los handles están directamente en `practicante`; el ERD vigente no contiene las tablas genéricas de perfiles competitivos/cuentas externas del modelo anterior.

El registro de cuenta base no recibe especialidad, código de estudiante ni carrera. **No crear perfiles con datos ficticios para cumplir columnas obligatorias.** Su creación y la obligatoriedad del perfil en el flujo de incorporación deben definirse en el contrato de US-03 o en un paso explícito de completado de perfil. Esta etapa se limita a preparar `auth`/`users`.

## Inventario del dominio y propiedad

Los nombres siguientes salen de las colecciones del ERD; no implican entidades JPA implementadas. Las antiguas entidades de scaffolding se retiraron.

| Tabla oficial | Módulo dueño objetivo | Relaciones / observaciones |
| --- | --- | --- |
| `usuario` | `users` | Cuenta base |
| `coach` | `profiles` | PK/FK a `usuario` |
| `practicante` | `profiles` | PK/FK a `usuario`; handles y datos académicos |
| `grupo_estudio` | `teams` | `coach_id` referencia `coach.usuario_id`, no directamente una cuenta genérica |
| `practicante_grupo` | `teams` | Membresía entre grupo y practicante |
| `solicitud_grupo` | `teams` | Postulación entre grupo y practicante; entidad presente con metadatos pendientes |
| `competencia` | `competitions` | Vinculada a un grupo |
| `competencia_problema` | `competitions` | Problema dentro de una competencia; orden, puntaje y asignación |
| `resolucion_problema` | `competitions` | Resolución de un problema de competencia por una membresía; relaciones pendientes de revisión |
| `problema` | `problems` | Catálogo con plataforma, URL y límites de ejecución |
| `tema` | `problems` | Nombre único |
| `problema_tema` | `problems` | PK compuesta problema/tema |
| `material` | `problems` | `problema_id` nullable: también permite biblioteca libre |
| `Categoria` | Pendiente | Relación con grupos/competencias y nombres ambiguos; no inventar dueño ni normalización física |

`analytics` deriva métricas de estas tablas. `assignments` conserva su capacidad funcional, pero el ERD no define las antiguas tablas genéricas de asignaciones; su integración debe revisarse sobre competencias/problemas. `ai` permanece como capacidad funcional sin tablas propias documentadas en esta versión.

No trasladar ni implementar estos otros módulos al adaptar `auth`/`users`.

## Cambios de equipos y competencias que afectan al contexto

- `grupo_estudio` contiene `cupo_maximo INT`, default `15`, y `horario_sesiones VARCHAR(150)` opcional. No hay una tabla de horarios independiente en este ERD.
- Tiene `codigo_invitacion VARCHAR(20)` obligatorio/único, estado `ACTIVO` / `ARCHIVADO` y fecha de creación.
- `practicante_grupo` relaciona `grupo_id` con `practicante_id`; hay unicidad del par y estados de membresía `ACTIVO` / `RETIRADO` / `EXPULSADO`.
- `solicitud_grupo` usa `PENDIENTE` / `ACEPTADA` / `RECHAZADA` / `CANCELADA`.
- `competencia_problema` tiene unicidad competencia/problema y competencia/orden de letra.
- Las resoluciones referencian una membresía y un problema de competencia. No usar automáticamente las tablas de resultados/asignaciones del diseño anterior como si siguieran vigentes.

Estas son definiciones documentales, no cambios implementados en código de equipos/competencias.

## Inconsistencias internas del archivo oficial a revisar

El JSON es legible, pero hay diferencias entre sus colecciones y listas de documentos:

1. `t_solicitud_grupo` existe en `tableEntities`, pero no en `doc.tableIds`.
2. `rel_grp_sg`, `rel_pra_sg`, `rel_pg_rsp` y `wiapNamjGgbljY0_ujikC` existen, pero no figuran en `doc.relationshipIds`.
3. `ix_sg_busqueda`, `ix_fk_c_rsp_pg` e `ix_fk_c_sg_practicante` no figuran en `doc.indexIds`.
4. `c_rsp_pg` está fuera de los `columnIds` de `resolucion_problema`, aunque una relación/índice todavía lo referencia. Hay otra columna de membresía incluida con tipo `SERIAL`; revisar duplicación y si debe ser una FK `INT` no autogenerada.
5. La relación desde `competencia` hacia `Categoria` referencia `uTEAp_JY9tT3j_ANZEjrA`, columna que no está en los `columnIds` de esa tabla. `Categoria.idGrupo` también está tipada `SERIAL` pese a su relación con un grupo.

No corregir el ERD silenciosamente ni generar un SQL global de referencia hasta resolver estas ambigüedades. Los campos base de `usuario` sí están definidos con sus tipos y restricciones.

## Migración segura del modelo anterior

Cambiar `@Table` y usar `ddl-auto=update` **no migra** filas de `users` a `usuario`, no combina nombres ni convierte roles/estados.

Antes de adaptar una base con datos:

1. Respaldar y revisar dependencias, IDs y claves foráneas de todos los módulos.
2. Verificar que IDs `BIGINT` existentes caben en un entero de 32 bits; no truncarlos.
3. Revisar nombres completos superiores a 150 caracteres, correos superiores a 100 y duplicados tras normalización.
4. Definir qué hacer con `ADMIN`; no convertirlo automáticamente a coach/practicante.
5. Acordar la equivalencia de `INACTIVE`/`BLOCKED` con el nuevo concepto `SUSPENDIDO`; no reactivar cuentas al migrar.
6. Conservar los hashes BCrypt existentes, sin rehashearlos ni sustituirlos por contraseñas ficticias.
7. Definir cómo migrar perfiles, especialidades/datos académicos y relaciones. No deducirlos de un rol sin los datos requeridos.
8. Validar registro/login y restricciones en PostgreSQL aislado antes de aplicar una migración aprobada.
9. Invalidar sesiones anteriores al cambiar el modelo de roles.

No se ejecutó ninguno de estos pasos sobre la base local. La alineación de `auth`/`users` se validó en PostgreSQL temporal aislado y no equivale a haber implementado las demás tablas oficiales ni migrado datos antiguos.
