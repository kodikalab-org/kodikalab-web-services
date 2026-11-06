package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.ProfileExceptionHandler;
import com.kodika.kodikalab.profiles.practitioner.dto.CodeforcesStatsResponse;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import com.kodika.kodikalab.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CodeforcesStatsControllerTests {
    CurrentUserResolver resolver = mock(CurrentUserResolver.class);
    CodeforcesStatsService service = mock(CodeforcesStatsService.class);
    MockMvc mvc;
    User user = new User();

    @BeforeEach
    void setUp() {
        when(resolver.currentUser()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new CodeforcesStatsController(resolver, service))
                .setControllerAdvice(new ProfileExceptionHandler()).build();
    }

    @Test
    void unlinkedContractContainsZerosAndEmptyArrays() throws Exception {
        when(service.getStats(user)).thenReturn(CodeforcesStatsResponse.unlinked());
        mvc.perform(get("/users/me/codeforces/stats"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linked").value(false))
                .andExpect(jsonPath("$.handle").isEmpty()).andExpect(jsonPath("$.problemsSolved").value(0))
                .andExpect(jsonPath("$.maxStreakDays").value(0)).andExpect(jsonPath("$.currentRating").value(0))
                .andExpect(jsonPath("$.acceptedSubmissions").value(0)).andExpect(jsonPath("$.acceptanceRate").value(0.0))
                .andExpect(jsonPath("$.activity").isEmpty()).andExpect(jsonPath("$.solvedByRating").isEmpty());
    }

    @Test
    void upstreamFailuresHaveUseful404And503Messages() throws Exception {
        when(service.getStats(user)).thenThrow(new NotFoundException("El usuario de Codeforces no existe"));
        mvc.perform(get("/users/me/codeforces/stats")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El usuario de Codeforces no existe"));
        doThrow(new CodeforcesUnavailableException()).when(service).getStats(user);
        mvc.perform(get("/users/me/codeforces/stats")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unauthorizedAndForbiddenAreNotSuccess() throws Exception {
        when(service.getStats(user)).thenThrow(new ForbiddenException("Solo practicantes"));
        mvc.perform(get("/users/me/codeforces/stats")).andExpect(status().isForbidden());
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Debe iniciar sesión"));
        mvc.perform(get("/users/me/codeforces/stats")).andExpect(status().isUnauthorized());
    }
}
