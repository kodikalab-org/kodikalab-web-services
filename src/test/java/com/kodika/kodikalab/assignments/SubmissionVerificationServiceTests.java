package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.PlatformSubmissionsResponse;
import com.kodika.kodikalab.assignments.dto.SubmissionFile;
import com.kodika.kodikalab.assignments.dto.VerificationResponse;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentView;
import com.kodika.kodikalab.competitions.problemresolution.FileComparison;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.ResolutionOrigin;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformImportResult;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformSubmissionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.SubmittedFile;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesSubmission;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verificación de envíos con dobles del cliente de Codeforces: no hay red ni base de datos. */
class SubmissionVerificationServiceTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00Z");
    static final long AT_START = START.toEpochSecond();

    CurrentUserResolver resolver;
    StudyGroupService groups;
    GroupMembershipService memberships;
    CompetitionProblemService competitionProblems;
    ProblemService problems;
    ProblemResolutionService resolutions;
    PractitionerProfileService profiles;
    CodeforcesClient codeforces;
    SubmissionVerificationService service;
    User user;
    AssignmentView view;
    ProblemSummary problem;
    PractitionerProfile profile;
    List<MemberAttempt> attempts;

    @BeforeEach
    void setUp() {
        resolver = mock(CurrentUserResolver.class);
        groups = mock(StudyGroupService.class);
        memberships = mock(GroupMembershipService.class);
        competitionProblems = mock(CompetitionProblemService.class);
        problems = mock(ProblemService.class);
        resolutions = mock(ProblemResolutionService.class);
        profiles = mock(PractitionerProfileService.class);
        codeforces = mock(CodeforcesClient.class);
        service = new SubmissionVerificationServiceImpl(resolver, groups, memberships, competitionProblems, problems,
                resolutions, profiles, codeforces);

        user = new User();
        user.setId(10);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(user);
        view = new AssignmentView(7, 11, "A", 100, "#FF0000", START.minusDays(1), 5, 1, "Simulacro",
                CompetitionStatus.EN_CURSO, START, START.plusHours(5), 300, PenaltyRule.ICPC_20_MIN, 60,
                CompetitionAccessType.PUBLICO_GRUPO);
        when(competitionProblems.findViewById(7)).thenReturn(Optional.of(view));
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 99)));
        when(memberships.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(20, 1, 10, "Ana", MembershipStatus.ACTIVO)));
        problem = new ProblemSummary(11, "To My Critics", "https://codeforces.com/problemset/problem/1850/A",
                SourcePlatform.CODEFORCES, "1850A", "800", 1000, 256);
        when(problems.findSummariesByIds(any())).thenReturn(List.of(problem));
        profile = new PractitionerProfile();
        profile.setUserId(10);
        profile.setCodeforcesHandle("test_handle");
        when(profiles.requirePractitionerProfile(10)).thenReturn(profile);
        attempts = new ArrayList<>();
        when(resolutions.findAttemptsByMembershipId(20)).thenAnswer(invocation -> List.copyOf(attempts));
        when(resolutions.importPlatformSubmissions(anyInt(), anyInt(), anyList(), any(), any())).thenAnswer(
                invocation -> {
                    List<PlatformSubmissionData> submitted = invocation.getArgument(2);
                    Set<Long> imported = new HashSet<>();
                    submitted.forEach(s -> imported.add(s.externalId()));
                    return new PlatformImportResult(imported, Set.of(), invocation.getArgument(3) != null);
                });
    }

    static CodeforcesSubmission submission(long id, long at, int contest, String index, String verdict) {
        return new CodeforcesSubmission(id, at, contest, index, null, verdict, "GNU C++17 (64)", 46, 2_097_152L, 7);
    }

    @SuppressWarnings("unchecked")
    private List<PlatformSubmissionData> imported() {
        ArgumentCaptor<List<PlatformSubmissionData>> captor = ArgumentCaptor.forClass(List.class);
        verify(resolutions).importPlatformSubmissions(eq(1), eq(7), captor.capture(), any(), any());
        return captor.getValue();
    }

    // ---- verify: filtro, mapeo e importación

    @Test
    void importsOnlySubmissionsOfTheAssignedProblemWithRealData() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 600, 1850, "A", "OK"),
                submission(299, AT_START + 500, 1850, "B", "OK"),
                submission(298, AT_START + 400, 1851, "A", "OK"),
                submission(297, AT_START + 300, 1850, "A", "WRONG_ANSWER")));
        attempts.add(new MemberAttempt(1, 7, Verdict.ACCEPTED, "C++", START, null));

        VerificationResponse response = service.verify(7, null, null);

        assertThat(response.found()).isTrue();
        assertThat(response.status()).isEqualTo(AssignmentStatus.RESUELTO);
        assertThat(response.submissions()).extracting(VerificationResponse.Submission::externalId)
                .containsExactly(300L, 297L);
        var first = response.submissions().get(0);
        assertThat(first.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(first.language()).isEqualTo("GNU C++17 (64)");
        assertThat(first.timeMs()).isEqualTo(46);
        assertThat(first.memoryKb()).isEqualTo(2048);
        assertThat(first.passedTests()).isEqualTo(7);
        assertThat(first.submittedAt()).isEqualTo(Instant.ofEpochSecond(AT_START + 600).atOffset(
                java.time.ZoneOffset.UTC));
        assertThat(first.url()).isEqualTo("https://codeforces.com/contest/1850/submission/300");
        assertThat(first.imported()).isTrue();
        assertThat(first.beforeCompetition()).isFalse();
        assertThat(imported()).extracting(PlatformSubmissionData::externalId).containsExactly(300L, 297L);
        assertThat(imported().get(1).verdict()).isEqualTo(Verdict.WRONG_ANSWER);
    }

    @Test
    void repeatingTheVerificationDoesNotReportNewImports() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 600, 1850, "A", "OK")));
        when(resolutions.importPlatformSubmissions(anyInt(), anyInt(), anyList(), any(), any()))
                .thenReturn(new PlatformImportResult(Set.of(300L), Set.of(), false))
                .thenReturn(new PlatformImportResult(Set.of(), Set.of(), false));

        var first = service.verify(7, null, null);
        var second = service.verify(7, null, null);

        assertThat(first.submissions().get(0).imported()).isTrue();
        assertThat(second.submissions().get(0).imported()).isFalse();
        assertThat(second.found()).isTrue();
    }

    @Test
    void submissionsBeforeTheCompetitionAreReportedButNeverImported() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 60, 1850, "A", "WRONG_ANSWER"),
                submission(299, AT_START - 60, 1850, "A", "OK")));

        VerificationResponse response = service.verify(7, null, null);

        assertThat(response.found()).isTrue();
        assertThat(response.submissions()).extracting(VerificationResponse.Submission::beforeCompetition)
                .containsExactly(false, true);
        assertThat(response.submissions().get(1).imported()).isFalse();
        assertThat(imported()).extracting(PlatformSubmissionData::externalId).containsExactly(300L);
    }

    @Test
    void onlyEarlySubmissionsStillAnswersFoundWithNothingToImport() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(299, AT_START - 60, 1850, "A", "OK")));

        VerificationResponse response = service.verify(7, null, null);

        assertThat(response.found()).isTrue();
        assertThat(imported()).isEmpty();
        assertThat(response.status()).isEqualTo(AssignmentStatus.SIN_INTENTOS);
    }

    @Test
    void noSubmissionOfTheProblemMeansNotFound() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(299, AT_START + 60, 1850, "B", "OK")));

        VerificationResponse response = service.verify(7, null, null);

        assertThat(response.found()).isFalse();
        assertThat(response.submissions()).isEmpty();
        assertThat(response.fileStored()).isFalse();
    }

    @Test
    void aSubmissionStillBeingJudgedIsInEvaluationAndDoesNotResolve() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                new CodeforcesSubmission(300L, AT_START + 60, 1850, "A", null, "TESTING", null, null, null, null)));
        attempts.add(new MemberAttempt(1, 7, Verdict.EN_EVALUACION, "Desconocido", START, null));

        VerificationResponse response = service.verify(7, null, null);

        var item = response.submissions().get(0);
        assertThat(item.verdict()).isEqualTo(Verdict.EN_EVALUACION);
        assertThat(item.timeMs()).isNull();
        assertThat(item.memoryKb()).isNull();
        assertThat(response.status()).isEqualTo(AssignmentStatus.PENDIENTE);
    }

    @Test
    void customWindowIsForwardedAndValidated() {
        when(codeforces.recentSubmissions("test_handle", 50)).thenReturn(List.of());

        service.verify(7, null, 50);

        verify(codeforces).recentSubmissions("test_handle", 50);
        for (int invalid : new int[] {0, -1, 101}) {
            assertThatThrownBy(() -> service.verify(7, null, invalid)).isInstanceOf(FieldValidationException.class)
                    .satisfies(e -> assertThat(((FieldValidationException) e).getErrors()).containsKey("count"));
        }
    }

    @Test
    void usesTheProblemUrlWhenTheSourceCodeIsNotParseable() {
        problem = new ProblemSummary(11, "To My Critics", "https://codeforces.com/contest/1850/problem/A",
                SourcePlatform.CODEFORCES, "legacy", "800", 1000, 256);
        when(problems.findSummariesByIds(any())).thenReturn(List.of(problem));
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 600, 1850, "A", "OK")));

        assertThat(service.verify(7, null, null).found()).isTrue();
    }

    // ---- verify: archivo

    @Test
    void validFileIsStoredWithItsHashOnTheMostRecentSubmission() throws Exception {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(297, AT_START + 300, 1850, "A", "WRONG_ANSWER"),
                submission(300, AT_START + 600, 1850, "A", "OK")));
        byte[] code = "int main() { return 0; }\n".getBytes(StandardCharsets.UTF_8);

        VerificationResponse response = service.verify(7, new SubmissionFile("C:\\tmp\\sol.CPP", code), null);

        assertThat(response.fileStored()).isTrue();
        ArgumentCaptor<SubmittedFile> file = ArgumentCaptor.forClass(SubmittedFile.class);
        verify(resolutions).importPlatformSubmissions(eq(1), eq(7), anyList(), file.capture(), eq(300L));
        assertThat(file.getValue().fileName()).isEqualTo("sol.CPP");
        assertThat(file.getValue().sourceCode()).isEqualTo("int main() { return 0; }\n");
        assertThat(file.getValue().sha256()).isEqualTo(
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(code)));
    }

    @Test
    void noFileMeansNothingToStore() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 600, 1850, "A", "OK")));

        assertThat(service.verify(7, null, null).fileStored()).isFalse();
        verify(resolutions).importPlatformSubmissions(eq(1), eq(7), anyList(), isNull(), eq(300L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sol.txt", "sol", "sol.exe", ".cpp.zip", ""})
    void rejectsUnsupportedExtensions(String name) {
        assertInvalidFile(new SubmissionFile(name, "x".getBytes(StandardCharsets.UTF_8)), "Extensión");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.cpp", "a.c", "a.cc", "a.py", "a.java", "a.kt", "a.rs", "a.js", "a.go"})
    void acceptsTheAllowedExtensions(String name) {
        when(codeforces.recentSubmissions(anyString(), anyInt())).thenReturn(List.of());

        service.verify(7, new SubmissionFile(name, "x".getBytes(StandardCharsets.UTF_8)), null);
    }

    @Test
    void rejectsFilesOver64kbEmptyAndNotUtf8() {
        assertInvalidFile(new SubmissionFile("a.cpp", new byte[64 * 1024 + 1]), "64 kB");
        assertInvalidFile(new SubmissionFile("a.cpp", new byte[0]), "vacío");
        assertInvalidFile(new SubmissionFile("a.cpp", new byte[] {(byte) 0xC3, (byte) 0x28}), "UTF-8");
        assertInvalidFile(new SubmissionFile("a.cpp", new byte[] {'a', 0, 'b'}), "texto");
    }

    @Test
    void fileOfExactly64kbIsAccepted() {
        when(codeforces.recentSubmissions(anyString(), anyInt())).thenReturn(List.of());
        byte[] content = new byte[64 * 1024];
        java.util.Arrays.fill(content, (byte) 'a');

        service.verify(7, new SubmissionFile("a.py", content), null);
    }

    private void assertInvalidFile(SubmissionFile file, String detail) {
        assertThatThrownBy(() -> service.verify(7, file, null)).isInstanceOf(FieldValidationException.class)
                .satisfies(e -> assertThat(((FieldValidationException) e).getErrors().get("file")).contains(detail));
        verifyNoInteractions(codeforces);
        verify(resolutions, never()).importPlatformSubmissions(anyInt(), anyInt(), anyList(), any(), any());
    }

    // ---- errores

    @Test
    void withoutLinkedHandleIsAConflictAndCodeforcesIsNotCalled() {
        profile.setCodeforcesHandle("  ");

        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ConflictException.class)
                .hasMessage("Vincula tu usuario de Codeforces en tu perfil para verificar");
        assertThatThrownBy(() -> service.platformSubmissions(7, null)).isInstanceOf(ConflictException.class)
                .hasMessage("Vincula tu usuario de Codeforces en tu perfil para verificar");
        verifyNoInteractions(codeforces);
    }

    @Test
    void withoutPractitionerProfileIsTheSameConflict() {
        when(profiles.requirePractitionerProfile(10)).thenThrow(new NotFoundException("perfil"));

        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ConflictException.class)
                .hasMessage("Vincula tu usuario de Codeforces en tu perfil para verificar");
    }

    @Test
    void nonCodeforcesProblemsAreRejected() {
        problem = new ProblemSummary(11, "Weird Algorithm", "https://cses.fi/problemset/task/1068", SourcePlatform.CSES,
                "1068", null, 1000, 512);
        when(problems.findSummariesByIds(any())).thenReturn(List.of(problem));

        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ConflictException.class)
                .hasMessage("La verificación automática solo está disponible para Codeforces");
        assertThatThrownBy(() -> service.platformSubmissions(7, null)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(codeforces);
    }

    @Test
    void competitionNotStartedIsAConflict() {
        view = new AssignmentView(7, 11, "A", 100, "#FF0000", START.minusDays(1), 5, 1, "Simulacro",
                CompetitionStatus.PROGRAMADA, START, START.plusHours(5), 300, PenaltyRule.ICPC_20_MIN, 60,
                CompetitionAccessType.PUBLICO_GRUPO);
        when(competitionProblems.findViewById(7)).thenReturn(Optional.of(view));

        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("aún no ha comenzado");
        verifyNoInteractions(codeforces);
    }

    @Test
    void codeforcesDownPropagatesTheUnavailableException() {
        when(codeforces.recentSubmissions(anyString(), anyInt())).thenThrow(new CodeforcesUnavailableException());

        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(CodeforcesUnavailableException.class);
        assertThatThrownBy(() -> service.platformSubmissions(7, null))
                .isInstanceOf(CodeforcesUnavailableException.class);
        verify(resolutions, never()).importPlatformSubmissions(anyInt(), anyInt(), anyList(), any(), any());
    }

    @Test
    void unknownCodeforcesHandleIsAConflictNotANotFoundOfTheAssignment() {
        when(codeforces.recentSubmissions(anyString(), anyInt())).thenThrow(new NotFoundException("no existe"));

        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void accessRulesMatchTheAssignedProblemsEndpoints() {
        assertThatThrownBy(() -> service.verify(null, null, null)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.verify(0, null, null)).isInstanceOf(FieldValidationException.class);

        when(competitionProblems.findViewById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify(99, null, null)).isInstanceOf(NotFoundException.class);

        when(memberships.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(20, 1, 10, "Ana", MembershipStatus.PENDIENTE),
                new GroupMemberData(21, 1, 11, "Beto", MembershipStatus.ACTIVO)));
        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ForbiddenException.class);

        user.setRole(Role.COACH);
        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ForbiddenException.class);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.SUSPENDIDO);
        assertThatThrownBy(() -> service.verify(7, null, null)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(codeforces);
    }

    // ---- platformSubmissions

    @Test
    void platformSubmissionsListsAllRecentOnesMarkingTheAssignedProblem() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 600, 1850, "A", "OK"),
                submission(299, AT_START + 500, 4, "A", "TIME_LIMIT_EXCEEDED"),
                new CodeforcesSubmission(298L, AT_START + 400, null, "A", null, "OK", null, null, null, null)));

        PlatformSubmissionsResponse response = service.platformSubmissions(7, null);

        assertThat(response.handle()).isEqualTo("test_handle");
        assertThat(response.count()).isEqualTo(10);
        assertThat(response.submissions()).extracting(PlatformSubmissionsResponse.Submission::matchesProblem)
                .containsExactly(true, false, false);
        assertThat(response.submissions().get(1).verdict()).isEqualTo(Verdict.TLE);
        assertThat(response.submissions().get(1).platformVerdict()).isEqualTo("TIME_LIMIT_EXCEEDED");
        assertThat(response.submissions().get(0).url()).isEqualTo("https://codeforces.com/contest/1850/submission/300");
        // Solo lectura: nunca guarda nada.
        verify(resolutions, never()).importPlatformSubmissions(anyInt(), anyInt(), anyList(), any(), any());
    }

    @Test
    void platformSubmissionsWorksBeforeTheCompetitionStartsButValidatesCount() {
        view = new AssignmentView(7, 11, "A", 100, "#FF0000", START.minusDays(1), 5, 1, "Simulacro",
                CompetitionStatus.PROGRAMADA, START, START.plusHours(5), 300, PenaltyRule.ICPC_20_MIN, 60,
                CompetitionAccessType.PUBLICO_GRUPO);
        when(competitionProblems.findViewById(7)).thenReturn(Optional.of(view));
        when(codeforces.recentSubmissions("test_handle", 100)).thenReturn(List.of());

        assertThat(service.platformSubmissions(7, 100).submissions()).isEmpty();
        assertThatThrownBy(() -> service.platformSubmissions(7, 101)).isInstanceOf(FieldValidationException.class);
        verify(codeforces, never()).recentSubmissions(anyString(), eq(101));
        verify(resolutions, never()).findAttemptsByMembershipId(anyInt());
    }

    @Test
    void attemptsOfTheVerifiedProblemDriveTheResultingStatus() {
        when(codeforces.recentSubmissions("test_handle", 10)).thenReturn(List.of(
                submission(300, AT_START + 600, 1850, "A", "COMPILATION_ERROR")));
        attempts.add(new MemberAttempt(1, 7, Verdict.COMPILATION_ERROR, "C++", START, null, null, null, 0,
                ResolutionOrigin.PLATAFORMA, 300L, null, FileComparison.NO_COMPARADO));
        attempts.add(new MemberAttempt(2, 8, Verdict.ACCEPTED, "C++", START, null));

        assertThat(service.verify(7, null, null).status()).isEqualTo(AssignmentStatus.EN_PROGRESO);
    }
}
