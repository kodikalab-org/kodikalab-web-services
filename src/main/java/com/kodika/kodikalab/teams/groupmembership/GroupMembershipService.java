package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.util.List;

public interface GroupMembershipService {
    List<GroupMemberData> findMembersByTeamId(Integer teamId);
}
