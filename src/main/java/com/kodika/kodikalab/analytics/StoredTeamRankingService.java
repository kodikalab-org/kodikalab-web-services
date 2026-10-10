package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.analytics.dto.LastValidRanking;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Service
public class StoredTeamRankingService {
    private final TeamRankingService rankingService;
    private final RankingSnapshotService snapshotService;

    public StoredTeamRankingService(TeamRankingService rankingService, RankingSnapshotService snapshotService) {
        this.rankingService = rankingService;
        this.snapshotService = snapshotService;
    }

    public TeamRankingResponse getRanking(Integer teamId) {
        OffsetDateTime calculatedAt = OffsetDateTime.now();
        try {
            TeamRankingResponse ranking = rankingService.getRanking(teamId);
            snapshotService.save(ranking, calculatedAt);
            return ranking;
        } catch (RankingDataException | DataAccessException | TransactionException exception) {
            var previous = recover(teamId);
            if (previous.isPresent()) {
                throw new RankingRecoveryException(exception, previous.get());
            }
            throw exception;
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
