# US-11 — Ranking interno de integrantes por equipo

## Alcance implementado

`GET /api/analytics/teams/{teamId}/standings` consulta el ranking con datos persistidos mediante Spring Data JPA. El controller usa el contexto `/api` configurado, sin repetirlo en su mapping. Se agrega únicamente la tabla aprobada `ranking_equipo_actual` para conservar el último resultado válido. No se agregan dependencias ni cambios de autenticación global.

Se conserva el cálculo existente de `RankingService`, sus DTOs y sus 48 pruebas. `TeamRankingService` obtiene el usuario desde `CurrentUserResolver`, autoriza la consulta y adapta las proyecciones persistidas al cálculo. `AnalyticsExceptionHandler` mantiene el formato `{ "message": "...", "errors": {} }` y limita su alcance a `AnalyticsController`.

## Consultas y autorización

Las consultas permanecen en los módulos propietarios y se exponen por sus servicios públicos:

- `StudyGroupService.findSummaryById`: equipo y usuario del coach responsable.
- `GroupMembershipService.findMembersByTeamId`: todas las membresías, incluidas las inactivas, con usuario, nombre y estado.
- `ProblemResolutionService.findResolutionsByTeamId`: resoluciones, membresía, competencia, problema de catálogo y equipos de ambos extremos.

Se permite consultar al coach responsable o a un practicante con membresía `ACTIVO` en el equipo. La cuenta debe estar `ACTIVO`. La identidad procede de la sesión HTTP existente, nunca de parámetros del cliente. Las resoluciones se leen después de autorizar al solicitante.

La consulta de resoluciones incluye filas cuya membresía **o** competencia pertenece al equipo solicitado. Antes del cálculo, ambas relaciones deben coincidir con ese equipo. Así se detectan referencias cruzadas desde cualquiera de los equipos afectados, en lugar de ocultarlas mediante un filtro que produciría resultados parciales. Los `left join` permiten detectar relaciones incompletas; no hay joins a colecciones que multipliquen filas.

La lectura y el cálculo se ejecutan en una transacción de lectura con aislamiento `REPEATABLE_READ`, para obtener equipos, membresías y resoluciones del mismo snapshot PostgreSQL. No pagina ni limita silenciosamente los registros. `StoredTeamRankingService` coordina la conservación una vez que esa transacción ha finalizado correctamente.

## Contrato interno y cálculo

`calculate(teamId, members, resolutions)` recibe el conjunto completo de membresías del equipo y todas sus resoluciones. Los DTOs de entrada son proyecciones internas, no requests HTTP:

- `RankingMemberData`: membresía, equipo, usuario, nombre y estado.
- `RankingResolutionData`: resolución, equipo de la competencia, membresía, problema de catálogo y veredicto.
- `TeamRankingResponse`: equipo, estado, criterios y posiciones de integrantes activos.

Reglas conservadas:

1. Cada `problema.id` con veredicto `ACCEPTED` cuenta una vez por membresía en el equipo consultado, aunque aparezca en varias competencias. No se deduplica por `competencia_problema.id`.
2. Cada cálculo conserva el contexto del equipo. Las resoluciones de otros equipos no suman puntos y las referencias cruzadas se rechazan.
3. La puntuación es la cantidad de problemas distintos aceptados. No se aplican tiempos, penalizaciones ni puntos de competencias.
4. Solo aparecen membresías `ACTIVO`; `RETIRADO` y `EXPULSADO` se validan, pero no reciben posiciones. Los integrantes activos sin aceptaciones tienen cero puntos cuando existe actividad registrada válida. La consulta de membresías (`GroupMembershipRepository.findMembersByTeamId`) solo devuelve esos tres estados: las filas de `practicante_grupo` con cualquier otro estado (por ejemplo, solicitudes de ingreso pendientes o rechazadas que agregue US-05/US-06) no son integrantes, no se materializan como enum y no alteran ni invalidan el ranking.
5. Orden descendente por puntuación. Empates con posiciones compartidas y saltos: `1, 1, 3`. Dentro del empate se ordena por ID de membresía para estabilizar la presentación, sin desempatar posiciones.
6. Los criterios se comunican como `DISTINCT_ACCEPTED_PROBLEMS_DESC` y `SHARED_POSITION_1_1_3`.
7. Sin resoluciones registradas se devuelve `NO_ACTIVITY` con lista vacía. Con intentos válidos pero sin aceptaciones se devuelve `CALCULATED` con puntuaciones cero compartidas. `PENDIENTE` es actividad registrada, sin sumar puntos. La actividad histórica de miembros retirados se valida; si no quedan miembros activos, la lista calculada queda vacía.

## Validación y errores

