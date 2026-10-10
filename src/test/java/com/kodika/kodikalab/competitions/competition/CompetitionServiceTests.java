package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.dto.CreateCompetitionRequest;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionServiceTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");
    static final OffsetDateTime END = START.plusHours(5);

    CompetitionRepository repository;
    StudyGroupService groups;
    CurrentUserResolver resolver;
    PasswordEncoder encoder;
    EntityManager entityManager;
    CompetitionService service;
    User coach;

    @BeforeEach
    void setUp() {
        repository = mock(CompetitionRepository.class);
        groups = mock(StudyGroupService.class);
        resolver = mock(CurrentUserResolver.class);
        encoder = mock(PasswordEncoder.class);
        entityManager = mock(EntityManager.class);
        service = new CompetitionServiceImpl(repository, groups, resolver, encoder, entityManager);
        coach = user(10, Role.COACH, UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(coach);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 10)));
        when(entityManager.getReference(StudyGroup.class, 1)).thenAnswer(invocation -> {
            StudyGroup group = new StudyGroup();
            group.setId(1);
            return group;
        });
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        when(repository.save(any())).thenAnswer(invocation -> {
            Competition competition = invocation.getArgument(0);
            competition.setId(7);
            return competition;
        });
    }

    @Test
    void createsWithErdDefaultsAndDurationDerivedFromDates() {
        var response = service.create(request(1, "  Simulacro 1  ", "  ", null, null, null, null, null, START, END));

        assertThat(response.id()).isEqualTo(7);
        assertThat(response.teamId()).isEqualTo(1);
        assertThat(response.eventName()).isEqualTo("Simulacro 1");
        assertThat(response.description()).isNull();
        assertThat(response.accessType()).isEqualTo(CompetitionAccessType.PUBLICO_GRUPO);
        assertThat(response.penaltyRule()).isEqualTo(PenaltyRule.ICPC_20_MIN);
        assertThat(response.durationMinutes()).isEqualTo(300);
        assertThat(response.scoreboardFreezeMinutes()).isEqualTo(60);
        assertThat(response.status()).isEqualTo(CompetitionStatus.PROGRAMADA);
        assertThat(response.startsAt()).isEqualTo(START);
        assertThat(saved().getAccessKey()).isNull();
        verify(encoder, never()).encode(any());
    }

    @Test
    void acceptsExplicitValuesIncludingFinishedCompetitionForPastEvents() {
        var response = service.create(request(1, "ICPC Regional", "Evento oficial", CompetitionAccessType.PUBLICO_GRUPO,
                null, PenaltyRule.IOI_POINTS, 15, CompetitionStatus.FINALIZADA, START, END));

        assertThat(response.penaltyRule()).isEqualTo(PenaltyRule.IOI_POINTS);
        assertThat(response.scoreboardFreezeMinutes()).isEqualTo(15);
        assertThat(response.status()).isEqualTo(CompetitionStatus.FINALIZADA);
        assertThat(response.description()).isEqualTo("Evento oficial");
    }

    @Test
    void defaultFreezeNeverExceedsAShortDuration() {
        var response = service.create(request(1, "Relámpago", null, null, null, null, null, null, START,
                START.plusMinutes(30)));

        assertThat(response.durationMinutes()).isEqualTo(30);
        assertThat(response.scoreboardFreezeMinutes()).isEqualTo(30);
    }

    @Test
    void privateCompetitionStoresOnlyAHashOfTheKey() {
        service.create(request(1, "Privada", null, CompetitionAccessType.PRIVADO_PASS, "secreto", null, null, null,
                START, END));

        verify(encoder).encode("secreto");
        assertThat(saved().getAccessKey()).isEqualTo("$2a$hash").isNotEqualTo("secreto");
        assertThat(saved().getAccessType()).isEqualTo(CompetitionAccessType.PRIVADO_PASS);
    }

    @Test
    void usesTheAuthenticatedCoachsTeamReferenceAndNeverTheCallersIdentity() {
        service.create(request(1, "Simulacro 1", null, null, null, null, null, null, START, END));

        assertThat(saved().getGroup().getId()).isEqualTo(1);
        verify(resolver).currentUser();
    }

    @Test
    void absentSessionIsUnauthorized() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sin sesión"));

        assertThatThrownBy(() -> service.create(valid())).isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("deniedUsers")
    void onlyAnActiveCoachCanCreate(User user) {
        when(resolver.currentUser()).thenReturn(user);

        assertThatThrownBy(() -> service.create(valid())).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    static Stream<User> deniedUsers() {
        return Stream.of(user(11, Role.PRACTICANTE, UserStatus.ACTIVO), user(10, Role.COACH, UserStatus.SUSPENDIDO));
    }

    @Test
    void missingTeamIsNotFound() {
        when(groups.findSummaryById(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(2, "Simulacro", null, null, null, null, null, null, START, END)))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void anotherCoachsTeamIsForbidden() {
        when(groups.findSummaryById(3)).thenReturn(Optional.of(new StudyGroupSummary(3, 99)));

        assertThatThrownBy(() -> service.create(request(3, "Simulacro", null, null, null, null, null, null, START, END)))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void incompleteTeamInformationIsAConflict() {
        when(groups.findSummaryById(4)).thenReturn(Optional.of(new StudyGroupSummary(4, null)));

        assertThatThrownBy(() -> service.create(request(4, "Simulacro", null, null, null, null, null, null, START, END)))
                .isInstanceOf(ConflictException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidTeamIds")
    void teamIdIsRequiredAndPositive(Integer teamId) {
        assertThatThrownBy(() -> service.create(request(teamId, "Simulacro", null, null, null, null, null, null,
                START, END))).isInstanceOfSatisfying(CompetitionValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey("teamId"));
        verify(groups, never()).findSummaryById(any());
    }

    static Stream<Integer> invalidTeamIds() {
        return Stream.of(null, 0, -1);
    }

    @Test
    void missingBodyIsRejected() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOfSatisfying(CompetitionValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey("body"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidValuesAreRejectedWithTheFieldToCorrectAndNothingIsSaved(CreateCompetitionRequest request,
                                                                        String field) {
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(CompetitionValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey(field));
        verify(repository, never()).save(any());
    }

    static Stream<Arguments> invalidRequests() {
        String longKey = "ñ".repeat(37);
        return Stream.of(
                Arguments.of(request(1, null, null, null, null, null, null, null, START, END), "eventName"),
                Arguments.of(request(1, "   ", null, null, null, null, null, null, START, END), "eventName"),
                Arguments.of(request(1, "x".repeat(151), null, null, null, null, null, null, START, END), "eventName"),
                Arguments.of(request(1, "Simulacro", "x".repeat(501), null, null, null, null, null, START, END),
                        "description"),
                Arguments.of(request(1, "Simulacro", null, CompetitionAccessType.PRIVADO_PASS, null, null, null, null,
                        START, END), "accessKey"),
                Arguments.of(request(1, "Simulacro", null, CompetitionAccessType.PRIVADO_PASS, "   ", null, null, null,
                        START, END), "accessKey"),
                Arguments.of(request(1, "Simulacro", null, CompetitionAccessType.PRIVADO_PASS, longKey, null, null, null,
                        START, END), "accessKey"),
                Arguments.of(request(1, "Simulacro", null, CompetitionAccessType.PUBLICO_GRUPO, "secreto", null, null,
                        null, START, END), "accessKey"),
                Arguments.of(request(1, "Simulacro", null, null, "secreto", null, null, null, START, END), "accessKey"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, null, null, null, END), "startsAt"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, null, null, START, null), "endsAt"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, null, null, START, START), "endsAt"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, null, null, START, START.minusHours(1)),
                        "endsAt"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, null, null, START,
                        START.plusSeconds(30)), "endsAt"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, -1, null, START, END),
                        "scoreboardFreezeMinutes"),
                Arguments.of(request(1, "Simulacro", null, null, null, null, 301, null, START, END),
                        "scoreboardFreezeMinutes"));
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        var request = request(1, " ", "x".repeat(501), CompetitionAccessType.PRIVADO_PASS, null, null, null, null,
                START, START.minusHours(1));

        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(CompetitionValidationException.class,
                exception -> assertThat(exception.getErrors()).containsOnlyKeys("eventName", "description", "accessKey",
                        "endsAt"));
    }

    @Test
    void boundaryValuesAreAccepted() {
        var response = service.create(request(1, "x".repeat(150), "y".repeat(500), null, null, null, 300, null, START,
                END));

        assertThat(response.eventName()).hasSize(150);
        assertThat(response.scoreboardFreezeMinutes()).isEqualTo(300);
    }

    @Test
    void duplicateNameAndStartInTheSameTeamIsAConflict() {
        when(repository.existsByGroupIdAndEventNameIgnoreCaseAndStartsAt(eq(1), eq("Simulacro 1"), eq(START)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(1, " Simulacro 1 ", null, null, null, null, null, null, START,
                END))).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void summaryExposesTheTeamAndStatusOfAnExistingCompetition() {
        Competition competition = new Competition();
        competition.setId(5);
        StudyGroup group = new StudyGroup();
        group.setId(1);
        competition.setGroup(group);
        competition.setStatus(CompetitionStatus.EN_CURSO);
        when(repository.findById(5)).thenReturn(Optional.of(competition));
        when(repository.findForUpdate(5)).thenReturn(Optional.of(competition));

        assertThat(service.findSummaryById(5)).hasValueSatisfying(summary -> {
            assertThat(summary.id()).isEqualTo(5);
            assertThat(summary.teamId()).isEqualTo(1);
            assertThat(summary.status()).isEqualTo(CompetitionStatus.EN_CURSO);
        });
        assertThat(service.findSummaryForUpdate(5)).isPresent();
        verify(repository).findForUpdate(5);
    }

    @Test
    void summaryOfAMissingCompetitionIsEmpty() {
        when(repository.findById(9)).thenReturn(Optional.empty());
        when(repository.findForUpdate(9)).thenReturn(Optional.empty());

        assertThat(service.findSummaryById(9)).isEmpty();
        assertThat(service.findSummaryForUpdate(9)).isEmpty();
    }

    private Competition saved() {
        ArgumentCaptor<Competition> captor = ArgumentCaptor.forClass(Competition.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static CreateCompetitionRequest valid() {
        return request(1, "Simulacro 1", null, null, null, null, null, null, START, END);
    }

    private static CreateCompetitionRequest request(Integer teamId, String name, String description,
                                                    CompetitionAccessType accessType, String accessKey,
                                                    PenaltyRule penaltyRule, Integer freeze, CompetitionStatus status,
                                                    OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return new CreateCompetitionRequest(teamId, name, description, accessType, accessKey, penaltyRule, freeze,
                status, startsAt, endsAt);
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
