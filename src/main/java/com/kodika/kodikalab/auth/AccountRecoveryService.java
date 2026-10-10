package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.RecoveryCodeRequest;
import com.kodika.kodikalab.auth.dto.RecoveryRequest;
import com.kodika.kodikalab.auth.dto.RecoveryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Recuperación de acceso sin correo electrónico (US-02, escenario alternativo). */
public interface AccountRecoveryService {

    /**
     * Verifica la titularidad con el código de recuperación, define la contraseña nueva y devuelve el código nuevo.
     * Cualquier fallo de verificación responde igual, sin revelar si la cuenta existe.
     */
    RecoveryResponse recover(@NotNull @Valid RecoveryRequest request);

    /** Devuelve el código vigente de la cuenta autenticada, previa confirmación de su contraseña actual. */
    RecoveryResponse showRecoveryCode(@NotNull @Valid RecoveryCodeRequest request);
}
