package com.kodika.kodikalab.profiles;

import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resuelve la cuenta {@code usuario} de la sesión actual. No valida el rol: cada
 * servicio de perfil verifica que el rol corresponda a su tabla.
 */
@Component
public class CurrentUserResolver {
    private static final String LOGIN_REQUIRED = "Debe iniciar sesión para gestionar su perfil";

    private final UserService userService;

    public CurrentUserResolver(UserService userService) {
        this.userService = userService;
    }

    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException(LOGIN_REQUIRED);
        }
        return userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException(LOGIN_REQUIRED));
    }
}
