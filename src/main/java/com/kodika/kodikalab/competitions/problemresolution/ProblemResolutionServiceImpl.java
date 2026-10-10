package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblem;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemRepository;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.UserStatus;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemResolutionServiceImpl implements ProblemResolutionService {
    private final ProblemResolutionRepository problemResolutionRepository;
    private final CompetitionProblemRepository assignmentRepository;
    private final GroupMembershipService membershipService;
    private final StudyGroupService groupService;
    private final CurrentUserResolver currentUserResolver;

    public ProblemResolutionServiceImpl(ProblemResolutionRepository problemResolutionRepository,
                                        CompetitionProblemRepository assignmentRepository,
                                        GroupMembershipService membershipService, StudyGroupService groupService,
                                        CurrentUserResolver currentUserResolver) {
        this.problemResolutionRepository = problemResolutionRepository;
        this.assignmentRepository = assignmentRepository;
        this.membershipService = membershipService;
        this.groupService = groupService;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamResolutionData> findResolutionsByTeamId(Integer teamId) {
        return problemResolutionRepository.findResolutionsByTeamId(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberAttempt> findAttemptsByMembershipId(Integer membershipId) {
        return problemResolutionRepository.findAttemptsByMembershipId(membershipId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TeamResolutionData registerManualAccepted(Integer teamId, Integer competitionProblemId,
                                                     ManualResolutionRequest request) {
        var user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO || user.getRole() != Role.PRACTICANTE
                || user.getId() == null || user.getId() <= 0) {
            throw new ForbiddenException("Solo un practicante activo puede registrar su resolución");
        }
        if (teamId == null || teamId <= 0) {
            throw new BadRequestException("Seleccione un contexto de equipo válido");
        }
        if (competitionProblemId == null || competitionProblemId <= 0) {
            throw new ResolutionValidationException(Map.of("competitionProblemId", "Seleccione un problema asignado válido"));
        }
        var team = groupService.findSummaryById(teamId)
                .orElseThrow(() -> new NotFoundException("Seleccione un contexto de equipo válido: el equipo no existe"));
        if (!teamId.equals(team.teamId())) {
            throw new ConflictException("La información del equipo no corresponde al contexto seleccionado");
        }
        GroupMembership membership = membershipService.findForUpdate(teamId, user.getId())
                .orElseThrow(this::invalidMembership);
        if (membership.getStatus() != MembershipStatus.ACTIVO || membership.getId() == null || membership.getId() <= 0
                || membership.getGroup() == null || !teamId.equals(membership.getGroup().getId())
                || membership.getPractitioner() == null
                || !user.getId().equals(membership.getPractitioner().getUserId())) {
            throw invalidMembership();
        }
        CompetitionProblem assignment = assignmentRepository.findById(competitionProblemId)
                .orElseThrow(() -> new NotFoundException("El problema no está asignado a una competencia existente"));
        validateAssignment(teamId, competitionProblemId, assignment);
        validateRequest(request);
        if (assignment.getCompetition().getStatus() == CompetitionStatus.PROGRAMADA) {
            throw new ConflictException(
                    "La competencia aún no ha comenzado: el coach debe iniciarla antes de registrar resoluciones");
        }
        if (problemResolutionRepository.existsByMembershipIdAndCompetitionProblemIdAndVerdict(
                membership.getId(), competitionProblemId, Verdict.ACCEPTED)) {
            throw new ConflictException("Ya existe una resolución ACCEPTED para esta membresía y problema de competencia");
        }
        ProblemResolution resolution = new ProblemResolution();
        resolution.setMembership(membership);
        resolution.setCompetitionProblem(assignment);
        resolution.setVerdict(Verdict.ACCEPTED);
        resolution.setLanguage(request.language().strip());
        resolution.setEvidenceUrl(request.evidenceUrl() == null || request.evidenceUrl().isBlank()
                ? null : request.evidenceUrl().strip());
        resolution.setSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ProblemResolution saved = problemResolutionRepository.saveAndFlush(resolution);
        return new TeamResolutionData(saved.getId(), membership.getId(), teamId, assignment.getId(),
                assignment.getCompetition().getId(), teamId, assignment.getProblem().getId(), saved.getVerdict());
    }

    private void validateAssignment(Integer teamId, Integer assignmentId, CompetitionProblem assignment) {
        if (!assignmentId.equals(assignment.getId()) || assignment.getCompetition() == null
                || assignment.getCompetition().getId() == null || assignment.getCompetition().getId() <= 0
                || assignment.getCompetition().getGroup() == null || assignment.getCompetition().getStatus() == null
                || assignment.getProblem() == null || assignment.getProblem().getId() == null
                || assignment.getProblem().getId() <= 0) {
            throw new ConflictException("La asignación, competencia o problema está incompleto");
        }
        if (!teamId.equals(assignment.getCompetition().getGroup().getId())) {
            throw new ResolutionValidationException(Map.of("teamId",
                    "Seleccione un contexto de equipo válido: el problema pertenece a otro equipo"));
        }
    }

    private void validateRequest(ManualResolutionRequest request) {
        if (request == null) {
            throw new ResolutionValidationException(Map.of("body", "Debe enviar los datos de la resolución"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.language() == null || request.language().isBlank() || request.language().strip().length() > 30) {
            errors.put("language", "El lenguaje es obligatorio y debe tener como máximo 30 caracteres");
        }
        String evidence = request.evidenceUrl();
        if (evidence != null && !evidence.isBlank()) {
            if (evidence.strip().length() > 500 || !validEvidenceUrl(evidence.strip())) {
                errors.put("evidenceUrl", "La evidencia debe ser una URL HTTP/HTTPS válida de hasta 500 caracteres");
            }
        }
        if (!errors.isEmpty()) {
            throw new ResolutionValidationException(errors);
        }
    }

    private boolean validEvidenceUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ForbiddenException invalidMembership() {
        return new ForbiddenException("Seleccione un contexto de equipo válido: necesita una membresía activa propia");
    }
}
