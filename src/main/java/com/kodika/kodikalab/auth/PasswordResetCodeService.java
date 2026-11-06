package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.security.JwtService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Código de 6 dígitos que se envía por correo para restablecer la contraseña.
 *
 * <p>No se guarda en ninguna tabla (el ERD no cambia): es un HMAC-SHA256 de
 * {@code correo + hash vigente de la contraseña + ventana de tiempo}, con una clave derivada del secreto del
 * servidor, reducido a 6 dígitos. Consecuencias:
 * <ul>
 *   <li>Es de un solo uso por construcción: al cambiar la contraseña cambia el hash y el código deja de valer.</li>
 *   <li>Vence solo: la ventana dura 10 minutos y se aceptan la actual y la anterior, así que un código vale como
 *       mínimo 10 minutos y como máximo 20.</li>
 *   <li>Pedir otro código dentro de la misma ventana devuelve el mismo número.</li>
 *   <li>Con solo 6 dígitos no resiste fuerza bruta por sí mismo: la protección es el límite de intentos de
 *       {@link PasswordResetAttemptLimiter}.</li>
 * </ul>
 */
@Service
public class PasswordResetCodeService {
    static final long WINDOW_SECONDS = 600;
    private static final String KEY_CONTEXT = "kodikalab:password-reset-code:v1";
    private static final int DIGITS = 6;
    private static final int MODULUS = 1_000_000;

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public PasswordResetCodeService(JwtService jwtService) {
        this(jwtService.deriveKey(KEY_CONTEXT), Clock.systemUTC());
    }

    PasswordResetCodeService(byte[] key, Clock clock) {
        this.key = key.clone();
        this.clock = clock;
    }

    /** Código vigente de la cuenta en este momento, por ejemplo {@code 483920}. */
    public String currentCode(String email, String passwordHash) {
        return codeFor(email, passwordHash, currentWindow());
    }

    /**
     * Compara en tiempo constante contra la ventana actual y la anterior; ignora los espacios del código recibido.
     * Siempre calcula las dos ventanas para no filtrar cuál coincidió.
     */
    public boolean matches(String email, String passwordHash, String provided) {
        String candidate = provided == null ? "" : provided.replaceAll("\\s+", "");
        if (candidate.length() != DIGITS) {
            // Se calcula igual para que el tiempo no dependa del formato recibido.
            candidate = "x".repeat(DIGITS);
        }
        byte[] received = candidate.getBytes(StandardCharsets.UTF_8);
        long window = currentWindow();
        boolean current = MessageDigest.isEqual(codeFor(email, passwordHash, window).getBytes(StandardCharsets.UTF_8),
                received);
        boolean previous = MessageDigest.isEqual(
                codeFor(email, passwordHash, window - 1).getBytes(StandardCharsets.UTF_8), received);
        return current | previous;
    }

    private long currentWindow() {
        return clock.instant().getEpochSecond() / WINDOW_SECONDS;
    }

    private String codeFor(String email, String passwordHash, long window) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(email.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(passwordHash.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(ByteBuffer.allocate(Long.BYTES).putLong(window).array());
            byte[] digest = mac.doFinal();
            int value = ((digest[0] & 0x7F) << 24) | ((digest[1] & 0xFF) << 16) | ((digest[2] & 0xFF) << 8)
                    | (digest[3] & 0xFF);
            return String.format("%0" + DIGITS + "d", value % MODULUS);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 no está disponible", exception);
        }
    }
}
