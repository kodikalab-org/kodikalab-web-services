
package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.TeamRole;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudyGroupControllerTests {

    private StudyGroupService studyGroupService;
    private GroupMembershipService membershipService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        studyGroupService = mock(StudyGroupService.class);
        membershipService = mock(GroupMembershipService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mvc = MockMvcBuilders.standaloneSetup(
                        new StudyGroupController(
                                studyGroupService,
                                membershipService
                        ))
                .setControllerAdvice(new TeamsExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createGroupReturns201() throws Exception {
        StudyGroup group = new StudyGroup();
        group.setId(1);
        group.setName("Entrenamiento de Grafos");
        group.setInvitationCode("ABC123DEF456");

        when(studyGroupService.createGroup(any())).thenReturn(group);

        mvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Entrenamiento de Grafos",
                                  "description": "Preparación para ICPC",
                                  "expectedLevel": "Div3",
                                  "maxCapacity": 15,
                                  "sessionSchedule": "Lunes",
                                  "visibility": "PUBLICO"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.name").value("Entrenamiento de Grafos"));

        verify(studyGroupService).createGroup(any());
    }

    @Test
    void getAvailableGroupsReturns200() throws Exception {
        when(studyGroupService.getAvailableGroups())
                .thenReturn(List.of());

        mvc.perform(get("/teams"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void myTeamsOfACoachIncludeTheInvitationCodeAndOmitTheMembershipFields() throws Exception {
        when(studyGroupService.findMyTeams()).thenReturn(List.of(new MyTeamResponse(1, "Grafos", null, "Div3", 15,
                "Lunes", GroupStatus.ACTIVO, GroupVisibility.PROTEGIDO, "ABC123DEF456", 3L, 2L, null)));

        mvc.perform(get("/teams/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(1))
                .andExpect(jsonPath("$[0].visibility").value("PROTEGIDO"))
                .andExpect(jsonPath("$[0].invitationCode").value("ABC123DEF456"))
                .andExpect(jsonPath("$[0].activeMembers").value(3))
                .andExpect(jsonPath("$[0].pendingRequests").value(2))
                .andExpect(jsonPath("$[0].membership").doesNotExist());
    }

    @Test
    void myTeamsOfAPractitionerShowTheMembershipStatusAndOmitTheCoachFields() throws Exception {
        var joined = OffsetDateTime.parse("2026-10-09T19:00:00-05:00");
        when(studyGroupService.findMyTeams()).thenReturn(List.of(new MyTeamResponse(1, "Grafos", null, "Div3", 15,
                "Lunes", GroupStatus.ACTIVO, GroupVisibility.PROTEGIDO, null, null, null,
                new MyTeamResponse.Membership(7, MembershipStatus.PENDIENTE, TeamRole.MIEMBRO, joined, null))));

        mvc.perform(get("/teams/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(1))
                .andExpect(jsonPath("$[0].membership.membershipId").value(7))
                .andExpect(jsonPath("$[0].membership.status").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].membership.teamRole").value("MIEMBRO"))
                .andExpect(jsonPath("$[0].invitationCode").doesNotExist())
                .andExpect(jsonPath("$[0].activeMembers").doesNotExist())
                .andExpect(jsonPath("$[0].pendingRequests").doesNotExist());
    }

    @Test
    void myTeamsRejectsAnAnonymousAndASuspendedAccount() throws Exception {
        when(studyGroupService.findMyTeams()).thenThrow(new UnauthorizedException("Debe iniciar sesión"));
        mvc.perform(get("/teams/me")).andExpect(status().isUnauthorized());

        doThrow(new ForbiddenException("La cuenta no está activa")).when(studyGroupService).findMyTeams();
        mvc.perform(get("/teams/me")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("La cuenta no está activa"));
    }

    @Test
    void duplicateRequestReturns409() throws Exception {
        when(membershipService.requestJoin(eq(1), any()))
                .thenThrow(new ConflictException(
                        "Ya tienes una solicitud pendiente para este grupo"
                ));

        mvc.perform(post("/teams/1/join")
                        .param("invitationCode", "CODIGO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void fullGroupReturns409() throws Exception {
        when(membershipService.requestJoin(eq(1), any()))
                .thenThrow(new ConflictException(
                        "El grupo alcanzo su capacidad maxima"
                ));

        mvc.perform(post("/teams/1/join")
                        .param("invitationCode", "CODIGO"))
                .andExpect(status().isConflict());
    }

    @Test
    void anotherCoachCannotReviewRequest() throws Exception {
        when(membershipService.reviewRequest(1, 2, true))
                .thenThrow(new ForbiddenException(
                        "No eres el coach responsable de este grupo"
                ));

        mvc.perform(patch("/teams/1/memberships/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"ACEPTAR"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void missingSessionReturns401() throws Exception {
        when(membershipService.requestJoin(eq(1), any()))
                .thenThrow(new UnauthorizedException(
                        "Debe iniciar sesión"
                ));

        mvc.perform(post("/teams/1/join")
                        .param("invitationCode", "CODIGO"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidDecisionReturns400() throws Exception {
        mvc.perform(patch("/teams/1/memberships/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"INVALIDA"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(membershipService);
    }
}

