# Tarea - US-03 Configuración de perfil del practicante según ERD

> Fuente obligatoria para esta historia: `docs/sdd/assets/oficial.erd`. Si Trello, capturas o textos funcionales mencionan plataformas/campos distintos al ERD, **prevalece el ERD** hasta que el equipo apruebe un cambio formal del modelo.
>
> Restricción de trabajo: **no realizar commits, push, merge ni cambios de rama sin autorización explícita del dueño del proyecto**.

## User Story

**US-03 - Configuración de perfil y vinculación de plataformas competitivas**

Como practicante, quiero gestionar mi perfil competitivo, para mantener actualizada la información sobre mi experiencia y progreso.

## Story Points

```txt
8
```

## Criterios de aceptación

### Escenario exitoso

**DADO** que un practicante accede a la configuración de su perfil, **CUANDO** completa o actualiza su información y confirma los cambios, **ENTONCES** el sistema valida los formatos, guarda los datos y muestra el perfil actualizado.

### Escenario de error

**DADO** que se intenta guardar el perfil, **CUANDO** algún dato obligatorio o identificador de una plataforma no cumple las reglas de validación, **ENTONCES** el sistema no guarda la información inválida, señala los campos que deben corregirse y conserva los datos previamente registrados.

### Escenario alternativo

**DADO** que Codeforces no puede validar el `codeforcesHandle` informado porque el usuario no existe, responde `404`/`FAILED` o la API no confirma el perfil, **CUANDO** el practicante guarda los demás datos válidos, **ENTONCES** el sistema actualiza la información disponible del perfil, no guarda el `codeforcesHandle` inválido ni el `codeforcesRating`, conserva los valores previos de Codeforces si existían y, si no existían, ambos permanecen en `null`.

## Verificación del ERD

El ERD vigente contiene estas tablas relacionadas con US-03:

### `usuario`

Tabla ya existente en el proyecto actual para cuenta base y autenticación.

```txt
usuario
- id SERIAL
- nombre_completo VARCHAR(150)
- correo VARCHAR(100)
- password_hash VARCHAR(255)
- rol VARCHAR(20)
- estado_cuenta VARCHAR(20) DEFAULT 'ACTIVO'
- fecha_registro TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
```

### `practicante`

Tabla todavía no implementada como entidad en el código actual. Es el perfil competitivo del usuario practicante.

```txt
practicante
- usuario_id INT
- codigo_estudiante VARCHAR(20)
- carrera VARCHAR(100)
- ciclo_academico INT DEFAULT 1
- nivel_competitivo VARCHAR(30) DEFAULT 'PRINCIPIANTE'
- codeforces_handle VARCHAR(50)
- codeforces_rating INT DEFAULT 0
- atcoder_handle VARCHAR(50)
- vjudge_handle VARCHAR(50)
```

Relación ERD:

```txt
usuario.id 1:1 practicante.usuario_id
```

La relación es identificadora: `practicante.usuario_id` debe ser PK y FK hacia `usuario.id`. No se debe crear un ID nuevo para `practicante`.

### `coach`

Tabla también pendiente como entidad. Se documenta porque comparte el mismo patrón 1:1 con `usuario`, pero no es el objetivo principal de esta US.

```txt
coach
- usuario_id INT
- especialidad_principal VARCHAR(120)
- organizacion_club VARCHAR(150)
- anios_experiencia INT DEFAULT 0
- presentacion VARCHAR(500)
```

Relación ERD:

```txt
usuario.id 1:1 coach.usuario_id
```

## Decisión de alcance: guiarse por ERD, no por Trello si difiere

La captura de Trello menciona:

```txt
T2 - Vincular plataformas (Codeforces, VJudge, LeetCode)
T3 - Registrar nivel y trayectoria
```

Pero el ERD vigente para `practicante` define estas plataformas/campos:

```txt
codeforces_handle
codeforces_rating
atcoder_handle
vjudge_handle
```

Por lo tanto:

- Implementar **Codeforces**, **AtCoder** y **VJudge** porque están en el ERD.
- **No implementar LeetCode** en persistencia, DTOs finales ni base de datos mientras no exista en el ERD.
- Si negocio insiste en LeetCode, primero debe actualizarse `oficial.erd` y el contrato técnico.
- No crear tablas genéricas de plataformas externas porque el ERD no las define.
- No crear columnas de estado de vinculación (`PENDIENTE`, `VERIFICADO`, etc.) porque el ERD no las define.
- La validación externa solo aplica a Codeforces mediante su API pública. Si Codeforces no confirma el usuario, no se guarda `codeforces_handle` ni `codeforces_rating`; los demás datos válidos sí pueden actualizarse.

