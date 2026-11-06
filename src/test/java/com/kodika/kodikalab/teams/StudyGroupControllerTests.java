package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.*;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudyGroupControllerTests {
    StudyGroupService groups = mock(StudyGroupService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new StudyGroupController(groups, mock(GroupMembershipService.class)))
            .setControllerAdvice(new TeamExceptionHandler()).build();

    @Test
    void authenticatedIdentityIsNotTakenFromQueryParameters() throws Exception {
        when(groups.findMyTeams()).thenReturn(List.of());
        mvc.perform(get("/api/teams/me").contextPath("/api").param("userId", "99"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(groups).findMyTeams();
    }

    @Test
    void mapsAuthenticationAuthorizationAndAvailabilityWithoutInternalDetails() throws Exception {
        doThrow(new UnauthorizedException("internal")).when(groups).findMyTeams();
        mvc.perform(get("/teams/me")).andExpect(status().isUnauthorized());
        doThrow(new ForbiddenException("Cuenta inactiva")).when(groups).findMyTeams();
        mvc.perform(get("/teams/me")).andExpect(status().isForbidden());
        doThrow(new DataAccessResourceFailureException("password internal")).when(groups).findMyTeams();
        mvc.perform(get("/teams/me")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("La información de los equipos no está disponible"));
    }
}
