package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemRequest;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblem;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblemClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CodeforcesProblemServiceTests {
    static final String WATERMELON_URL = "https://codeforces.com/problemset/problem/4/A";
    static final CodeforcesProblem WATERMELON = new CodeforcesProblem(4, "A", "Watermelon", 800,
            List.of("brute force", "math"));

    CodeforcesProblemClient client;
    ProblemService problems;
    CurrentUserResolver resolver;
    CodeforcesProblemService service;

    @BeforeEach
    void setUp() {
        client = mock(CodeforcesProblemClient.class);
        problems = mock(ProblemService.class);
        resolver = mock(CurrentUserResolver.class);
        var catalog = new CodeforcesProblemCatalog(client, Duration.ofHours(6), Clock.systemUTC());
        service = new CodeforcesProblemServiceImpl(client, catalog, problems, resolver);
        when(resolver.currentUser()).thenReturn(user(Role.COACH, UserStatus.ACTIVO));
        when(problems.findExisting(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(problems.findIdsBySourceCodes(any(), anyCollection())).thenReturn(Map.of());
    }

    // ---- import

    @Test
    void importsAnUnknownProblemWithMappedFieldsTopicsAndDocumentedLimits() {
        when(client.findProblem(4, "A")).thenReturn(Optional.of(WATERMELON));
        var stored = stored(12);
        when(problems.create(any())).thenReturn(stored);

        var response = service.importProblem(new ImportCodeforcesProblemRequest("  4a "));

        assertThat(response.created()).isTrue();
        assertThat(response.problem()).isSameAs(stored);
        verify(problems).create(new CreateProblemRequest("Watermelon", WATERMELON_URL, SourcePlatform.CODEFORCES,
                "4A", "800", 2000, 256, List.of("brute force", "math")));
    }

    @Test
    void acceptsAFullProblemUrl() {
        when(client.findProblem(4, "A")).thenReturn(Optional.of(WATERMELON));
        when(problems.create(any())).thenReturn(stored(12));

        service.importProblem(new ImportCodeforcesProblemRequest("https://codeforces.com/contest/4/problem/a"));

        verify(client).findProblem(4, "A");
        verify(problems, atLeastOnce()).findExisting(SourcePlatform.CODEFORCES, "4A", WATERMELON_URL);
    }

    @Test
    void leavesTheDifficultyEmptyWhenCodeforcesDoesNotRateTheProblem() {
        when(client.findProblem(4, "A")).thenReturn(Optional.of(
                new CodeforcesProblem(4, "A", "Watermelon", null, List.of())));
        when(problems.create(any())).thenReturn(stored(12));

        service.importProblem(new ImportCodeforcesProblemRequest("4A"));

        verify(problems).create(new CreateProblemRequest("Watermelon", WATERMELON_URL, SourcePlatform.CODEFORCES,
                "4A", null, 2000, 256, List.of()));
    }

    @Test
    void fillsAMissingDifficultyFromTheProblemsetWhenItIsAlreadyInMemory() {
        when(client.listProblems()).thenReturn(List.of(WATERMELON));
        service.search("watermelon", null); // loads the problemset copy
        when(client.findProblem(4, "A")).thenReturn(Optional.of(
                new CodeforcesProblem(4, "A", "Watermelon", null, List.of("math"))));
        when(problems.create(any())).thenReturn(stored(12));

        service.importProblem(new ImportCodeforcesProblemRequest("4A"));

        verify(problems).create(new CreateProblemRequest("Watermelon", WATERMELON_URL, SourcePlatform.CODEFORCES,
                "4A", "800", 2000, 256, List.of("math")));
    }

    @Test
    void anAlreadyImportedProblemIsReturnedWithoutCallingCodeforces() {
        var existing = stored(3);
        when(problems.findExisting(SourcePlatform.CODEFORCES, "4A", WATERMELON_URL))
                .thenReturn(Optional.of(existing));

        var response = service.importProblem(new ImportCodeforcesProblemRequest("4A"));

        assertThat(response.created()).isFalse();
        assertThat(response.problem()).isSameAs(existing);
        verifyNoInteractions(client);
        verify(problems, never()).create(any());
    }

    @Test
    void anUnknownContestOrIndexIsNotFound() {
        when(client.findProblem(4, "Z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importProblem(new ImportCodeforcesProblemRequest("4Z")))
                .isInstanceOf(NotFoundException.class).hasMessage("El problema no existe en Codeforces");
        verify(problems, never()).create(any());
    }

    @Test
    void aCodeforcesFailureIsPropagatedAndNothingIsSaved() {
        when(client.findProblem(anyInt(), anyString())).thenThrow(new CodeforcesUnavailableException());

        assertThatThrownBy(() -> service.importProblem(new ImportCodeforcesProblemRequest("4A")))
                .isInstanceOf(CodeforcesUnavailableException.class)
                .hasMessage("Codeforces no está disponible temporalmente. Inténtelo de nuevo más tarde.");
        verify(problems, never()).create(any());
    }

    @Test
    void anInvalidCodeIsRejectedBeforeAnyLookup() {
        for (String text : new String[]{null, "", "nonsense"}) {
            assertThatThrownBy(() -> service.importProblem(new ImportCodeforcesProblemRequest(text)))
                    .isInstanceOfSatisfying(FieldValidationException.class,
                            exception -> assertThat(exception.getErrors()).containsOnlyKeys("code"));
        }
        assertThatThrownBy(() -> service.importProblem(null)).isInstanceOf(FieldValidationException.class);
        verifyNoInteractions(client);
        verify(problems, never()).findExisting(any(), anyString(), anyString());
    }

    @Test
    void losingARaceReturnsTheRowTheOtherRequestSaved() {
        when(client.findProblem(4, "A")).thenReturn(Optional.of(WATERMELON));
        var winner = stored(5);
        when(problems.findExisting(any(), anyString(), anyString()))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        when(problems.create(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = service.importProblem(new ImportCodeforcesProblemRequest("4A"));

        assertThat(response.created()).isFalse();
        assertThat(response.problem()).isSameAs(winner);
    }

    @Test
    void aConflictWithNoRowToReturnIsNotSwallowed() {
        when(client.findProblem(4, "A")).thenReturn(Optional.of(WATERMELON));
        when(problems.create(any())).thenThrow(new FieldConflictException("dup", Map.of("url", "x")));

        assertThatThrownBy(() -> service.importProblem(new ImportCodeforcesProblemRequest("4A")))
                .isInstanceOf(FieldConflictException.class);
    }

    @Test
    void concurrentImportsOfTheSameProblemSaveItOnce() throws Exception {
        when(client.findProblem(4, "A")).thenReturn(Optional.of(WATERMELON));
        var saved = new java.util.concurrent.atomic.AtomicReference<ProblemResponse>();
        when(problems.findExisting(any(), anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(problems.create(any())).thenAnswer(invocation -> {
            Thread.sleep(50);
            saved.set(stored(9));
            return saved.get();
        });
        var created = java.util.Collections.synchronizedList(new ArrayList<Boolean>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Thread thread = new Thread(() -> created.add(
                    service.importProblem(new ImportCodeforcesProblemRequest("4A")).created()));
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(created).containsOnlyOnce(true).hasSize(6);
        verify(problems, org.mockito.Mockito.times(1)).create(any());
    }

    @Test
    void onlyAnActiveCoachMayImportOrSearch() {
        for (User user : new User[]{user(Role.PRACTICANTE, UserStatus.ACTIVO), user(Role.COACH, UserStatus.SUSPENDIDO)}) {
            when(resolver.currentUser()).thenReturn(user);
            assertThatThrownBy(() -> service.importProblem(new ImportCodeforcesProblemRequest("4A")))
                    .isInstanceOf(ForbiddenException.class);
            assertThatThrownBy(() -> service.search("watermelon", null)).isInstanceOf(ForbiddenException.class);
        }
        verifyNoInteractions(client);
    }

    // ---- search

    static final CodeforcesProblem A1850 = new CodeforcesProblem(1850, "A", "To My Critics", 800, List.of("implementation"));
    static final CodeforcesProblem B1850 = new CodeforcesProblem(1850, "B", "Ten Words of Wisdom", 800, List.of());
    static final CodeforcesProblem MELON_2 = new CodeforcesProblem(1234, "C", "Watermelon Returns", null, List.of("math"));

    private void catalogOf(CodeforcesProblem... list) {
        when(client.listProblems()).thenReturn(List.of(list));
    }

    @Test
    void searchesByCodePrefix() {
        catalogOf(A1850, B1850, WATERMELON, MELON_2);

        var result = service.search("1850", null);

        assertThat(result).extracting("code").containsExactly("1850A", "1850B");
        assertThat(result.get(0).title()).isEqualTo("To My Critics");
        assertThat(result.get(0).difficultyRating()).isEqualTo("800");
        assertThat(result.get(0).topics()).containsExactly("implementation");
        assertThat(result.get(0).url()).isEqualTo("https://codeforces.com/problemset/problem/1850/A");
        assertThat(service.search("1850b", null)).extracting("code").containsExactly("1850B");
    }

    @Test
    void searchesByTitleIgnoringCaseAndListsCodeMatchesFirst() {
        var codeMatch = new CodeforcesProblem(20, "A", "Something else", 900, List.of());
        catalogOf(MELON_2, codeMatch, WATERMELON, A1850);

        assertThat(service.search("WaTerMeLon", null)).extracting("code").containsExactly("1234C", "4A");
        assertThat(service.search("20a", null)).extracting("code").containsExactly("20A");
        assertThat(service.search("critic", null)).extracting("code").containsExactly("1850A");
    }

    @Test
    void aProblemWithoutDifficultyHasANullRating() {
        catalogOf(MELON_2);

        assertThat(service.search("returns", null).get(0).difficultyRating()).isNull();
    }

    @Test
    void marksProblemsAlreadyImportedWithTheirLocalId() {
        catalogOf(A1850, B1850);
        when(problems.findIdsBySourceCodes(eq(SourcePlatform.CODEFORCES), anyCollection()))
                .thenReturn(Map.of("1850B", 12));

        var result = service.search("1850", null);

        assertThat(result.get(0).problemId()).isNull();
        assertThat(result.get(1).problemId()).isEqualTo(12);
    }

    @Test
    void appliesTheLimitAndDefaultsToTwenty() {
        List<CodeforcesProblem> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(new CodeforcesProblem(1000 + i, "A", "Problem " + i, 800, List.of()));
        }
        when(client.listProblems()).thenReturn(many);

        assertThat(service.search("problem", null)).hasSize(20);
        assertThat(service.search("problem", 5)).hasSize(5);
        assertThat(service.search("problem", 50)).hasSize(50);
        assertThat(service.search("10", 3)).extracting("code").containsExactly("1000A", "1001A", "1002A");
    }

    @Test
    void rejectsShortBlankOrOutOfRangeCriteria() {
        for (String query : new String[]{null, "", "  ", "a", " b "}) {
            assertThatThrownBy(() -> service.search(query, null)).isInstanceOfSatisfying(FieldValidationException.class,
                    exception -> assertThat(exception.getErrors()).containsOnlyKeys("q"));
        }
        for (int limit : new int[]{0, -1, 51}) {
            assertThatThrownBy(() -> service.search("watermelon", limit))
                    .isInstanceOfSatisfying(FieldValidationException.class,
                            exception -> assertThat(exception.getErrors()).containsOnlyKeys("limit"));
        }
        verifyNoInteractions(client);
    }

    @Test
    void searchFailsWithUnavailableWhenThereIsNoCopyAndCodeforcesIsDown() {
        when(client.listProblems()).thenThrow(new CodeforcesUnavailableException());

        assertThatThrownBy(() -> service.search("watermelon", null)).isInstanceOf(CodeforcesUnavailableException.class);
    }

    private static ProblemResponse stored(int id) {
        return new ProblemResponse(id, "Watermelon", WATERMELON_URL, SourcePlatform.CODEFORCES, "4A", "800", 2000, 256,
                List.of(new ProblemResponse.TopicInfo(1, "brute force")));
    }

    private static User user(Role role, UserStatus status) {
        User user = new User();
        user.setId(10);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
