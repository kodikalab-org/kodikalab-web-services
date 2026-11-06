package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.PasswordResetConfirmRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetResponse;
import com.kodika.kodikalab.auth.dto.PasswordResetVerifyRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class PasswordResetServiceImpl implements PasswordResetService {
    // Hash BCrypt ficticio y no secreto: con una cuenta ausente se calcula igual el código, para no distinguirla.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    static final String INVALID_CODE = "Código inválido o vencido";
    static final String REQUEST_ACCEPTED =
            "Si el correo está registrado, te enviamos un código para restablecer tu contraseña";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetCodeService codes;
    private final PasswordResetAttemptLimiter limiter;
    private final PasswordResetMailer mailer;

    public PasswordResetServiceImpl(UserService userService, PasswordEncoder passwordEncoder,
                                    PasswordResetCodeService codes, PasswordResetAttemptLimiter limiter,
                                    PasswordResetMailer mailer) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.codes = codes;
        this.limiter = limiter;
        this.mailer = mailer;
    }

    @Override
    public PasswordResetResponse requestCode(PasswordResetRequest request) {
        // Es una condición del servidor, no de la cuenta: responder 503 no revela qué correos existen.
        if (!mailer.isConfigured()) {
            throw new ServiceUnavailableException("El envío de correos no está configurado");
        }
        limiter.acquireSend(request.email());
        User user = userService.findByEmail(request.email()).orElse(null);
        if (isEligible(user)) {
            mailer.sendCode(user.getEmail(), user.getFullName(), codes.currentCode(user.getEmail(),
                    user.getPasswordHash()));
        }
        return new PasswordResetResponse(REQUEST_ACCEPTED);
    }

    @Override
    public PasswordResetResponse verifyCode(PasswordResetVerifyRequest request) {
        limiter.checkNotLocked(request.email());
        verified(request.email(), request.code());
        return new PasswordResetResponse("Código verificado. Ya puedes crear tu nueva contraseña");
    }

    @Override
    public PasswordResetResponse resetPassword(PasswordResetConfirmRequest request) {
        // BCrypt limita la entrada a 72 bytes: la contraseña nueva nunca se trunca en silencio.
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8");
        }
        limiter.checkNotLocked(request.email());
        User user = verified(request.email(), request.code());
        String currentHash = user.getPasswordHash();
        String newHash = passwordEncoder.encode(request.newPassword());
        // Compare-and-set: dos solicitudes simultáneas con el mismo código no pueden ganar las dos.
        if (!userService.replacePasswordHash(user.getId(), currentHash, newHash)) {
            throw new UnauthorizedException(INVALID_CODE);
        }
        limiter.clear(request.email());
        return new PasswordResetResponse("Contraseña actualizada. Inicia sesión con tu nueva contraseña");
    }

    /** Devuelve la cuenta si el código es correcto; si no, cuenta el intento fallido y responde siempre igual. */
    private User verified(String email, String code) {
        User user = userService.findByEmail(email).orElse(null);
        boolean eligible = isEligible(user);
        String accountEmail = eligible ? user.getEmail() : email;
        String hash = eligible ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
        boolean matches = codes.matches(accountEmail, hash, code);
        if (!eligible || !matches) {
            limiter.recordFailure(email);
            throw new UnauthorizedException(INVALID_CODE);
        }
        return user;
    }

    private boolean isEligible(User user) {
        return user != null && user.getPasswordHash() != null && user.getRole() != null
                && user.getStatus() == UserStatus.ACTIVO;
    }
}
