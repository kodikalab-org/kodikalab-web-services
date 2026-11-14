package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblem;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemRepository;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformImportResult;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformSubmissionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.SubmittedFile;
import com.kodika.kodikalab.problems.problem.Problem;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Persistencia idempotente de envíos importados, con el repositorio simulado (sin base de datos). */
class PlatformImportServiceTests {
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-20T15:00:00Z");

    ProblemResolutionRepository repository;
    GroupMembershipService memberships;
    ProblemResolutionService service;
    List<ProblemResolution> stored;

    @BeforeEach
    void setUp() {
        repository = mock(ProblemResolutionRepository.class);
        CompetitionProblemRepository assignments = mock(CompetitionProblemRepository.class);
        memberships = mock(GroupMembershipService.class);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        service = new ProblemResolutionServiceImpl(repository, assignments, memberships,
                mock(StudyGroupService.class), resolver);
        User user = new User();
        user.setId(10);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(user);
        StudyGroup group = new StudyGroup();
        group.setId(1);
        PractitionerProfile practitioner = new PractitionerProfile();
        practitioner.setUserId(10);
        GroupMembership membership = new GroupMembership();
        membership.setId(20);
        membership.setGroup(group);
        membership.setPractitioner(practitioner);
        membership.setStatus(MembershipStatus.ACTIVO);
        when(memberships.findForUpdate(1, 10)).thenReturn(Optional.of(membership));
        Competition competition = new Competition();
        competition.setId(30);
        competition.setGroup(group);
        competition.setStatus(CompetitionStatus.EN_CURSO);
        Problem problem = new Problem();
        problem.setId(40);
        CompetitionProblem assignment = new CompetitionProblem();
        assignment.setId(50);
        assignment.setCompetition(competition);
        assignment.setProblem(problem);
        when(assignments.findById(50)).thenReturn(Optional.of(assignment));
        stored = new ArrayList<>();
        when(repository.findByMembershipIdAndCompetitionProblemIdAndExternalSubmissionIdIn(any(), any(),
                anyCollection())).thenAnswer(invocation -> stored.stream()
                .filter(row -> ((java.util.Collection<Long>) invocation.getArgument(2))
                        .contains(row.getExternalSubmissionId())).toList());
        when(repository.save(any())).thenAnswer(invocation -> {
            ProblemResolution row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(100 + stored.size());
                stored.add(row);
            }
            return row;
        });
    }

    static PlatformSubmissionData data(long id, Verdict verdict) {
        return new PlatformSubmissionData(id, verdict, "GNU C++17 (64)", 31, 3400, 12, AT,
                "https://codeforces.com/contest/1850/submission/" + id);
    }

    @Test
    void importsNewSubmissionsWithAllTheirData() {
        PlatformImportResult result = service.importPlatformSubmissions(1, 50,
                List.of(data(300, Verdict.ACCEPTED)), null, null);

        assertThat(result.imported()).containsExactly(300L);
        assertThat(result.fileStored()).isFalse();
        ProblemResolution row = stored.get(0);
        assertThat(row.getOrigin()).isEqualTo(ResolutionOrigin.PLATAFORMA);
        assertThat(row.getExternalSubmissionId()).isEqualTo(300L);
        assertThat(row.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(row.getLanguage()).isEqualTo("GNU C++17 (64)");
        assertThat(row.getExecutionTimeMs()).isEqualTo(31);
        assertThat(row.getMemoryUsedKb()).isEqualTo(3400);
        assertThat(row.getPassedTests()).isEqualTo(12);
        assertThat(row.getSubmittedAt()).isEqualTo(AT);
        assertThat(row.getEvidenceUrl()).isEqualTo("https://codeforces.com/contest/1850/submission/300");
        assertThat(row.getMembership().getId()).isEqualTo(20);
        assertThat(row.getFileComparison()).isNull();
    }

    @Test
    void importingTheSameSubmissionsTwiceCreatesNoDuplicates() {
        var submissions = List.of(data(300, Verdict.ACCEPTED), data(299, Verdict.WRONG_ANSWER));
        service.importPlatformSubmissions(1, 50, submissions, null, null);

        PlatformImportResult second = service.importPlatformSubmissions(1, 50, submissions, null, null);

        assertThat(second.imported()).isEmpty();
        assertThat(second.updated()).isEmpty();
        assertThat(stored).hasSize(2);
    }

    @Test
    void aSubmissionImportedWhileTestingGetsItsRealVerdictLater() {
        service.importPlatformSubmissions(1, 50, List.of(data(300, Verdict.EN_EVALUACION)), null, null);

        PlatformImportResult result = service.importPlatformSubmissions(1, 50, List.of(data(300, Verdict.TLE)), null,
                null);

        assertThat(result.imported()).isEmpty();
        assertThat(result.updated()).containsExactly(300L);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getVerdict()).isEqualTo(Verdict.TLE);
    }

    @Test
    void aFinalVerdictIsNeverOverwritten() {
        service.importPlatformSubmissions(1, 50, List.of(data(300, Verdict.ACCEPTED)), null, null);

        service.importPlatformSubmissions(1, 50, List.of(data(300, Verdict.WRONG_ANSWER)), null, null);

        assertThat(stored.get(0).getVerdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void fileIsAttachedToTheChosenSubmissionAsNotCompared() {
        var file = new SubmittedFile("sol.cpp", "int main(){}", "abc123");

        PlatformImportResult result = service.importPlatformSubmissions(1, 50,
                List.of(data(300, Verdict.ACCEPTED), data(299, Verdict.WRONG_ANSWER)), file, 300L);

        assertThat(result.fileStored()).isTrue();
        ProblemResolution latest = stored.stream().filter(r -> r.getExternalSubmissionId() == 300L).findFirst()
                .orElseThrow();
        assertThat(latest.getFileName()).isEqualTo("sol.cpp");
        assertThat(latest.getSourceCode()).isEqualTo("int main(){}");
        assertThat(latest.getSha256()).isEqualTo("abc123");
        assertThat(latest.getFileComparison()).isEqualTo(FileComparison.NO_COMPARADO);
        assertThat(stored.stream().filter(r -> r.getExternalSubmissionId() == 299L).findFirst().orElseThrow()
                .getFileName()).isNull();
    }

    @Test
    void fileWithoutAnEligibleSubmissionIsNotStored() {
        PlatformImportResult result = service.importPlatformSubmissions(1, 50, List.of(),
                new SubmittedFile("sol.cpp", "x", "h"), null);

        assertThat(result.fileStored()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void longLanguageNamesAreTruncatedAndMissingOnesNamed() {
        service.importPlatformSubmissions(1, 50, List.of(
                new PlatformSubmissionData(1L, Verdict.OTRO, "L".repeat(50), null, null, null, AT, null),
                new PlatformSubmissionData(2L, Verdict.OTRO, null, null, null, null, AT, null)), null, null);

        assertThat(stored.get(0).getLanguage()).hasSize(30);
        assertThat(stored.get(1).getLanguage()).isEqualTo("Desconocido");
    }

    @Test
    void requiresAnActiveMembershipOfTheTeam() {
        when(memberships.findForUpdate(1, 10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importPlatformSubmissions(1, 50, List.of(data(1, Verdict.OTRO)), null, null))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }
}
