# Tarea - US-02 Inicio de sesión seguro y acceso según rol

> Contrato implementado vigente: `docs/sdd/03-api-contracts.md`. Usa `usuario`, roles Java/HTTP/SQL `PRACTICANTE`/`COACH` y estados `ACTIVO`/`SUSPENDIDO`. Se conserva sesión HTTP, sin JWT; recuperación de acceso pendiente. Los ejemplos siguientes son exclusivamente de prueba.

## User Story

**US-02 - Inicio de sesión seguro y acceso según el rol de usuario**

Como usuario, quiero acceder a la plataforma de forma segura, para utilizar las funcionalidades disponibles según mi rol.

## Story Points

```txt
3
```

## Criterios de aceptación

### Escenario exitoso

**DADO** que una cuenta se encuentra activa, **CUANDO** el usuario ingresa sus credenciales y solicita iniciar sesión, **ENTONCES** el sistema valida su identidad, crea una sesión segura y dirige al espacio correspondiente a su rol.

### Escenario de error

**DADO** que se intenta iniciar sesión, **CUANDO** las credenciales no son válidas o la cuenta no se encuentra habilitada, **ENTONCES** el sistema deniega el acceso, informa la condición detectada y permite reintentar o iniciar la recuperación de acceso.

### Escenario alternativo

**DADO** que el usuario no recuerda sus credenciales, **CUANDO** solicita recuperar el acceso, **ENTONCES** el sistema verifica la titularidad de la cuenta, permite definir nuevas credenciales y habilita un nuevo intento de inicio de sesión.

## Objetivo técnico

Implementar el inicio de sesión dentro del módulo `auth`, validando credenciales contra los usuarios registrados en el módulo `users` y preparando la respuesta según el rol del usuario.

Durante esta historia no es obligatorio cerrar todos los endpoints con JWT si el equipo aún mantiene seguridad abierta para desarrollo, pero sí debe quedar preparado el flujo de autenticación.

## Módulos involucrados

```txt
com.kodika.kodikalab.auth
com.kodika.kodikalab.users
com.kodika.kodikalab.security
com.kodika.kodikalab.common
```

## Archivos esperados

### Módulo `auth`

```txt
src/main/java/com/kodika/kodikalab/auth/AuthController.java
src/main/java/com/kodika/kodikalab/auth/AuthService.java
src/main/java/com/kodika/kodikalab/auth/AuthServiceImpl.java
src/main/java/com/kodika/kodikalab/auth/dto/LoginRequest.java
src/main/java/com/kodika/kodikalab/auth/dto/AuthResponse.java
```

### Módulo `users`

```txt
src/main/java/com/kodika/kodikalab/users/User.java
src/main/java/com/kodika/kodikalab/users/UserRepository.java
src/main/java/com/kodika/kodikalab/users/UserStatus.java
src/main/java/com/kodika/kodikalab/users/Role.java
```

### Módulo `security`

```txt
src/main/java/com/kodika/kodikalab/config/SecurityConfig.java
```

O, si se migra a modular:

```txt
src/main/java/com/kodika/kodikalab/security/SecurityConfig.java
```

### Módulo `common` opcional

```txt
src/main/java/com/kodika/kodikalab/common/exception/UnauthorizedException.java
src/main/java/com/kodika/kodikalab/common/exception/BadRequestException.java
src/main/java/com/kodika/kodikalab/common/exception/GlobalExceptionHandler.java
```

## Endpoint a implementar

```http
POST /api/auth/login
```

### Request esperado

```json
{
  "email": "test@gmail.com",
  "password": "Password123"
}
```

### Response exitoso sugerido

```json
{
  "message": "Inicio de sesión exitoso",
  "email": "test@gmail.com",
  "role": "PRACTICANTE"
}
```

Si se decide generar JWT en esta historia, la respuesta puede incluir:

```json
{
  "message": "Inicio de sesión exitoso",
  "token": "jwt-token",
  "email": "test@gmail.com",
  "role": "PRACTICANTE"
}
```

## Reglas de negocio

- El correo es obligatorio.
- El correo debe tener formato válido.
- La contraseña es obligatoria.
- El usuario debe existir.
- La cuenta debe estar activa.
- La contraseña ingresada debe coincidir con el hash almacenado.
- Nunca se debe devolver `passwordHash` en la respuesta.
- La respuesta debe incluir el rol del usuario para que el frontend pueda dirigir la experiencia.
- Si las credenciales son incorrectas, usar un mensaje genérico para no revelar si el correo existe.

Mensaje sugerido:

```txt
Credenciales inválidas
```

## Dependencias con US-01

Esta historia depende de que exista previamente:

- Entidad `User`.
- `UserRepository`.
- Contraseña almacenada como hash.
- `PasswordEncoder` configurado.
- Registro de usuarios funcional o usuarios creados manualmente en base de datos para pruebas.

Si US-01 aún no está implementada, se puede probar US-02 creando usuarios de prueba directamente en la base de datos con contraseña hasheada, aunque lo ideal es usar el registro.

## Lógica esperada en el servicio

Flujo sugerido para `AuthServiceImpl.login`:

1. Recibir `LoginRequest`.
2. Buscar usuario por email.
3. Si no existe, devolver error de credenciales inválidas.
4. Verificar que el usuario esté en estado `ACTIVO`.
5. Comparar contraseña plana contra `passwordHash` usando `PasswordEncoder.matches`.
6. Si no coincide, devolver error de credenciales inválidas.
7. Construir `AuthResponse` con mensaje, email y rol.
8. Si se implementa JWT, generar token y agregarlo al response.

## Validaciones sugeridas en DTO

```java
@NotBlank
@Email
private String email;

@NotBlank
private String password;
```

## Consideraciones de seguridad

- No devolver si el error fue por correo inexistente o contraseña incorrecta; usar `Credenciales inválidas`.
- No devolver el hash de contraseña.
- No registrar contraseñas en logs.
- Mantener `PasswordEncoder` como BCrypt.
- Si se genera JWT, usar el secreto desde configuración/env y no hardcodearlo.

## Pruebas manuales mínimas

### Caso exitoso

Enviar credenciales correctas de un usuario activo.

Resultado esperado:

- HTTP `200 OK`.
- Mensaje `Inicio de sesión exitoso`.
- Respuesta con email y rol.
- Si aplica, respuesta con token JWT.

### Contraseña incorrecta

Enviar correo existente con contraseña incorrecta.

Resultado esperado:

- HTTP `401 Unauthorized` o equivalente.
- Mensaje `Credenciales inválidas`.
- No se devuelve información sensible.

### Usuario inexistente

Enviar correo no registrado.

Resultado esperado:

- HTTP `401 Unauthorized` o equivalente.
- Mensaje `Credenciales inválidas`.
- No se revela si el correo existe.

### Cuenta inactiva

Intentar iniciar sesión con un usuario en estado distinto de `ACTIVO`.

Resultado esperado:

- HTTP `401 Unauthorized` o `403 Forbidden`.
- Mensaje indicando que la cuenta no está habilitada o mensaje genérico según decisión del equipo.

## Comando de verificación

```bash
./mvnw clean compile
```

En Windows PowerShell:

```powershell
.\mvnw.cmd clean compile
```

## Notas

- Esta tarea corresponde únicamente a **US-02**.
- No implementar registro en esta tarea; registro corresponde a **US-01**.
- No implementar perfil competitivo en esta tarea; perfil corresponde a **US-03**.
- No modificar código de equipos en esta rama.
- Mantener el diseño como monolito modular.
