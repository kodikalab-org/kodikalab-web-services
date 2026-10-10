package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RecoveryCodeRequest;
import com.kodika.kodikalab.auth.dto.RecoveryRequest;
import com.kodika.kodikalab.auth.dto.RecoveryResponse;
import com.kodika.kodikalab.config.OpenApiConfig;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.auth.dto.LoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro e inicio de sesión. Endpoints públicos: no requieren token.")
@SecurityRequirements
public class AuthController {
    private final AuthService authService;
    private final AccountRecoveryService recoveryService;

    public AuthController(AuthService authService, AccountRecoveryService recoveryService) {
        this.authService = authService;
        this.recoveryService = recoveryService;
    }

    @Operation(summary = "Registrar una cuenta (US-01)",
            description = "Crea una cuenta PRACTICANTE o COACH. El registro no inicia sesión. La respuesta incluye el "
                    + "`recoveryCode` de la cuenta: el titular debe guardarlo, porque permite recuperar el acceso sin correo.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Iniciar sesión (US-02)",
            description = "Valida las credenciales y devuelve un token JWT (`token`, `tokenType`, `expiresIn` en "
                    + "segundos) más el rol de la cuenta para que el cliente dirija al espacio correspondiente.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Recuperar el acceso sin correo (US-02, escenario alternativo)",
            description = "El titular verifica su identidad con el `recoveryCode` de su cuenta y define una contraseña "
                    + "nueva; después puede iniciar sesión de nuevo. El código es de un solo uso: la respuesta trae el "
                    + "código nuevo y el anterior deja de servir. Los tokens emitidos antes del cambio también dejan de "
                    + "valer. Cualquier fallo de verificación responde igual (401), sin revelar si el correo existe.")
    @PostMapping("/recovery")
    public ResponseEntity<RecoveryResponse> recover(@Valid @RequestBody RecoveryRequest request) {
        return ResponseEntity.ok(recoveryService.recover(request));
    }

    @Operation(summary = "Consultar el código de recuperación vigente",
            description = "Requiere token y la contraseña actual. Sirve para las cuentas creadas antes de esta función "
                    + "o cuando el titular perdió su código.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/recovery-code")
    public ResponseEntity<RecoveryResponse> recoveryCode(@Valid @RequestBody RecoveryCodeRequest request) {
        return ResponseEntity.ok(recoveryService.showRecoveryCode(request));
    }
}
