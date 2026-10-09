package com.kodika.kodikalab.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.analytics.dto.LastValidRanking;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingSnapshotService {
    private final TeamRankingSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public RankingSnapshotService(TeamRankingSnapshotRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(TeamRankingResponse ranking, OffsetDateTime calculatedAt) {
        try {
            repository.replaceIfNewer(ranking.teamId(), calculatedAt, objectMapper.writeValueAsString(ranking));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo conservar el ranking válido", exception);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<LastValidRanking> findByTeamId(Integer teamId) {
        return repository.findById(teamId).map(snapshot -> {
            try {
                TeamRankingResponse ranking = objectMapper.readValue(snapshot.getResult(), TeamRankingResponse.class);
                if (!teamId.equals(ranking.teamId()) || ranking.status() == null || snapshot.getCalculatedAt() == null) {
                    throw new IllegalStateException("El ranking conservado es inconsistente");
                }
                return new LastValidRanking(snapshot.getCalculatedAt(), ranking);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("No se pudo recuperar el ranking válido", exception);
            }
        });
    }
}
