package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.PasswordResetConfirmRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetResponse;
import com.kodika.kodikalab.auth.dto.PasswordResetVerifyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Restablecimiento de contraseña con un código de 6 dígitos enviado por correo (US-02, escenario alternativo).
 * Tres pasos: pedir el código, comprobarlo y definir la contraseña nueva.
 */
public interface PasswordResetService {

    /**
     * Envía el código al correo si pertenece a una cuenta activa. La respuesta es la misma exista o no la cuenta,
     * para no revelar qué correos están registrados.
     */
    PasswordResetResponse requestCode(@NotNull @Valid PasswordResetRequest request);

    /** Comprueba el código sin consumirlo: sirve para avanzar al paso de la contraseña nueva. */
    PasswordResetResponse verifyCode(@NotNull @Valid PasswordResetVerifyRequest request);

    /** Vuelve a comprobar el código, define la contraseña nueva y deja de valer el código y los tokens anteriores. */
    PasswordResetResponse resetPassword(@NotNull @Valid PasswordResetConfirmRequest request);
}
