package com.kodika.kodikalab.teams.groupmembership;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMembershipRepository
        extends JpaRepository<GroupMembership, Integer> {

    Optional<GroupMembership> findByGroupIdAndPractitionerUserId(
            Integer groupId,
            Integer practitionerId
    );

    long countByGroupIdAndStatus(
            Integer groupId,
            MembershipStatus status
    );

    List<GroupMembership> findByGroupIdAndStatus(
            Integer groupId,
            MembershipStatus status
    );
}