package com.kodika.kodikalab.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingSnapshotServiceTests {
    TeamRankingSnapshotRepository repository = mock(TeamRankingSnapshotRepository.class);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    RankingSnapshotService service = new RankingSnapshotService(repository, mapper);

    @Test
    void serializesAndRecoversCompleteRankingWithTimestamp() {
        var ranking = new TeamRankingResponse(1, TeamRankingResponse.Status.CALCULATED,
                "DISTINCT_ACCEPTED_PROBLEMS_DESC", "SHARED_POSITION_1_1_3", List.of(
                new TeamRankingResponse.MemberStanding(2, 3, "Usuario Prueba", 4, 1)));
        var time = OffsetDateTime.parse("2026-10-09T12:00:00Z");
        service.save(ranking, time);
        var json = ArgumentCaptor.forClass(String.class);
        verify(repository).replaceIfNewer(eq(1), eq(time), json.capture());
        var snapshot = new TeamRankingSnapshot();
        snapshot.setTeamId(1);
        snapshot.setCalculatedAt(time);
        snapshot.setResult(json.getValue());
        when(repository.findById(1)).thenReturn(Optional.of(snapshot));

        var recovered = service.findByTeamId(1).orElseThrow();
        assertThat(recovered.calculatedAt()).isEqualTo(time);
        assertThat(recovered.ranking()).isEqualTo(ranking);
        assertThatThrownBy(() -> recovered.ranking().members().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void teamWithoutStoredResultHasNoFallback() {
        when(repository.findById(2)).thenReturn(Optional.empty());
        assertThat(service.findByTeamId(2)).isEmpty();
    }

    @Test
    void corruptedStoredResultIsRejected() {
        var snapshot = new TeamRankingSnapshot();
        snapshot.setResult("invalid JSON");
        when(repository.findById(1)).thenReturn(Optional.of(snapshot));
        assertThatThrownBy(() -> service.findByTeamId(1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storedResultFromAnotherTeamIsRejected() throws Exception {
        var snapshot = new TeamRankingSnapshot();
        snapshot.setCalculatedAt(OffsetDateTime.now());
        snapshot.setResult(mapper.writeValueAsString(new TeamRankingResponse(2,
                TeamRankingResponse.Status.NO_ACTIVITY, "order", "tie", List.of())));
        when(repository.findById(1)).thenReturn(Optional.of(snapshot));
        assertThatThrownBy(() -> service.findByTeamId(1)).isInstanceOf(IllegalStateException.class);
    }
}
