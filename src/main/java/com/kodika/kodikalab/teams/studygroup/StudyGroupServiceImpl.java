
package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.coach.CoachProfileRepository;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupServiceImpl implements StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final CurrentUserResolver currentUserResolver;

    public StudyGroupServiceImpl(
            StudyGroupRepository studyGroupRepository,
            CoachProfileRepository coachProfileRepository,
            CurrentUserResolver currentUserResolver) {
        this.studyGroupRepository = studyGroupRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    @Transactional
    public StudyGroup createGroup(CreateStudyGroupRequest request) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach puede crear grupos");
        }

        CoachProfile coach = coachProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Primero debes completar tu perfil de coach"
                ));

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
}
