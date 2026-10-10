
package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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