## Objetivo técnico

Implementar el módulo `profiles` para permitir que un usuario con rol `PRACTICANTE` consulte, cree y actualice su perfil `practicante`, asociado a la cuenta `usuario` autenticada.

Actualmente existen `auth` y `users`, pero no existen entidades `practicante` ni `coach`. Esta US debe agregar como mínimo la entidad `PractitionerProfile` mapeada a la tabla física `practicante`.

## Principios de implementación

- `usuario` es la cuenta base.
- `practicante` es el perfil competitivo de un usuario con rol `PRACTICANTE`.
- `coach` es otro perfil 1:1, pero no debe mezclarse con esta tarea salvo que sea necesario preparar estructura mínima.
- El endpoint de configuración opera sobre la cuenta del practicante autenticado, no sobre IDs arbitrarios recibidos del cliente.
- El cliente no envía `usuarioId`; se obtiene desde la sesión/usuario actual.
- No se crean perfiles falsos durante registro o login.
- No se implementa LeetCode hasta que exista en el ERD.
- No se realizan commits sin autorización explícita.

## Tareas incluidas

### T2 - US-03 · Vincular plataformas según ERD

Implementar captura y actualización de identificadores competitivos existentes en `practicante`:

```txt
codeforcesHandle  -> codeforces_handle
atcoderHandle     -> atcoder_handle
vjudgeHandle      -> vjudge_handle
```

También exponer `codeforcesRating` mapeado a `codeforces_rating`, pero el valor debe venir de la API de Codeforces, no del frontend.

#### Reglas para T2

- Los handles son opcionales.
- Cada handle debe tener máximo 50 caracteres.
- Si se informa un handle, debe cumplir un formato seguro definido por DTO/servicio antes de llamar a una API externa.
- `codeforcesRating` no debe ser confiado desde el request del cliente.
- Si `codeforcesHandle` está vacío, guardar `codeforces_handle = null` y `codeforces_rating = null`, salvo que se decida conservar un valor previo por una actualización parcial.
- Si `codeforcesHandle` está presente, validar contra `https://codeforces.com/api/user.info?handles={handle}`.
- Si Codeforces responde `OK`, guardar el handle confirmado y el `rating` devuelto; si el usuario no tiene rating, guardar `null` o `0` según decisión final del contrato, preferentemente `null` para distinguir ausencia real de rating.
- Si Codeforces responde `FAILED`, `404`, usuario inexistente, timeout o error no confirmable, no guardar el handle informado ni rating; conservar valores previos de Codeforces si existían y, si no existían, dejarlos en `null`.
- No agregar `leetcodeHandle`.
- No agregar estados persistidos de validación externa.

### T3 - US-03 · Registrar nivel y trayectoria del practicante

Implementar creación/actualización de los datos obligatorios del perfil `practicante`:

```txt
codigoEstudiante  -> codigo_estudiante
carrera           -> carrera
cicloAcademico    -> ciclo_academico
nivelCompetitivo  -> nivel_competitivo
```

#### Reglas para T3

- `codigoEstudiante` es obligatorio y máximo 20 caracteres.
- `codigoEstudiante` debe ser único si el equipo confirma la restricción del SDD vigente.
- `carrera` es obligatoria y máximo 100 caracteres.
- `cicloAcademico` es obligatorio y debe ser mayor o igual a 1.
- `nivelCompetitivo` es obligatorio.
- Valores válidos de `nivelCompetitivo`:

```txt
PRINCIPIANTE
INTERMEDIO
AVANZADO
```

## Módulos involucrados

```txt
com.kodika.kodikalab.profiles
com.kodika.kodikalab.users
com.kodika.kodikalab.auth
com.kodika.kodikalab.security
com.kodika.kodikalab.common
```

## Archivos actuales relevantes

Actualmente existen:

```txt
src/main/java/com/kodika/kodikalab/users/User.java
src/main/java/com/kodika/kodikalab/users/UserRepository.java
src/main/java/com/kodika/kodikalab/users/Role.java
src/main/java/com/kodika/kodikalab/users/UserStatus.java
src/main/java/com/kodika/kodikalab/auth/AuthController.java
src/main/java/com/kodika/kodikalab/auth/AuthService.java
src/main/java/com/kodika/kodikalab/auth/AuthServiceImpl.java
src/main/java/com/kodika/kodikalab/security/LoginSessionService.java
src/main/java/com/kodika/kodikalab/config/SecurityConfig.java
```

