package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.RankingMemberData;
import com.kodika.kodikalab.analytics.dto.RankingResolutionData;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.MemberStanding;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RankingService {
    private static final String ORDERING_CRITERION = "DISTINCT_ACCEPTED_PROBLEMS_DESC";
    private static final String TIE_CRITERION = "SHARED_POSITION_1_1_3";
    private static final Set<String> MEMBERSHIP_STATUSES = Set.of("ACTIVO", "RETIRADO", "EXPULSADO");
    private static final Set<String> VERDICTS = Set.of(
            "PENDIENTE", "ACCEPTED", "WRONG_ANSWER", "TLE", "MLE", "ERROR");

    public TeamRankingResponse calculate(Integer teamId, List<RankingMemberData> members,
                                         List<RankingResolutionData> resolutions) {
        requirePositiveId(teamId, "teamId");
        if (members == null || resolutions == null) {
            throw invalid(members == null ? "members" : "resolutions", "La información no está disponible");
        }
        Map<Integer, RankingMemberData> memberships = validateMembers(teamId, members);
        validateResolutions(teamId, memberships, resolutions);
        if (resolutions.isEmpty()) {
            return response(teamId, Status.NO_ACTIVITY, List.of());
        }

        Map<Integer, Set<Integer>> acceptedProblems = new HashMap<>();
        for (RankingResolutionData resolution : resolutions) {
            if ("ACCEPTED".equals(resolution.verdict())) {
                acceptedProblems.computeIfAbsent(resolution.membershipId(), id -> new HashSet<>())
                        .add(resolution.problemId());
            }
        }
        List<RankingMemberData> orderedMembers = members.stream()
                .filter(member -> "ACTIVO".equals(member.status()))
                .sorted(Comparator.comparingInt((RankingMemberData member) ->
                                score(member, acceptedProblems)).reversed()
                        .thenComparing(RankingMemberData::membershipId))
                .toList();
        List<MemberStanding> standings = new ArrayList<>();
        int position = 0;
        int previousScore = -1;
        for (RankingMemberData member : orderedMembers) {
            int score = score(member, acceptedProblems);
            if (score != previousScore) {
                position = standings.size() + 1;
            }
            standings.add(new MemberStanding(member.membershipId(), member.userId(), member.fullName(), score, position));
            previousScore = score;
        }
        return response(teamId, Status.CALCULATED, standings);
    }

    private Map<Integer, RankingMemberData> validateMembers(Integer teamId, List<RankingMemberData> members) {
        Map<Integer, RankingMemberData> memberships = new HashMap<>();
        Set<Integer> users = new HashSet<>();
        for (int index = 0; index < members.size(); index++) {
            RankingMemberData member = members.get(index);
            String field = "members[" + index + "]";
            if (member == null) {
                throw invalid(field, "La membresía está incompleta");
            }
            requirePositiveId(member.membershipId(), field + ".membershipId");
            requirePositiveId(member.userId(), field + ".userId");
            if (!teamId.equals(member.teamId())) {
                throw invalid(field + ".teamId", "La membresía no pertenece al equipo consultado");
            }
            if (member.fullName() == null || member.fullName().isBlank()) {
                throw invalid(field + ".fullName", "El nombre del integrante no está disponible");
            }
            if (member.status() == null || !MEMBERSHIP_STATUSES.contains(member.status())) {
                throw invalid(field + ".status", "El estado de membresía no es válido");
            }
            if (memberships.putIfAbsent(member.membershipId(), member) != null || !users.add(member.userId())) {
                throw invalid(field, "La membresía o el integrante está duplicado");
            }
        }
        return memberships;
    }

    private void validateResolutions(Integer teamId, Map<Integer, RankingMemberData> memberships,
                                     List<RankingResolutionData> resolutions) {
        Map<Integer, RankingResolutionData> seen = new HashMap<>();
        for (int index = 0; index < resolutions.size(); index++) {
            RankingResolutionData resolution = resolutions.get(index);
            String field = "resolutions[" + index + "]";
            if (resolution == null) {
                throw invalid(field, "La resolución está incompleta");
            }
            requirePositiveId(resolution.resolutionId(), field + ".resolutionId");
            requirePositiveId(resolution.membershipId(), field + ".membershipId");
            requirePositiveId(resolution.problemId(), field + ".problemId");
            if (!teamId.equals(resolution.teamId())) {
                throw invalid(field + ".teamId", "La resolución no pertenece al equipo consultado");
            }
            if (!memberships.containsKey(resolution.membershipId())) {
                throw invalid(field + ".membershipId", "La membresía de la resolución no está disponible");
            }
            if (resolution.verdict() == null || !VERDICTS.contains(resolution.verdict())) {
                throw invalid(field + ".verdict", "El veredicto de la resolución no es válido");
            }
            RankingResolutionData previous = seen.putIfAbsent(resolution.resolutionId(), resolution);
            if (previous != null && !previous.equals(resolution)) {
                throw invalid(field + ".resolutionId", "Existen datos contradictorios para la misma resolución");
            }
        }
    }

    private int score(RankingMemberData member, Map<Integer, Set<Integer>> acceptedProblems) {
        return acceptedProblems.getOrDefault(member.membershipId(), Set.of()).size();
    }

    private TeamRankingResponse response(Integer teamId, Status status, List<MemberStanding> standings) {
        return new TeamRankingResponse(teamId, status, ORDERING_CRITERION, TIE_CRITERION, standings);
    }

    private void requirePositiveId(Integer id, String field) {
        if (id == null || id <= 0) {
            throw invalid(field, "El identificador debe ser un entero positivo");
        }
    }

    private RankingDataException invalid(String field, String message) {
        return new RankingDataException("No se pudo calcular un ranking válido", Map.of(field, message));
    }
}
