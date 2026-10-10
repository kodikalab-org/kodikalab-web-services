package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Código de recuperación de la cuenta, para recuperar el acceso sin correo electrónico (US-02, escenario alternativo).
 *
 * <p>No se guarda en ninguna tabla: es un HMAC-SHA256 de {@code correo + hash vigente de la contraseña}, calculado con
 * una clave derivada del secreto del servidor. Consecuencias:
 * <ul>
 *   <li>120 bits de entropía (24 caracteres Base32 en 6 grupos): no se puede adivinar ni calcular sin el secreto.</li>
 *   <li>Es de un solo uso por construcción: al cambiar la contraseña cambia el hash y el código anterior deja de valer.</li>
 *   <li>Existe para todas las cuentas, también las anteriores a esta función, sin migrar datos.</li>
 *   <li>Si se rota {@code JWT_SECRET}, los códigos cambian; cada titular puede consultar el nuevo con su contraseña.</li>
 * </ul>
 */
@Service
public class RecoveryCodeService {
    private static final String KEY_CONTEXT = "kodikalab:recovery-code:v1";
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int CODE_BYTES = 15;
    private static final int GROUP_SIZE = 4;

    private final byte[] key;

    public RecoveryCodeService(JwtService jwtService) {
        this.key = jwtService.deriveKey(KEY_CONTEXT);
    }

    /** Código vigente de la cuenta, por ejemplo {@code ABCD-EFGH-IJKL-MNOP-QRST-UVWX}. */
    public String codeFor(String email, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(email.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(passwordHash.getBytes(StandardCharsets.UTF_8));
            return group(base32(Arrays.copyOf(mac.doFinal(), CODE_BYTES)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 no está disponible", exception);
        }
    }

    /** Compara en tiempo constante; ignora mayúsculas, espacios y guiones del código recibido. */
    public boolean matches(String email, String passwordHash, String provided) {
        byte[] expected = normalize(codeFor(email, passwordHash)).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, normalize(provided).getBytes(StandardCharsets.UTF_8));
    }

    static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 31]);
        }
        return out.toString();
    }

    private static String group(String code) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < code.length(); i += GROUP_SIZE) {
            if (i > 0) {
                out.append('-');
            }
            out.append(code, i, Math.min(i + GROUP_SIZE, code.length()));
        }
        return out.toString();
    }
}
