package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionResponse;
import com.kodika.kodikalab.competitions.competition.dto.CreateCompetitionRequest;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionServiceImpl implements CompetitionService {
    private static final int DEFAULT_FREEZE_MINUTES = 60;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final CompetitionRepository competitionRepository;
    private final StudyGroupService groupService;
    private final CurrentUserResolver currentUserResolver;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public CompetitionServiceImpl(CompetitionRepository competitionRepository, StudyGroupService groupService,
                                  CurrentUserResolver currentUserResolver, PasswordEncoder passwordEncoder,
                                  EntityManager entityManager) {
        this.competitionRepository = competitionRepository;
        this.groupService = groupService;
        this.currentUserResolver = currentUserResolver;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public CompetitionResponse create(CreateCompetitionRequest request) {
        User coach = activeCoach();
        if (request == null) {
            throw new CompetitionValidationException(Map.of("body", "Debe enviar los datos de la competencia"));
        }
        Integer teamId = request.teamId();
        if (teamId == null || teamId <= 0) {
            throw new CompetitionValidationException(
                    Map.of("teamId", "El equipo es obligatorio y debe ser un entero positivo"));
        }
        var team = groupService.findSummaryById(teamId).orElseThrow(() -> new NotFoundException("El equipo no existe"));
        if (!teamId.equals(team.teamId()) || team.coachUserId() == null) {
            throw new ConflictException("La información del equipo o su coach responsable está incompleta");
        }
        if (!team.coachUserId().equals(coach.getId())) {
            throw new ForbiddenException("No tiene autorización para crear competencias en este equipo");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String name = request.eventName() == null ? "" : request.eventName().strip();
        if (name.isEmpty() || name.length() > 150) {
            errors.put("eventName", "El nombre es obligatorio y debe tener como máximo 150 caracteres");
        }
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().strip();
        if (description != null && description.length() > 500) {
            errors.put("description", "La descripción debe tener como máximo 500 caracteres");
        }
        CompetitionAccessType accessType = request.accessType() == null
                ? CompetitionAccessType.PUBLICO_GRUPO : request.accessType();
        boolean hasKey = request.accessKey() != null && !request.accessKey().isBlank();
        if (accessType == CompetitionAccessType.PRIVADO_PASS) {
            if (!hasKey) {
                errors.put("accessKey", "La clave de acceso es obligatoria para una competencia PRIVADO_PASS");
            } else if (request.accessKey().getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
                errors.put("accessKey", "La clave de acceso no puede superar 72 bytes");
            }
        } else if (hasKey) {
            errors.put("accessKey", "La clave de acceso solo se admite en competencias PRIVADO_PASS");
        }

        long durationMinutes = 0;
        if (request.startsAt() == null) {
            errors.put("startsAt", "La fecha de inicio es obligatoria");
        }
        if (request.endsAt() == null) {
            errors.put("endsAt", "La fecha de fin es obligatoria");
        }
        if (request.startsAt() != null && request.endsAt() != null) {
            durationMinutes = Duration.between(request.startsAt(), request.endsAt()).toMinutes();
            if (durationMinutes < 1) {
                errors.put("endsAt", "La fecha de fin debe ser al menos un minuto posterior a la de inicio");
            } else if (durationMinutes > Integer.MAX_VALUE) {
                errors.put("endsAt", "La duración de la competencia excede el máximo permitido");
            }
        }
        boolean durationKnown = errors.get("endsAt") == null && errors.get("startsAt") == null
                && request.startsAt() != null && request.endsAt() != null;
        Integer freeze = request.scoreboardFreezeMinutes();
        if (durationKnown) {
            if (freeze == null) {
                freeze = (int) Math.min(DEFAULT_FREEZE_MINUTES, durationMinutes);
            } else if (freeze < 0 || freeze > durationMinutes) {
                errors.put("scoreboardFreezeMinutes",
                        "Debe estar entre 0 y la duración de la competencia (" + durationMinutes + " minutos)");
            }
        } else if (freeze != null && freeze < 0) {
            errors.put("scoreboardFreezeMinutes", "No puede ser negativo");
        }
        if (!errors.isEmpty()) {
            throw new CompetitionValidationException(errors);
        }
        if (competitionRepository.existsByGroupIdAndEventNameIgnoreCaseAndStartsAt(teamId, name, request.startsAt())) {
            throw new ConflictException("Ya existe una competencia con ese nombre e inicio en el equipo");
        }

        Competition competition = new Competition();
        competition.setGroup(entityManager.getReference(StudyGroup.class, teamId));
        competition.setEventName(name);
        competition.setDescription(description);
        competition.setAccessType(accessType);
        competition.setAccessKey(hasKey ? passwordEncoder.encode(request.accessKey()) : null);
        competition.setPenaltyRule(request.penaltyRule() == null ? PenaltyRule.ICPC_20_MIN : request.penaltyRule());
        competition.setDurationMinutes((int) durationMinutes);
        competition.setScoreboardFreezeMinutes(freeze);
        competition.setStatus(request.status() == null ? CompetitionStatus.PROGRAMADA : request.status());
        competition.setStartsAt(request.startsAt());
        competition.setEndsAt(request.endsAt());
        return response(competitionRepository.save(competition), teamId);
    }

    private User activeCoach() {
        User coach = currentUserResolver.currentUser();
        if (coach.getStatus() != UserStatus.ACTIVO || coach.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach activo puede crear competencias");
        }
        return coach;
    }

    private CompetitionResponse response(Competition saved, Integer teamId) {
        return new CompetitionResponse(saved.getId(), teamId, saved.getEventName(), saved.getDescription(),
                saved.getAccessType(), saved.getPenaltyRule(), saved.getDurationMinutes(),
                saved.getScoreboardFreezeMinutes(), saved.getStatus(), saved.getStartsAt(), saved.getEndsAt());
    }
}
