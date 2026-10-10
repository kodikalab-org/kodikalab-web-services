package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.util.List;
import java.util.Optional;

public interface GroupMembershipService {
    List<GroupMemberData> findMembersByTeamId(Integer teamId);

    Optional<GroupMembership> findForUpdate(Integer teamId, Integer userId);
}
