package com.kodika.kodikalab.support;

import com.kodika.kodikalab.auth.RecoveryCodeService;
import com.kodika.kodikalab.security.JwtProperties;
import com.kodika.kodikalab.security.JwtService;

/** Servicio JWT con una clave de pruebas para tests que no levantan el contexto de Spring. */
public final class TestJwt {
    public static final String SECRET = "clave-de-pruebas-kodikalab-solo-para-tests-0123456789";
    public static final long EXPIRATION_MILLIS = 3_600_000L;

    private TestJwt() {
    }

    public static JwtService service() {
        return new JwtService(new JwtProperties(SECRET, EXPIRATION_MILLIS, "kodikalab"));
    }

    public static RecoveryCodeService recoveryCodes() {
        return new RecoveryCodeService(service());
    }
}
