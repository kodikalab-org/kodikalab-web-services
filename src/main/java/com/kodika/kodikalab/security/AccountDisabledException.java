package com.kodika.kodikalab.security;

import org.springframework.security.access.AccessDeniedException;

/** La cuenta del token existe pero ya no está habilitada (por ejemplo, fue suspendida después de iniciar sesión). */
public class AccountDisabledException extends AccessDeniedException {
    public AccountDisabledException() {
        super("La cuenta no está habilitada");
    }
}
