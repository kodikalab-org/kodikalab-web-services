package com.kodika.kodikalab.teams.joinrequest;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileRepository;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipRepository;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.TeamRole;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupRepository;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupJoinRequestServiceImpl implements GroupJoinRequestService {

    private final GroupJoinRequestRepository requestRepository;
    private final StudyGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final PractitionerProfileRepository practitionerRepository;
    private final CurrentUserResolver currentUserResolver;

    public GroupJoinRequestServiceImpl(
            GroupJoinRequestRepository requestRepository,
            StudyGroupRepository groupRepository,
            GroupMembershipRepository membershipRepository,
            PractitionerProfileRepository practitionerRepository,
            CurrentUserResolver currentUserResolver) {

        this.requestRepository = requestRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.practitionerRepository = practitionerRepository;
        this.currentUserResolver = currentUserResolver;
    }

    // US05 - Solicitar ingreso
    @Override
    @Transactional
    public GroupJoinRequest requestJoin(Integer groupId) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException(
                    "Solo los practicantes pueden solicitar ingreso"
            );
        }

        PractitionerProfile practitioner = practitionerRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Primero debes completar tu perfil de practicante"
                ));

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo solicitado no existe"
                ));

        if (group.getStatus() != GroupStatus.ACTIVO) {
            throw new BadRequestException(
                    "No se puede solicitar ingreso a un grupo archivado"
            );
        }

        if (membershipRepository
                .findByGroupIdAndPractitionerUserId(groupId, user.getId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVO)
                .isPresent()) {
            throw new ConflictException(
                    "Ya perteneces a este grupo"
            );
        }

        if (requestRepository.existsByGroupIdAndPractitionerUserIdAndStatus(
                groupId, user.getId(), JoinRequestStatus.PENDIENTE)) {
            throw new ConflictException(
                    "Ya tienes una solicitud pendiente para este grupo"
            );
        }

        GroupJoinRequest request = new GroupJoinRequest();
        request.setGroup(group);
        request.setPractitioner(practitioner);
        request.setStatus(JoinRequestStatus.PENDIENTE);
        request.setRequestedAt(OffsetDateTime.now());

        return requestRepository.save(request);
    }

    // US06 - Aceptar o rechazar
    @Override
    @Transactional
    public GroupJoinRequest reviewRequest(
            Integer groupId,
            Integer requestId,
            boolean accept) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException(
                    "Solo los coaches pueden revisar solicitudes"
            );
        }

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo no existe"
                ));

        if (!group.getCoach().getUserId().equals(user.getId())) {
            throw new ForbiddenException(
                    "No eres el coach responsable de este grupo"
            );
        }

        GroupJoinRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(
                        "La solicitud no existe"
                ));

        if (!request.getGroup().getId().equals(groupId)) {
            throw new BadRequestException(
                    "La solicitud no pertenece a este grupo"
            );
        }

        if (request.getStatus() != JoinRequestStatus.PENDIENTE) {
            throw new ConflictException(
                    "Esta solicitud ya fue respondida"
            );
        }

        if (accept) {

            if (group.getStatus() != GroupStatus.ACTIVO) {
                throw new ConflictException(
                        "No se pueden aceptar integrantes en un grupo archivado"
                );
            }

            long activeMembers = membershipRepository
                    .countByGroupIdAndStatus(
                            groupId, MembershipStatus.ACTIVO
                    );

            if (activeMembers >= group.getMaxCapacity()) {
                throw new ConflictException(
                        "El grupo alcanzó su capacidad máxima"
                );
            }

            Integer practitionerId = request.getPractitioner().getUserId();

            GroupMembership membership = membershipRepository
                    .findByGroupIdAndPractitionerUserId(groupId, practitionerId)
                    .orElseGet(GroupMembership::new);

            if (membership.getStatus() == MembershipStatus.ACTIVO) {
                throw new ConflictException(
                        "El practicante ya es integrante del grupo"
                );
            }

            membership.setGroup(group);
            membership.setPractitioner(request.getPractitioner());
            membership.setStatus(MembershipStatus.ACTIVO);
            membership.setTeamRole(TeamRole.MIEMBRO);
            membership.setJoinedAt(OffsetDateTime.now());
            membership.setLeftAt(null);

            membershipRepository.save(membership);
        }

        request.setStatus(
                accept ? JoinRequestStatus.ACEPTADA : JoinRequestStatus.RECHAZADA
        );

        request.setRespondedAt(OffsetDateTime.now());

        return requestRepository.save(request);
    }
}
