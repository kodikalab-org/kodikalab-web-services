# 19 - US04 Borrador de grupo y US06 Aclaración al postulante

Este documento describe las dos mejoras solicitadas sobre la implementación de US04, US05 y US06 del módulo
`teams`, sin agregar tablas nuevas y manteniendo la alineación con `assets/oficial.erd`.

## Alcance

- **US04 — Guardar borrador del grupo:** el coach puede guardar un grupo incompleto y publicarlo después.
- **US06 — Pedir aclaración al postulante:** el coach puede pedir información adicional antes de aceptar o
  rechazar una solicitud, y el postulante responde por el API.

Ambas funcionalidades usan las tablas existentes `grupo_estudio` y `practicante_grupo`.

## US04 — Borrador del grupo

### Modelo

`GroupStatus` agrega el valor `BORRADOR`:

| Valor | Significado |
| --- | --- |
| `BORRADOR` | Grupo guardado por el coach, aún no visible ni disponible para ingreso |
| `ACTIVO` | Grupo publicado |
| `ARCHIVADO` | Grupo cerrado |

`grupo_estudio.estado` pasa a admitir los tres valores. No se agregan columnas.

### Endpoints

| Método | Ruta | Rol | Uso |
| --- | --- | --- | --- |
| `POST` | `/api/teams` | `COACH` | Crear grupo publicado, o borrador con `"draft": true` |
| `PATCH` | `/api/teams/{id}/draft` | `COACH` responsable | Guardar cambios de un borrador |
| `POST` | `/api/teams/{id}/publish` | `COACH` responsable | Validar y publicar el borrador |

### Reglas

- Con `draft: true` solo se exige `name`; el resto de campos puede quedar `null`.
- Sin `draft`, el grupo se crea `ACTIVO` y debe incluir nombre, nivel esperado y cupo entre 1 y 1000.
- `PATCH /teams/{id}/draft` solo funciona sobre grupos `BORRADOR`; si ya está publicado responde `409`.
- `POST /teams/{id}/publish` exige nombre, nivel esperado, cupo válido y visibilidad distinta de `ARCHIVADO`.
  Si el borrador no está completo responde `400`; si ya está `ACTIVO` o `ARCHIVADO`, `409`.
- Un grupo `BORRADOR` no aparece en `GET /api/teams` y `POST /api/teams/{id}/join` responde `409`.
- El coach sí ve sus borradores en `GET /api/teams/me`, con su estado y su código de invitación.

### Flujo

```text
POST /teams {"name": "...", "draft": true}
        → 201 status = BORRADOR
PATCH /teams/{id}/draft {...}
        → 200 status = BORRADOR
POST /teams/{id}/publish
        → 200 status = ACTIVO   (y ya se lista en GET /teams)
```

## US06 — Aclaración al postulante

### Modelo

`MembershipStatus` agrega `ACLARACION_SOLICITADA`. `MembershipDecision` agrega `SOLICITAR_ACLARACION`.

La aclaración se guarda en la misma fila de `practicante_grupo`, en cuatro columnas opcionales:

| Columna | Tipo | Uso |
| --- | --- | --- |
| `aclaracion_solicitada` | `VARCHAR(500)` | Pregunta del coach |
| `fecha_aclaracion` | `TIMESTAMPTZ` | Momento de la pregunta |
| `aclaracion_respuesta` | `VARCHAR(500)` | Respuesta del postulante |
| `fecha_respuesta_aclaracion` | `TIMESTAMPTZ` | Momento de la respuesta |

No se crea una tabla `solicitud_grupo` ni una tabla de mensajes.

### Endpoints

| Método | Ruta | Rol | Uso |
| --- | --- | --- | --- |
| `PATCH` | `/api/teams/{id}/memberships/{memberId}` | `COACH` responsable | `ACEPTAR`, `RECHAZAR` o `SOLICITAR_ACLARACION` |
| `GET` | `/api/teams/{id}/memberships?status=...` | `COACH` responsable | Listar `PENDIENTE` o `ACLARACION_SOLICITADA` |
| `POST` | `/api/teams/{id}/memberships/{memberId}/clarification` | `PRACTICANTE` dueño | Responder la aclaración |

### Reglas

- `SOLICITAR_ACLARACION` exige `message` (máximo 500 caracteres) y solo aplica a solicitudes `PENDIENTE`.
- La membresía pasa a `ACLARACION_SOLICITADA`; el listado de `PENDIENTE` deja de mostrarla y aparece en
  `ACLARACION_SOLICITADA`.
- Mientras no haya respuesta, `ACEPTAR` y `RECHAZAR` responden `409`; tampoco se puede pedir una segunda
  aclaración sobre la misma solicitud.
- Solo el practicante dueño de la membresía puede responder; otro practicante recibe `403`.
- Responder exige estado `ACLARACION_SOLICITADA` y un mensaje no vacío; la solicitud vuelve a `PENDIENTE`.
- La pregunta se conserva después de la respuesta, para que el coach tenga contexto al decidir.
- Si el practicante vuelve a solicitar ingreso tras un rechazo o retiro, se limpian las columnas de aclaración.
- Los contadores de `GET /api/teams/me` del coach cuentan como pendientes tanto `PENDIENTE` como
  `ACLARACION_SOLICITADA`.

### Flujo

```text
Postulante: POST /teams/{id}/join                 → PENDIENTE
Coach:      PATCH .../memberships/{mid}
            {"decision":"SOLICITAR_ACLARACION","message":"..."}
                                                  → ACLARACION_SOLICITADA
Postulante: GET /teams/me                         → ve la pregunta
Postulante: POST .../memberships/{mid}/clarification {"message":"..."}
                                                  → PENDIENTE (con respuesta guardada)
Coach:      PATCH .../memberships/{mid} {"decision":"ACEPTAR"}
                                                  → ACTIVO
```

## Persistencia y migración

`assets/migracion_borrador_aclaracion.sql`:

1. recrea `grupo_estudio_estado_check` con `BORRADOR`, `ACTIVO`, `ARCHIVADO`;
2. recrea `practicante_grupo_estado_check` con `ACLARACION_SOLICITADA`;
3. agrega las cuatro columnas de aclaración con `ADD COLUMN IF NOT EXISTS`.

`assets/migracion_estados_membresia.sql` se mantiene como migración de estados y ahora también incluye
`ACLARACION_SOLICITADA`, para que ejecutarla después no quite el valor.

`ddl-auto: update` no modifica restricciones CHECK existentes; la migración es obligatoria en bases ya creadas.

## Alineación con el ERD y pruebas

- `assets/oficial.erd` documenta los nuevos valores y columnas de `practicante_grupo`.
- `ErdSchemaIntegrationTests` incluye las columnas nuevas en el modelo esperado.
- `OficialErdFileTests` valida que el archivo `.erd` coincida con ese modelo.
- Pruebas unitarias y de controller cubren: borrador con solo nombre, borrador incompleto, publicación,
  edición de un grupo publicado (409), coach ajeno, aclaración sin mensaje, aclaración duplicada, decisión
  antes de la respuesta (409), respuesta del postulante y respuesta de un tercero (403).
- `TeamsIntegrationTests` agrega tres escenarios HTTP contra PostgreSQL: borrador oculto hasta publicar,
  flujo completo de aclaración y postulante ajeno.
