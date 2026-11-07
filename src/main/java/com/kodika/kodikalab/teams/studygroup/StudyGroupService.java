package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.List;
import java.util.Optional;

public interface StudyGroupService {

    /** US04: crea un grupo publicado o, con {@code draft = true}, un borrador incompleto. */
    StudyGroup createGroup(CreateStudyGroupRequest request);

    /** US04: guarda cambios de un grupo en borrador. Solo el coach responsable puede editarlo. */
    StudyGroup updateDraft(Integer groupId, UpdateStudyGroupDraftRequest request);

    /** US04: valida los datos mínimos y publica un borrador para que aparezca en el listado. */
    StudyGroup publishGroup(Integer groupId);

    List<StudyGroup> getAvailableGroups();

    /** Equipos del usuario autenticado: los grupos del coach o las membresías del practicante. */
    List<MyTeamResponse> findMyTeams();

    /**
     * Exige que el usuario autenticado (activo) pueda consultar el equipo: su coach responsable o un integrante con
     * membresía ACTIVO. Lanza BadRequest, NotFound o Forbidden según corresponda.
     */
    void requireAccessibleTeam(Integer teamId);

    Optional<StudyGroupSummary> findSummaryById(Integer teamId);
}
