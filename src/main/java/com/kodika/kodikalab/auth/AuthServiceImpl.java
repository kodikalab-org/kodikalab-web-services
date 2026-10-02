package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.users.UserService;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AuthServiceImpl implements AuthService {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        // BCrypt limits inputs to 72 bytes; never truncate passwords silently.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        userService.createUser(request.firstName(), request.lastName(), request.email(), passwordHash, request.role());
        return new AuthResponse("Registro exitoso", request.email(), request.role());
    }
}
