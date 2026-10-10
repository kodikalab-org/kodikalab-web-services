package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.common.exception.ConstraintViolations;
import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentData;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentView;
import com.kodika.kodikalab.competitions.competitionproblem.dto.NewAssignment;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.problems.problem.Problem;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionProblemServiceImpl implements CompetitionProblemService {
    private final CompetitionProblemRepository competitionProblemRepository;
    private final EntityManager entityManager;

    public CompetitionProblemServiceImpl(CompetitionProblemRepository competitionProblemRepository,
                                         EntityManager entityManager) {
        this.competitionProblemRepository = competitionProblemRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamAssignedProblem> findAssignedProblemsByTeamId(Integer teamId) {
        return competitionProblemRepository.findAssignedProblemsByTeamId(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentData> findByCompetitionId(Integer competitionId) {
        return competitionProblemRepository.findByCompetition_IdOrderByLetterAsc(competitionId).stream()
                .map(CompetitionProblemServiceImpl::data).toList();
    }

    @Override
    @Transactional
    public List<AssignmentData> assign(Integer competitionId, List<NewAssignment> assignments) {
        Competition competition = entityManager.getReference(Competition.class, competitionId);
        OffsetDateTime assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<CompetitionProblem> rows = new ArrayList<>();
        for (NewAssignment assignment : assignments) {
            CompetitionProblem row = new CompetitionProblem();
            row.setCompetition(competition);
            row.setProblem(entityManager.getReference(Problem.class, assignment.problemId()));
            row.setLetter(assignment.letter());
            row.setScore(assignment.score());
            row.setBalloonColor(assignment.balloonColor());
            row.setAssignedAt(assignedAt);
            rows.add(row);
        }
        try {
            return competitionProblemRepository.saveAllAndFlush(rows).stream()
                    .map(CompetitionProblemServiceImpl::data).toList();
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.isUniqueViolationOf(exception, "uq_competencia_problema",
                    "uq_competencia_orden_letra")) {
                throw new FieldConflictException("Otra asignación modificó la competencia al mismo tiempo; "
                        + "no se registró ninguna", Map.of("problems", "Un problema o una letra ya fue asignado"));
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentView> findViewsByTeamId(Integer teamId) {
        return competitionProblemRepository.findWithCompetitionByTeamId(teamId).stream()
                .map(CompetitionProblemServiceImpl::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentView> findViewById(Integer competitionProblemId) {
        return competitionProblemRepository.findDetailById(competitionProblemId)
                .map(CompetitionProblemServiceImpl::view);
    }

    private static AssignmentData data(CompetitionProblem row) {
        return new AssignmentData(row.getId(), row.getCompetition().getId(), row.getProblem().getId(), row.getLetter(),
                row.getScore(), row.getBalloonColor(), row.getAssignedAt());
    }

    private static AssignmentView view(CompetitionProblem row) {
        Competition competition = row.getCompetition();
        Integer teamId = competition.getGroup() == null ? null : competition.getGroup().getId();
        return new AssignmentView(row.getId(), row.getProblem().getId(), row.getLetter(), row.getScore(),
                row.getBalloonColor(), row.getAssignedAt(), competition.getId(), teamId, competition.getEventName(),
                competition.getStatus(), competition.getStartsAt(), competition.getEndsAt(),
                competition.getDurationMinutes(), competition.getPenaltyRule(),
                competition.getScoreboardFreezeMinutes(), competition.getAccessType());
    }
}
