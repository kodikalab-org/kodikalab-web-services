# 09 - Diagrama de Componentes (C4 nivel 3)

Página de componentes alineada de `Diagrama_C4_Kodikalab.drawio` (en la versión actual del archivo se llama
"Copia de COMPONENTES-CORREGIDO"; antes "COMPONENTES-ALINEADO"). Describe el contenedor **API RESTful**
(Spring Boot) con los nombres del diagrama de clases y del ERD: un repositorio por clase/tabla.

## Trazabilidad

| Controller | Service | Repositorios (clase → tabla) | US |
| --- | --- | --- | --- |
| UsuarioController | UsuarioService | UsuarioRepository → `usuario`, PracticanteRepository → `practicante`, CoachRepository → `coach` | US01–US03 |
| GrupoEstudioController | GrupoEstudioService | GrupoEstudioRepository → `grupo_estudio`, PracticanteGrupoRepository → `practicante_grupo` | US04–US06, US14 |
| CompetenciaController | CompetenciaService | CompetenciaRepository → `competencia`, CompetenciaProblemaRepository → `competencia_problema`, CategoriaRepository → `categoria` | US07, US08, US13 |
| CompetenciaController | ResultadoCompetenciaService | ResultadoCompetenciaRepository → `resolucion_problema` | US09–US12, US14 |
| ProblemaController | ProblemaService | ProblemaRepository → `problema`, `problema_tema`; TemaRepository → `tema` | US07, US16 |
| ProblemaController | MaterialService | MaterialRepository → `material` | US19, US20 |
| AsistenteIAController | AsistenteIAService | — (usa LLMClient y la base vectorial) | US15–US18 |

Clientes de integración: EmailClient (correo), JuezExternoClient (Codeforces, AtCoder, VJudge), BibliotecaClient
(biblioteca virtual), LLMClient (proveedor de IA).

## Correspondencia con el código actual

El código usa hoy nombres en inglés y un servicio por entidad (ver la tabla de equivalencias en
`05-architecture.md`). El renombrado a los nombres de este diagrama está pendiente. Mientras tanto, por ejemplo,
`GrupoEstudioController` es `teams.StudyGroupController` y `UsuarioController` corresponde a
`auth.AuthController` + `profiles.ProfileController`.

## Inconsistencias pendientes entre artefactos

1. `Categoria`: en el diagrama de clases clasifica `Problema` y en la página "Base de Datos" clasifica `Competencia`; según `oficial.erd` (oficial) engloba únicamente al grupo de estudio (1:1). Ambos dibujos deben corregirse.
2. `ResultadoCompetencia` (clases) corresponde a la tabla `resolucion_problema` del ERD.
3. Las diferencias de atributos entre el diagrama de clases y el ERD están en `04-database-model.md`.
4. El diagrama de contenedores menciona Kattis/DMOJ y tiene la Biblioteca Virtual rotulada como "Servicio de Correo Electrónico".
