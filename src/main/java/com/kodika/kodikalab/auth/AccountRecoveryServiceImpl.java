package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.RecoveryCodeRequest;
import com.kodika.kodikalab.auth.dto.RecoveryRequest;
import com.kodika.kodikalab.auth.dto.RecoveryResponse;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AccountRecoveryServiceImpl implements AccountRecoveryService {
    // Hash BCrypt ficticio y no secreto: con una cuenta ausente se calcula igual el código, para no distinguirla.
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final String INVALID_RECOVERY = "Datos de recuperación inválidos";
    private static final String INVALID_CREDENTIALS = "Credenciales inválidas";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryCodeService recoveryCodes;
    private final CurrentUserResolver currentUserResolver;

    public AccountRecoveryServiceImpl(UserService userService, PasswordEncoder passwordEncoder,
                                      RecoveryCodeService recoveryCodes, CurrentUserResolver currentUserResolver) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.recoveryCodes = recoveryCodes;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public RecoveryResponse recover(RecoveryRequest request) {
        // BCrypt limita la entrada a 72 bytes: la contraseña nueva nunca se trunca en silencio.
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8");
        }
        User user = userService.findByEmail(request.email()).orElse(null);
        boolean usable = user != null && user.getPasswordHash() != null && user.getRole() != null;
        String email = usable ? user.getEmail() : request.email();
        String currentHash = usable ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
        boolean codeMatches = recoveryCodes.matches(email, currentHash, request.recoveryCode());
        if (!usable || !codeMatches || user.getStatus() != UserStatus.ACTIVO) {
            throw new UnauthorizedException(INVALID_RECOVERY);
        }
        String newHash = passwordEncoder.encode(request.newPassword());
        // Compare-and-set: dos solicitudes simultáneas con el mismo código no pueden ganar las dos.
        if (!userService.replacePasswordHash(user.getId(), currentHash, newHash)) {
            throw new UnauthorizedException(INVALID_RECOVERY);
        }
        return new RecoveryResponse("Contraseña actualizada. Inicie sesión con la nueva contraseña y guarde su nuevo "
                + "código de recuperación: el anterior ya no sirve.", recoveryCodes.codeFor(email, newHash));
    }

    @Override
    public RecoveryResponse showRecoveryCode(RecoveryCodeRequest request) {
        User user = currentUserResolver.currentUser();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72 || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return new RecoveryResponse("Código de recuperación vigente. Guárdelo en un lugar seguro: permite recuperar "
                + "el acceso sin correo y cambia cada vez que se cambia la contraseña.",
                recoveryCodes.codeFor(user.getEmail(), user.getPasswordHash()));
    }
}
