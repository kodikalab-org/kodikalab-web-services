# 08 - AI Working Context

Este archivo define reglas para cualquier asistente IA o agente que trabaje en KodikaLab.

## Reglas obligatorias

- Respetar el paquete raíz `com.kodika.kodikalab`.
- Respetar la estructura por capas existente.
- No modificar `.env` ni exponer secretos.
- No hacer commits, push o cambios de ramas sin autorización explícita.
- No cambiar `server.servlet.context-path: /api` sin aprobación.
- No reactivar JWT ni cerrar endpoints mientras el equipo esté en fase de desarrollo inicial, salvo pedido explícito.
- El proyecto usa enfoque code-first: las entidades JPA son la fuente de verdad del modelo de datos.
- Antes de modificar entidades, revisar `docs/sdd/04-database-model.md`.
- Antes de modificar endpoints, revisar `docs/sdd/03-api-contracts.md`.
- Después de cambios relevantes en Java, ejecutar `./mvnw clean compile`.

## Contexto técnico

- Java 21.
- Spring Boot 3.5.6.
- Maven.
- PostgreSQL.
- Swagger UI disponible en `http://localhost:8080/api/swagger-ui.html`.
- Seguridad temporalmente abierta para desarrollo.

## Criterio de implementación

Implementar únicamente la historia o tarea solicitada. No agregar lógica extra, librerías o cambios arquitectónicos sin confirmación del equipo.
