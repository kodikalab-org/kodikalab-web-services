package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.CompetitionAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformImportResult;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformSubmissionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.SubmittedFile;
import java.util.List;

public interface ProblemResolutionService {
    List<TeamResolutionData> findResolutionsByTeamId(Integer teamId);

    TeamResolutionData registerManualAccepted(Integer teamId, Integer competitionProblemId,
                                              ManualResolutionRequest request);

    /**
     * Registra, de forma idempotente, los envíos leídos de la plataforma para el practicante autenticado: los ya
     * importados (mismo id externo) no se duplican, salvo que estuvieran {@code EN_EVALUACION} y ahora tengan veredicto.
     * Si {@code file} no es nulo se guarda en el envío {@code fileSubmissionId} (con {@code NO_COMPARADO}).
     */
    PlatformImportResult importPlatformSubmissions(Integer teamId, Integer competitionProblemId,
                                                   List<PlatformSubmissionData> submissions, SubmittedFile file,
                                                   Long fileSubmissionId);

    /** Intentos de una membresía sobre cualquier problema asignado, el más reciente primero. */
    List<MemberAttempt> findAttemptsByMembershipId(Integer membershipId);

    /**
     * Intentos sobre los problemas de una competencia hechos por membresías de su equipo, del más antiguo al más
     * reciente. Las resoluciones de membresías de otros equipos nunca se incluyen.
     */
    List<CompetitionAttempt> findAttemptsByCompetition(Integer competitionId, Integer teamId);
}
