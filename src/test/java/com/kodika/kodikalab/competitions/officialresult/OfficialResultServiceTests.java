package com.kodika.kodikalab.competitions.officialresult;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competition.CompetitionRepository;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultRequest;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OfficialResultServiceTests {
    OfficialResultRepository repository;
    CompetitionRepository competitions;
    StudyGroupService groups;
    CurrentUserResolver resolver;
    OfficialResultService service;
    Competition competition;
    User coach;

    @BeforeEach
    void setUp() {
        repository = mock(OfficialResultRepository.class);
        competitions = mock(CompetitionRepository.class);
        groups = mock(StudyGroupService.class);
        resolver = mock(CurrentUserResolver.class);
        service = new OfficialResultServiceImpl(repository, competitions, groups, resolver);
        coach = new User();
        coach.setId(10);
        coach.setRole(Role.COACH);
        coach.setStatus(UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(coach);
        StudyGroup group = new StudyGroup();
        group.setId(1);
        competition = new Competition();
        competition.setId(2);
        competition.setGroup(group);
        competition.setStatus(CompetitionStatus.FINALIZADA);
        competition.setEventName("Competencia Prueba");
        competition.setEndsAt(OffsetDateTime.parse("2026-10-01T18:00:00Z"));
        when(competitions.findById(2)).thenReturn(Optional.of(competition));
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 10)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            OfficialResult result = invocation.getArgument(0);
            result.setId(3);
            return result;
        });
    }

    @Test
    void coachCanCreateConfirmedResultWithoutIndividualResolutions() {
        var result = service.create(2, new OfficialResultRequest(4, 0, true));
        assertThat(result.id()).isEqualTo(3);
        assertThat(result.teamId()).isEqualTo(1);
        assertThat(result.competitionId()).isEqualTo(2);
        assertThat(result.finalPosition()).isEqualTo(4);
        assertThat(result.solvedProblems()).isZero();
        assertThat(result.status()).isEqualTo(OfficialResultStatus.CONFIRMADO);
        assertThat(result.registeredAt()).isNotNull();
        assertThat(result.confirmedAt()).isAfterOrEqualTo(result.registeredAt());
    }

    @Test
    void partialResultCanRemainPendingBeforeCompetitionEnds() {
        competition.setStatus(CompetitionStatus.PROGRAMADA);
        var result = service.create(2, new OfficialResultRequest(null, 2, false));
        assertThat(result.status()).isEqualTo(OfficialResultStatus.PENDIENTE);
        assertThat(result.finalPosition()).isNull();
        assertThat(result.solvedProblems()).isEqualTo(2);
        assertThat(result.confirmedAt()).isNull();
    }

    @Test
    void completeFieldsDoNotImplyConfirmation() {
        var result = service.create(2, new OfficialResultRequest(1, 8, false));
        assertThat(result.status()).isEqualTo(OfficialResultStatus.PENDIENTE);
        assertThat(result.confirmedAt()).isNull();
    }

    @Test
    void pendingCanBeCompletedAndExplicitlyConfirmed() {
        OfficialResult pending = pending();
        when(repository.findForUpdate(2)).thenReturn(Optional.of(pending));
        var result = service.update(2, new OfficialResultRequest(2, 5, true));
        assertThat(result.status()).isEqualTo(OfficialResultStatus.CONFIRMADO);
        assertThat(result.finalPosition()).isEqualTo(2);
        assertThat(result.solvedProblems()).isEqualTo(5);
        assertThat(result.registeredAt()).isEqualTo(pending.getRegisteredAt());
        assertThat(result.confirmedAt()).isNotNull();
        verify(repository).findForUpdate(2);
    }

    @Test
    void pendingCanBeReplacedWithoutPublishing() {
        when(repository.findForUpdate(2)).thenReturn(Optional.of(pending()));
        var result = service.update(2, new OfficialResultRequest(3, null, false));
        assertThat(result.status()).isEqualTo(OfficialResultStatus.PENDIENTE);
        assertThat(result.finalPosition()).isEqualTo(3);
        assertThat(result.solvedProblems()).isNull();
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new OfficialResultRequest(0, 1, false), "finalPosition"),
                Arguments.of(new OfficialResultRequest(-1, 1, true), "finalPosition"),
                Arguments.of(new OfficialResultRequest(1, -1, false), "solvedProblems"),
                Arguments.of(new OfficialResultRequest(null, 1, true), "finalPosition"),
                Arguments.of(new OfficialResultRequest(1, null, true), "solvedProblems"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidCreationIdentifiesFieldAndDoesNotSave(OfficialResultRequest request, String field) {
        assertThatThrownBy(() -> service.create(2, request)).isInstanceOfSatisfying(
                OfficialResultValidationException.class, exception -> assertThat(exception.getErrors()).containsKey(field));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void allMissingConfirmationFieldsAreReportedTogether() {
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(null, null, true)))
                .isInstanceOfSatisfying(OfficialResultValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsKeys("finalPosition", "solvedProblems"));
    }

    @Test
    void missingRequestIsRejected() {
        assertThatThrownBy(() -> service.create(2, null)).isInstanceOf(OfficialResultValidationException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAMADA", "EN_CURSO"})
    void cannotConfirmUnfinishedCompetition(String status) {
        competition.setStatus(CompetitionStatus.valueOf(status));
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOfSatisfying(OfficialResultValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsKey("competition.status"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidConfirmationKeepsValidPendingFieldsAndAllowsRetry() {
        OfficialResult pending = pending();
        when(repository.findForUpdate(2)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.update(2, new OfficialResultRequest(0, -1, true)))
                .isInstanceOf(OfficialResultValidationException.class);
        assertThat(pending.getFinalPosition()).isNull();
        assertThat(pending.getSolvedProblems()).isEqualTo(2);
        assertThat(pending.getStatus()).isEqualTo(OfficialResultStatus.PENDIENTE);
        verify(repository, never()).saveAndFlush(any());
        assertThat(service.update(2, new OfficialResultRequest(2, 3, true)).status())
                .isEqualTo(OfficialResultStatus.CONFIRMADO);
    }

    @Test
    void confirmedResultCannotBeChangedOrReturnedToPending() {
        OfficialResult confirmed = pending();
        confirmed.setStatus(OfficialResultStatus.CONFIRMADO);
        confirmed.setFinalPosition(1);
        when(repository.findForUpdate(2)).thenReturn(Optional.of(confirmed));
        assertThatThrownBy(() -> service.update(2, new OfficialResultRequest(4, 1, false)))
                .isInstanceOf(ConflictException.class);
        assertThat(confirmed.getFinalPosition()).isEqualTo(1);
        assertThat(confirmed.getStatus()).isEqualTo(OfficialResultStatus.CONFIRMADO);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void duplicatePendingOrConfirmedResultIsRejectedBeforeWrite() {
        when(repository.findByCompetitionId(2)).thenReturn(Optional.of(pending()));
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentUniqueViolationIsRecognizedWithoutDependingOnDatabaseLanguage() {
        doThrow(new DataIntegrityViolationException("Insert",
                new SQLException("restricción uq_resultado_oficial_competencia", "23505")))
                .when(repository).saveAndFlush(any());
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unrelatedIntegrityErrorIsNotReportedAsDuplicate() {
        var failure = new DataIntegrityViolationException("Otra restricción");
        doThrow(failure).when(repository).saveAndFlush(any());
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true))).isSameAs(failure);
    }

    @Test
    void historyRequestsOnlyConfirmedRowsForAuthorizedTeam() {
        OfficialResult confirmed = pending();
        confirmed.setStatus(OfficialResultStatus.CONFIRMADO);
        when(repository.findHistory(1, OfficialResultStatus.CONFIRMADO)).thenReturn(List.of(confirmed));
        assertThat(service.history(1)).hasSize(1).allSatisfy(result -> {
            assertThat(result.teamId()).isEqualTo(1);
            assertThat(result.status()).isEqualTo(OfficialResultStatus.CONFIRMADO);
        });
        verify(repository).findHistory(1, OfficialResultStatus.CONFIRMADO);
    }

    @Test
    void emptyHistoryIsValid() {
        assertThat(service.history(1)).isEmpty();
    }

    @Test
    void coachCanConsultOwnPendingDetail() {
        when(repository.findByCompetitionId(2)).thenReturn(Optional.of(pending()));
        assertThat(service.get(2).status()).isEqualTo(OfficialResultStatus.PENDIENTE);
    }

    @Test
    void missingResultCannotBeConsultedOrUpdated() {
        assertThatThrownBy(() -> service.get(2)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void missingCompetitionIsRejected() {
        when(competitions.findById(2)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void missingTeamIsRejected() {
        when(groups.findSummaryById(1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history(1)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void invalidIdentifiersAreRejected(Integer id) {
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.history(id)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void practitionerCannotReadOrWriteResults() {
        coach.setRole(Role.PRACTICANTE);
        assertAllOperationsForbidden();
        verifyNoInteractions(repository, competitions, groups);
    }

    @Test
    void suspendedCoachCannotReadOrWriteResults() {
        coach.setStatus(UserStatus.SUSPENDIDO);
        assertAllOperationsForbidden();
        verifyNoInteractions(repository, competitions, groups);
    }

    @Test
    void unrelatedCoachCannotReadOrWriteResults() {
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 20)));
        assertAllOperationsForbidden();
        verifyNoInteractions(repository);
    }

    @Test
    void unauthenticatedRequestCannotReadOrWriteResults() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sesión requerida"));
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.history(1)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(repository, competitions, groups);
    }

    @Test
    void unavailableInformationDoesNotBecomeAnEmptyHistory() {
        when(repository.findHistory(1, OfficialResultStatus.CONFIRMADO))
                .thenThrow(new DataAccessResourceFailureException("No disponible"));
        assertThatThrownBy(() -> service.history(1)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private void assertAllOperationsForbidden() {
        assertThatThrownBy(() -> service.create(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(2, new OfficialResultRequest(1, 2, true)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.get(2)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.history(1)).isInstanceOf(ForbiddenException.class);
    }

    private OfficialResult pending() {
        OfficialResult result = new OfficialResult();
        result.setId(3);
        result.setCompetition(competition);
        result.setSolvedProblems(2);
        result.setStatus(OfficialResultStatus.PENDIENTE);
        result.setRegisteredAt(OffsetDateTime.parse("2026-10-02T10:00:00Z"));
        return result;
    }
}