No existen todavía:

```txt
src/main/java/com/kodika/kodikalab/profiles/PractitionerProfile.java
src/main/java/com/kodika/kodikalab/profiles/CoachProfile.java
```

## Archivos a crear para US-03

### Entidad y enum

```txt
src/main/java/com/kodika/kodikalab/profiles/PractitionerProfile.java
src/main/java/com/kodika/kodikalab/profiles/PractitionerLevel.java
```

`CoachProfile` puede quedar fuera de esta US si no se usa. Si se crea, debe ser solo por alineación estructural con ERD y no debe exponer endpoints de coach en esta historia.

### Repositorio

```txt
src/main/java/com/kodika/kodikalab/profiles/PractitionerProfileRepository.java
```

### Servicio

```txt
src/main/java/com/kodika/kodikalab/profiles/ProfileService.java
src/main/java/com/kodika/kodikalab/profiles/ProfileServiceImpl.java
```

### Controller

```txt
src/main/java/com/kodika/kodikalab/profiles/ProfileController.java
```

### DTOs

```txt
src/main/java/com/kodika/kodikalab/profiles/dto/PractitionerProfileRequest.java
src/main/java/com/kodika/kodikalab/profiles/dto/PractitionerProfileResponse.java
```

No crear DTO de LeetCode mientras no exista en el ERD.

## Mapeo JPA esperado

Entidad `PractitionerProfile`:

```txt
@Table(name = "practicante")
```

Campos esperados:

```txt
usuario_id INT PK/FK
codigo_estudiante VARCHAR(20)
carrera VARCHAR(100)
ciclo_academico INT
nivel_competitivo VARCHAR(30)
codeforces_handle VARCHAR(50)
codeforces_rating INT
atcoder_handle VARCHAR(50)
vjudge_handle VARCHAR(50)
```

Relación con `User`:

```java
@Id
@Column(name = "usuario_id")
private Integer userId;

@OneToOne(fetch = FetchType.LAZY)
@MapsId
@JoinColumn(name = "usuario_id")
private User user;
```

O equivalente correcto con PK compartida. No usar `@GeneratedValue` en `PractitionerProfile`.

`nivelCompetitivo`:

```java
@Enumerated(EnumType.STRING)
@Column(name = "nivel_competitivo", nullable = false, length = 30)
private PractitionerLevel nivelCompetitivo;
```

## Endpoints sugeridos

Como el contrato pendiente en `docs/sdd/03-api-contracts.md` lista `Users/Profile`, se recomienda usar:

```http
GET /api/users/me
PUT /api/users/me
```

Opcionalmente, si el equipo quiere separar el perfil:

```http
GET /api/profiles/me
PUT /api/profiles/me
```

Elegir una sola convención antes de implementar. Si se usa `/api/users/me`, la respuesta debe representar usuario + perfil practicante sin exponer datos sensibles.

No implementar en esta US:

```http
POST /api/users/me/handles
POST /api/users/me/handles/{platform}/retry
```

Esos endpoints solo tendrían sentido si el ERD agregara una tabla/estado de vinculación externa.

## Request sugerido según ERD

```json
{
  "codigoEstudiante": "20240001",
  "carrera": "Ingeniería de Software",
  "cicloAcademico": 5,
  "nivelCompetitivo": "INTERMEDIO",
  "codeforcesHandle": "tourist",
  "atcoderHandle": "tourist_atcoder",
  "vjudgeHandle": "usuario_vjudge"
}
```

## Response sugerido según ERD

```json
{
  "message": "Perfil actualizado correctamente",
  "profile": {
    "email": "test@gmail.com",
    "role": "PRACTICANTE",
    "codigoEstudiante": "20240001",
    "carrera": "Ingeniería de Software",
    "cicloAcademico": 5,
    "nivelCompetitivo": "INTERMEDIO",
    "codeforcesHandle": "tourist",
    "codeforcesRating": 0,
    "atcoderHandle": "tourist_atcoder",
    "vjudgeHandle": "usuario_vjudge"
  }
}
```

No incluir:

```txt
passwordHash
sessionId
leetcodeHandle
platform.status persistido
```

## Validaciones sugeridas en DTO

