package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.coach.CoachProfileService;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipRepository;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.TeamRole;
import com.kodika.kodikalab.teams.groupmembership.dto.MembershipCount;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** "Mis equipos": los grupos del coach o las membresías del practicante. */
class StudyGroupServiceTests {
    static final OffsetDateTime JOINED = OffsetDateTime.parse("2026-10-09T19:00:00-05:00");

    StudyGroupRepository groups;
    GroupMembershipRepository memberships;
    CurrentUserResolver resolver;
    CoachProfileService coachProfiles;
    StudyGroupService service;

    @BeforeEach
    void setUp() {
        groups = mock(StudyGroupRepository.class);
        memberships = mock(GroupMembershipRepository.class);
        resolver = mock(CurrentUserResolver.class);
        coachProfiles = mock(CoachProfileService.class);
        service = new StudyGroupServiceImpl(groups, coachProfiles, resolver, memberships);
    }

    @Test
    void coachSeesTheirGroupsWithTheInvitationCodeAndTheMemberCounts() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(groups.findByCoachUserId(10)).thenReturn(List.of(group(1, "Grafos", GroupVisibility.PROTEGIDO),
                group(2, "DP", GroupVisibility.PUBLICO)));
        when(memberships.countActiveAndPendingByGroupIds(List.of(1, 2))).thenReturn(List.of(
                new MembershipCount(1, MembershipStatus.ACTIVO, 3L),
                new MembershipCount(1, MembershipStatus.PENDIENTE, 2L)));

        List<MyTeamResponse> teams = service.findMyTeams();

