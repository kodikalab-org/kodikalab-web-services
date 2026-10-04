package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface AuthService {
    AuthResponse register(@NotNull @Valid RegisterRequest request);
}
