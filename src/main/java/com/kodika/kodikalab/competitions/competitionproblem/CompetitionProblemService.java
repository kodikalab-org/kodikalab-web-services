package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentData;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentView;
import com.kodika.kodikalab.competitions.competitionproblem.dto.NewAssignment;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;
import java.util.Optional;

public interface CompetitionProblemService {
    List<TeamAssignedProblem> findAssignedProblemsByTeamId(Integer teamId);

    /** Problemas ya asignados a la competencia, ordenados por letra. */
    List<AssignmentData> findByCompetitionId(Integer competitionId);

    /**
     * Registra todas las asignaciones o ninguna. Recibe datos ya autorizados y validados (competencia existente,
     * problemas del catálogo, letras libres); la unicidad de problema y de letra por competencia la protege además
     * la base de datos.
     */
    List<AssignmentData> assign(Integer competitionId, List<NewAssignment> assignments);

    /** Asignaciones del equipo con las condiciones de su competencia, para la vista de problemas asignados. */
    List<AssignmentView> findViewsByTeamId(Integer teamId);

    Optional<AssignmentView> findViewById(Integer competitionProblemId);
}
