package com.kodika.kodikalab.security;

import com.kodika.kodikalab.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Emite y valida los tokens de acceso (JWT firmado con HMAC-SHA256). El token solo identifica a la cuenta
 * (correo, id y rol); nunca contiene contraseñas ni datos de negocio, y el rol efectivo siempre se vuelve a leer
 * de la base de datos en cada petición.
 */
@Service
public class JwtService {
    static final int MIN_SECRET_BYTES = 32;
    private static final String TOKEN_TYPE = "Bearer";
    private static final String ROLE_CLAIM = "role";
    private static final String USER_ID_CLAIM = "uid";
    private static final String PASSWORD_STAMP_CLAIM = "pwd";

    private final SecretKey key;
    private final String issuer;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret() == null ? "" : properties.secret().strip();
        if (secret.isEmpty()) {
            throw new IllegalStateException("JWT_SECRET no está configurado. Defina en las variables de entorno un "
                    + "secreto de al menos " + MIN_SECRET_BYTES + " caracteres (por ejemplo, generado con "
                    + "'openssl rand -hex 32'). Ver README.");
        }
        if (secret.toUpperCase(Locale.ROOT).startsWith("CHANGE_ME")) {
            throw new IllegalStateException("JWT_SECRET conserva el valor de ejemplo de .env.example. "
                    + "Reemplácelo por un secreto propio de al menos " + MIN_SECRET_BYTES + " caracteres.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET es demasiado corto: se requieren al menos "
                    + MIN_SECRET_BYTES + " caracteres.");
        }
        if (properties.expiration() <= 0) {
            throw new IllegalStateException("jwt.expiration debe ser mayor que cero (milisegundos).");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.issuer = properties.issuer();
        this.lifetime = Duration.ofMillis(properties.expiration());
        this.clock = clock;
    }

    /** Emite un token para la cuenta indicada; el rol y el id son informativos para el cliente. */
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .claim(USER_ID_CLAIM, user.getId())
                .claim(ROLE_CLAIM, user.getRole() == null ? null : user.getRole().name())
                .claim(PASSWORD_STAMP_CLAIM, passwordStamp(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, TOKEN_TYPE, lifetime.toSeconds());
    }

    /**
     * Valida firma, algoritmo, emisor y vigencia. Cualquier fallo (token mal formado, manipulado, sin firma,
     * firmado con otra clave o vencido) devuelve vacío: el llamador nunca debe distinguir la causa hacia afuera.
     */
    public Optional<TokenClaims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank() || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(subject, claims.get(USER_ID_CLAIM, Integer.class),
                    claims.get(ROLE_CLAIM, String.class), claims.get(PASSWORD_STAMP_CLAIM, String.class),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Huella corta del hash vigente de la contraseña. Va dentro del token y se compara en cada petición: al cambiar la
     * contraseña (por ejemplo, con la recuperación de acceso) los tokens emitidos antes dejan de valer.
     */
    public String passwordStamp(User user) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((user.getPasswordHash() == null ? "" : user.getPasswordHash()).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, 12));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    /**
     * Clave de 32 bytes derivada del secreto para un uso concreto (separación de dominios): quien la usa nunca ve el
     * secreto y una clave derivada para un fin no sirve para otro.
     */
    public byte[] deriveKey(String context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 no está disponible", exception);
        }
    }

    /** Token firmado más los datos que se devuelven al cliente. */
    public record IssuedToken(String token, String tokenType, long expiresInSeconds) {
        @Override
        public String toString() {
            return "IssuedToken[token=REDACTED, tokenType=" + tokenType + ", expiresInSeconds=" + expiresInSeconds + "]";
        }
    }

    /** Datos verificados de un token; {@code userId} y {@code role} pueden ser nulos en tokens ajenos al sistema. */
    public record TokenClaims(String email, Integer userId, String role, String passwordStamp, Instant expiresAt) {
    }
}
