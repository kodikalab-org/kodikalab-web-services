package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.security.JwtProperties;
import com.kodika.kodikalab.security.JwtService;
import com.kodika.kodikalab.support.TestJwt;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryCodeServiceTests {
    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private final RecoveryCodeService service = TestJwt.recoveryCodes();

    @Test
    void codeHasSixGroupsOfFourBase32Characters() {
        assertThat(service.codeFor("test@gmail.com", HASH)).matches("^[A-Z2-7]{4}(-[A-Z2-7]{4}){5}$");
    }

    @Test
    void sameAccountAndPasswordAlwaysGiveTheSameCode() {
        assertThat(service.codeFor("test@gmail.com", HASH)).isEqualTo(service.codeFor("test@gmail.com", HASH));
        assertThat(TestJwt.recoveryCodes().codeFor("test@gmail.com", HASH)).isEqualTo(service.codeFor("test@gmail.com", HASH));
    }

    @Test
    void codeChangesWithTheAccountTheHashAndTheServerSecret() {
        Set<String> codes = new HashSet<>();
        codes.add(service.codeFor("test@gmail.com", HASH));
        codes.add(service.codeFor("otro@gmail.com", HASH));
        codes.add(service.codeFor("test@gmail.com", HASH + "x"));
        var otherSecret = new RecoveryCodeService(new JwtService(
                new JwtProperties("otro-secreto-distinto-de-al-menos-32-caracteres", 60_000, "kodikalab")));
        codes.add(otherSecret.codeFor("test@gmail.com", HASH));
        assertThat(codes).hasSize(4);
    }

    @Test
    void emailAndHashAreNotConcatenatedAmbiguously() {
        assertThat(service.codeFor("a@gmail.com", "bc")).isNotEqualTo(service.codeFor("a@gmail.comb", "c"));
    }

    @Test
    void manyAccountsNeverCollide() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            codes.add(service.codeFor("test" + i + "@gmail.com", HASH));
        }
        assertThat(codes).hasSize(500);
    }

    @Test
    void matchesIgnoresCaseSpacesAndDashes() {
        String code = service.codeFor("test@gmail.com", HASH);
        assertThat(service.matches("test@gmail.com", HASH, code)).isTrue();
        assertThat(service.matches("test@gmail.com", HASH, code.toLowerCase())).isTrue();
        assertThat(service.matches("test@gmail.com", HASH, code.replace("-", ""))).isTrue();
        assertThat(service.matches("test@gmail.com", HASH, "  " + code.replace("-", " ") + "  ")).isTrue();
    }

    @Test
    void matchesRejectsACodeOfAnotherAccountOrPassword() {
        String code = service.codeFor("test@gmail.com", HASH);
        assertThat(service.matches("otro@gmail.com", HASH, code)).isFalse();
        assertThat(service.matches("test@gmail.com", HASH + "x", code)).isFalse();
        assertThat(service.matches("test@gmail.com", HASH, code.substring(0, code.length() - 1))).isFalse();
        assertThat(service.matches("test@gmail.com", HASH, code + "A")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "----", "AAAA-AAAA-AAAA-AAAA-AAAA-AAAA", "no-es-un-codigo"})
    void matchesRejectsGarbage(String provided) {
        assertThat(service.matches("test@gmail.com", HASH, provided)).isFalse();
    }
}