```java
@NotBlank
@Size(max = 20)
private String codigoEstudiante;

@NotBlank
@Size(max = 100)
private String carrera;

@NotNull
@Min(1)
private Integer cicloAcademico;

@NotNull
private PractitionerLevel nivelCompetitivo;

@Size(max = 50)
@Pattern(regexp = "^[A-Za-z0-9._-]*$")
private String codeforcesHandle;

// codeforcesRating no debe venir del request: se obtiene desde la API de Codeforces.

@Size(max = 50)
@Pattern(regexp = "^[A-Za-z0-9._-]*$")
private String atcoderHandle;

@Size(max = 50)
@Pattern(regexp = "^[A-Za-z0-9._-]*$")
private String vjudgeHandle;
```

## Plan de implementación

1. Revisar `User.java` para confirmar:
   - Tabla física `usuario`.
   - PK `Integer` compatible con `usuario.id SERIAL`.
   - Rol `PRACTICANTE` disponible.
2. Crear enum `PractitionerLevel` con:
   - `PRINCIPIANTE`
   - `INTERMEDIO`
   - `AVANZADO`
3. Crear entidad `PractitionerProfile` mapeada a `practicante` con PK compartida `usuario_id`.
4. Crear `PractitionerProfileRepository`.
5. Crear DTO request/response según campos del ERD.
6. Crear cliente de integración Codeforces:
   - Consumir `GET https://codeforces.com/api/user.info?handles={handle}`.
   - Configurar timeout corto para no bloquear el guardado de otros datos.
   - Mapear respuesta `OK` a handle/rating confirmado.
   - Mapear respuesta `FAILED`, `404`, timeout o error de red a validación no confirmada.
7. Crear servicio de perfil:
   - Obtener usuario actual desde sesión o mecanismo existente.
   - Validar que el usuario tenga rol `PRACTICANTE`.
   - Buscar perfil por `usuario_id`.
   - Si no existe, crear perfil asociado a ese usuario.
   - Si existe, actualizar los campos permitidos.
   - Si hay `codeforcesHandle`, validarlo primero con Codeforces.
   - Si Codeforces confirma el usuario, persistir `codeforces_handle` y `codeforces_rating`.
   - Si Codeforces no confirma el usuario o falla la API, no persistir el nuevo handle/rating; conservar los valores previos de Codeforces o dejarlos `null` si no existían.
   - Guardar en transacción los demás datos válidos.
8. Crear controller para `GET` y `PUT` del perfil actual.
9. Manejar errores:
   - No autenticado.
   - Usuario no practicante.
   - Datos inválidos.
   - Código de estudiante duplicado si se aplica unicidad.
10. Actualizar documentación del contrato si se confirma la ruta final.
11. Compilar y ejecutar pruebas.
12. No hacer commit hasta recibir autorización explícita.

## Reglas de negocio

- Solo `PRACTICANTE` puede crear o actualizar `practicante`.
- `COACH` no puede crear perfil de practicante.
- El perfil actualizado siempre corresponde al usuario autenticado.
- No aceptar `usuarioId` desde el body para evitar modificar perfiles ajenos.
- No modificar `usuario.rol` desde la configuración de perfil.
- No modificar contraseña desde esta US.
- No devolver `passwordHash`.
- No implementar LeetCode porque no está en el ERD.
- No crear nuevas tablas o columnas no presentes en el ERD.

## Manejo del escenario alternativo

El criterio alternativo se resuelve con la API pública de Codeforces, sin agregar columnas fuera del ERD.

Flujo esperado:

1. Validar localmente formato y longitud de `codeforcesHandle`.
2. Si el campo está vacío, no hay vinculación Codeforces: `codeforces_handle` y `codeforces_rating` quedan `null`, salvo que el endpoint se defina como actualización parcial y se quiera conservar un valor previo.
3. Si el campo tiene valor, consultar:

```txt
GET https://codeforces.com/api/user.info?handles={handle}
```

4. Si Codeforces responde `OK`, persistir:

```txt
codeforces_handle = handle confirmado por Codeforces
codeforces_rating = rating devuelto por Codeforces, si existe
```

5. Si Codeforces responde `FAILED`, `404`, usuario inexistente, timeout o error no confirmable:
   - No guardar el `codeforcesHandle` informado.
   - No guardar `codeforcesRating`.
   - Actualizar los demás campos válidos del perfil.
   - Conservar valores previos de Codeforces si existían.
   - Si no existían valores previos, dejar `codeforces_handle = null` y `codeforces_rating = null`.
6. No persistir estados `PENDIENTE`, `VERIFICADO` o `INVALIDO` porque el ERD no los define.

