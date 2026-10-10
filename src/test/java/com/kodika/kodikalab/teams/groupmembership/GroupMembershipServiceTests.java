
package com.kodika.kodikalab.teams.groupmembership;

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

        GroupMembership result = service.reviewRequest(10, 5, true);

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

        GroupMembership result = service.reviewRequest(10, 5, false);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.RECHAZADO);
        verify(membershipRepository).save(pending);
    }

    @Test
    void anotherCoachCannotReviewMembership() {
        User otherCoach = new User();
        otherCoach.setId(99);
        otherCoach.setRole(Role.COACH);

        when(currentUserResolver.currentUser()).thenReturn(otherCoach);

        assertThatThrownBy(() -> service.reviewRequest(10, 5, true))
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

