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

- Clases: nombres en español del diagrama de clases (renombrado pendiente; ver `05-architecture.md`). Paquetes de módulo, rutas y claves JSON: sin cambios hasta definir el alcance del renombrado.
- **Valores de enums de cuenta: español tanto en Java como en HTTP y SQL**, según el ERD (`PRACTICANTE`/`COACH`, `ACTIVO`/`SUSPENDIDO`).
- **Tablas y columnas físicas: nombres exactos del ERD**, actualmente mayoritariamente en español y snake_case.
- No traducir automáticamente `usuario` a `users` ni imponer la antigua regla de tablas inglesas/plurales.
- Mantener `@Table` y `@Column` explícitos.
- Persistir los enums directamente con `@Enumerated(EnumType.STRING)`, no como ordinales. Sus constantes coinciden con el ERD: no se necesitan converters ni traducciones.
- `Categoria` engloba únicamente al grupo de estudio. Se mapea como en `oficial.erd` (1:1 con `grupo_estudio`), con los identificadores en minúsculas que PostgreSQL crea para nombres sin comillas (`categoria`, `idcategoria`, `descripcioncategoria`, `nombrecategoria`, `idgrupo`); `idcategoria` es INT sin autoincremento, como en el ERD. No relacionarla con competencias ni problemas.

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

Ambos usan **PK compartida**: `usuario_id INT`, también FK a `usuario.id`. Las entidades JPA implementadas (`PractitionerProfile` → `practicante`, `CoachProfile` → `coach`) usan composición `@OneToOne` / `@MapsId`; no agregar un identificador autogenerado adicional ni una columna discriminadora que el ERD no contiene.

Ambas tablas se crean en desarrollo mediante `ddl-auto: update`. **En ambientes con datos reales se requiere una migración aprobada**; `ddl-auto` no sustituye esa migración.

### `coach`

| Columna | Tipo | Restricción / default |
| --- | --- | --- |
| `usuario_id` | `INT` | PK y FK a `usuario.id` |
| `especialidad_principal` | `VARCHAR(120)` | Obligatorio |
| `organizacion_club` | `VARCHAR(150)` | Opcional |
| `anios_experiencia` | `INT` | Obligatorio; default `0` |
| `presentacion` | `VARCHAR(500)` | Opcional |

Implementado en US-03 mediante `GET/PUT /api/users/me` con sesión `COACH`. `especialidad_principal` es texto libre (el ERD no define catálogo); `anios_experiencia` se valida entre 0 y 60; `organizacion_club` y `presentacion` vacíos se guardan como `null`. Es prerrequisito de US-04: `grupo_estudio.coach_id` referencia `coach.usuario_id`.

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

Niveles documentados e implementados: `PRINCIPIANTE`, `INTERMEDIO`, `AVANZADO`. Los handles están directamente en `practicante`; el ERD vigente no contiene las tablas genéricas de perfiles competitivos/cuentas externas del modelo anterior.

US-03 implementa la creación/actualización de `practicante` para la cuenta autenticada con rol `PRACTICANTE`, mediante `GET /api/users/me` y `PUT /api/users/me`. Se persisten Codeforces, AtCoder y VJudge porque son las columnas del ERD; **LeetCode no se persiste** mientras no exista en `oficial.erd`. `codeforces_rating` se obtiene desde la API pública de Codeforces; si Codeforces no confirma el handle, no se persiste el nuevo handle/rating y ambos quedan `null` cuando no había valores previos.

El registro de cuenta base no recibe especialidad, código de estudiante ni carrera. **No crear perfiles con datos ficticios para cumplir columnas obligatorias.** El perfil se crea cuando el practicante completa la configuración de perfil.

## Inventario del dominio y propiedad

Todas las tablas visibles del ERD tienen entidad JPA. `usuario`, `coach` y `practicante` tienen lógica (US-01 a US-03); el módulo teams implementa US04, US05 y US06; los módulos problems y competitions mantienen funcionalidades pendientes de desarrollo.*. FKs dibujadas como `SERIAL` (`resolucion_problema.practicante_grupo_id`, `Categoria.idGrupo`) se mapean como `INT`, porque una FK no se autogenera. Las antiguas entidades de scaffolding se retiraron; sus tablas (`teams`, `problems`, `submissions`...) pueden seguir en bases locales y no se eliminan sin migración aprobada.

| Tabla oficial | Módulo dueño objetivo | Relaciones / observaciones |
| --- | --- | --- |
| `usuario` | `users` | Cuenta base |
| `coach` | `profiles` | PK/FK a `usuario` |
| `practicante` | `profiles` | PK/FK a `usuario`; handles y datos académicos |
| `grupo_estudio` | `teams` | `coach_id` referencia `coach.usuario_id`, no directamente una cuenta genérica |
| `practicante_grupo` | `teams` | Membresía entre grupo y practicante |
| `competencia` | `competitions` | Vinculada a un grupo |
| `competencia_problema` | `competitions` | Problema dentro de una competencia; orden, puntaje y asignación |
| `resolucion_problema` | `competitions` | Resolución de un problema de competencia por una membresía; relaciones pendientes de revisión |
| `problema` | `problems` | Catálogo con plataforma, URL y límites de ejecución |
| `tema` | `problems` | Nombre único |
| `problema_tema` | `problems` | PK compuesta problema/tema |
| `material` | `problems` | `problema_id` nullable: también permite biblioteca libre |
| `Categoria` | `competitions` | 1:1 con `grupo_estudio`; engloba únicamente al grupo |

