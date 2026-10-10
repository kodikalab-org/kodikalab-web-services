package com.kodika.kodikalab.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.*;

class LoginSessionServiceTests {
    private final LoginSessionService service = new LoginSessionService(new HttpSessionSecurityContextRepository());

    @AfterEach
    void clearThreadContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savesAuthenticatedIdentityAndRoleWithoutPassword() {
        var request = new MockHttpServletRequest();
        service.startSession("test@gmail.com", "PRACTICANTE", request, new MockHttpServletResponse());
        SecurityContext context = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().isAuthenticated()).isTrue();
        assertThat(context.getAuthentication().getName()).isEqualTo("test@gmail.com");
        assertThat(context.getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_PRACTICANTE");
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(String.class);
    }

    @Test
    void rotatesExistingSessionIdToPreventSessionFixation() {
        var request = new MockHttpServletRequest();
        String previous = request.getSession(true).getId();
        service.startSession("test.coach@gmail.com", "COACH", request, new MockHttpServletResponse());
        assertThat(request.getSession(false).getId()).isNotEqualTo(previous);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_COACH");
    }
}
