# Tarea - US-03 (parte 2) Perfil del Coach según ERD

> Fuente obligatoria: `docs/sdd/assets/oficial.erd`. Leer también `docs/sdd/03-api-contracts.md`, `04-database-model.md`, `05-architecture.md`, `06-security-strategy.md` y `08-ai-working-context.md` antes de tocar código.
>
> Restricción: **no realizar commits, push, merge ni cambios de rama sin autorización explícita del dueño del proyecto.** No modificar `.env`, no ejecutar DDL manual sobre la base local.

## Contexto

El perfil `practicante` ya está implementado en `com.kodika.kodikalab.profiles` (`PractitionerProfile`, `ProfileServiceImpl`, `ProfileController` en `GET/PUT /api/users/me`). Hoy un `COACH` que llama a `/api/users/me` recibe `403 "Solo los practicantes pueden gestionar este perfil"`.

Esta tarea agrega el perfil `coach` (1:1 con `usuario`) siguiendo **exactamente el mismo patrón** que `practicante`. Es requisito para US-04: `grupo_estudio.coach_id` es FK a `coach.usuario_id`, así que un coach necesita perfil antes de crear grupos.

## Tabla `coach` según ERD

| Columna | Tipo | Nulo | Default | Comentario ERD |
|---|---|---|---|---|
| `usuario_id` | `INT` | NOT NULL | — | PK, FK a `usuario.id` |
| `especialidad_principal` | `VARCHAR(120)` | NOT NULL | — | DP, Grafos, Flujo, etc. (texto libre) |
| `organizacion_club` | `VARCHAR(150)` | NULL | — | Universidad / Club |
| `anios_experiencia` | `INT` | NOT NULL | `0` | |
| `presentacion` | `VARCHAR(500)` | NULL | — | Perfil público |

Relación: `usuario.id 1:1 coach.usuario_id` (identificadora, PK compartida, `@MapsId`, **sin** `@GeneratedValue`).

No crear columnas, tablas, enums de especialidad ni estados de verificación que el ERD no define.

## Decisiones de diseño (fijadas para que los agentes no diverjan)

| # | Decisión | Motivo |
|---|---|---|
| D1 | Un único endpoint `GET/PUT /api/users/me` que se comporta según el rol de la sesión: `PRACTICANTE` → perfil `practicante`, `COACH` → perfil `coach`. | El contrato ya publica `/users/me` como "mi perfil"; el frontend no necesita conocer dos rutas. Las pruebas y Postman de practicante siguen válidos. |
| D2 | El `PUT` recibe el body como `JsonNode` y lo convierte al DTO del rol con el `ObjectMapper` **inyectado por Spring** (no `new ObjectMapper()`), luego valida con el `jakarta.validation.Validator` inyectado. | Un `@RequestBody` tipado no puede elegir el DTO según el rol. Usar el mapper de Spring preserva `FAIL_ON_UNKNOWN_PROPERTIES=false` (campos ajenos, como `leetcodeHandle`, se ignoran). |
| D3 | `especialidadPrincipal` es texto libre (no enum). | El ERD lo define `VARCHAR(120)` con ejemplos, no como dominio cerrado. |
| D4 | `aniosExperiencia`: obligatorio, `>= 0` y `<= 60`. | `>= 0` por semántica; el tope evita valores absurdos. Confirmar con el equipo; si no se acepta el tope, quitar solo `@Max`. |
| D5 | `organizacionClub` y `presentacion`: opcionales; vacío/blanco se guarda como `null`. | Columnas nulas en el ERD; mismo criterio que los handles de practicante. |
| D6 | Un `COACH` que envía un body de practicante recibe `400` (le faltan campos de coach), no `403`. Un `PRACTICANTE` que envía un body de coach recibe `400`. Ninguno puede escribir en la tabla del otro rol. | Con D1 el rol decide la tabla; la regla "COACH no crea `practicante`" se mantiene. |
| D7 | `GET` sin perfil creado → `404` con mensaje propio de coach. | Igual que practicante. |
| D8 | La tabla `coach` se creará en desarrollo por `ddl-auto: update`, igual que `practicante`. Documentar que en ambientes reales se necesita una migración aprobada. | `08-ai-working-context.md` prohíbe usar `ddl-auto` como sustituto de migración. |

