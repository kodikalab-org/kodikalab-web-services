package com.kodika.kodikalab.profiles;

import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CurrentUserResolverTests {
    UserService users;
    CurrentUserResolver resolver;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        resolver = new CurrentUserResolver(users);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUserFromSession() {
        User coach = new User();
        coach.setEmail("coach@gmail.com");
        coach.setRole(Role.COACH);
        when(users.findByEmail("coach@gmail.com")).thenReturn(Optional.of(coach));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("coach@gmail.com", null, List.of()));

        assertThat(resolver.currentUser()).isSameAs(coach);
    }

    @Test
    void missingAuthenticationIsUnauthorized() {
        assertThatThrownBy(resolver::currentUser)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Debe iniciar sesión para gestionar su perfil");
        verifyNoInteractions(users);
    }

    @Test
    void anonymousAuthenticationIsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(resolver::currentUser).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(users);
    }

    @Test
    void sessionForDeletedAccountIsUnauthorized() {
        when(users.findByEmail("test@gmail.com")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("test@gmail.com", null, List.of()));

        assertThatThrownBy(resolver::currentUser).isInstanceOf(UnauthorizedException.class);
    }
}
