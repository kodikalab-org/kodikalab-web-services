package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.LastValidRanking;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoredTeamRankingServiceTests {
    TeamRankingService calculator = mock(TeamRankingService.class);
    RankingSnapshotService snapshots = mock(RankingSnapshotService.class);
    StoredTeamRankingService service = new StoredTeamRankingService(calculator, snapshots);
    TeamRankingResponse valid = new TeamRankingResponse(1, TeamRankingResponse.Status.CALCULATED,
            "DISTINCT_ACCEPTED_PROBLEMS_DESC", "SHARED_POSITION_1_1_3", List.of());
    LastValidRanking previous = new LastValidRanking(OffsetDateTime.parse("2026-10-09T12:00:00Z"), valid);

    @Test
    void savesOnlyAfterCalculationCompletes() {
        when(calculator.getRanking(1)).thenReturn(valid);

        assertThat(service.getRanking(1)).isSameAs(valid);

        var order = inOrder(calculator, snapshots);
        order.verify(calculator).getRanking(1);
        order.verify(snapshots).save(eq(valid), any(OffsetDateTime.class));
        verify(snapshots, never()).findByTeamId(any());
    }

    @Test
    void validNoActivityReplacesPreviousResult() {
        var empty = new TeamRankingResponse(1, TeamRankingResponse.Status.NO_ACTIVITY, "order", "tie", List.of());
        when(calculator.getRanking(1)).thenReturn(empty);

        assertThat(service.getRanking(1)).isSameAs(empty);
        verify(snapshots).save(eq(empty), any());
    }

    static Stream<RuntimeException> failures() {
        return Stream.of(new RankingDataException("Datos inconsistentes", Map.of("resolutions", "Incompletas")),
                new DataAccessResourceFailureException("Internal SQL"),
                new CannotCreateTransactionException("Database unavailable"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void failurePreservesSnapshotAndRechecksPermissionBeforeReadingIt(RuntimeException failure) {
        when(calculator.getRanking(1)).thenThrow(failure);
        when(snapshots.findByTeamId(1)).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOfSatisfying(RankingRecoveryException.class,
                exception -> {
                    assertThat(exception.getCause()).isSameAs(failure);
                    assertThat(exception.getLastValidRanking()).isSameAs(previous);
                });
        var order = inOrder(calculator, snapshots);
        order.verify(calculator).getRanking(1);
        order.verify(calculator).authorize(1);
        order.verify(snapshots).findByTeamId(1);
        verify(snapshots, never()).save(any(), any());
    }

    @Test
    void noPreviousResultKeepsOriginalError() {
        var failure = new RankingDataException("Datos inconsistentes", Map.of());
        when(calculator.getRanking(1)).thenThrow(failure);
        when(snapshots.findByTeamId(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRanking(1)).isSameAs(failure);
        verify(snapshots, never()).save(any(), any());
    }

    static Stream<RuntimeException> denied() {
        return Stream.of(new UnauthorizedException("Sin sesión"), new ForbiddenException("Acceso denegado"),
                new NotFoundException("El equipo no existe"));
    }

    @ParameterizedTest
    @MethodSource("denied")
    void revokedPermissionOrDeletedTeamNeverExposesSnapshot(RuntimeException denial) {
        when(calculator.getRanking(1)).thenThrow(new RankingDataException("Datos", Map.of()));
        doThrow(denial).when(calculator).authorize(1);

        assertThatThrownBy(() -> service.getRanking(1)).isSameAs(denial);
        verifyNoInteractions(snapshots);
    }

    @Test
    void unavailableAuthorizationNeverReadsSnapshot() {
        var failure = new DataAccessResourceFailureException("Database unavailable");
        when(calculator.getRanking(1)).thenThrow(failure);
        doThrow(new DataAccessResourceFailureException("Still unavailable")).when(calculator).authorize(1);

        assertThatThrownBy(() -> service.getRanking(1)).isSameAs(failure);
        verifyNoInteractions(snapshots);
    }

    @Test
    void failedSnapshotReadKeepsOriginalError() {
        var failure = new RankingDataException("Datos", Map.of());
        when(calculator.getRanking(1)).thenThrow(failure);
        when(snapshots.findByTeamId(1)).thenThrow(new DataAccessResourceFailureException("Snapshot unavailable"));

        assertThatThrownBy(() -> service.getRanking(1)).isSameAs(failure);
    }

    @ParameterizedTest
    @MethodSource("snapshotWriteFailures")
    void failedSnapshotWriteStillReturnsTheCalculatedRanking(RuntimeException failure) {
        when(calculator.getRanking(1)).thenReturn(valid);
        doThrow(failure).when(snapshots).save(eq(valid), any());

        assertThat(service.getRanking(1)).isSameAs(valid);
        verify(calculator, never()).authorize(any());
        verify(snapshots, never()).findByTeamId(any());
    }

    static Stream<RuntimeException> snapshotWriteFailures() {
        return Stream.of(new DataAccessResourceFailureException("Write failed"),
                new CannotCreateTransactionException("Database unavailable"),
                new IllegalStateException("No se pudo conservar el ranking válido"));
    }

    @Test
    void unauthorizedRequestDoesNotAccessSnapshotStorage() {
        when(calculator.getRanking(1)).thenThrow(new ForbiddenException("No autorizado"));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(snapshots);
    }
}