## Contrato objetivo

### Request (`PUT /api/users/me` con sesión `COACH`)

```json
{
  "especialidadPrincipal": "Grafos y Programación Dinámica",
  "organizacionClub": "Club de Programación Competitiva",
  "aniosExperiencia": 4,
  "presentacion": "Entrenador de maratones ICPC."
}
```

### Response

```json
{
  "message": "Perfil actualizado correctamente",
  "profile": {
    "email": "test@gmail.com",
    "role": "COACH",
    "especialidadPrincipal": "Grafos y Programación Dinámica",
    "organizacionClub": "Club de Programación Competitiva",
    "aniosExperiencia": 4,
    "presentacion": "Entrenador de maratones ICPC."
  }
}
```

No incluir `passwordHash`, `sessionId`, `usuarioId` ni campos de practicante.

### Errores (mismo formato `{ "message", "errors" }` de `ProfileExceptionHandler`)

- `400`: campos inválidos (con `errors` por campo), JSON inválido o body del rol equivocado.
- `401`: sin sesión.
- `404`: `GET` sin perfil de coach creado.

### Validaciones del DTO `CoachProfileRequest`

```java
@NotBlank(message = "La especialidad principal es obligatoria")
@Size(max = 120, message = "La especialidad principal debe tener como máximo 120 caracteres")
String especialidadPrincipal,

@Size(max = 150, message = "La organización o club debe tener como máximo 150 caracteres")
String organizacionClub,

@NotNull(message = "Los años de experiencia son obligatorios")
@Min(value = 0, message = "Los años de experiencia no pueden ser negativos")
@Max(value = 60, message = "Los años de experiencia deben ser como máximo 60")
Integer aniosExperiencia,

@Size(max = 500, message = "La presentación debe tener como máximo 500 caracteres")
String presentacion
```

Hacer `strip()` en el constructor compacto del record, igual que `PractitionerProfileRequest`.

## Firmas acordadas (contrato entre agentes)

Todas en `com.kodika.kodikalab.profiles` salvo indicación.

```java
// Entidad
@Entity @Table(name = "coach")
public class CoachProfile {
    @Id @Column(name = "usuario_id") Integer userId;
    @OneToOne(fetch = LAZY) @MapsId @JoinColumn(name = "usuario_id", nullable = false) User user;
    @Column(name = "especialidad_principal", nullable = false, length = 120) String mainSpecialty;
    @Column(name = "organizacion_club", length = 150) String organization;
    @ColumnDefault("0") @Column(name = "anios_experiencia", nullable = false) Integer yearsOfExperience;
    @Column(name = "presentacion", length = 500) String presentation;
}

public interface CoachProfileRepository extends JpaRepository<CoachProfile, Integer> {
    Optional<CoachProfile> findByUserId(Integer userId);
}

// DTOs en profiles.dto
public record CoachProfileRequest(...)            // ver validaciones arriba
public record CoachProfileResponse(String message, CoachProfileData profile) {
    public static CoachProfileResponse of(String message, CoachProfile profile);
    public record CoachProfileData(String email, Role role, String especialidadPrincipal,
                                   String organizacionClub, Integer aniosExperiencia, String presentacion) {}
}

// Resolución del usuario autenticado (extraída de ProfileServiceImpl.currentPractitioner)
@Component
public class CurrentUserResolver {
    /** Lanza UnauthorizedException si no hay sesión válida. No valida rol. */
    public User currentUser();
}

// Servicio de coach
public interface CoachProfileService {
    CoachProfileResponse getProfile(User coach);
    CoachProfileResponse saveProfile(User coach, CoachProfileRequest request);
}
```

`ProfileService` de practicante se adapta para recibir el `User` ya resuelto **o** seguir resolviéndolo internamente vía `CurrentUserResolver`; elegir una sola forma y aplicarla a ambos servicios. Preferencia: ambos reciben `User` y verifican el rol como defensa (`ForbiddenException` si no corresponde).

El controller queda así (conceptualmente):

