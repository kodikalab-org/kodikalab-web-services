# 02 - User Stories

## Relación con el ERD vigente

Las historias funcionales se conservan. El modelo físico de referencia es `assets/oficial.erd`; actualizarlo no equivale a implementar nuevas historias ni a completar criterios sin contrato técnico.

- US01/US02 usan la cuenta base `usuario`, con roles SQL `COACH`/`PRACTICANTE` y estados `ACTIVO`/`SUSPENDIDO`; ver `03-api-contracts.md` para la correspondencia Java/HTTP.
- US03 implementa el perfil del rol de la sesión: `practicante` (datos académicos, nivel y handles de Codeforces/AtCoder/VJudge) y `coach` (especialidad, organización, experiencia y presentación). No se crean perfiles con datos ficticios durante el registro base.
- US05/US06 (solicitud de ingreso y aceptación/rechazo) no usan una tabla de solicitudes propia; su mecanismo se define al implementar esas historias.
- Equipos, competencias y catálogo se describen con las tablas oficiales nuevas, pero su código se adapta únicamente en la tarea del módulo correspondiente.
- El escenario de recuperación de acceso de US02 se resuelve sin correo electrónico, con un código de recuperación de la cuenta; ver `03-api-contracts.md` y `06-security-strategy.md`.

## Sprint 1

### US01 - Registro con rol

Como usuario nuevo, quiero registrarme indicando mi rol para acceder a las funcionalidades correspondientes.

### US02 - Inicio de sesión seguro

Como usuario registrado, quiero iniciar sesión de forma segura para acceder a mi cuenta.

### US03 - Perfil y vinculación de plataformas

Como usuario, quiero gestionar mi perfil y vincular mis plataformas externas para centralizar mi información competitiva.

### US04 - Creación de grupo

Como usuario, quiero crear un grupo para practicar programación competitiva con otros integrantes.

### US05 - Solicitud de ingreso

Como usuario, quiero solicitar ingreso a un grupo para participar con sus miembros.

### US06 - Aceptación/rechazo de postulantes

Como administrador o responsable de grupo, quiero aceptar o rechazar postulantes para controlar la membresía del equipo.

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
