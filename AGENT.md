# AGENT.md - KodikaLab

Antes de modificar este proyecto, revisar:

- `README.md`
- `docs/sdd/README.md`
- `docs/sdd/00-project-context.md`
- `docs/sdd/05-architecture.md`
- `docs/sdd/08-ai-working-context.md`

## Reglas para asistentes IA

- No ejecutar `git add`, `git commit`, `git push` ni cambios de rama sin autorización explícita.
- No modificar `.env` ni exponer secretos.
- No cambiar el context path `/api` sin aprobación.
- No activar seguridad JWT mientras el equipo esté desarrollando endpoints base, salvo solicitud explícita.
- Mantener Java 21 y Spring Boot 3.5.6.
- Mantener la estructura `controller`, `service`, `service/impl`, `repository`, `entity`, `dto`, `config`.
- Ejecutar `./mvnw clean compile` después de cambios relevantes en Java.
