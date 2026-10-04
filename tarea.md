# Tarea - US-01 Registro de cuenta con asignación de rol

## User Story

**US-01 - Registro de cuenta con asignación de rol**

Como usuario nuevo de KodikaLab, quiero registrarme en la plataforma seleccionando mi rol y usando mi correo institucional, para acceder al entorno de entrenamiento de programación competitiva.

## Story Points

```txt
5
```

## Criterios de aceptación

### Escenario exitoso

**DADO** que el usuario se encuentra en la pantalla de registro, **CUANDO** ingresa su nombre, correo y contraseña, selecciona el rol `Practicante` y presiona `Crear cuenta`, **ENTONCES** el sistema registra la cuenta, muestra el mensaje `Registro exitoso` y permite continuar hacia el inicio de sesión.

### Escenario de error

**DADO** que el usuario está en el formulario de registro, **CUANDO** ingresa un correo que ya se encuentra registrado y hace clic en `Crear cuenta`, **ENTONCES** el sistema bloquea la creación y muestra el mensaje `El correo institucional ya está vinculado a una cuenta existente`.

### Escenario alternativo

**DADO** que el usuario ingresa sus datos válidos pero introduce una contraseña débil como `12345`, **CUANDO** pulsa el botón `Crear cuenta`, **ENTONCES** el sistema rechaza la solicitud y muestra el mensaje `La contraseña debe contener al menos 8 caracteres, una mayúscula y un número`.

## Objetivo técnico

Implementar el registro de usuarios dentro del monolito modular, usando el módulo `auth` para el caso de uso de registro y el módulo `users` para la entidad persistente del usuario.

## Módulos involucrados

```txt
com.kodika.kodikalab.auth
com.kodika.kodikalab.users
com.kodika.kodikalab.common
```

## Archivos esperados

### Módulo `users`

```txt
src/main/java/com/kodika/kodikalab/users/User.java
src/main/java/com/kodika/kodikalab/users/Role.java
src/main/java/com/kodika/kodikalab/users/UserStatus.java
src/main/java/com/kodika/kodikalab/users/UserRepository.java
```

### Módulo `auth`

```txt
src/main/java/com/kodika/kodikalab/auth/AuthController.java
src/main/java/com/kodika/kodikalab/auth/AuthService.java
src/main/java/com/kodika/kodikalab/auth/AuthServiceImpl.java
src/main/java/com/kodika/kodikalab/auth/dto/RegisterRequest.java
src/main/java/com/kodika/kodikalab/auth/dto/AuthResponse.java
```

### Módulo `common` opcional

```txt
src/main/java/com/kodika/kodikalab/common/exception/BadRequestException.java
src/main/java/com/kodika/kodikalab/common/exception/ConflictException.java
src/main/java/com/kodika/kodikalab/common/exception/GlobalExceptionHandler.java
```

## Endpoint a implementar

```http
POST /api/auth/register
```

### Request esperado

```json
{
  "firstName": "Matias",
  "lastName": "Del Castillo",
  "email": "matias@upc.edu.pe",
  "password": "Password123",
  "role": "PRACTITIONER"
}
```

### Response exitoso sugerido

```json
{
  "message": "Registro exitoso",
  "email": "matias@upc.edu.pe",
  "role": "PRACTITIONER"
}
```

## Reglas de negocio

- El correo debe ser obligatorio.
- El correo debe tener formato válido.
- El correo no debe estar registrado previamente.
- La contraseña debe ser obligatoria.
- La contraseña debe tener mínimo 8 caracteres.
- La contraseña debe contener al menos una mayúscula.
- La contraseña debe contener al menos un número.
- El nombre y apellido deben ser obligatorios.
- El rol debe ser obligatorio.
- Solo se deben permitir roles definidos en el enum `Role`.
- La contraseña debe guardarse hasheada, nunca en texto plano.
- El usuario nuevo debe crearse con estado `ACTIVE`.

## Entidad `User`

Debe alinearse con la tabla `users` del modelo de datos.

Campos mínimos:

```txt
id
firstName
lastName
email
passwordHash
status
role
createdAt
```

Consideraciones:

- Usar `@Entity`.
- Usar `@Table(name = "users")`.
- Usar `jakarta.persistence`.
- Usar `GenerationType.IDENTITY`.
- `email` debe ser único.
- `role` y `status` pueden manejarse con `@Enumerated(EnumType.STRING)`.

## Repository

Crear `UserRepository` con:

```java
boolean existsByEmail(String email);
Optional<User> findByEmail(String email);
```

## Seguridad de contraseña

Agregar un bean de `PasswordEncoder` si aún no existe.

Sugerencia:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Durante esta US no es obligatorio activar JWT ni cerrar endpoints.

## Pasos de implementación

1. Revisar `docs/sdd/05-architecture.md` y confirmar estructura modular.
2. Crear paquete `users`.
3. Crear enums `Role` y `UserStatus`.
4. Crear entidad `User` alineada con `users`.
5. Crear `UserRepository`.
6. Crear paquete `auth`.
7. Crear DTO `RegisterRequest` con validaciones.
8. Crear DTO `AuthResponse`.
9. Crear `AuthService`.
10. Implementar `AuthServiceImpl.register`.
11. Crear `AuthController` con `POST /auth/register`.
12. Agregar `PasswordEncoder` si no existe.
13. Manejar error de correo duplicado.
14. Manejar error de contraseña débil.
15. Compilar el proyecto.
16. Probar el endpoint desde Swagger o Postman.

## Validaciones sugeridas en DTO

```java
@NotBlank
private String firstName;

@NotBlank
private String lastName;

@NotBlank
@Email
private String email;

@NotBlank
private String password;

@NotNull
private Role role;
```

La validación de fortaleza de contraseña puede implementarse en el servicio al inicio.

## Pruebas manuales mínimas

### Caso exitoso

Enviar un usuario nuevo con contraseña válida.

Resultado esperado:

- HTTP `201 Created` o `200 OK`.
- Mensaje `Registro exitoso`.
- Usuario persistido en PostgreSQL.
- Contraseña almacenada como hash.

### Correo duplicado

Enviar dos veces el mismo correo.

Resultado esperado:

- La segunda solicitud falla.
- No se crea un usuario duplicado.
- Se muestra mensaje de correo ya registrado.

### Contraseña débil

Enviar contraseña `12345`.

Resultado esperado:

- La solicitud falla.
- No se crea el usuario.
- Se muestra mensaje de contraseña débil.

## Comando de verificación

```bash
./mvnw clean compile
```

En Windows PowerShell:

```powershell
.\mvnw.cmd clean compile
```

## Notas

- No activar JWT todavía si el equipo sigue con seguridad abierta para desarrollo.
- No mezclar esta US con login. Login corresponde a US-02.
- No implementar perfil competitivo en esta tarea. Perfil corresponde a US-03.
- No modificar código de equipos en esta rama.
- Mantener el diseño como monolito modular.