        assertThat(teams).hasSize(2);
        MyTeamResponse first = teams.get(0);
        assertThat(first.groupId()).isEqualTo(1);
        assertThat(first.name()).isEqualTo("Grafos");
        assertThat(first.visibility()).isEqualTo(GroupVisibility.PROTEGIDO);
        assertThat(first.invitationCode()).isEqualTo("CODIGO-1");
        assertThat(first.activeMembers()).isEqualTo(3L);
        assertThat(first.pendingRequests()).isEqualTo(2L);
        assertThat(first.membership()).isNull();
        // Un grupo sin integrantes ni solicitudes informa ceros, no valores ausentes.
        assertThat(teams.get(1).activeMembers()).isZero();
        assertThat(teams.get(1).pendingRequests()).isZero();
    }

    @Test
    void coachWithoutGroupsGetsAnEmptyListWithoutCountingMembers() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(groups.findByCoachUserId(10)).thenReturn(List.of());

        assertThat(service.findMyTeams()).isEmpty();
        verify(memberships, never()).countActiveAndPendingByGroupIds(any());
    }

    @Test
    void practitionerSeesEveryMembershipWithItsStatusAndNeverTheInvitationCode() {
        when(resolver.currentUser()).thenReturn(user(20, Role.PRACTICANTE, UserStatus.ACTIVO));
        when(memberships.findByPractitionerUserIdWithGroup(20)).thenReturn(List.of(
                membership(100, group(1, "Grafos", GroupVisibility.PROTEGIDO), MembershipStatus.PENDIENTE),
                membership(101, group(2, "DP", GroupVisibility.PUBLICO), MembershipStatus.ACTIVO),
                membership(102, group(3, "Flujo", GroupVisibility.PROTEGIDO), MembershipStatus.RECHAZADO)));

        List<MyTeamResponse> teams = service.findMyTeams();

        assertThat(teams).extracting(MyTeamResponse::groupId).containsExactly(1, 2, 3);
        assertThat(teams).extracting(team -> team.membership().status())
                .containsExactly(MembershipStatus.PENDIENTE, MembershipStatus.ACTIVO, MembershipStatus.RECHAZADO);
        assertThat(teams.get(1).membership().membershipId()).isEqualTo(101);
        assertThat(teams.get(1).membership().teamRole()).isEqualTo(TeamRole.MIEMBRO);
        assertThat(teams.get(1).membership().joinedAt()).isEqualTo(JOINED);
        assertThat(teams).allSatisfy(team -> {
            assertThat(team.invitationCode()).isNull();
            assertThat(team.activeMembers()).isNull();
            assertThat(team.pendingRequests()).isNull();
        });
        verifyNoInteractions(groups);
    }

    @Test
    void practitionerWithoutMembershipsGetsAnEmptyList() {
        when(resolver.currentUser()).thenReturn(user(20, Role.PRACTICANTE, UserStatus.ACTIVO));
        when(memberships.findByPractitionerUserIdWithGroup(20)).thenReturn(List.of());

        assertThat(service.findMyTeams()).isEmpty();
    }

    @Test
    void suspendedAccountCannotListTeams() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.SUSPENDIDO));

        assertThatThrownBy(() -> service.findMyTeams()).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(groups, memberships);
    }

    @Test
    void absentSessionIsUnauthorized() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sin sesión"));

        assertThatThrownBy(() -> service.findMyTeams()).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(groups, memberships);
    }

    @Test
    void draftOnlyRequiresAName() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(coachProfiles.requireCoachProfile(10)).thenReturn(coach(10));
        when(groups.save(any(StudyGroup.class))).thenAnswer(call -> call.getArgument(0));

        StudyGroup draft = service.createGroup(new CreateStudyGroupRequest(
                "  Mi grupo en preparación  ", null, null, null, null, null, true));

        assertThat(draft.getStatus()).isEqualTo(GroupStatus.BORRADOR);
        assertThat(draft.getName()).isEqualTo("Mi grupo en preparación");
        assertThat(draft.getVisibility()).isEqualTo(GroupVisibility.PUBLICO);
        assertThat(draft.getInvitationCode()).isNotBlank();
        verify(groups).save(draft);
    }

    @Test
    void draftWithoutNameIsRejected() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(coachProfiles.requireCoachProfile(10)).thenReturn(coach(10));

        assertThatThrownBy(() -> service.createGroup(new CreateStudyGroupRequest(
                "   ", null, null, null, null, null, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nombre");

        verify(groups, never()).save(any());
    }

    @Test
    void publishingAGroupRequiresLevelAndCapacity() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(coachProfiles.requireCoachProfile(10)).thenReturn(coach(10));

        assertThatThrownBy(() -> service.createGroup(new CreateStudyGroupRequest(
                "Grupo completo", null, null, 10, null, null, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("nivel esperado");

        verify(groups, never()).save(any());
    }

    @Test
    void draftCanBePublishedWhenItHasTheRequiredData() {
        StudyGroup draft = group(5, "Borrador", GroupVisibility.PUBLICO);
        draft.setCoach(coach(10));
        draft.setStatus(GroupStatus.BORRADOR);
        draft.setExpectedLevel("Div3");
        draft.setMaxCapacity(8);

        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(groups.findById(5)).thenReturn(Optional.of(draft));
        when(groups.save(any(StudyGroup.class))).thenAnswer(call -> call.getArgument(0));

        StudyGroup published = service.publishGroup(5);

        assertThat(published.getStatus()).isEqualTo(GroupStatus.ACTIVO);
        assertThat(published.getVisibility()).isEqualTo(GroupVisibility.PUBLICO);
        verify(groups).save(draft);
    }

    @Test
    void anotherCoachCannotPublishOrEditTheDraft() {
        StudyGroup draft = group(5, "Borrador", GroupVisibility.PUBLICO);
        draft.setStatus(GroupStatus.BORRADOR);
        draft.setCoach(coach(99));

        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(groups.findById(5)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.publishGroup(5))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("coach responsable");
    }

    @Test
    void aPublishedGroupCannotBeEditedAsDraft() {
        StudyGroup published = group(5, "Publicado", GroupVisibility.PUBLICO);
        published.setStatus(GroupStatus.ACTIVO);
        published.setCoach(coach(10));

        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(groups.findById(5)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.updateDraft(5, new UpdateStudyGroupDraftRequest(
                "Nuevo nombre", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("borrador");

        verify(groups, never()).save(any());
    }

    private static CoachProfile coach(int userId) {
        CoachProfile coach = new CoachProfile();
        coach.setUserId(userId);
        return coach;
    }

    private static StudyGroup group(int id, String name, GroupVisibility visibility) {
        StudyGroup group = new StudyGroup();
        group.setId(id);
        group.setName(name);
        group.setDescription("Descripción " + id);
        group.setExpectedLevel("Div3");
        group.setMaxCapacity(15);
        group.setSessionSchedule("Lunes");
        group.setInvitationCode("CODIGO-" + id);
        group.setStatus(GroupStatus.ACTIVO);
        group.setVisibility(visibility);
        return group;
    }

    private static GroupMembership membership(int id, StudyGroup group, MembershipStatus status) {
        GroupMembership membership = new GroupMembership();
        membership.setId(id);
        membership.setGroup(group);
        membership.setStatus(status);
        membership.setTeamRole(TeamRole.MIEMBRO);
        membership.setJoinedAt(JOINED);
        return membership;
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
