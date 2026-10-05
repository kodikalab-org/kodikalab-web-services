package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.auth.dto.LoginRequest;
import com.kodika.kodikalab.security.LoginSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginSessionService loginSessionService;

    public AuthController(AuthService authService, LoginSessionService loginSessionService) {
        this.authService = authService;
        this.loginSessionService = loginSessionService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        AuthResponse result = authService.login(request);
        loginSessionService.startSession(result.email(), result.role().name(), servletRequest, servletResponse);
        return ResponseEntity.ok(result);
    }
}
