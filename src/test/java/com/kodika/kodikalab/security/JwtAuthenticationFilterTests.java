package com.kodika.kodikalab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {
    private final JwtService jwt = TestJwt.service();
    private final UserService users = mock(UserService.class);
    private final FilterChain chain = mock(FilterChain.class);
    private JwtAuthenticationFilter filter;
    private User account;

    @BeforeEach
    void setUp() {
        var paths = PathPatternRequestMatcher.withDefaults();
        filter = new JwtAuthenticationFilter(jwt, users, new RestAccessDeniedHandler(new ObjectMapper()),
                new OrRequestMatcher(paths.matcher(HttpMethod.POST, "/auth/login")));
        account = new User();
        account.setId(3);
        account.setEmail("test@gmail.com");
        account.setRole(Role.PRACTICANTE);
        account.setStatus(UserStatus.ACTIVO);
        when(users.findByEmail("test@gmail.com")).thenReturn(Optional.of(account));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest get(String authorization) {
        var request = new MockHttpServletRequest("GET", "/teams");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private Authentication run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        final Authentication[] seen = new Authentication[1];
        FilterChain capturing = (req, res) -> seen[0] = SecurityContextHolder.getContext().getAuthentication();
        filter.doFilter(request, response, capturing);
        return seen[0];
    }

    private String token() {
        return jwt.issue(account).token();
    }

    @Test
    void validTokenAuthenticatesWithTheEmailAndTheRoleStoredInTheDatabase() throws Exception {
        Authentication authentication = run(get("Bearer " + token()), new MockHttpServletResponse());
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("test@gmail.com");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_PRACTICANTE");
    }

    @Test
    void authorityComesFromTheDatabaseNotFromTheRoleClaim() throws Exception {
        account.setRole(Role.COACH);
        String tokenIssuedAsCoach = token();
        account.setRole(Role.PRACTICANTE);
        Authentication authentication = run(get("Bearer " + tokenIssuedAsCoach), new MockHttpServletResponse());
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_PRACTICANTE");
    }

    @Test
    void tokenIssuedBeforeAPasswordChangeIsRejected() throws Exception {
        account.setPasswordHash("$2a$10$hash-anterior");
        String tokenBeforeTheChange = token();
        account.setPasswordHash("$2a$10$hash-nuevo-tras-recuperar-el-acceso");
        var request = get("Bearer " + tokenBeforeTheChange);
        assertThat(run(request, new MockHttpServletResponse())).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isEqualTo(true);
        assertThat(run(get("Bearer " + token()), new MockHttpServletResponse())).isNotNull();
    }

    @Test
    void schemeIsCaseInsensitiveAndSurroundingSpacesAreIgnored() throws Exception {
        assertThat(run(get("bearer   " + token() + "  "), new MockHttpServletResponse())).isNotNull();
    }

    @Test
    void requestWithoutCredentialsContinuesAnonymouslyWithoutMarkingTheTokenInvalid() throws Exception {
        var request = get(null);
        assertThat(run(request, new MockHttpServletResponse())).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNull();
        verifyNoInteractions(users);
    }

    @Test
    void otherAuthorizationSchemesAreIgnored() throws Exception {
        var request = get("Basic dGVzdDp0ZXN0");
        assertThat(run(request, new MockHttpServletResponse())).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNull();
    }

    @Test
    void invalidTokenContinuesAnonymouslyAndIsMarkedForTheEntryPoint() throws Exception {
        var request = get("Bearer esto.no.es-un-jwt");
        assertThat(run(request, new MockHttpServletResponse())).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isEqualTo(true);
        verifyNoInteractions(users);
    }

    @Test
    void validTokenOfAnUnknownAccountIsTreatedAsInvalid() throws Exception {
        when(users.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        var request = get("Bearer " + token());
        assertThat(run(request, new MockHttpServletResponse())).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isEqualTo(true);
    }

    @Test
    void accountWithoutRoleCannotAuthenticate() throws Exception {
        String token = token();
        account.setRole(null);
        assertThat(run(get("Bearer " + token), new MockHttpServletResponse())).isNull();
    }

    @Test
    void suspendedAccountGets403ImmediatelyAndTheChainIsNotInvoked() throws Exception {
        String token = token();
        account.setStatus(UserStatus.SUSPENDIDO);
        var response = new MockHttpServletResponse();
        filter.doFilter(get("Bearer " + token), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("La cuenta no está habilitada");
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void publicEndpointsIgnoreWhateverTokenTheClientAttaches() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.addHeader("Authorization", "Bearer basura");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE)).isNull();
    }
}