Esto sí es posible porque ambos campos de Codeforces son opcionales en el ERD. El default `0` de `codeforces_rating` no debe forzarse desde el servicio cuando la validación no confirma un usuario.

## Manejo de errores

Formato recomendado:

```json
{
  "message": "Datos de perfil inválidos",
  "errors": {
    "codigoEstudiante": "El código de estudiante es obligatorio",
    "codeforcesHandle": "El identificador de Codeforces no cumple el formato permitido"
  }
}
```

Casos esperados:

- `400 Bad Request`: validaciones locales inválidas.
- `401 Unauthorized`: usuario no autenticado.
- `403 Forbidden`: usuario autenticado no es `PRACTICANTE`.
- `409 Conflict`: código de estudiante duplicado, si se configura unicidad.

## Pruebas manuales mínimas

### Caso exitoso: crear perfil de practicante

1. Registrar o tener un usuario con rol `PRACTICANTE`.
2. Iniciar sesión.
3. Enviar `PUT /api/users/me` o la ruta final elegida con datos válidos.

Resultado esperado:

- HTTP `200 OK` o `201 Created` si se decide diferenciar creación.
- Se crea fila en `practicante` con `usuario_id = usuario.id`.
- Se devuelve el perfil actualizado.
- No se devuelve `passwordHash`.

### Caso exitoso: actualizar perfil existente

Enviar cambios en `carrera`, `cicloAcademico`, `nivelCompetitivo`, `codeforcesHandle`, `atcoderHandle` o `vjudgeHandle`.

Resultado esperado:

- HTTP `200 OK`.
- Se actualiza la misma fila `practicante`.
- No cambia `usuario.id` ni `usuario.rol`.

### Error: dato obligatorio faltante

Enviar `codigoEstudiante`, `carrera` o `nivelCompetitivo` vacío/nulo.

Resultado esperado:

- HTTP `400 Bad Request`.
- Se reporta el campo inválido.
- No se pierden datos previos.

### Error: handle inválido

Enviar un handle con caracteres no permitidos o longitud mayor a 50.

Resultado esperado:

- HTTP `400 Bad Request`.
- Se reporta el campo inválido.
- No se guardan datos inválidos.

### Error: usuario coach

Intentar actualizar perfil de practicante con usuario `COACH`.

Resultado esperado:

- HTTP `403 Forbidden`.
- No se crea fila en `practicante`.

### Verificación de LeetCode fuera de alcance

Enviar `leetcodeHandle` en el JSON.

Resultado esperado recomendado:

- Ignorarlo explícitamente solo si el DTO no lo modela y Jackson lo permite, o rechazarlo si se configura validación estricta.
- En cualquier caso, no persistirlo porque no existe en el ERD.

## Dependencias con US-01 y US-02

Esta historia depende de:

- `usuario` ya implementado.
- Registro con rol `PRACTICANTE`.
- Login o mecanismo para identificar al usuario actual.
- Sesión HTTP vigente si se usa el flujo implementado de US-02.

No corresponde a esta historia:

- Implementar registro.
- Implementar login.
- Cambiar roles existentes.
- Crear JWT si el proyecto usa sesión HTTP.
- Implementar perfil de coach completo.
- Implementar equipos, competencias, ranking o analytics.

## Comandos de verificación

Linux/Git Bash:

```bash
./mvnw clean compile
./mvnw test
```

Windows PowerShell:

```powershell
.\mvnw.cmd clean compile
.\mvnw.cmd test
```

## Checklist antes de entregar

- [x] `PractitionerProfile` usa `@Table(name = "practicante")`.
- [x] `usuario_id` es PK/FK compartida, sin `@GeneratedValue`.
- [x] No existe `leetcodeHandle` persistido.
- [x] No se crearon tablas no presentes en el ERD.
- [x] Solo `PRACTICANTE` puede actualizar perfil de practicante.
- [x] El body no permite cambiar `usuarioId`.
- [x] No se devuelve `passwordHash`.
- [x] `./mvnw clean compile` pasa.
- [x] Pruebas manuales documentadas.
- [x] No se hizo commit sin autorización.

## Notas finales

- Esta tarea corresponde únicamente a **US-03**.
- El foco real según ERD es crear/actualizar la entidad `practicante` asociada a `usuario`.
- Aunque Trello mencione LeetCode, esta implementación debe guiarse por `oficial.erd`: Codeforces, AtCoder y VJudge.
- Cualquier diferencia entre negocio y ERD debe resolverse actualizando primero el ERD y luego el contrato técnico.
