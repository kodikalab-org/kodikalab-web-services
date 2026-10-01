# 04 - Database Model

## Estrategia oficial: Code-first

KodikaLab usará enfoque **code-first** durante el desarrollo del backend.

Esto significa que la fuente de verdad técnica del modelo de datos está en el código Java, principalmente en las entidades JPA ubicadas en:

```txt
src/main/java/com/kodika/kodikalab/entity
```

El ERD, los diagramas visuales y los scripts SQL deben generarse o actualizarse a partir de lo definido en el código.

## Configuración de Hibernate

Para desarrollo local se usa:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

Con esta configuración, Hibernate puede crear o actualizar tablas, columnas, claves foráneas e índices derivados de las entidades JPA.

## Rol del ERD

El ERD sigue siendo importante, pero funciona como referencia visual y documentación del modelo.

Flujo esperado:

```txt
Entidades JPA -> PostgreSQL -> ERD / diagrama visual / script SQL
```

No al revés.

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
| `ProblemSubmission` | `submissions` |

## Reglas para modificar el modelo

- Primero modificar la entidad JPA correspondiente.
- Mantener `@Table(name = "...")` explícito en entidades.
- Mantener `@Column(name = "...")` cuando el nombre Java y el nombre SQL difieran.
- Mantener relaciones JPA explícitas cuando se implementen asociaciones reales.
- Si se cambia una entidad, actualizar documentación y diagramas derivados cuando corresponda.
- Evitar nombres reservados de PostgreSQL como `user`; usar `users`.

## Nota para entrega final

Antes de una entrega formal, el equipo puede cambiar temporalmente a:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

para verificar que el esquema generado y las entidades estén alineados sin que Hibernate modifique la base de datos.
