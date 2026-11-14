package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.PlatformSubmissionsResponse;
import com.kodika.kodikalab.assignments.dto.VerificationResponse;
import com.kodika.kodikalab.config.SecurityConfig;
import com.kodika.kodikalab.security.JwtService;
import com.kodika.kodikalab.support.Bearer;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de rol de {@link com.kodika.kodikalab.config.SecurityConfig} con tokens reales y sin base de datos. */
@WebMvcTest(SubmissionVerificationController.class)
@Import({SecurityConfig.class, JwtService.class, AssignmentExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=0123456789abcdef0123456789abcdef0123")
class SubmissionVerificationSecurityTests {
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtService jwt;
    @MockitoBean
    UserService users;
    @MockitoBean
    SubmissionVerificationService service;

    @BeforeEach
    void setUp() {
        when(service.verify(any(), any(), any())).thenReturn(
                new VerificationResponse(7, false, AssignmentStatus.SIN_INTENTOS, false, List.of()));
        when(service.platformSubmissions(any(), any())).thenReturn(
                new PlatformSubmissionsResponse(7, "h", 10, List.of()));
    }

    private String token(Role role, UserStatus status) {
        User user = new User();
        user.setId(role == Role.COACH ? 1 : 2);
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setStatus(status);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return jwt.issue(user).token();
    }

    @Test
    void practitionerCanVerifyAndListSubmissionsWithOrWithoutBody() throws Exception {
        String token = token(Role.PRACTICANTE, UserStatus.ACTIVO);

        mvc.perform(post("/problems/assigned/7/verification").with(Bearer.of(token))).andExpect(status().isOk());
        mvc.perform(get("/problems/assigned/7/platform-submissions").with(Bearer.of(token)))
                .andExpect(status().isOk());
    }

    @Test
    void coachIsForbiddenOnBothEndpoints() throws Exception {
        String token = token(Role.COACH, UserStatus.ACTIVO);

        mvc.perform(post("/problems/assigned/7/verification").with(Bearer.of(token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/problems/assigned/7/platform-submissions").with(Bearer.of(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mvc.perform(post("/problems/assigned/7/verification")).andExpect(status().isUnauthorized());
        mvc.perform(get("/problems/assigned/7/platform-submissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedPractitionerIsForbidden() throws Exception {
        String token = token(Role.PRACTICANTE, UserStatus.SUSPENDIDO);

        mvc.perform(post("/problems/assigned/7/verification").with(Bearer.of(token)))
                .andExpect(status().isForbidden());
    }
}
