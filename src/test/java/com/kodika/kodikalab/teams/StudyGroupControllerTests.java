
package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipDecision;
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
                new MyTeamResponse.Membership(7, MembershipStatus.PENDIENTE, TeamRole.MIEMBRO, joined, null, null))));

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
    void myTeamsIdentityIsNotTakenFromQueryParameters() throws Exception {
        when(studyGroupService.findMyTeams()).thenReturn(List.of());

        mvc.perform(get("/teams/me").param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(studyGroupService).findMyTeams();
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
    void joinByCodeReturns429WhenTheAttemptLimitIsExhausted() throws Exception {
        when(membershipService.joinByCode("ABC123DEF456"))
                .thenThrow(new TooManyRequestsException("Demasiados intentos"));

        mvc.perform(post("/teams/join-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationCode":"ABC123DEF456"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Demasiados intentos"));
    }

    @Test
    void joinByCodeReturns201WithTheMembership() throws Exception {
        StudyGroup group = new StudyGroup();
        group.setId(4);
        GroupMembership membership = new GroupMembership();
        membership.setId(9);
        membership.setGroup(group);
        membership.setStatus(MembershipStatus.ACTIVO);

        when(membershipService.joinByCode("abc123def456")).thenReturn(membership);

        mvc.perform(post("/teams/join-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationCode":"abc123def456"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Ingreso al grupo registrado correctamente"))
                .andExpect(jsonPath("$.membershipId").value(9))
                .andExpect(jsonPath("$.groupId").value(4))
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void joinByCodeRequiresTheCode() throws Exception {
        mvc.perform(post("/teams/join-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationCode":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.invitationCode").exists());

        mvc.perform(post("/teams/join-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(membershipService, never()).joinByCode(any());
    }

    @Test
    void joinByCodeWithAnUnknownCodeReturns404() throws Exception {
        when(membershipService.joinByCode(any()))
                .thenThrow(new NotFoundException("El código de invitación no es válido"));

        mvc.perform(post("/teams/join-by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"invitationCode":"NOEXISTE0000"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El código de invitación no es válido"));
    }

    @Test
    void joinByIdStillAcceptsTheOptionalInvitationCodeParam() throws Exception {
        StudyGroup group = new StudyGroup();
        group.setId(1);
        GroupMembership membership = new GroupMembership();
        membership.setId(3);
        membership.setGroup(group);
        membership.setStatus(MembershipStatus.PENDIENTE);

        when(membershipService.requestJoin(1, null)).thenReturn(membership);

        mvc.perform(post("/teams/1/join"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDIENTE"));

        verify(membershipService).requestJoin(1, null);
        verify(membershipService, never()).joinByCode(any());
    }

    @Test
    void anotherCoachCannotReviewRequest() throws Exception {
        when(membershipService.reviewRequest(1, 2, MembershipDecision.ACEPTAR, null))
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
    void draftIsSavedWithOnlyANameAndReportsItsStatus() throws Exception {
        StudyGroup draft = new StudyGroup();
        draft.setId(5);
        draft.setName("Borrador de grafos");
        draft.setStatus(GroupStatus.BORRADOR);

        when(studyGroupService.createGroup(any())).thenReturn(draft);

        mvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Borrador de grafos","draft":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BORRADOR"))
                .andExpect(jsonPath("$.message").value("Borrador del grupo guardado correctamente"));
    }

    @Test
    void draftCanBeUpdatedAndPublished() throws Exception {
        StudyGroup group = new StudyGroup();
        group.setId(5);
        group.setName("Borrador corregido");
        group.setExpectedLevel("Div2");
        group.setMaxCapacity(10);
        group.setStatus(GroupStatus.ACTIVO);

        when(studyGroupService.updateDraft(eq(5), any())).thenReturn(group);
        when(studyGroupService.publishGroup(5)).thenReturn(group);

        mvc.perform(patch("/teams/5/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Borrador corregido","expectedLevel":"Div2","maxCapacity":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Borrador corregido"));

        mvc.perform(post("/teams/5/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void coachCanRequestAClarification() throws Exception {
        GroupMembership waiting = new GroupMembership();
        waiting.setId(2);
        waiting.setGroup(group(1));
        waiting.setStatus(MembershipStatus.ACLARACION_SOLICITADA);
        waiting.setClarificationRequest("¿Puedes adjuntar tu perfil de Codeforces?");

        when(membershipService.reviewRequest(1, 2, MembershipDecision.SOLICITAR_ACLARACION,
                "¿Puedes adjuntar tu perfil de Codeforces?")).thenReturn(waiting);

        mvc.perform(patch("/teams/1/memberships/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"SOLICITAR_ACLARACION","message":"¿Puedes adjuntar tu perfil de Codeforces?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACLARACION_SOLICITADA"))
                .andExpect(jsonPath("$.message").value("Aclaración solicitada al postulante"))
                .andExpect(jsonPath("$.clarificationRequest").value("¿Puedes adjuntar tu perfil de Codeforces?"));
    }

    @Test
    void clarificationCanBeListedByItsOwnStatus() throws Exception {
        when(membershipService.getRequests(1, MembershipStatus.ACLARACION_SOLICITADA))
                .thenReturn(List.of());

        mvc.perform(get("/teams/1/memberships")
                        .param("status", "ACLARACION_SOLICITADA"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void practitionerAnswersTheClarification() throws Exception {
        GroupMembership answered = new GroupMembership();
        answered.setId(2);
        answered.setGroup(group(1));
        answered.setStatus(MembershipStatus.PENDIENTE);
        answered.setClarificationRequest("¿Puedes adjuntar tu perfil?");
        answered.setClarificationResponse("Sí, mi usuario es practicante_1");

        when(membershipService.respondClarification(1, 2, "Sí, mi usuario es practicante_1"))
                .thenReturn(answered);

        mvc.perform(post("/teams/1/memberships/2/clarification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Sí, mi usuario es practicante_1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.clarificationResponse").value("Sí, mi usuario es practicante_1"));
    }

    @Test
    void clarificationWithoutAMessageReturns400() throws Exception {
        mvc.perform(post("/teams/1/memberships/2/clarification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"   "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(membershipService);
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

    private static StudyGroup group(int id) {
        StudyGroup group = new StudyGroup();
        group.setId(id);
        return group;
    }
}