```java
@GetMapping
public ResponseEntity<?> getCurrentProfile() {
    User user = currentUserResolver.currentUser();
    return switch (user.getRole()) {
        case PRACTICANTE -> ok(profileService.getProfile(user));
        case COACH -> ok(coachProfileService.getProfile(user));
    };
}

@PutMapping
public ResponseEntity<?> updateCurrentProfile(@RequestBody JsonNode body) {
    User user = currentUserResolver.currentUser();
    return switch (user.getRole()) {
        case PRACTICANTE -> ok(profileService.saveProfile(user, profileRequestReader.read(body, PractitionerProfileRequest.class)));
        case COACH -> ok(coachProfileService.saveProfile(user, profileRequestReader.read(body, CoachProfileRequest.class)));
    };
}
```

`ProfileRequestReader` (componente nuevo): convierte con el `ObjectMapper` de Spring y valida con el `Validator` de Spring. Si hay violaciones lanza `ProfileValidationException(Map<String,String> errors)`; si la conversión falla por enum inválido de `nivelCompetitivo` debe seguir devolviendo `"El nivel competitivo debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO"`; cualquier otro error de conversión → `"La solicitud debe contener un JSON válido con los campos esperados"`. `ProfileExceptionHandler` agrega el handler de `ProfileValidationException` (400, `message` = primer error, `errors` = mapa).

**Cuidado:** al quitar `@Valid @RequestBody PractitionerProfileRequest`, los mensajes y el formato actuales de practicante deben quedar **idénticos**. Las pruebas existentes de `ProfileControllerTests` son la red de seguridad.

## Plan de ejecución por agente

Leyenda: **Opus** = diseño, código con riesgo de regresión, revisión. **Sonnet** = código mecánico siguiendo firmas fijadas, pruebas, Postman, documentación.

### Fase 0 — Contrato (Opus) · bloqueante

1. Validar D1–D8 contra el SDD; si alguna choca con la documentación, detenerse y reportar.
2. Actualizar `docs/sdd/03-api-contracts.md`, sección `Users/Profile`: agregar la subsección del coach con request/response/errores de este documento y aclarar que `/users/me` depende del rol.
3. Entregable: contrato escrito. Las fases 1 y 2 usan exactamente esos nombres de campos.

### Fase 1 — Persistencia y DTOs (Sonnet) · tras Fase 0

1. Crear `CoachProfile` y `CoachProfileRepository` con las firmas acordadas (Lombok `@Getter/@Setter`, igual que `PractitionerProfile`).
2. Crear `dto/CoachProfileRequest` y `dto/CoachProfileResponse`.
3. Actualizar `architecture/RuntimeBoundaryTests`: entidades `User`, `PractitionerProfile`, `CoachProfile`; repositorios `userRepository`, `practitionerProfileRepository`, `coachProfileRepository`.
4. `./mvnw clean compile` debe pasar.

### Fase 2 — Servicio, resolución de usuario y controller (Opus) · tras Fase 1

1. Extraer `CurrentUserResolver` desde `ProfileServiceImpl.currentPractitioner()` (manteniendo mensajes en español).
2. Adaptar `ProfileService`/`ProfileServiceImpl` a la forma elegida (recibir `User`). Conservar: consulta a Codeforces fuera de la transacción, `TransactionOperations`, mensaje de advertencia de Codeforces, chequeo de código duplicado.
3. Crear `CoachProfileService`/`CoachProfileServiceImpl`: buscar por `usuario_id`; si no existe crear con `setUser(user)`; asignar campos (blancos → `null` en opcionales); `saveAndFlush` en `@Transactional`; mensaje `"Perfil actualizado correctamente"`; `GET` sin perfil → `NotFoundException("El perfil de coach aún no está registrado")`; si el rol no es `COACH` → `ForbiddenException("Solo los coaches pueden gestionar este perfil")`.
4. Crear `ProfileRequestReader` y `ProfileValidationException`; extender `ProfileExceptionHandler`.
5. Reescribir `ProfileController` con el `switch` por rol.
6. Ejecutar `ProfileControllerTests` y `ProfileServiceTests` existentes: deben seguir pasando (ajustar solo la construcción de objetos, no las aserciones de mensajes).

### Fase 3 — Pruebas y Postman (Sonnet) · puede empezar en paralelo a Fase 2 una vez fijadas las firmas; termina tras Fase 2

