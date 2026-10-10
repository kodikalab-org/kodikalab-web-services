# US-12 — Reporte de temas con menor resolución del equipo

## Alcance y reglas aprobadas

Backend de consulta en `GET /api/analytics/teams/{teamId}/weaknesses`, sin nuevas tablas, entidades, dependencias ni cambios en US-11. La identidad procede de `CurrentUserResolver`: solo el coach responsable del equipo, con cuenta `ACTIVO`, puede consultar. El rol o ID enviados por el cliente no determinan permisos.

La métrica aprobada es la cobertura de resolución del equipo por tema:

```text
100 × problemas distintos aceptados del tema / problemas distintos asignados del tema
```

Se utilizan competencias con estado `FINALIZADA`: existe en `CompetitionStatus` junto con `PROGRAMADA` y `EN_CURSO` y representa el estado concluido del modelo actual. Se respeta ese estado persistido; no se infiere un cambio de estado a partir de la fecha. El universo no tiene un filtro temporal adicional. Incluye membresías actualmente `ACTIVO`, incluso si el integrante se incorporó después de alguna competencia histórica, conforme a la población aprobada.

Una aceptación `ACCEPTED` de cualquier integrante incluido cuenta como problema resuelto para el equipo. La clave de deduplicación es el problema del catálogo, no la asignación ni el intento. Un problema repetido en varias competencias finalizadas cuenta una vez. Un problema con varios temas participa una vez en cada tema. Los totales de problemas por tema no deben sumarse como un total de problemas únicos del equipo.

Esto mide cobertura colectiva sobre el catálogo incluido, no dominio individual ni porcentaje de intentos correctos. No utiliza penalizaciones, tiempos, puntajes de competencias ni métricas de otras historias.

## Comparación, equivalencias y prioridad

Se comparan proporciones exactas mediante productos cruzados enteros. `1/3` y `2/6` empatan aunque sus muestras sean distintas. Dos porcentajes mostrados iguales por redondeo no se consideran equivalentes si sus proporciones exactas difieren. El porcentaje de presentación tiene dos decimales con `HALF_UP`; ese redondeo no interviene en la comparación.

La respuesta incluye todos los temas analizados, ordenados por proporción ascendente. Todos los temas con el mínimo exacto reciben `lowestCoverage = true`. El ID de tema solo estabiliza la presentación dentro del empate. No se aplica un umbral de debilidad, una cuota de temas ni un desempate adicional. Si todos tienen cobertura completa, todos comparten el mínimo relativo; eso no afirma que exista un déficit de resolución.

Por tema se muestran:

- `assignedProblems`: problemas distintos asignados en competencias finalizadas.
- `solvedProblems`: problemas distintos con alguna aceptación de los integrantes activos.
- `unsolvedProblems`: diferencia entre los dos anteriores; incluye problemas sin intentos y con intentos pendientes sin aceptación.
- `solvingMembers`: integrantes activos distintos con alguna aceptación en el tema. Un integrante se cuenta una vez aunque resuelva varios problemas.
- `pendingResolutions`: intentos `PENDIENTE` con IDs distintos dentro del ámbito analizado.
- `coveragePercentage` y `lowestCoverage`.

El coach puede priorizar utilizando el volumen sin resolver, la cobertura y la participación, sin que el backend invente ponderaciones. `comparisonCriterion` y `comparisonExplanation` comunican la comparación exacta y la inclusión de todos los empates.

## Pendientes e información insuficiente

`PENDIENTE` no cuenta como aceptación ni invalida automáticamente el reporte. Se informa mediante `pendingResolutions` global y por tema. Una repetición exacta de la misma resolución no duplica el recuento. Con problemas multitema, un mismo intento puede estar en varios recuentos por tema; el total global lo cuenta una vez.

Se evita emitir conclusiones cuando no hay integrantes activos, problemas asignados en competencias finalizadas o ninguna resolución definitiva de los integrantes activos en esas competencias. Si solo existen pendientes, el error indica su cantidad. Los veredictos definitivos sin aceptaciones constituyen información suficiente para una cobertura conocida de cero. No se exige una muestra mínima arbitraria.

Con información suficiente en el conjunto, un tema sin aceptaciones, e incluso sin intentos, aparece con cobertura cero. Construir el denominador desde los problemas asignados evita omitir esos temas. Miembros retirados/expulsados y competencias programadas/en curso no aportan aceptaciones, pendientes ni evidencia de actividad suficiente.

## Integración y consistencia

`TeamTopicReportService` autoriza antes de leer datos de rendimiento y coordina los servicios públicos existentes:

- `StudyGroupService`: equipo y coach responsable.
- `GroupMembershipService`: todas las membresías, para validar también referencias históricas.
- `CompetitionProblemService.findAssignedProblemsByTeamId`: proyección de asignación, competencia, problema, equipo y estado.
- `ProblemResolutionService.findResolutionsByTeamId`: consulta existente de US-11, reutilizada sin modificaciones. Incluye resoluciones asociadas al equipo desde la membresía o desde la competencia, para detectar referencias cruzadas.
- `ProblemTopicService.findTopicsByProblemIds`: clasificación de los problemas incluidos, sin unirla a resoluciones y multiplicar los intentos.

Las dos consultas nuevas permanecen en sus repositorios propietarios y solo agregan lecturas. Una lista de problemas vacía evita ejecutar un `IN` vacío. No hay paginación ni recortes silenciosos del universo.

