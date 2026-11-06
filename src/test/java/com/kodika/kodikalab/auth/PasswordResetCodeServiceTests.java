package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.support.MutableClock;
import com.kodika.kodikalab.support.TestJwt;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetCodeServiceTests {
    private static final String EMAIL = "test@gmail.com";
    private static final String HASH = "$2a$10$hash-de-prueba-uno";
    // Inicio exacto de una ventana de 10 minutos: facilita razonar sobre los bordes.
    private static final Instant WINDOW_START = Instant.ofEpochSecond(PasswordResetCodeService.WINDOW_SECONDS * 3_000_000L);

    private MutableClock clock;
    private PasswordResetCodeService codes;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(WINDOW_START);
        codes = new PasswordResetCodeService(key("kodikalab:password-reset-code:v1"), clock);
    }

    private static byte[] key(String context) {
        return TestJwt.service().deriveKey(context);
    }

    @Test
    void producesExactlySixDigitsIncludingLeadingZeros() {
        boolean sawLeadingZero = false;
        for (int i = 0; i < 400; i++) {
            String code = codes.currentCode("persona" + i + "@gmail.com", HASH);
            assertThat(code).matches("\\d{6}");
            sawLeadingZero |= code.startsWith("0");
        }
        assertThat(sawLeadingZero).as("con 400 correos algún código empieza con cero").isTrue();
    }

    @Test
    void theSameCodeIsReturnedAllThroughTheWindow() {
        String first = codes.currentCode(EMAIL, HASH);
        clock.advance(Duration.ofSeconds(PasswordResetCodeService.WINDOW_SECONDS - 1));
        assertThat(codes.currentCode(EMAIL, HASH)).isEqualTo(first);
    }

    @Test
    void theCodeChangesWhenTheNextWindowStarts() {
        String first = codes.currentCode(EMAIL, HASH);
        clock.advance(Duration.ofSeconds(PasswordResetCodeService.WINDOW_SECONDS));
        assertThat(codes.currentCode(EMAIL, HASH)).isNotEqualTo(first);
    }

    @Test
    void aCodeStillWorksInTheNextWindowButNotTwoWindowsLater() {
        String code = codes.currentCode(EMAIL, HASH);
        assertThat(codes.matches(EMAIL, HASH, code)).isTrue();
        clock.advance(Duration.ofSeconds(PasswordResetCodeService.WINDOW_SECONDS));
        assertThat(codes.matches(EMAIL, HASH, code)).as("ventana anterior").isTrue();
        clock.advance(Duration.ofSeconds(PasswordResetCodeService.WINDOW_SECONDS));
        assertThat(codes.matches(EMAIL, HASH, code)).as("dos ventanas atrás").isFalse();
    }

    @Test
    void aCodeIssuedAtTheEndOfAWindowKeepsWorkingForAtLeastTenMinutes() {
        clock.advance(Duration.ofSeconds(PasswordResetCodeService.WINDOW_SECONDS - 1));
        String code = codes.currentCode(EMAIL, HASH);
        clock.advance(Duration.ofSeconds(PasswordResetCodeService.WINDOW_SECONDS));
        assertThat(codes.matches(EMAIL, HASH, code)).isTrue();
    }

    @Test
    void theCodeStopsWorkingAsSoonAsThePasswordChanges() {
        String code = codes.currentCode(EMAIL, HASH);
        assertThat(codes.matches(EMAIL, "$2a$10$hash-de-prueba-dos", code)).isFalse();
    }

    @Test
    void theCodeBelongsToOneEmailAndOneServerKey() {
        String code = codes.currentCode(EMAIL, HASH);
        assertThat(codes.matches("otra@gmail.com", HASH, code)).isFalse();
        PasswordResetCodeService otherKey = new PasswordResetCodeService(key("otro-contexto"), clock);
        assertThat(otherKey.matches(EMAIL, HASH, code)).isFalse();
        assertThat(otherKey.currentCode(EMAIL, HASH)).isNotEqualTo(code);
    }

    @Test
    void spacesAreIgnoredButOtherFormatsAreRejected() {
        String code = codes.currentCode(EMAIL, HASH);
        assertThat(codes.matches(EMAIL, HASH, " " + code.substring(0, 3) + " " + code.substring(3) + " ")).isTrue();
        assertThat(codes.matches(EMAIL, HASH, code.substring(0, 5))).isFalse();
        assertThat(codes.matches(EMAIL, HASH, code + "0")).isFalse();
        assertThat(codes.matches(EMAIL, HASH, "abcdef")).isFalse();
        assertThat(codes.matches(EMAIL, HASH, "")).isFalse();
        assertThat(codes.matches(EMAIL, HASH, null)).isFalse();
    }

    @Test
    void aWrongSixDigitCodeIsRejected() {
        String code = codes.currentCode(EMAIL, HASH);
        String wrong = String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
        assertThat(codes.matches(EMAIL, HASH, wrong)).isFalse();
    }
}
