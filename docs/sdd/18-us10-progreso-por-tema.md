# US-10 — Progreso por tema

## Alcance

`GET /api/analytics/teams/{teamId}/progress/me/topics`: el practicante consulta cuánto ha resuelto de cada tema en uno de
sus equipos, para identificar fortalezas y temas por reforzar. Es el avance **personal** de la tarjeta US-10; el coach ya
dispone del reporte del equipo por tema en [US-12](14-us12-temas-menor-resolucion.md). El contrato HTTP está en
`03-api-contracts.md`.

## Decisiones aprobadas

- **Alcance por equipo.** Igual que US-14, el avance de cada equipo es independiente y el practicante elige el contexto con
  `teamId`. No hay un resumen que mezcle equipos.
- **Temas.** Se muestran los temas de los problemas asignados al equipo. Los que el practicante aún no ha tocado salen con
  estado `SIN_ACTIVIDAD`, que es el estado inicial del tercer escenario de la tarjeta.
- **Temas a reforzar.** Se marcan con `needsReinforcement` los temas con la menor cobertura (todos los empatados), siempre
  que sea menor que 100%. Es la comparación exacta de proporciones de US-12, así que el coach y el practicante ven el mismo
  criterio.
- **Acceso.** Solo el practicante, su propio avance: cuenta `ACTIVO`, rol `PRACTICANTE` y membresía `ACTIVO` en el equipo.

## Reglas de cálculo

- Universo: todos los problemas asignados al equipo, sea cual sea el estado de la competencia.
- Un problema cuenta una sola vez por tema, aunque se asigne en varias competencias o tenga varios temas.
- Resuelto: al menos un intento `ACCEPTED` del practicante. Es la misma regla de US-11 y US-14: el `solvedProblems` total
  coincide con `acceptedProblems` de `progress/me`. Un registro manual provisional cuenta, con la limitación ya documentada
  en [US-14](16-us14-avance-independiente.md).
- `pendingProblems`: problemas con un intento `PENDIENTE` y sin ninguno aceptado.
- Estado por tema: `COMPLETADO` si todos los problemas están resueltos; `SIN_ACTIVIDAD` si no hay ningún intento (de
  cualquier veredicto) en sus problemas; `EN_PROGRESO` en los demás casos.
- Cobertura: `100 × resueltos / asignados` con dos decimales. El redondeo es solo de presentación; la comparación usa
  proporciones exactas.
- Orden: de menor a mayor cobertura y, con la misma, por nombre del tema.
- Un problema sin tema no hace fallar la consulta: se cuenta en `unclassifiedProblems` y no aparece en ningún tema. Hace
  falta porque el catálogo permite crear problemas sin temas y todavía no hay forma de agregárselos después.
- Un equipo sin problemas asignados devuelve `200` con `topics: []`.

## Errores

Cuerpo `{ "message": "...", "errors": {} }`. `400` identificador no numérico o no positivo; `401` sin token; `403` rol
distinto de `PRACTICANTE`, cuenta no activa o sin membresía `ACTIVO`; `404` equipo inexistente.

Si los datos son inconsistentes (un intento que no corresponde a un problema asignado, intentos contradictorios con el mismo
id, un tema de un problema que no es del equipo, nombres de tema contradictorios) responde `409` y `errors` nombra el dato
afectado, por ejemplo `attempts[0].competitionProblemId`. No se devuelve ningún indicador para no mostrar valores
incorrectos. Si la información no está disponible responde `503` con un mensaje que invita a reintentar. La consulta no
escribe nada, así que repetirla es seguro.

## Datos y persistencia

No agrega tablas, columnas ni restricciones: usa `problema_tema`, `competencia_problema`, `resolucion_problema` y
`practicante_grupo` tal como están.

## Pruebas

- `TopicProgressCalculatorTests` (cálculo, estados, empates, problemas sin tema y datos inconsistentes),
  `TopicProgressServiceTests` (autorización y lectura de datos) y `TopicProgressControllerTests` (contrato HTTP y errores).
- `AssignmentsIntegrationTests` (PostgreSQL): el recorrido real, el avance independiente de otro integrante y los accesos
  denegados.
- `SecurityIntegrationTests` y `RuntimeBoundaryTests`: `401` sin token, `403` para el coach y ruta publicada.
- Postman: `US07-US14-flujo` y `SEC-jwt-roles`.

## Verificación — 2026-10-10

Suite completa sobre una base temporal: 1029 pruebas, 0 fallos, 0 omitidas. Las colecciones Postman de seguridad y
del flujo pasan contra la aplicación real.
