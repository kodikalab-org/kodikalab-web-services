package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.auth.dto.LoginRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.security.JwtService;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AuthServiceImpl implements AuthService {
    // Non-secret BCrypt fixture: missing accounts still perform a password comparison.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String INVALID_CREDENTIALS = "Credenciales inválidas";
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RecoveryCodeService recoveryCodes;

    public AuthServiceImpl(UserService userService, PasswordEncoder passwordEncoder, JwtService jwtService,
                           RecoveryCodeService recoveryCodes) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.recoveryCodes = recoveryCodes;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        String fullName = request.firstName() + " " + request.lastName();
        if (fullName.length() > 150) {
            throw new BadRequestException("El nombre completo debe tener como máximo 150 caracteres");
        }
        // BCrypt limits inputs to 72 bytes; never truncate passwords silently.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        userService.createUser(fullName, request.email(), passwordHash, request.role());
        // El código de recuperación se entrega una sola vez; después se consulta con la contraseña (ver AccountRecoveryService).
        return new AuthResponse("Registro exitoso", request.email(), request.role(), null, null, null,
                recoveryCodes.codeFor(request.email(), passwordHash));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        User user = userService.findByEmail(request.email()).orElse(null);
        String hash = user == null || user.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !passwordMatches
                || user.getStatus() != UserStatus.ACTIVO || user.getRole() == null) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        JwtService.IssuedToken issued = jwtService.issue(user);
        return new AuthResponse("Inicio de sesión exitoso", user.getEmail(), user.getRole(),
                issued.token(), issued.tokenType(), issued.expiresInSeconds());
    }
}
