
package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupRepository;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GroupMembershipServiceTests {

    private GroupMembershipRepository membershipRepository;
    private StudyGroupRepository groupRepository;
    private PractitionerProfileService practitionerProfileService;
    private CurrentUserResolver currentUserResolver;
    private GroupMembershipServiceImpl service;

    private User practitionerUser;
    private User coachUser;
    private PractitionerProfile practitioner;
    private StudyGroup group;

    @BeforeEach
    void setUp() {
        membershipRepository = mock(GroupMembershipRepository.class);
        groupRepository = mock(StudyGroupRepository.class);
        practitionerProfileService = mock(PractitionerProfileService.class);
        currentUserResolver = mock(CurrentUserResolver.class);

        service = new GroupMembershipServiceImpl(
                membershipRepository,
                groupRepository,
                practitionerProfileService,
                currentUserResolver
        );

        practitionerUser = new User();
        practitionerUser.setId(2);
        practitionerUser.setRole(Role.PRACTICANTE);

        coachUser = new User();
        coachUser.setId(1);
        coachUser.setRole(Role.COACH);

        practitioner = new PractitionerProfile();
        practitioner.setUser(practitionerUser);
        practitioner.setUserId(2);

        CoachProfile coach = new CoachProfile();
        coach.setUser(coachUser);
        coach.setUserId(1);

        group = new StudyGroup();
        group.setId(10);
        group.setCoach(coach);
        group.setStatus(GroupStatus.ACTIVO);
        group.setVisibility(GroupVisibility.PROTEGIDO);
        group.setMaxCapacity(3);
        group.setInvitationCode("ABC123");

        when(groupRepository.findById(10)).thenReturn(Optional.of(group));
        when(currentUserResolver.currentUser()).thenReturn(practitionerUser);
        when(practitionerProfileService.requirePractitionerProfile(2))
                .thenReturn(practitioner);
        when(membershipRepository.save(any(GroupMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void protectedGroupCreatesPendingRequest() {
        GroupMembership result = service.requestJoin(10, null);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDIENTE);
        assertThat(result.getJoinedAt()).isNotNull();
        assertThat(result.getPractitioner()).isEqualTo(practitioner);
        verify(membershipRepository).save(result);
    }

    @Test
    void publicGroupAllowsDirectEntry() {
        group.setVisibility(GroupVisibility.PUBLICO);

        GroupMembership result = service.requestJoin(10, null);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVO);
    }

    @Test
    void validInvitationCodeAllowsDirectEntry() {
        GroupMembership result = service.requestJoin(10, "ABC123");

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVO);
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        GroupMembership existing = membership(MembershipStatus.PENDIENTE);

        when(membershipRepository.findByGroupIdAndPractitionerUserId(10, 2))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requestJoin(10, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("solicitud pendiente");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void rejectedMembershipCanRequestAgain() {
        GroupMembership existing = membership(MembershipStatus.RECHAZADO);

        when(membershipRepository.findByGroupIdAndPractitionerUserId(10, 2))
                .thenReturn(Optional.of(existing));

        GroupMembership result = service.requestJoin(10, null);

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDIENTE);
        verify(membershipRepository).save(existing);
    }

    @Test
    void fullGroupRejectsNewRequests() {
        when(membershipRepository.countByGroupIdAndStatus(
                10, MembershipStatus.ACTIVO)).thenReturn(3L);

        assertThatThrownBy(() -> service.requestJoin(10, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("capacidad maxima");
    }

    @Test
    void coachCanAcceptPendingMembership() {
        when(currentUserResolver.currentUser()).thenReturn(coachUser);
        GroupMembership pending = membership(MembershipStatus.PENDIENTE);

        when(membershipRepository.findById(5))
                .thenReturn(Optional.of(pending));

        GroupMembership result = service.reviewRequest(10, 5, MembershipDecision.ACEPTAR, null);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVO);
        assertThat(result.getJoinedAt()).isNotNull();
        verify(membershipRepository).save(pending);
    }

    @Test
    void coachCanRejectPendingMembership() {
        when(currentUserResolver.currentUser()).thenReturn(coachUser);
        GroupMembership pending = membership(MembershipStatus.PENDIENTE);

        when(membershipRepository.findById(5))
                .thenReturn(Optional.of(pending));

        GroupMembership result = service.reviewRequest(10, 5, MembershipDecision.RECHAZAR, null);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.RECHAZADO);
        verify(membershipRepository).save(pending);
    }

    @Test
    void anotherCoachCannotReviewMembership() {
        User otherCoach = new User();
        otherCoach.setId(99);
        otherCoach.setRole(Role.COACH);

        when(currentUserResolver.currentUser()).thenReturn(otherCoach);

        assertThatThrownBy(() -> service.reviewRequest(10, 5, MembershipDecision.ACEPTAR, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("coach responsable");
    }

    @Test
    void onlyActiveMembersCountTowardsCapacity() {
        when(membershipRepository.countByGroupIdAndStatus(
                10, MembershipStatus.ACTIVO)).thenReturn(2L);

        GroupMembership result = service.requestJoin(10, null);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDIENTE);
        verify(membershipRepository)
                .countByGroupIdAndStatus(10, MembershipStatus.ACTIVO);
    }

    @Test
    void coachRequestsClarificationAndTheRequestStopsBeingPending() {
        when(currentUserResolver.currentUser()).thenReturn(coachUser);
        GroupMembership pending = membership(MembershipStatus.PENDIENTE);
        when(membershipRepository.findById(5)).thenReturn(Optional.of(pending));

        GroupMembership result = service.reviewRequest(10, 5, MembershipDecision.SOLICITAR_ACLARACION,
                "¿Puedes adjuntar el enlace de tu perfil de Codeforces?");

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACLARACION_SOLICITADA);
        assertThat(result.getClarificationRequest()).contains("Codeforces");
        assertThat(result.getClarificationRequestedAt()).isNotNull();
        assertThat(result.getClarificationResponse()).isNull();
        verify(membershipRepository).save(pending);
    }

    @Test
    void clarificationRequiresAMessage() {
        when(currentUserResolver.currentUser()).thenReturn(coachUser);
        when(membershipRepository.findById(5)).thenReturn(Optional.of(membership(MembershipStatus.PENDIENTE)));

        assertThatThrownBy(() -> service.reviewRequest(10, 5, MembershipDecision.SOLICITAR_ACLARACION, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("qué aclaración");
    }

    @Test
    void cannotAskForClarificationTwice() {
        when(currentUserResolver.currentUser()).thenReturn(coachUser);
        GroupMembership waiting = membership(MembershipStatus.ACLARACION_SOLICITADA);
        when(membershipRepository.findById(5)).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.reviewRequest(10, 5, MembershipDecision.SOLICITAR_ACLARACION, "Otra"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya se solicitó");
    }

    @Test
    void cannotAcceptOrRejectWhileClarificationIsUnanswered() {
        when(currentUserResolver.currentUser()).thenReturn(coachUser);
        GroupMembership waiting = membership(MembershipStatus.ACLARACION_SOLICITADA);
        when(membershipRepository.findById(5)).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.reviewRequest(10, 5, MembershipDecision.ACEPTAR, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("aún no respondió");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void practitionerAnswersClarificationAndRequestReturnsToPending() {
        GroupMembership waiting = membership(MembershipStatus.ACLARACION_SOLICITADA);
        waiting.setClarificationRequest("¿Cuál es tu handle?");
        waiting.setClarificationRequestedAt(OffsetDateTime.now());
        when(membershipRepository.findById(5)).thenReturn(Optional.of(waiting));

        GroupMembership result = service.respondClarification(10, 5, "Mi handle es usuario_prueba");

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.PENDIENTE);
        assertThat(result.getClarificationRequest()).isEqualTo("¿Cuál es tu handle?");
        assertThat(result.getClarificationResponse()).isEqualTo("Mi handle es usuario_prueba");
        assertThat(result.getClarificationRespondedAt()).isNotNull();
        verify(membershipRepository).save(waiting);
    }

    @Test
    void anotherPractitionerCannotAnswerTheClarification() {
        GroupMembership waiting = membership(MembershipStatus.ACLARACION_SOLICITADA);
        when(membershipRepository.findById(5)).thenReturn(Optional.of(waiting));

        User intruder = new User();
        intruder.setId(99);
        intruder.setRole(Role.PRACTICANTE);
        when(currentUserResolver.currentUser()).thenReturn(intruder);

        assertThatThrownBy(() -> service.respondClarification(10, 5, "No soy el postulante"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Solo el postulante");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void cannotAnswerWhenNoClarificationWasRequested() {
        when(membershipRepository.findById(5)).thenReturn(Optional.of(membership(MembershipStatus.PENDIENTE)));

        assertThatThrownBy(() -> service.respondClarification(10, 5, "Respuesta sin pregunta"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está esperando");
    }

    private GroupMembership membership(MembershipStatus status) {
        GroupMembership membership = new GroupMembership();
        membership.setId(5);
        membership.setGroup(group);
        membership.setPractitioner(practitioner);
        membership.setStatus(status);
        membership.setTeamRole(TeamRole.MIEMBRO);
        return membership;
    }
}

