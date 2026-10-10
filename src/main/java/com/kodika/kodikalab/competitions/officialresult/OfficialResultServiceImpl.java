package com.kodika.kodikalab.competitions.officialresult;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ConstraintViolations;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competition.CompetitionRepository;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultRequest;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultResponse;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OfficialResultServiceImpl implements OfficialResultService {
    private final OfficialResultRepository repository;
    private final CompetitionRepository competitionRepository;
    private final StudyGroupService groupService;
    private final CurrentUserResolver currentUserResolver;

    public OfficialResultServiceImpl(OfficialResultRepository repository, CompetitionRepository competitionRepository,
                                     StudyGroupService groupService, CurrentUserResolver currentUserResolver) {
        this.repository = repository;
        this.competitionRepository = competitionRepository;
        this.groupService = groupService;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public OfficialResultResponse create(Integer competitionId, OfficialResultRequest request) {
        Competition competition = authorizedCompetition(competitionId);
        if (repository.findByCompetitionId(competitionId).isPresent()) {
            throw duplicate();
        }
        validate(request, competition);
        OfficialResult result = new OfficialResult();
        result.setCompetition(competition);
        result.setRegisteredAt(OffsetDateTime.now(ZoneOffset.UTC));
        apply(result, request);
        try {
            return response(repository.saveAndFlush(result));
        } catch (DataIntegrityViolationException exception) {
            if (ConstraintViolations.isUniqueViolationOf(exception, "uq_resultado_oficial_competencia")) {
                throw duplicate();
            }
            throw exception;
        }
    }

    @Override
    public OfficialResultResponse update(Integer competitionId, OfficialResultRequest request) {
        Competition competition = authorizedCompetition(competitionId);
        OfficialResult result = repository.findForUpdate(competitionId).orElseThrow(this::missing);
        if (result.getStatus() != OfficialResultStatus.PENDIENTE) {
            throw new ConflictException("Solo se puede completar o confirmar un resultado PENDIENTE");
        }
        validate(request, competition);
        apply(result, request);
        return response(repository.saveAndFlush(result));
    }

    @Override
    @Transactional(readOnly = true)
    public OfficialResultResponse get(Integer competitionId) {
        authorizedCompetition(competitionId);
        return response(repository.findByCompetitionId(competitionId).orElseThrow(this::missing));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficialResultResponse> history(Integer teamId) {
        authorizeTeam(teamId, activeCoach());
        return repository.findHistory(teamId, OfficialResultStatus.CONFIRMADO).stream().map(this::response).toList();
    }

    private Competition authorizedCompetition(Integer competitionId) {
        User coach = activeCoach();
        positiveId(competitionId);
        Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new NotFoundException("La competencia no existe"));
        if (competition.getGroup() == null) {
            throw new ConflictException("La competencia no tiene un equipo asociado");
        }
        authorizeTeam(competition.getGroup().getId(), coach);
        return competition;
    }

    private User activeCoach() {
        User coach = currentUserResolver.currentUser();
        if (coach.getStatus() != UserStatus.ACTIVO || coach.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach activo puede gestionar resultados oficiales");
        }
        return coach;
    }

    private void authorizeTeam(Integer teamId, User coach) {
        positiveId(teamId);
        var team = groupService.findSummaryById(teamId)
                .orElseThrow(() -> new NotFoundException("El equipo no existe"));
        if (!teamId.equals(team.teamId()) || team.coachUserId() == null) {
            throw new ConflictException("La información del equipo o su coach responsable está incompleta");
        }
        if (!team.coachUserId().equals(coach.getId())) {
            throw new ForbiddenException("No tiene autorización para gestionar resultados oficiales de este equipo");
        }
    }

    private void positiveId(Integer id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("El identificador debe ser un entero positivo");
        }
    }

    private void validate(OfficialResultRequest request, Competition competition) {
        if (request == null) {
            throw new OfficialResultValidationException(Map.of("body", "Debe enviar los datos del resultado"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.finalPosition() != null && request.finalPosition() <= 0) {
            errors.put("finalPosition", "La posición final debe ser mayor que cero");
        }
        if (request.solvedProblems() != null && request.solvedProblems() < 0) {
            errors.put("solvedProblems", "La cantidad de problemas resueltos no puede ser negativa");
        }
        if (request.confirm()) {
            if (request.finalPosition() == null) {
                errors.put("finalPosition", "La posición final es obligatoria para confirmar");
            }
            if (request.solvedProblems() == null) {
                errors.put("solvedProblems", "Los problemas resueltos son obligatorios para confirmar");
            }
            if (competition.getStatus() != CompetitionStatus.FINALIZADA) {
                errors.put("competition.status", "La competencia debe estar FINALIZADA para confirmar");
            }
        }
        if (!errors.isEmpty()) {
            throw new OfficialResultValidationException(errors);
        }
    }

    private void apply(OfficialResult result, OfficialResultRequest request) {
        result.setFinalPosition(request.finalPosition());
        result.setSolvedProblems(request.solvedProblems());
        result.setStatus(request.confirm() ? OfficialResultStatus.CONFIRMADO : OfficialResultStatus.PENDIENTE);
        result.setConfirmedAt(request.confirm() ? OffsetDateTime.now(ZoneOffset.UTC) : null);
    }

    private OfficialResultResponse response(OfficialResult result) {
        Competition competition = result.getCompetition();
        return new OfficialResultResponse(result.getId(), competition.getId(), competition.getGroup().getId(),
                competition.getEventName(), competition.getEndsAt(), result.getFinalPosition(),
                result.getSolvedProblems(), result.getStatus(), result.getRegisteredAt(), result.getConfirmedAt());
    }

    private ConflictException duplicate() {
        return new ConflictException("Ya existe un resultado para esta competencia; complete el pendiente mediante PUT");
    }

    private NotFoundException missing() {
        return new NotFoundException("La competencia no tiene un resultado registrado");
    }
}
