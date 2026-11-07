package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.CompetitionAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import java.util.List;

public interface ProblemResolutionService {
    List<TeamResolutionData> findResolutionsByTeamId(Integer teamId);

    TeamResolutionData registerManualAccepted(Integer teamId, Integer competitionProblemId,
                                              ManualResolutionRequest request);

    /** Intentos de una membresía sobre cualquier problema asignado, el más reciente primero. */
    List<MemberAttempt> findAttemptsByMembershipId(Integer membershipId);

    /**
     * Intentos sobre los problemas de una competencia hechos por membresías de su equipo, del más antiguo al más
     * reciente. Las resoluciones de membresías de otros equipos nunca se incluyen.
     */
    List<CompetitionAttempt> findAttemptsByCompetition(Integer competitionId, Integer teamId);
}
