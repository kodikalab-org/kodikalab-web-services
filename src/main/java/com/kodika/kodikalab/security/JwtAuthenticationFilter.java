package com.kodika.kodikalab.security;

import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica cada petición con el token {@code Authorization: Bearer <jwt>}, sin sesión HTTP.
 *
 * <ul>
 *   <li>Token ausente o ilegible: la petición sigue sin autenticar y las reglas de {@code SecurityConfig} deciden
 *       (401 en los recursos protegidos).</li>
 *   <li>Token válido de una cuenta inexistente: igual que si fuera inválido.</li>
 *   <li>Token válido de una cuenta que ya no está {@code ACTIVO}: 403 inmediato, sin esperar a que venza el token.</li>
 *   <li>En otro caso, la autoridad se deriva del rol guardado en la base de datos, nunca del contenido del token.</li>
 * </ul>
 *
 * No es un bean de Spring: lo construye {@code SecurityConfig} para que solo se ejecute dentro de la cadena de seguridad.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    static final String INVALID_TOKEN_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".INVALID_TOKEN";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;
    private final AccessDeniedHandler accessDeniedHandler;
    private final RequestMatcher publicEndpoints;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService,
                                   AccessDeniedHandler accessDeniedHandler, RequestMatcher publicEndpoints) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.accessDeniedHandler = accessDeniedHandler;
        this.publicEndpoints = publicEndpoints;
    }

    /** Los recursos públicos (login, registro, documentación) ignoran cualquier token que el cliente adjunte. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            Optional<User> account = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim())
                    .flatMap(claims -> userService.findByEmail(claims.email()))
                    .filter(user -> user.getRole() != null);
            if (account.isEmpty()) {
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
            } else if (account.get().getStatus() != UserStatus.ACTIVO) {
                SecurityContextHolder.clearContext();
                accessDeniedHandler.handle(request, response, new AccountDisabledException());
                return;
            } else {
                authenticate(account.get(), request);
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(User user, HttpServletRequest request) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