Se valida todo el conjunto antes de devolver posiciones. Una colección vacía significa ausencia conocida de registros; `null` significa información no disponible. Los errores de lectura no se convierten en colecciones vacías.

Se rechazan identificadores ausentes/no positivos, nombres ausentes, estados/veredictos inválidos, integrantes duplicados, membresías ajenas al equipo, resoluciones sin membresía conocida y datos contradictorios para el mismo ID de resolución. Repetir exactamente una resolución o el mismo problema aceptado no aumenta la puntuación. Las colecciones de entrada no se modifican y la lista de respuesta es inmutable.

| HTTP | Situación |
| --- | --- |
| `200` | Ranking calculado o ausencia explícita de actividad (`NO_ACTIVITY`). |
| `400` | Identificador de equipo no entero o no positivo. |
| `401` | Sesión no autenticada. |
| `403` | Cuenta suspendida, integrante inactivo o usuario ajeno al equipo. |
| `404` | Equipo inexistente para una solicitud autenticada. |
| `409` | Datos incompletos o inconsistentes detectados; `errors` identifica el campo/índice afectado. |
| `503` | Información no disponible por fallo de persistencia o de transacción. |
| `500` | Error inesperado de cálculo. |

Los errores no contienen posiciones parciales del cálculo fallido ni detalles SQL. Si JPA no puede materializar un enum persistido inválido, se trata como fallo de persistencia (`503`); en ese caso no se conoce el índice de la fila afectada. Las validaciones detienen el cálculo en la primera inconsistencia.

## Conservación del último ranking válido

`ranking_equipo_actual` contiene solo una fila por equipo: PK/FK `grupo_id`, `calculado_en` y `resultado` JSONB. Su entidad y repositorio pertenecen a `analytics`. El resultado permanece en PostgreSQL tras reiniciar la aplicación. El modelo y DDL equivalente están documentados en `04-database-model.md` como extensión aprobada del ERD.

Después de un cálculo completo y de finalizar su transacción de lectura, `RankingSnapshotService` guarda la respuesta en una transacción independiente. Un resultado válido `NO_ACTIVITY` también sustituye al anterior, porque representa ausencia conocida de registros. Los errores nunca se escriben. Conservar el resultado es un respaldo secundario: si el guardado falla (persistencia, transacción o serialización), se registra una advertencia en el log y la respuesta `200` con el ranking ya calculado se entrega igualmente; el fallo del respaldo nunca convierte un ranking válido en un error. La fecha corresponde al inicio de la consulta; el upsert solo reemplaza una fecha anterior, evitando que una consulta iniciada antes sobrescriba otra más reciente al terminar tarde. Este orden depende de los relojes de aplicación; varias instancias deben tener sus relojes sincronizados.

Ante `RankingDataException`, fallo de persistencia o fallo de transacción, se realiza otra lectura de autorización reutilizando las mismas validaciones y `CurrentUserResolver`. Solo si esa autorización vigente termina correctamente se intenta recuperar el resultado guardado en otra transacción. Así se evita consultar desde una transacción que PostgreSQL ya haya abortado. Una membresía revocada, cuenta suspendida o equipo eliminado no recibe resultados guardados.

La respuesta conserva el HTTP `409` o `503`, `message` y `errors`. Si se puede recuperar el resultado, añade `lastValidRanking`, claramente separado del cálculo fallido:

```json
{
  "message": "No se pudo calcular un ranking válido",
  "errors": { "resolutions[0].teamId": "La membresía y la competencia deben pertenecer al equipo consultado" },
  "lastValidRanking": {
    "calculatedAt": "2026-10-09T12:00:00Z",
    "ranking": {
      "teamId": 1,
      "status": "CALCULATED",
      "orderingCriterion": "DISTINCT_ACCEPTED_PROBLEMS_DESC",
      "tieCriterion": "SHARED_POSITION_1_1_3",
      "members": [
        { "membershipId": 1, "userId": 10, "fullName": "Usuario Prueba", "acceptedProblems": 2, "position": 1 }
      ]
    }
  }
}
```

Si no existe un resultado previo, falla su lectura o no se puede verificar la autorización, no se incluye `lastValidRanking`. Una caída total de PostgreSQL impide leer permisos y resultados: se devuelve el error y se conserva la fila para cuando el servicio se recupere. No se usan permisos antiguos ni una caché de usuarios. El resultado guardado muestra los integrantes del momento indicado, sin recalcular posiciones parciales con datos actuales. Las respuestas `200` conservan su contrato anterior.

## Reutilización y pendientes

Las proyecciones y consultas de equipos, membresías y resoluciones pueden reutilizarse para US-12; no se implementan indicadores por tema. No se registran resultados oficiales de US-13. Se conserva el contexto independiente necesario para este ranking, sin desarrollar los flujos ni estadísticas de US-14.