`TopicCoverageCalculator` valida y calcula sin acceder a repositorios. Se validan identificadores, estados, miembros duplicados, referencias de resolución, coincidencia de ambos equipos, pertenencia a la asignación, veredictos, duplicados contradictorios y clasificaciones. Se revisa la integridad de todas las filas recuperadas del equipo antes de filtrar su contribución por estado de competencia/membresía; una referencia cruzada no se oculta por ser de una actividad fuera del cálculo.

Todo problema incluido debe tener al menos un tema válido. Si falta, se señalan sus IDs y se evita emitir un reporte parcial. Una colección `null` representa información no disponible; no se sustituye por una colección vacía. Los errores JPA al materializar enums u otras filas se propagan como indisponibilidad, sin publicar conclusiones.

La lectura y el cálculo usan una transacción `readOnly` con aislamiento `REPEATABLE_READ`, consistente para las consultas de PostgreSQL. El reporte no escribe datos ni conserva estado entre peticiones ni utiliza la tabla de rankings de US-11.

`TopicReportController` y su handler tienen alcance propio para preservar los archivos, autorización y contratos del ranking. El context path `/api` permanece en la configuración global.

## Errores y reintento

Todos los errores conservan el formato `{ "message": "...", "errors": {} }`, sin temas calculados parcialmente ni detalles SQL:

| HTTP | Situación |
| --- | --- |
| `400` | Identificador de equipo inválido. |
| `401` | Sesión ausente o no autenticada. |
| `403` | Cuenta suspendida, rol distinto de coach o coach ajeno al equipo. |
| `404` | Equipo inexistente para una solicitud de coach autenticado. |
| `409` | Datos insuficientes o inconsistentes; `errors` identifica la información afectada. |
| `503` | Error de acceso a datos o de transacción. |
| `500` | Error inesperado. |

Corregidos los datos o recuperada su disponibilidad, el coach puede repetir el mismo GET. No existe un trabajo en segundo plano ni un endpoint de reintento adicional. No se conserva un reporte anterior: ese requisito no forma parte de US-12.

Contrato JSON y ejemplo: [03-api-contracts.md](03-api-contracts.md). Comandos de pruebas: [tests/README.md](../../tests/README.md).

## Verificación

Las pruebas de cálculo cubren consolidación, temas sin aceptaciones, múltiples temas, proporciones equivalentes, falsos empates por redondeo, integrantes inactivos, actividad incompleta, pendientes, datos inconsistentes e indisponibles. Las pruebas del servicio cubren autorización y reintento; las HTTP cubren el contrato y los errores. La suite PostgreSQL está preparada con schema aislado y login real.

La integración requiere `TOPIC_REPORT_TEST_DB_URL`, `TOPIC_REPORT_TEST_DB_USER` y, si corresponde, `TOPIC_REPORT_TEST_DB_PASSWORD`, con permisos para crear/eliminar un schema de pruebas. No usar una base compartida ni modificar `.env`.

### Resultados — 2026-10-09

- Compilación `clean compile`: correcta, 125 archivos de producción con Java 21 y Maven 3.9.16.
- Suite final: **330 casos contabilizados, 270 ejecutados correctamente, 0 fallos, 0 errores y 60 omitidos**.
- US-12: 23 casos de `TopicCoverageCalculatorTests`, 10 de `TeamTopicReportServiceTests` y 11 de `TopicReportControllerTests`, todos aprobados. `TopicReportIntegrationTests` contabilizó 8 omitidos por ausencia de `TOPIC_REPORT_TEST_DB_URL`; no se verificó SQL real.
- Las pruebas existentes, incluidas las de US-11, pasaron. `RuntimeBoundaryTests` agrega solo la ruta implementada; conserva sus expectativas de entidades/repositorios y de endpoints inexistentes. Los archivos existentes de US-11 no se modificaron.
- Spring arrancó y validó las consultas JPQL nuevas. En esa ejecución se desactivaron DDL, inicialización SQL y metadatos JDBC mediante parámetros, sin cambiar la configuración del proyecto. Esto no demuestra ejecución de las consultas contra PostgreSQL.
- El wrapper falló antes de arrancar Maven por acceso denegado al preparar su caché. Se usó directamente Maven 3.9.16 ya disponible en la caché, offline y con el repositorio Maven del usuario; sin cambios al wrapper, POM ni dependencias. Las pruebas existentes de HTTP local se ejecutaron con acceso local permitido.
- La primera ejecución encontró un error en la preparación del mock de reintento; se corrigió usando `doReturn` para sustituir un comportamiento que lanzaba una excepción. La ejecución final completa terminó correctamente.
- No se ejecutaron DDL, commits, push, merges, rebases ni cambios de rama. La validación contra PostgreSQL aislado queda pendiente.

Comandos reproducibles con un wrapper operativo:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd '-Dtest=TopicCoverageCalculatorTests,TeamTopicReportServiceTests,TopicReportControllerTests' test
.\mvnw.cmd '-Dspring.jpa.hibernate.ddl-auto=none' '-Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false' '-Dspring.sql.init.mode=never' test
```

El último comando comprueba la suite disponible sin una conexión configurada; las suites PostgreSQL opt-in permanecen omitidas mientras no se definan sus variables. Para validar SQL usar el comando de integración documentado en `tests/README.md`.
