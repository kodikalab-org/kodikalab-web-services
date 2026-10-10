package com.kodika.kodikalab.security;

import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final JwtProperties PROPERTIES = new JwtProperties(TestJwt.SECRET, TestJwt.EXPIRATION_MILLIS, "kodikalab");

    private final JwtService service = new JwtService(PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));

    private static User user(Role role) {
        User user = new User();
        user.setId(7);
        user.setEmail("test@gmail.com");
        user.setPasswordHash("$2a$10$hash-que-no-debe-aparecer-en-el-token");
        user.setRole(role);
        return user;
    }

    private static JwtService at(Instant instant) {
        return new JwtService(PROPERTIES, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static String payloadOf(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    @Test
    void issuedTokenCarriesIdentityRoleAndExpiry() {
        var issued = service.issue(user(Role.COACH));
        assertThat(issued.tokenType()).isEqualTo("Bearer");
        assertThat(issued.expiresInSeconds()).isEqualTo(TestJwt.EXPIRATION_MILLIS / 1000);
        assertThat(service.parse(issued.token())).get().satisfies(claims -> {
            assertThat(claims.email()).isEqualTo("test@gmail.com");
            assertThat(claims.userId()).isEqualTo(7);
            assertThat(claims.role()).isEqualTo("COACH");
            assertThat(claims.expiresAt()).isEqualTo(NOW.plusMillis(TestJwt.EXPIRATION_MILLIS));
        });
    }

    @Test
    void tokenCarriesTheStampOfTheCurrentPasswordHash() {
        User account = user(Role.COACH);
        var claims = service.parse(service.issue(account).token()).orElseThrow();
        assertThat(claims.passwordStamp()).isEqualTo(service.passwordStamp(account)).hasSize(16);
        account.setPasswordHash("$2a$10$otro-hash-de-otra-contrasena");
        assertThat(service.passwordStamp(account)).isNotEqualTo(claims.passwordStamp());
    }

    @Test
    void stampDoesNotExposeTheHashAndToleratesAMissingOne() {
        User account = user(Role.COACH);
        assertThat(service.passwordStamp(account)).doesNotContain("2a").doesNotContain(account.getPasswordHash());
        account.setPasswordHash(null);
        assertThat(service.passwordStamp(account)).isEqualTo(service.passwordStamp(account)).isNotBlank();
    }

    @Test
    void derivedKeysAreStableSeparatedByContextAndBoundToTheSecret() {
        assertThat(service.deriveKey("recuperacion")).hasSize(32).isEqualTo(service.deriveKey("recuperacion"))
                .isNotEqualTo(service.deriveKey("otro-uso"));
        var other = new JwtService(new JwtProperties("otro-secreto-distinto-de-al-menos-32-caracteres", 60_000, "kodikalab"));
        assertThat(other.deriveKey("recuperacion")).isNotEqualTo(service.deriveKey("recuperacion"));
    }

    @Test
    void tokenNeverContainsThePasswordHash() {
        String token = service.issue(user(Role.PRACTICANTE)).token();
        assertThat(payloadOf(token)).doesNotContain("hash", "password", "$2a$");
    }

    @Test
    void everyTokenHasItsOwnIdentifier() {
        Set<String> payloads = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            payloads.add(payloadOf(service.issue(user(Role.COACH)).token()));
        }
        assertThat(payloads).hasSize(20);
    }

    @Test
    void tokenIsValidUntilItsExpirationAndRejectedAfterwards() {
        String token = service.issue(user(Role.COACH)).token();
        Duration lifetime = Duration.ofMillis(TestJwt.EXPIRATION_MILLIS);
        assertThat(at(NOW.plus(lifetime).minusSeconds(2)).parse(token)).isPresent();
        assertThat(at(NOW.plus(lifetime).plusSeconds(2)).parse(token)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = service.issue(user(Role.PRACTICANTE)).token();
        String[] parts = token.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payloadOf(token).replace("PRACTICANTE", "COACH").getBytes(StandardCharsets.UTF_8));
        assertThat(service.parse(parts[0] + "." + forged + "." + parts[2])).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        var other = new JwtService(new JwtProperties("otra-clave-distinta-de-al-menos-32-caracteres!!", 60_000, "kodikalab"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.parse(other.issue(user(Role.COACH)).token())).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        var other = new JwtService(new JwtProperties(TestJwt.SECRET, 60_000, "otro-emisor"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.parse(other.issue(user(Role.COACH)).token())).isEmpty();
    }

    @Test
    void unsignedTokenWithAlgNoneIsRejected() {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"test@gmail.com\",\"iss\":\"kodikalab\",\"exp\":"
                + NOW.plusSeconds(600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        assertThat(service.parse(header + "." + payload + ".")).isEmpty();
        assertThat(service.parse(header + "." + payload)).isEmpty();
    }

    @Test
    void tokenWithoutSubjectOrExpirationIsRejected() {
        SecretKey key = new SecretKeySpec(TestJwt.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String withoutSubject = Jwts.builder().issuer("kodikalab")
                .expiration(Date.from(NOW.plusSeconds(600))).signWith(key, Jwts.SIG.HS256).compact();
        String withoutExpiration = Jwts.builder().issuer("kodikalab").subject("test@gmail.com")
                .signWith(key, Jwts.SIG.HS256).compact();
        String complete = Jwts.builder().issuer("kodikalab").subject("test@gmail.com")
                .expiration(Date.from(NOW.plusSeconds(600))).signWith(key, Jwts.SIG.HS256).compact();
        assertThat(service.parse(complete)).as("control: el mismo token con todos los campos es válido").isPresent();
        assertThat(service.parse(withoutSubject)).isEmpty();
        assertThat(service.parse(withoutExpiration)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "no-es-un-jwt", "a.b.c", "a.b", "....", "Bearer abc"})
    void garbageIsRejectedWithoutThrowing(String token) {
        assertThat(service.parse(token)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingSecretFailsFastWithInstructions(String secret) {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(secret, 1000, "kodikalab")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("openssl rand -hex 32");
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("corta", 1000, "kodikalab")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("demasiado corto");
    }

    @Test
    void exampleSecretFromEnvExampleIsRejected() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(
                "CHANGE_ME_SUPER_SECRET_KEY_FOR_LOCAL_DEVELOPMENT", 1000, "kodikalab")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(".env.example");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void nonPositiveLifetimeFailsFast(long expiration) {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(TestJwt.SECRET, expiration, "kodikalab")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("jwt.expiration");
    }

    @Test
    void secretAndTokenNeverAppearInToStringOutput() {
        assertThat(PROPERTIES.toString()).doesNotContain(TestJwt.SECRET).contains("REDACTED");
        var issued = service.issue(user(Role.COACH));
        assertThat(issued.toString()).doesNotContain(issued.token());
    }
}
