package com.kodika.kodikalab.controller;

import com.kodika.kodikalab.dto.AuthResponse;
import com.kodika.kodikalab.dto.LoginRequest;
import com.kodika.kodikalab.dto.RegisterRequest;
import com.kodika.kodikalab.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("legacyAuthController")
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Historical scaffold retained; POST /auth/register is owned by the auth module.
    @Deprecated(forRemoval = false)
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
