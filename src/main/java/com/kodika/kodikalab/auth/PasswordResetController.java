package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.PasswordResetConfirmRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetResponse;
import com.kodika.kodikalab.auth.dto.PasswordResetVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password-reset")
@Tag(name = "Restablecer contraseña", description = "Recuperación de la contraseña con un código enviado por correo. "
        + "Endpoints públicos: no requieren token.")
@SecurityRequirements
public class PasswordResetController {
    private final PasswordResetService passwordReset;

    public PasswordResetController(PasswordResetService passwordReset) {
        this.passwordReset = passwordReset;
    }

    @Operation(summary = "Pedir el código por correo (US-02, escenario alternativo)",
            description = "Envía un código de 6 dígitos al correo si pertenece a una cuenta activa. Responde 200 con el "
                    + "mismo mensaje exista o no la cuenta. Como mucho un envío por minuto por correo (429). Responde "
                    + "503 si el servidor no tiene el correo configurado.")
    @PostMapping("/request")
    public ResponseEntity<PasswordResetResponse> request(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok(passwordReset.requestCode(request));
    }

    @Operation(summary = "Comprobar el código recibido",
            description = "Verifica el código sin consumirlo. Un código incorrecto, vencido o de una cuenta inexistente "
                    + "responde igual (401). Tras 5 intentos fallidos en 15 minutos el correo queda bloqueado (429).")
    @PostMapping("/verify")
    public ResponseEntity<PasswordResetResponse> verify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return ResponseEntity.ok(passwordReset.verifyCode(request));
    }

    @Operation(summary = "Definir la contraseña nueva",
            description = "Vuelve a verificar el código y define la contraseña nueva, que cumple la misma política que el "
                    + "registro. El código es de un solo uso y los tokens emitidos antes del cambio dejan de valer.")
    @PostMapping("/confirm")
    public ResponseEntity<PasswordResetResponse> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return ResponseEntity.ok(passwordReset.resetPassword(request));
    }
}
