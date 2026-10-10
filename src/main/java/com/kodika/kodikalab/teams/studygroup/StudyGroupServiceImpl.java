
package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.coach.CoachProfileService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupServiceImpl implements StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final CoachProfileService coachProfileService;
    private final CurrentUserResolver currentUserResolver;

    public StudyGroupServiceImpl(
            StudyGroupRepository studyGroupRepository,
            CoachProfileService coachProfileService,
            CurrentUserResolver currentUserResolver) {
        this.studyGroupRepository = studyGroupRepository;
        this.coachProfileService = coachProfileService;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    @Transactional
    public StudyGroup createGroup(CreateStudyGroupRequest request) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach puede crear grupos");
        }

        CoachProfile coach = coachProfileService.requireCoachProfile(user.getId());

        if (request.visibility() == GroupVisibility.ARCHIVADO) {
            throw new BadRequestException(
                    "No se puede crear un grupo con visibilidad ARCHIVADO"
            );
        }

        StudyGroup group = new StudyGroup();

        group.setCoach(coach);
        group.setName(request.name().trim());
        group.setDescription(request.description());
        group.setExpectedLevel(request.expectedLevel().trim());
        group.setMaxCapacity(request.maxCapacity());
        group.setSessionSchedule(request.sessionSchedule());

        group.setVisibility(
                request.visibility() != null
                        ? request.visibility()
                        : GroupVisibility.PUBLICO
        );

        group.setStatus(GroupStatus.ACTIVO);
        group.setCreatedAt(OffsetDateTime.now());

        String invitationCode = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        group.setInvitationCode(invitationCode);

        return studyGroupRepository.save(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyGroup> getAvailableGroups() {
        return studyGroupRepository.findByStatusOrderByIdAsc(GroupStatus.ACTIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyGroupSummary> findSummaryById(Integer teamId) {
        return studyGroupRepository.findSummaryById(teamId);
    }
}
