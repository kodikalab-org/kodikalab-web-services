package com.kodika.kodikalab.assignments.dto;

import com.kodika.kodikalab.assignments.AssignmentStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.problemresolution.FileComparison;
import com.kodika.kodikalab.competitions.problemresolution.ResolutionOrigin;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Un problema asignado con su información, sus condiciones de realización y, para un practicante, su estado.
 * Para el coach responsable {@code status}, {@code attemptCount} y {@code lastAttempt} son {@code null}: ve la
 * asignación del equipo, no un avance personal.
 */
public record AssignedProblemResponse(Integer competitionProblemId, String letter, Integer score,
                                      String balloonColor, OffsetDateTime assignedAt, CompetitionInfo competition,
                                      ProblemInfo problem, AssignmentStatus status, Integer attemptCount,
                                      AttemptInfo lastAttempt) {
    public record CompetitionInfo(Integer id, String name, CompetitionStatus status, OffsetDateTime startsAt,
                                  OffsetDateTime endsAt, Integer durationMinutes, PenaltyRule penaltyRule,
                                  Integer scoreboardFreezeMinutes, CompetitionAccessType accessType) {
    }

    public record ProblemInfo(Integer id, String title, String url, SourcePlatform sourcePlatform, String sourceCode,
                              String difficultyRating, Integer timeLimitMs, Integer memoryLimitMb,
                              List<String> topics) {
        public ProblemInfo {
            topics = List.copyOf(topics);
        }
    }

    /**
     * Intento propio. {@code origin} es {@code MANUAL} para filas anteriores a la verificación con la plataforma.
     * {@code externalSubmissionId} y {@code passedTests} solo existen en intentos {@code PLATAFORMA};
     * {@code fileName}/{@code fileComparison} solo si el practicante adjuntó su código.
     */
    public record AttemptInfo(Integer resolutionId, Verdict verdict, String language, OffsetDateTime submittedAt,
                              String evidenceUrl, Integer executionTimeMs, Integer memoryUsedKb, Integer passedTests,
                              ResolutionOrigin origin, Long externalSubmissionId, String fileName,
                              FileComparison fileComparison) {
        public AttemptInfo(Integer resolutionId, Verdict verdict, String language, OffsetDateTime submittedAt,
                           String evidenceUrl) {
            this(resolutionId, verdict, language, submittedAt, evidenceUrl, null, null, null, ResolutionOrigin.MANUAL,
                    null, null, null);
        }
    }
}
