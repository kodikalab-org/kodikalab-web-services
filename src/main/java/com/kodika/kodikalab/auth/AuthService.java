package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface AuthService {
    AuthResponse register(@NotNull @Valid RegisterRequest request);
    AuthResponse login(@NotNull @Valid LoginRequest request);

    /** Emite un token nuevo para la cuenta autenticada en la petición actual (cuenta {@code ACTIVO}). */
    AuthResponse refresh();
}
