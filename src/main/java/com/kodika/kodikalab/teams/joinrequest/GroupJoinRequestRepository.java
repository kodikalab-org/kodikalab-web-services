package com.kodika.kodikalab.teams.joinrequest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupJoinRequestRepository
        extends JpaRepository<GroupJoinRequest, Integer> {

    boolean existsByGroupIdAndPractitionerUserIdAndStatus(
            Integer groupId,
            Integer practitionerId,
            JoinRequestStatus status
    );

    Optional<GroupJoinRequest> findByGroupIdAndPractitionerUserIdAndStatus(
            Integer groupId,
            Integer practitionerId,
            JoinRequestStatus status
    );

    List<GroupJoinRequest> findByGroupIdAndStatus(
            Integer groupId,
            JoinRequestStatus status
    );
}
