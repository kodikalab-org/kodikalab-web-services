package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.analytics.dto.LastValidRanking;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Service
public class StoredTeamRankingService {
    private static final Logger log = LoggerFactory.getLogger(StoredTeamRankingService.class);

    private final TeamRankingService rankingService;
    private final RankingSnapshotService snapshotService;

    public StoredTeamRankingService(TeamRankingService rankingService, RankingSnapshotService snapshotService) {
        this.rankingService = rankingService;
        this.snapshotService = snapshotService;
    }

    public TeamRankingResponse getRanking(Integer teamId) {
        OffsetDateTime calculatedAt = OffsetDateTime.now();
        TeamRankingResponse ranking;
        try {
            ranking = rankingService.getRanking(teamId);
        } catch (RankingDataException | DataAccessException | TransactionException exception) {
            var previous = recover(teamId);
            if (previous.isPresent()) {
                throw new RankingRecoveryException(exception, previous.get());
            }
            throw exception;
        }
        keep(ranking, calculatedAt);
        return ranking;
    }

    /** El respaldo es secundario: si no se puede guardar, el ranking ya calculado igualmente se entrega. */
    private void keep(TeamRankingResponse ranking, OffsetDateTime calculatedAt) {
        try {
            snapshotService.save(ranking, calculatedAt);
        } catch (RuntimeException exception) {
            log.warn("No se pudo conservar el último ranking válido del equipo {}", ranking.teamId(), exception);
        }
    }

    private Optional<LastValidRanking> recover(Integer teamId) {
        try {
            rankingService.authorize(teamId);
            return snapshotService.findByTeamId(teamId);
        } catch (UnauthorizedException | ForbiddenException | NotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
