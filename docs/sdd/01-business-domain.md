# 01 - Business Domain

## Fuente vigente

El dominio físico se documenta en `assets/oficial.erd`. Esta actualización del SDD no implica que todas las entidades/capacidades ya estén implementadas.

## Glosario

| Término | Definición en el modelo oficial |
| --- | --- |
| Usuario | Cuenta base en `usuario`: nombre completo, correo, hash, rol, estado y fecha de registro |
| Rol | Tipo de cuenta persistido como `COACH` o `PRACTICANTE`; el diagrama no incluye `ADMIN` |
| Estado de cuenta | `ACTIVO` o `SUSPENDIDO`; solo la cuenta activa puede iniciar sesión |
| Coach | Perfil de entrenador en `coach`, con `usuario_id` como PK/FK compartida y especialidad obligatoria |
| Practicante | Perfil académico/competitivo en `practicante`, con PK/FK compartida, código de estudiante y carrera obligatorios |
| Perfil | Información del rol separada de las credenciales de la cuenta; su creación requiere datos reales y contrato propio |
| Plataforma externa | Integración representada por handles en `practicante`: Codeforces, AtCoder y VJudge |
| Grupo de estudio | Equipo de entrenamiento en `grupo_estudio`, dirigido por un perfil coach, con cupo, invitación y horario descriptivo |
| Membresía | Fila de `practicante_grupo`; vincula un practicante y un grupo y registra estado/rol dentro del equipo |
| Solicitud de grupo | Postulación en `solicitud_grupo`, distinta de la membresía; sus metadatos del ERD necesitan revisión |
| Competencia | Evento vinculado a un grupo, con acceso, reglas, duración, estado y fechas |
| Problema de competencia | Fila de `competencia_problema` que relaciona el catálogo con un evento y define letra/puntaje |
| Problema | Reto del catálogo `problema`, con plataforma, URL y límites de ejecución |
| Tema | Clasificador de `tema`; la relación muchos-a-muchos con problemas está en `problema_tema` |
| Resolución | Envío de una membresía sobre un problema de competencia en `resolucion_problema`; revisar la FK de membresía del ERD |
| Material | Recurso en `material`; puede vincularse a un problema o ser parte de una biblioteca libre |
| Categoría | Elemento `Categoria` del diagrama cuyo alcance, nombres y relaciones deben aclararse antes de implementarlo |
| Progreso / ranking / debilidad | Métricas derivadas; el diagrama no define tablas transaccionales propias para ellas |
| Asistente IA | Capacidad funcional del producto; esta versión del ERD no especifica su almacenamiento |

## Enums en español y persistencia directa

Las clases, rutas y claves JSON conservan sus nombres actuales; los valores de los enums coinciden en Java, HTTP y SQL con el ERD:

- `User` → tabla `usuario` mediante mapeo explícito.
- `Role`: `PRACTICANTE`, `COACH`.
- `UserStatus`: `ACTIVO`, `SUSPENDIDO`.

JPA usa `@Enumerated(EnumType.STRING)` sin conversores ni ordinales. El registro recibe y devuelve `PRACTICANTE` o `COACH`; las autoridades de sesión son `ROLE_PRACTICANTE` o `ROLE_COACH`. Se rechaza el antiguo valor HTTP `PRACTITIONER`. Los valores legacy `ADMIN`, `INACTIVE` y `BLOCKED` no se convierten automáticamente ni se mantienen como valores oficiales.

La autenticación no completa un perfil académico ni concede autorización global por sí sola. Los endpoints siguen abiertos temporalmente para desarrollo; las reglas de acceso requieren definición adicional.