`analytics` deriva métricas de estas tablas. `assignments` conserva su capacidad funcional, pero el ERD no define las antiguas tablas genéricas de asignaciones; su integración debe revisarse sobre competencias/problemas. `ai` permanece como capacidad funcional sin tablas propias documentadas en esta versión.

No trasladar ni implementar estos otros módulos al adaptar `auth`/`users`.

## Cambios de equipos y competencias que afectan al contexto

- `grupo_estudio` contiene `cupo_maximo INT`, default `15`, y `horario_sesiones VARCHAR(150)` opcional. No hay una tabla de horarios independiente en este ERD.
- Tiene `codigo_invitacion VARCHAR(20)` obligatorio/único, estado `ACTIVO` / `ARCHIVADO` y fecha de creación.
- `practicante_grupo` relaciona `grupo_id` con `practicante_id`; hay unicidad del par y estados de membresía PENDIENTE / ACTIVO / RECHAZADO / RETIRADO / EXPULSADO.
- `competencia_problema` tiene unicidad competencia/problema y competencia/orden de letra.
- Las resoluciones referencian una membresía y un problema de competencia. No usar automáticamente las tablas de resultados/asignaciones del diseño anterior como si siguieran vigentes.

Las funcionalidades US04, US05 y US06 de Teams están implementadas. Las funcionalidades restantes de competencias continúan pendientes de desarrollo..


## Implementación de Teams — US04, US05 y US06

Las funcionalidades de creación de grupos, solicitudes de ingreso y revisión de membresías están implementadas mediante las tablas `grupo_estudio` y `practicante_grupo`.

No se utiliza una tabla adicional `solicitud_grupo`.

### Grupo de estudio — `grupo_estudio`

La entidad `StudyGroup` representa un grupo administrado por un coach.

Campos principales:

| Columna | Descripción |
| --- | --- |
| `id` | Identificador del grupo |
| `coach_id` | FK al perfil del coach responsable |
| `nombre` | Nombre del grupo |
| `descripcion` | Descripción opcional |
| `nivel_esperado` | Nivel competitivo esperado |
| `cupo_maximo` | Capacidad máxima |
| `horario_sesiones` | Horario opcional |
| `codigo_invitacion` | Código único de invitación |
| `estado` | `ACTIVO` o `ARCHIVADO` |
| `fecha_creacion` | Fecha de creación |
| `visibilidad` | `PUBLICO`, `PROTEGIDO` o `ARCHIVADO` |

Los grupos nuevos se crean con estado `ACTIVO` y no pueden utilizar visibilidad `ARCHIVADO`.

### Solicitudes y membresías — `practicante_grupo`

La entidad `GroupMembership` representa tanto una solicitud de ingreso como una membresía efectiva.

Se reutiliza la misma fila para registrar las transiciones de estado, sin crear una entidad adicional.

Estados implementados:

| Estado | Significado |
| --- | --- |
| `PENDIENTE` | Solicitud esperando revisión |
| `ACTIVO` | Practicante integrante del grupo |
| `RECHAZADO` | Solicitud rechazada |
| `RETIRADO` | Practicante que abandonó el grupo |
| `EXPULSADO` | Practicante expulsado |

Las solicitudes utilizan el identificador real de `practicante_grupo`.

La combinación de `grupo_id` y `practicante_id` es única.

### Reglas de ingreso

- Un grupo `PUBLICO` permite ingreso directo con estado `ACTIVO`.
- Un grupo `PROTEGIDO` permite ingreso directo con un código de invitación válido.
- Un grupo `PROTEGIDO` sin código válido registra una solicitud `PENDIENTE`.
- No se permiten solicitudes duplicadas mientras exista una membresía `PENDIENTE` o `ACTIVO`.
- Una membresía `RECHAZADO` o `RETIRADO` puede reutilizarse para solicitar ingreso nuevamente.
- Según la política implementada, una membresía `EXPULSADO` no puede solicitar ingreso nuevamente.
- Solamente las membresías `ACTIVO` se consideran para el cálculo de capacidad.
- No se permiten nuevos ingresos o solicitudes cuando el grupo alcanza su cupo máximo.
- No se permite ingresar a grupos inactivos o con visibilidad `ARCHIVADO`.

### Revisión de solicitudes

Solo el coach responsable puede revisar solicitudes pendientes.

La aceptación realiza:

`PENDIENTE → ACTIVO`

Al aceptar, se verifica el cupo disponible y se actualiza `fecha_ingreso`.

El rechazo realiza:

`PENDIENTE → RECHAZADO`

