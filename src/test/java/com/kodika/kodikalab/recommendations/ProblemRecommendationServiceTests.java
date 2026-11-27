package com.kodika.kodikalab.recommendations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.analytics.TeamTopicReportService;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.problems.codeforces.CodeforcesProblemCatalog;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblem;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import com.kodika.kodikalab.recommendations.ProblemRecommendation.Source;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProblemRecommendationServiceTests {
    private final TeamTopicReportService reports = mock(TeamTopicReportService.class);
    private final CompetitionProblemService assignments = mock(CompetitionProblemService.class);
    private final ProblemResolutionService resolutions = mock(ProblemResolutionService.class);
    private final ProblemService problems = mock(ProblemService.class);
    private final CodeforcesProblemCatalog codeforces = mock(CodeforcesProblemCatalog.class);
    private final ProblemRecommendationService service =
            new ProblemRecommendationService(reports, assignments, resolutions, problems, codeforces);
    private final Map<Integer, List<ProblemResponse>> bank = new HashMap<>();

    @BeforeEach
    void teamThatSolvedProblemsRated900And1100() {
        weakTopics(topic(1, "graphs", 4, 1, true), topic(2, "strings", 2, 2, false));
        when(resolutions.findResolutionsByTeamId(7)).thenReturn(List.of(
                resolution(501, Verdict.ACCEPTED), resolution(502, Verdict.ACCEPTED),
                resolution(503, Verdict.WRONG_ANSWER)));
        when(problems.findSummariesByIds(anyCollection())).thenReturn(List.of(summary(501, "900"),
                summary(502, "1100")));
        when(assignments.findAssignedProblemsByTeamId(7)).thenReturn(List.of(
                new TeamAssignedProblem(1, 1, 7, 10, CompetitionStatus.FINALIZADA)));
        when(problems.search(any())).thenAnswer(call -> {
            ProblemSearch search = call.getArgument(0);
            List<ProblemResponse> items = bank.getOrDefault(search.topicId(), List.of());
            return new ProblemPageResponse(items, 0, 100, items.size(), 1);
        });
        when(problems.findIdsBySourceCodes(eq(SourcePlatform.CODEFORCES), anyCollection())).thenReturn(Map.of());
        when(codeforces.problems()).thenReturn(List.of());
    }

    @Test
    void proposesUnassignedProblemsAroundWhatTheTeamAlreadySolved() {
        bank.put(1, List.of(local(10, "Assigned", "1100"), local(11, "Too easy", "800"),
                local(12, "Upper", "1300"), local(13, "Lower", "1000"), local(14, "Too hard", "1301"),
                local(15, "Unrated", null), local(16, "Middle", "1200")));

        var result = service.recommend(7, RecommendationCriteria.NONE);

        // Median of 900 and 1100 is 1000, so the range is 1000 to 1300, easiest first.
        assertThat(result.minDifficulty()).isEqualTo(1000);
        assertThat(result.maxDifficulty()).isEqualTo(1300);
        assertThat(result.problems()).extracting(ProblemRecommendation::title)
                .containsExactly("Lower", "Middle", "Upper");
        assertThat(result.problems().getFirst().reason()).contains("1 de 4").contains("1000").contains("1300");
        verifyNoInteractions(codeforces);
    }

    @Test
    void aRequestedRangeWinsOverTheTeamHistory() {
        bank.put(1, List.of(local(11, "Easy", "800"), local(12, "Hard", "1900")));

        var result = service.recommend(7, new RecommendationCriteria(1800, 2000, null));

        assertThat(result.problems()).extracting(ProblemRecommendation::title).containsExactly("Hard");
        verify(problems, never()).findSummariesByIds(anyCollection());
    }

    @Test
    void anInvalidRequestedRangeIsRejected() {
        assertThatThrownBy(() -> service.recommend(7, new RecommendationCriteria(1500, 1000, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void withoutRatedHistoryEveryProblemIsListedFromEasiestToHardestWithUnratedLast() {
        when(problems.findSummariesByIds(anyCollection())).thenReturn(List.of(summary(501, null)));
        bank.put(1, List.of(local(11, "Unrated", null), local(12, "Hard", "2000"), local(13, "Easy", "800")));

        var result = service.recommend(7, RecommendationCriteria.NONE);

        assertThat(result.minDifficulty()).isNull();
        assertThat(result.problems()).extracting(ProblemRecommendation::title)
                .containsExactly("Easy", "Hard", "Unrated");
    }

    @Test
    void severalEquallyWeakTopicsAreGroupedByTopicAndSortedByDifficultyWithoutRepeating() {
        weakTopics(topic(1, "graphs", 4, 1, true), topic(3, "math", 4, 1, true));
        bank.put(1, List.of(local(21, "Shared", "1200"), local(22, "Graph", "1000")));
        bank.put(3, List.of(local(21, "Shared", "1200"), local(23, "Math", "1100")));

        var result = service.recommend(7, RecommendationCriteria.NONE);

        assertThat(result.problems()).extracting(ProblemRecommendation::topic, ProblemRecommendation::title)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("graphs", "Graph"),
                        org.assertj.core.groups.Tuple.tuple("graphs", "Shared"),
                        org.assertj.core.groups.Tuple.tuple("math", "Math"));
    }

    @Test
    void aNamedTopicReplacesTheWeakestOnes() {
        bank.put(2, List.of(local(31, "String problem", "1000")));

        var result = service.recommend(7, new RecommendationCriteria(null, null, "Strings"));

        assertThat(result.problems()).extracting(ProblemRecommendation::title).containsExactly("String problem");
    }

    @Test
    void anUnknownTopicIsReportedWithTheTeamTopics() {
        var result = service.recommend(7, new RecommendationCriteria(null, null, "geometry"));

        assertThat(result.problems()).isEmpty();
        assertThat(result.notices()).singleElement().asString().contains("geometry").contains("graphs");
    }

    @Test
    void codeforcesCompletesTheListWithProblemsThatAreNotInTheCatalog() {
        bank.put(1, List.of(local(11, "Local", "1100")));
        when(codeforces.problems()).thenReturn(List.of(
                cf(1, "A", "Registered", 1100, "graphs"), cf(2, "B", "New hard", 1300, "graphs"),
                cf(3, "C", "New easy", 1000, "Graphs"), cf(4, "D", "Other topic", 1100, "math"),
                cf(5, "E", "Out of range", 2500, "graphs"), cf(6, "F", "Extra", 1250, "graphs")));
        when(problems.findIdsBySourceCodes(eq(SourcePlatform.CODEFORCES), anyCollection()))
                .thenReturn(Map.of("1A", 99));

        var result = service.recommend(7, RecommendationCriteria.NONE);

        assertThat(result.problems()).extracting(ProblemRecommendation::title)
                .containsExactly("New easy", "Local", "Extra");
        ProblemRecommendation external = result.problems().getFirst();
        assertThat(external.source()).isEqualTo(Source.CODEFORCES);
        assertThat(external.problemId()).isNull();
        assertThat(external.url()).isEqualTo("https://codeforces.com/problemset/problem/3/C");
        assertThat(external.reason()).contains("impórtalo");
    }

    @Test
    void ifCodeforcesFailsTheLocalOptionsRemainAndTheLimitationIsReported() {
        bank.put(1, List.of(local(11, "Local", "1100")));
        when(codeforces.problems()).thenThrow(new CodeforcesUnavailableException());

        var result = service.recommend(7, RecommendationCriteria.NONE);

        assertThat(result.problems()).extracting(ProblemRecommendation::title).containsExactly("Local");
        assertThat(result.notices()).singleElement().asString().contains("Codeforces");
    }

    @Test
    void withoutSuitableOptionsNothingIsInvented() {
        var result = service.recommend(7, RecommendationCriteria.NONE);

        assertThat(result.problems()).isEmpty();
        assertThat(result.notices()).singleElement().asString().contains("graphs").contains("1000");
    }

    @Test
    void theCatalogIsReadPageByPage() {
        List<ProblemResponse> secondPage = new ArrayList<>(List.of(local(41, "Second page", "1000")));
        doAnswer(call -> {
            ProblemSearch search = call.getArgument(0);
            return search.page() == 0 ? new ProblemPageResponse(List.of(), 0, 100, 101, 2)
                    : new ProblemPageResponse(secondPage, 1, 100, 101, 2);
        }).when(problems).search(any());

        assertThat(service.recommend(7, RecommendationCriteria.NONE).problems())
                .extracting(ProblemRecommendation::title).containsExactly("Second page");
    }

    @Test
    void permissionIsCheckedByTheReportBeforeAnythingElseIsRead() {
        when(reports.getReport(7)).thenThrow(new ForbiddenException("No tiene autorización"));

        assertThatThrownBy(() -> service.recommend(7, RecommendationCriteria.NONE))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(assignments, resolutions, problems, codeforces);
    }

    private void weakTopics(TopicPerformance... topics) {
        when(reports.getReport(7)).thenReturn(
                new TeamTopicReportResponse(7, "m", "c", "e", 3, 0, List.of(topics)));
    }

    private static TopicPerformance topic(int id, String name, int assigned, int solved, boolean lowest) {
        return new TopicPerformance(id, name, assigned, solved, assigned - solved, 1, 0, BigDecimal.TEN, lowest);
    }

    private static TeamResolutionData resolution(int problemId, Verdict verdict) {
        return new TeamResolutionData(problemId, 1, 7, problemId, 1, 7, problemId, verdict);
    }

    private static ProblemSummary summary(int id, String rating) {
        return new ProblemSummary(id, "Solved " + id, "u", SourcePlatform.CODEFORCES, "x", rating, 1000, 256);
    }

    private static ProblemResponse local(int id, String title, String rating) {
        return new ProblemResponse(id, title, "https://example.test/" + id, SourcePlatform.CODEFORCES, id + "A",
                rating, 1000, 256, List.of());
    }

    private static CodeforcesProblem cf(int contest, String index, String name, int rating, String tag) {
        return new CodeforcesProblem(contest, index, name, rating, List.of(tag));
    }
}
