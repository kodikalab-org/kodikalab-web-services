package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamProgressResponse;
import com.kodika.kodikalab.analytics.dto.TeamProgressResponse.Registration;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.Status;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndependentProgressService {
    private final ProblemResolutionService resolutionService;
    private final TeamRankingService rankingService;
    private final GroupMembershipService membershipService;
    private final CurrentUserResolver currentUserResolver;

    public IndependentProgressService(ProblemResolutionService resolutionService, TeamRankingService rankingService,
                                      GroupMembershipService membershipService, CurrentUserResolver currentUserResolver) {
        this.resolutionService = resolutionService;
        this.rankingService = rankingService;
        this.membershipService = membershipService;
        this.currentUserResolver = currentUserResolver;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Registration register(Integer teamId, Integer competitionProblemId, ManualResolutionRequest request) {
        Integer userId = activePractitionerId();
        var resolution = resolutionService.registerManualAccepted(teamId, competitionProblemId, request);
        var progress = calculate(teamId, userId);
        if (!resolution.membershipId().equals(progress.membershipId())) {
            throw new RankingDataException("El avance no corresponde a la resolución registrada",
                    Map.of("membershipId", "La resolución y el avance deben pertenecer a la misma membresía"));
        }
        return new Registration(resolution, "MANUAL_PROVISIONAL", progress);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TeamProgressResponse getProgress(Integer teamId) {
        return calculate(teamId, activePractitionerId());
    }

    private TeamProgressResponse calculate(Integer teamId, Integer userId) {
        var ranking = rankingService.getRanking(teamId);
        var member = ranking.members().stream().filter(m -> userId.equals(m.userId())).findFirst();
        if (member.isPresent()) {
            var own = member.get();
            return new TeamProgressResponse(teamId, own.membershipId(), userId, own.acceptedProblems());
        }
        if (ranking.status() == Status.NO_ACTIVITY) {
            var own = membershipService.findMembersByTeamId(teamId).stream()
                    .filter(m -> userId.equals(m.userId()) && teamId.equals(m.teamId()) && m.status() == MembershipStatus.ACTIVO)
                    .findFirst().orElseThrow(this::invalidMembership);
            return new TeamProgressResponse(teamId, own.membershipId(), userId, 0);
        }
        throw invalidMembership();
    }

    private Integer activePractitionerId() {
        var user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO || user.getRole() != Role.PRACTICANTE
                || user.getId() == null || user.getId() <= 0) {
            throw new ForbiddenException("Solo un practicante activo puede consultar o registrar su avance");
        }
        return user.getId();
    }

    private ForbiddenException invalidMembership() {
        return new ForbiddenException("Seleccione un contexto de equipo válido: necesita una membresía activa propia");
    }
}
