package com.kodika.kodikalab.problems.problem;

import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopic;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicRepository;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.problems.topic.Topic;
import com.kodika.kodikalab.problems.topic.TopicService;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemServiceTests {
    static final String URL = "https://codeforces.com/problemset/problem/1/A";

    ProblemRepository repository;
    ProblemTopicRepository topicLinks;
    TopicService topics;
    CurrentUserResolver resolver;
    EntityManager entityManager;
    ProblemService service;
    User coach;

    @BeforeEach
    void setUp() {
        repository = mock(ProblemRepository.class);
        topicLinks = mock(ProblemTopicRepository.class);
        topics = mock(TopicService.class);
        resolver = mock(CurrentUserResolver.class);
        entityManager = mock(EntityManager.class);
        service = new ProblemServiceImpl(repository, topicLinks, topics, resolver, entityManager);
        coach = user(10, Role.COACH, UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(coach);
        when(repository.save(any())).thenAnswer(invocation -> {
            Problem problem = invocation.getArgument(0);
            problem.setId(7);
            return problem;
        });
        when(topics.findOrCreateAll(anyCollection())).thenAnswer(invocation -> {
            List<Topic> result = new ArrayList<>();
            int id = 100;
            for (Object name : (java.util.Collection<?>) invocation.getArgument(0)) {
                Topic topic = new Topic();
                topic.setId(id++);
                topic.setName((String) name);
                result.add(topic);
            }
            return result;
        });
    }

    // ---- create

    @Test
    void createsWithErdDefaultsAndNoTopics() {
        var response = service.create(request("  Theatre Square  ", URL, null, null, null, null, null, null));

        assertThat(response.id()).isEqualTo(7);
        assertThat(response.title()).isEqualTo("Theatre Square");
        assertThat(response.url()).isEqualTo(URL);
        assertThat(response.sourcePlatform()).isEqualTo(SourcePlatform.CODEFORCES);
        assertThat(response.sourceCode()).isNull();
        assertThat(response.difficultyRating()).isNull();
        assertThat(response.timeLimitMs()).isEqualTo(1000);
        assertThat(response.memoryLimitMb()).isEqualTo(256);
        assertThat(response.topics()).isEmpty();
        verify(entityManager, never()).persist(any());
    }

    @Test
    void storesAllFieldsAndLinksDeduplicatedTopicsKeepingTheFirstSpelling() {
        var response = service.create(request(" Registration ", URL, SourcePlatform.ATCODER, " ABC-1 ", " 1400 ", 2000,
                512, List.of(" Grafos ", "grafos", "DP", "GRAFOS")));

        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSourcePlatform()).isEqualTo(SourcePlatform.ATCODER);
        assertThat(saved.getValue().getSourceCode()).isEqualTo("ABC-1");
        assertThat(saved.getValue().getDifficultyRating()).isEqualTo("1400");
        assertThat(saved.getValue().getTimeLimitMs()).isEqualTo(2000);
        assertThat(saved.getValue().getMemoryLimitMb()).isEqualTo(512);

        verify(topics).findOrCreateAll(List.of("Grafos", "DP"));
        ArgumentCaptor<Object> links = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(2)).persist(links.capture());
        assertThat(links.getAllValues()).allSatisfy(link -> {
            assertThat(link).isInstanceOf(ProblemTopic.class);
            assertThat(((ProblemTopic) link).getProblem().getId()).isEqualTo(7);
        });
        assertThat(response.topics()).extracting("name").containsExactly("Grafos", "DP");
    }

    @Test
    void blankOptionalTextsAreStoredAsNull() {
        service.create(request("Problema", URL, null, "   ", "  ", null, null, List.of()));

        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSourceCode()).isNull();
        assertThat(saved.getValue().getDifficultyRating()).isNull();
    }

    @Test
    void boundaryLengthsAreAccepted() {
        String longUrl = "https://example.com/" + "a".repeat(300 - "https://example.com/".length());

        var response = service.create(request("t".repeat(150), longUrl, null, "c".repeat(50), "d".repeat(30), 1, 1,
                List.of("x".repeat(80))));

        assertThat(response.title()).hasSize(150);
        assertThat(response.url()).hasSize(300);
    }

    @Test
    void absentSessionIsUnauthorized() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sin sesión"));

        assertThatThrownBy(() -> service.create(valid())).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("deniedUsers")
    void onlyAnActiveCoachCanRegisterProblems(User user) {
        when(resolver.currentUser()).thenReturn(user);

        assertThatThrownBy(() -> service.create(valid())).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    static Stream<User> deniedUsers() {
        return Stream.of(user(11, Role.PRACTICANTE, UserStatus.ACTIVO), user(10, Role.COACH, UserStatus.SUSPENDIDO));
    }

    @Test
    void missingBodyIsRejected() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey("body"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidValuesAreRejectedWithTheFieldToCorrect(CreateProblemRequest request, String field) {
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey(field));
        verify(repository, never()).save(any());
        verify(topics, never()).findOrCreateAll(any());
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(request(null, URL, null, null, null, null, null, null), "title"),
                Arguments.of(request("   ", URL, null, null, null, null, null, null), "title"),
                Arguments.of(request("t".repeat(151), URL, null, null, null, null, null, null), "title"),
                Arguments.of(request("T", null, null, null, null, null, null, null), "url"),
                Arguments.of(request("T", "  ", null, null, null, null, null, null), "url"),
                Arguments.of(request("T", "https://example.com/" + "a".repeat(290), null, null, null, null, null, null),
                        "url"),
                Arguments.of(request("T", "ftp://example.com/a", null, null, null, null, null, null), "url"),
                Arguments.of(request("T", "javascript:alert(1)", null, null, null, null, null, null), "url"),
                Arguments.of(request("T", "https://user:secret@example.com/a", null, null, null, null, null, null),
                        "url"),
                Arguments.of(request("T", "no es una url", null, null, null, null, null, null), "url"),
                Arguments.of(request("T", "https:///sin-host", null, null, null, null, null, null), "url"),
                Arguments.of(request("T", URL, null, "c".repeat(51), null, null, null, null), "sourceCode"),
                Arguments.of(request("T", URL, null, null, "d".repeat(31), null, null, null), "difficultyRating"),
                Arguments.of(request("T", URL, null, null, null, 0, null, null), "timeLimitMs"),
                Arguments.of(request("T", URL, null, null, null, null, 0, null), "memoryLimitMb"),
                Arguments.of(request("T", URL, null, null, null, null, null, List.of("   ")), "topics[0]"),
                Arguments.of(request("T", URL, null, null, null, null, null, List.of("ok", "x".repeat(81))),
                        "topics[1]"),
                Arguments.of(request("T", URL, null, null, null, null, null,
                        java.util.stream.IntStream.range(0, 21).mapToObj(i -> "tema" + i).toList()), "topics"));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var request = request(" ", "ftp://x", null, "c".repeat(51), null, 0, 0, List.of(" "));

        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsOnlyKeys("title", "url", "sourceCode",
                        "timeLimitMs", "memoryLimitMb", "topics[0]"));
    }

    @Test
    void duplicateSourceCodeOnTheSamePlatformIsAConflict() {
        when(repository.existsBySourcePlatformAndSourceCodeIgnoreCase(SourcePlatform.CODEFORCES, "1A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("T", URL, null, "1A", null, null, null, null)))
                .isInstanceOfSatisfying(FieldConflictException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("sourceCode"));
        verify(repository, never()).save(any());
    }

    @Test
    void duplicateUrlIsAConflictAndBothConflictsAreReportedTogether() {
        when(repository.existsByUrlIgnoreCase(URL)).thenReturn(true);
        when(repository.existsBySourcePlatformAndSourceCodeIgnoreCase(SourcePlatform.CODEFORCES, "1A")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("T", URL, null, "1A", null, null, null, null)))
                .isInstanceOfSatisfying(FieldConflictException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("sourceCode", "url"));
    }

    @Test
    void sourceCodeIsOnlyCheckedWhenPresent() {
        service.create(request("T", URL, null, null, null, null, null, null));

        verify(repository, never()).existsBySourcePlatformAndSourceCodeIgnoreCase(any(), any());
    }

    // ---- search

    @Test
    void searchRequiresAnActiveAccount() {
        when(resolver.currentUser()).thenReturn(user(11, Role.PRACTICANTE, UserStatus.SUSPENDIDO));

        assertThatThrownBy(() -> service.search(new ProblemSearch(null, null, null, null, 0, 20)))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).findAll(ArgumentMatchers.<Specification<Problem>>any(), any(Pageable.class));
    }

    @ParameterizedTest
    @MethodSource("invalidSearches")
    void invalidSearchCriteriaAreRejected(ProblemSearch search, String field) {
        assertThatThrownBy(() -> service.search(search)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey(field));
    }

    static Stream<Arguments> invalidSearches() {
        return Stream.of(
                Arguments.of(new ProblemSearch(null, null, null, null, -1, 20), "page"),
                Arguments.of(new ProblemSearch(null, null, null, null, 0, 0), "size"),
                Arguments.of(new ProblemSearch(null, null, null, null, 0, 101), "size"),
                Arguments.of(new ProblemSearch("q".repeat(101), null, null, null, 0, 20), "q"),
                Arguments.of(new ProblemSearch(null, null, "d".repeat(31), null, 0, 20), "difficulty"),
                Arguments.of(new ProblemSearch(null, 0, null, null, 0, 20), "topicId"));
    }

    @Test
    void searchReturnsAPageWithTheTopicsOfEachProblem() {
        Problem first = problem(1, "Alfa");
        Problem second = problem(2, "Beta");
        when(repository.findAll(ArgumentMatchers.<Specification<Problem>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 5));
        when(topicLinks.findTopicsByProblemIds(Set.of(1, 2))).thenReturn(List.of(
                new ProblemTopicData(1, 10, "Grafos"), new ProblemTopicData(1, 11, "DP")));

        var page = service.search(new ProblemSearch(" alfa ", 10, "1000", SourcePlatform.CODEFORCES, 1, 2));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(ArgumentMatchers.<Specification<Problem>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().toString()).isEqualTo("title: ASC,id: ASC");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalItems()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).extracting("title").containsExactly("Alfa", "Beta");
        assertThat(page.items().get(0).topics()).extracting("name").containsExactly("Grafos", "DP");
        assertThat(page.items().get(1).topics()).isEmpty();
    }

    @Test
    void emptyResultDoesNotQueryTopics() {
        when(repository.findAll(ArgumentMatchers.<Specification<Problem>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var page = service.search(new ProblemSearch(null, null, null, null, 0, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        verify(topicLinks, never()).findTopicsByProblemIds(any());
    }

    // ---- findSummariesByIds

    @Test
    void summariesOfNoIdsDoNotQueryTheRepository() {
        assertThat(service.findSummariesByIds(List.of())).isEmpty();
        verify(repository, never()).findAllById(any());
    }

    @Test
    void summariesExposeCatalogDataOnly() {
        when(repository.findAllById(List.of(1))).thenReturn(List.of(problem(1, "Alfa")));

        var summaries = service.findSummariesByIds(List.of(1));

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).id()).isEqualTo(1);
        assertThat(summaries.get(0).title()).isEqualTo("Alfa");
        assertThat(summaries.get(0).url()).isEqualTo(URL);
        assertThat(summaries.get(0).timeLimitMs()).isEqualTo(1000);
    }

    private static CreateProblemRequest valid() {
        return request("Theatre Square", URL, null, null, null, null, null, null);
    }

    private static CreateProblemRequest request(String title, String url, SourcePlatform platform, String sourceCode,
                                                String difficulty, Integer timeLimitMs, Integer memoryLimitMb,
                                                List<String> topics) {
        return new CreateProblemRequest(title, url, platform, sourceCode, difficulty, timeLimitMs, memoryLimitMb,
                topics);
    }

    private static Problem problem(int id, String title) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle(title);
        problem.setUrl(URL);
        problem.setSourcePlatform(SourcePlatform.CODEFORCES);
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitMb(256);
        return problem;
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