La conservación mínima del último ranking válido está implementada con el contrato aprobado. No se implementa historial ni caché en memoria. Queda pendiente ejecutar sus pruebas contra PostgreSQL real; no se ha aplicado el esquema a una base compartida.

Los endpoints de carga/gestión de equipos, membresías y resoluciones de US-04/06/07/09 siguen siendo responsabilidad de sus módulos. US-11 consulta los datos persistidos existentes; no crea registros para suplir esas funcionalidades.

## Verificación de la integración inicial — 2026-10-09

- Los siete archivos previos se respaldaron fuera del repositorio y sus SHA-256 se comprobaron antes y después del `git merge --ff-only develop` autorizado. Se mantuvo la rama `feature/US11-ranking-interno-equipos`.
- `clean compile`: correcto con Java 21 y Maven 3.9.16, 111 archivos de producción.
- Suite Maven: **256 casos contabilizados, 207 ejecutados correctamente, 0 fallos, 0 errores y 49 omitidos** por falta de configuración PostgreSQL. Incluye 82 pruebas de cálculo, servicio y HTTP de ranking, además de las pruebas existentes y de límites de runtime.
- El arranque de contexto valida las consultas JPQL, pero no prueba su ejecución SQL. Se desactivaron DDL, inicialización SQL y acceso a metadatos JDBC solo mediante parámetros de esa ejecución, sin cambiar la configuración del proyecto.
- `RankingIntegrationTests` está preparada y fue omitida por ausencia de `RANKING_TEST_DB_URL`. No se ha validado contra PostgreSQL real. Las demás suites PostgreSQL también se omitieron por sus condiciones opt-in.
- El wrapper falló antes de iniciar Maven por acceso denegado al preparar su caché. Se utilizó Maven 3.9.16 ya disponible en la caché, en modo offline y con el repositorio local del usuario. No se modificaron wrapper, POM ni dependencias.
- El entorno restringido bloqueó conexiones HTTP locales de las pruebas existentes de Codeforces. La ejecución final se realizó con acceso local permitido y terminó correctamente.

Para ejecutar pruebas unitarias y HTTP con un wrapper operativo:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd '-Dtest=RankingServiceTests,TeamRankingServiceTests,AnalyticsControllerTests' test
```

Para reproducir la suite disponible sin PostgreSQL (las suites opt-in permanecen omitidas si no se configuran):

```powershell
.\mvnw.cmd '-Dspring.jpa.hibernate.ddl-auto=none' '-Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false' '-Dspring.sql.init.mode=never' test
```

La integración requiere `RANKING_TEST_DB_URL`, `RANKING_TEST_DB_USER` y, cuando corresponda, `RANKING_TEST_DB_PASSWORD`, apuntando a una base exclusiva de pruebas. Crea un schema aleatorio `ranking_test_<uuid>`, ejecuta login real por HTTP y elimina solo ese schema al terminar. Una interrupción puede requerir limpieza manual. Ver `tests/README.md`.

## Verificación de conservación del último resultado — 2026-10-09

- `clean compile`: correcto, 117 archivos de producción, Java 21 y Maven 3.9.16.
- Suite final: **278 casos contabilizados, 226 ejecutados correctamente, 0 fallos, 0 errores y 52 omitidos**. Se usaron los mismos parámetros de DDL/metadatos desactivados indicados arriba.
- Incluye 101 casos de ranking: cálculo original, autorización original, HTTP, serialización/recuperación y coordinación de transacciones. Los nuevos casos comprueban errores con y sin resultado previo, permisos revocados, autorización no disponible, lectura/escritura fallida del resultado guardado, preservación del error y sustitución por un resultado válido sin actividad.
- Las pruebas PostgreSQL preparadas incluyen el ciclo válido → inconsistente → corregido, una única fila por equipo, rechazo de escrituras anteriores y acceso denegado tras revocar una membresía. `RankingIntegrationTests` contabilizó 13 omitidos; no se ejecutaron SQL ni DDL de la tabla nueva porque falta `RANKING_TEST_DB_URL`. La suite del esquema también permanece omitida por falta de `ERD_TEST_DB_URL`.
- `RuntimeBoundaryTests` registra explícitamente la nueva entidad y repositorio. `ErdSchemaIntegrationTests` conserva sus comprobaciones anteriores y agrega columnas, tipos, PK y FK de la extensión aprobada.
- El wrapper sigue fallando antes de iniciar Maven por acceso denegado a su caché. Se ejecutó el Maven ya instalado en modo offline, sin modificar el wrapper, POM ni dependencias.
- Sin commits, push, merges ni cambios de rama durante esta ampliación. La tabla aún debe crearse y verificarse en PostgreSQL real antes de considerar validada la persistencia.