1. `CoachProfileServiceTests` (Mockito, sin BD):
   - crea perfil nuevo asociado al usuario (`user` asignado, sin ID manual);
   - actualiza perfil existente sin crear otra fila;
   - `organizacionClub`/`presentacion` en blanco → `null`;
   - `GET` sin perfil → `NotFoundException`;
   - usuario `PRACTICANTE` → `ForbiddenException` y sin interacción con el repositorio.
2. `ProfileControllerTests` (standalone MockMvc, con `ProfileRequestReader` real usando `Jackson2ObjectMapperBuilder.json().build()` y `LocalValidatorFactoryBean`):
   - coach `PUT` válido → `200` con `role = COACH` y sin `passwordHash` ni campos de practicante;
   - coach con `especialidadPrincipal` vacío → `400` y `errors.especialidadPrincipal`;
   - coach con `aniosExperiencia = -1` → `400`;
   - coach enviando body de practicante → `400`, el servicio de practicante no se invoca;
   - practicante enviando body de coach → `400`, el servicio de coach no se invoca;
   - todos los casos actuales de practicante siguen pasando sin cambiar sus mensajes esperados.
3. Postman: crear `tests/US03-coach.postman_collection.json` (mismo estilo que `US03-profile`): registrar COACH, login, `GET` 404, `PUT` crear, `GET` consultar, `PUT` actualizar, `PUT` inválido conserva datos previos, `GET` verificación, opcionales en blanco → `null`. Usar solo datos genéricos (`test.us03.coach.{id}@gmail.com`).
4. Actualizar en `tests/US03-profile.postman_collection.json` el caso **12**: con D6 ahora es `400` (body sin campos de coach), y su nombre debe decir "COACH con body de practicante es rechazado y no crea practicante".

### Fase 4 — Documentación, verificación y revisión (Opus) · final

1. `docs/sdd/04-database-model.md`: `coach` pasa a "implementado" (mismo texto que `practicante`) y nota de migración (D8).
2. `docs/sdd/08-ai-working-context.md`: actualizar "Artefactos y fase actual" (perfiles `practicante` y `coach` implementados en `profiles`).
3. Ejecutar `./mvnw clean compile` y `./mvnw test`. Las suites `KodikalabApplicationTests` y `RuntimeBoundaryTests` necesitan PostgreSQL con `DB_PASSWORD`; si no está disponible, reportarlo explícitamente como "no ejecutado", nunca como "pasa".
4. Revisar el diff completo contra el checklist y reportar al dueño del proyecto. **No hacer commit.**

## Dependencias y paralelismo

```txt
Fase 0 (Opus) ──► Fase 1 (Sonnet) ──► Fase 2 (Opus) ──► Fase 4 (Opus)
                          └─────────► Fase 3 (Sonnet) ──┘
```

Fase 3 puede escribir pruebas de servicio y la colección Postman mientras corre Fase 2, porque las firmas están fijadas arriba; las pruebas de controller se cierran cuando Fase 2 termine.

## Fuera de alcance

- Crear grupos (`grupo_estudio`, US-04) o cualquier lógica que use `coach_id`.
- Perfil público del coach consultable por otros usuarios (el ERD dice "perfil público" en `presentacion`, pero no hay historia ni endpoint definido).
- Verificación/aprobación de coaches, cambio de rol, edición de `usuario.nombre_completo`, correo o contraseña.
- Reglas por rol declaradas en `SecurityConfig` (JWT); la pertenencia y propiedad siguen validándose en el servicio.

## Checklist antes de entregar

- [x] `CoachProfile` usa `@Table(name = "coach")` y PK/FK compartida `usuario_id` sin `@GeneratedValue`.
- [x] Solo existen las columnas del ERD; `organizacion_club` y `presentacion` admiten `null`.
- [x] `GET/PUT /api/users/me` responde según el rol de la sesión.
- [x] Un COACH no puede crear/modificar `practicante` ni un PRACTICANTE `coach`.
- [x] El body no permite enviar `usuarioId` ni cambiar el rol.
- [x] No se devuelve `passwordHash`.
- [x] Las pruebas de practicante existentes pasan sin cambiar sus mensajes.
- [x] `./mvnw clean compile` pasa; resultado real de `./mvnw test` reportado.
- [x] Contrato (`03`), modelo (`04`) y contexto IA (`08`) actualizados.
- [x] Colección Postman de coach creada y caso 12 de practicante ajustado.
- [x] No se hizo commit sin autorización.