No se crea ni se elimina ninguna fila adicional durante la revisión.

### Compatibilidad con PostgreSQL

El ERD oficial documentaba originalmente los estados `ACTIVO`, `RETIRADO` y `EXPULSADO`.

Para implementar US05 y US06 sin incorporar otra tabla, el enum Java `MembershipStatus` incorpora `PENDIENTE` y `RECHAZADO`.

Esto requiere ampliar la restricción CHECK de `practicante_grupo.estado` en bases PostgreSQL existentes.

La ampliación de valores debe revisarse y aprobarse como diferencia respecto del ERD oficial. No se deben agregar columnas ni tablas para esta funcionalidad.

Hibernate puede generar los cinco valores en esquemas nuevos, pero `ddl-auto: update` no garantiza modificar correctamente las restricciones CHECK existentes.

Se requiere una migración SQL reproducible y validada antes del despliegue.


## Diferencias del drawio frente a `oficial.erd`

`oficial.erd` es el ERD oficial. La página "Base de Datos" del drawio coincide con él en tablas, columnas,
longitudes y valores, salvo en lo siguiente, que debe corregirse **en el drawio**:

| Elemento | Página "Base de Datos" del drawio | `oficial.erd` (oficial) y código |
| --- | --- | --- |
| `categoria` | `id SERIAL`, `grupo_id`, `nombre`, `descripcion`; un grupo **define** N categorías | 1:1 con `grupo_estudio`: `idcategoria`, `idgrupo`, `nombrecategoria`, `descripcioncategoria` |
| `competencia.categoria_id` | FK a `categoria`; una categoría clasifica competencias | **No existe**: la categoría engloba únicamente al grupo |
| `resolucion_problema.practicante_grupo_id` | `INT` FK | Dibujada `SERIAL`; se mapea `INT`, porque una FK no se autogenera |
| `solicitud_grupo` | No existe | Restos ocultos en el `.erd`; no forma parte del modelo |

El drawio no muestra obligatoriedad ni valores por defecto; se toman de `oficial.erd`.

## Diferencias del diagrama de clases frente al ERD

Para datos manda el ERD; estas diferencias deben corregirse en el diagrama de clases ("CODIGO"):

| Clase | Diferencia frente al ERD |
| --- | --- |
| `Practicante`, `Coach` | Heredan de `Usuario`; el ERD (y el código) usa tablas separadas 1:1 con `usuario_id` como PK/FK compartida |
| `Practicante` | Tiene `distrito`, `experienciaMeses`, `disponibilidad`, `objetivo` (no están en el ERD); le faltan `carrera`, handles de Codeforces/AtCoder/VJudge y `codeforcesRating` |
| `Coach` | `anosExperiencia` (ERD `anios_experiencia`) |
| `GrupoEstudio` | `nivelObjetivo` (ERD `nivel_esperado`), `cicloAcademico` (no está), `horarioRecurrente` (ERD `horario_sesiones`); le falta `cupoMaximo` |
| `Categoria` | Atributo `tipo` (no está); **clasifica `Problema`**, mientras el ERD la asocia únicamente al grupo de estudio |
| `Competencia` | `tipoEvento` (ERD `tipo_acceso`); le faltan `claveAcceso`, `reglaPenalizacion`, `duracionMinutos`, `congelarScoreboardMin` y la categoría |
| `CompetenciaProblema` | `int orden` (ERD `orden_letra` VARCHAR: A, B, C...), `double puntaje` (ERD INT); le faltan `colorGlobo` y `fechaAsignacion` |
| `ResultadoCompetencia` | Tabla `resolucion_problema`; `resultado` corresponde a `veredicto` |
| `Problema` | `urlOriginal` (ERD `url_problema`), `int dificultadRating` (ERD VARCHAR) |
| `Material` | `tipoMaterial` (ERD `tipo_recurso`), `resumen` (no está); le falta `enlaceUrl` |
| Tipos | IDs `Long` (ERD `SERIAL`/`INT` → `Integer`) y fechas `LocalDateTime` (ERD `timestamptz` → `OffsetDateTime`) |

## Inconsistencias internas de `oficial.erd`

1. Queda una relación oculta desde `competencia` hacia `Categoria` (columna `uTEAp_JY9tT3j_ANZEjrA`, fuera de los `columnIds`) que no forma parte del modelo: la categoría solo se relaciona con el grupo. `Categoria.idGrupo` está tipada `SERIAL` pese a ser FK y se mapea `INT`.
2. `c_rsp_pg` está fuera de los `columnIds` de `resolucion_problema`, aunque una relación/índice la referencia; la columna visible está tipada `SERIAL`.
3. Quedan elementos ocultos de `solicitud_grupo` (tabla, relaciones `rel_grp_sg`/`rel_pra_sg` e índices `ix_sg_busqueda`/`ix_fk_c_sg_practicante`) que ya no forman parte del modelo.

Estos restos ocultos no afectan al modelo vigente. No corregir el archivo silenciosamente: cualquier limpieza del `.erd` se acuerda con el equipo.

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
