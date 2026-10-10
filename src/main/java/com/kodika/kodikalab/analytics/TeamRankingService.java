package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.RankingMemberData;
import com.kodika.kodikalab.analytics.dto.RankingResolutionData;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamRankingService {
    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService studyGroupService;
    private final GroupMembershipService membershipService;
    private final ProblemResolutionService resolutionService;
    private final RankingService rankingService;

    public TeamRankingService(CurrentUserResolver currentUserResolver, StudyGroupService studyGroupService,
                              GroupMembershipService membershipService, ProblemResolutionService resolutionService,
                              RankingService rankingService) {
        this.currentUserResolver = currentUserResolver;
        this.studyGroupService = studyGroupService;
        this.membershipService = membershipService;
        this.resolutionService = resolutionService;
        this.rankingService = rankingService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TeamRankingResponse getRanking(Integer teamId) {
        List<GroupMemberData> members = loadAuthorizedMembers(teamId);
        List<RankingMemberData> rankingMembers = members.stream().map(member -> member == null ? null
                : new RankingMemberData(member.membershipId(), member.teamId(), member.userId(), member.fullName(),
                        member.status() == null ? null : member.status().name())).toList();
        List<RankingResolutionData> resolutions = rankingResolutions(teamId);
        return rankingService.calculate(teamId, rankingMembers, resolutions);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void authorize(Integer teamId) {
        loadAuthorizedMembers(teamId);
    }

    private List<GroupMemberData> loadAuthorizedMembers(Integer teamId) {
        User requester = currentUserResolver.currentUser();
        if (requester.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        if (teamId == null || teamId <= 0) {
            throw new BadRequestException("El identificador del equipo debe ser un entero positivo");
        }
        StudyGroupSummary team = studyGroupService.findSummaryById(teamId)
                .orElseThrow(() -> new NotFoundException("El equipo no existe"));
        requirePositiveId(team.coachUserId(), "team.coachUserId");
        if (!teamId.equals(team.teamId())) {
            throw invalid("team.teamId", "El equipo recuperado no corresponde a la consulta");
        }
        List<GroupMemberData> members = membershipService.findMembersByTeamId(teamId);
        if (members == null) {
            throw invalid("members", "La información de membresías no está disponible");
        }
        if (!isAuthorized(requester, team, members)) {
            throw new ForbiddenException("No tiene autorización para consultar el ranking de este equipo");
        }
        return members;
    }

    private boolean isAuthorized(User requester, StudyGroupSummary team, List<GroupMemberData> members) {
        if (requester.getId() == null || requester.getId() <= 0) {
            return false;
        }
        if (requester.getRole() == Role.COACH) {
            return requester.getId().equals(team.coachUserId());
        }
        return requester.getRole() == Role.PRACTICANTE && members.stream().anyMatch(member -> member != null
                && team.teamId().equals(member.teamId()) && requester.getId().equals(member.userId())
                && member.status() == MembershipStatus.ACTIVO);
    }

    private List<RankingResolutionData> rankingResolutions(Integer teamId) {
        List<TeamResolutionData> resolutions = resolutionService.findResolutionsByTeamId(teamId);
        if (resolutions == null) {
            throw invalid("resolutions", "La información de resoluciones no está disponible");
        }
        List<RankingResolutionData> result = new ArrayList<>();
        for (int index = 0; index < resolutions.size(); index++) {
            TeamResolutionData resolution = resolutions.get(index);
            String field = "resolutions[" + index + "]";
            if (resolution == null) {
                throw invalid(field, "La resolución está incompleta");
            }
            requirePositiveId(resolution.competitionProblemId(), field + ".competitionProblemId");
            requirePositiveId(resolution.competitionId(), field + ".competitionId");
            if (!teamId.equals(resolution.membershipTeamId()) || !teamId.equals(resolution.competitionTeamId())) {
                throw invalid(field + ".teamId", "La membresía y la competencia deben pertenecer al equipo consultado");
            }
            result.add(new RankingResolutionData(resolution.resolutionId(), resolution.competitionTeamId(),
                    resolution.membershipId(), resolution.problemId(),
                    resolution.verdict() == null ? null : resolution.verdict().name()));
        }
        return result;
    }

    private void requirePositiveId(Integer id, String field) {
        if (id == null || id <= 0) {
            throw invalid(field, "La relación necesaria no tiene un identificador válido");
        }
    }

    private RankingDataException invalid(String field, String message) {
        return new RankingDataException("No se pudo calcular un ranking válido", Map.of(field, message));
    }
}
