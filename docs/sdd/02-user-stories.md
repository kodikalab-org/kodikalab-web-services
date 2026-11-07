# 02 - User Stories

## Relación con el ERD vigente

Las historias funcionales se conservan. El modelo físico de referencia es `assets/oficial.erd`; actualizarlo no equivale a implementar nuevas historias ni a completar criterios sin contrato técnico.

- US01/US02 usan la cuenta base `usuario`, con roles SQL `COACH`/`PRACTICANTE` y estados `ACTIVO`/`SUSPENDIDO`; ver `03-api-contracts.md` para la correspondencia Java/HTTP.
- US03 implementa el perfil del rol de la sesión: `practicante` (datos académicos, nivel y handles de Codeforces/AtCoder/VJudge) y `coach` (especialidad, organización, experiencia y presentación). No se crean perfiles con datos ficticios durante el registro base.
- US05/US06 (solicitud de ingreso, aceptación/rechazo y pedido de aclaración) no usan una tabla de solicitudes propia: una solicitud es una fila de `practicante_grupo` en estado `PENDIENTE`, y la aclaración se guarda en esa misma fila.
- Equipos, competencias y catálogo se describen con las tablas oficiales nuevas, pero su código se adapta únicamente en la tarea del módulo correspondiente.
- El escenario de recuperación de acceso de US02 sigue pendiente de contrato, no se da por completado por tener login.

## Sprint 1

### US01 - Registro con rol

Como usuario nuevo, quiero registrarme indicando mi rol para acceder a las funcionalidades correspondientes.

### US02 - Inicio de sesión seguro

Como usuario registrado, quiero iniciar sesión de forma segura para acceder a mi cuenta.

### US03 - Perfil y vinculación de plataformas

Como usuario, quiero gestionar mi perfil y vincular mis plataformas externas para centralizar mi información competitiva.

### US04 - Creación de grupo

Como coach, quiero crear un grupo y poder guardarlo como borrador para practicar programación competitiva con otros integrantes.

Criterios implementados:
- `POST /api/teams` con `"draft": true` guarda un borrador con solo el nombre; sin la bandera el grupo se publica completo.
- `PATCH /api/teams/{id}/draft` guarda cambios del borrador (solo el coach responsable).
- `POST /api/teams/{id}/publish` valida nombre, nivel esperado y cupo, y publica el grupo.
- Un borrador no aparece en `GET /api/teams` y nadie puede solicitar ingreso hasta que se publique.

### US05 - Solicitud de ingreso

Como usuario, quiero solicitar ingreso a un grupo para participar con sus miembros.

### US06 - Aceptación/rechazo de postulantes

Como administrador o responsable de grupo, quiero aceptar o rechazar postulantes, o pedirle una aclaración al postulante, para controlar la membresía del equipo.

Criterios implementados:
- `PATCH /api/teams/{id}/memberships/{memberId}` acepta `ACEPTAR`, `RECHAZAR` o `SOLICITAR_ACLARACION`; esta última exige un `message`.
- La solicitud pasa a `ACLARACION_SOLICITADA` y el coach no puede aceptar ni rechazar hasta que el postulante responda.
- `POST /api/teams/{id}/memberships/{memberId}/clarification` permite al postulante dueño de la solicitud responder; vuelve a `PENDIENTE`.
- El coach consulta las solicitudes por estado (`PENDIENTE` o `ACLARACION_SOLICITADA`) y ve la pregunta y la respuesta en `/api/teams/me` y en el listado.

### US07 - Asignación de problemas

Como responsable de equipo, quiero asignar problemas a los integrantes para guiar su práctica.

### US08 - Visualización de problemas asignados

Como usuario, quiero visualizar mis problemas asignados para saber qué debo resolver.

### US09 - Registro de problemas resueltos

Como usuario, quiero registrar problemas resueltos para mantener actualizado mi avance.

### US10 - Progreso por tema

Como usuario o responsable de equipo, quiero consultar el progreso por tema para identificar fortalezas y debilidades.

### US11 - Ranking interno

Como integrante de un equipo, quiero ver el ranking interno para comparar mi desempeño.

### US12 - Reporte de temas con menor resolución

Como responsable de equipo, quiero conocer los temas con menor resolución para planificar refuerzos.

### US13 - Resultados en competencias

Como equipo, queremos registrar resultados de competencias para medir desempeño histórico.

### US14 - Avance independiente multi-equipo

Como usuario que pertenece a varios equipos, quiero consultar mi avance independiente para separar mi progreso personal.

### US15-US18 - Asistente IA

Como usuario, quiero consultar un asistente inteligente para recibir orientación, explicación o recomendaciones.

### US19 - Acceso a libros/recursos

Como usuario, quiero acceder a recursos relacionados con problemas o temas para reforzar mi aprendizaje.
