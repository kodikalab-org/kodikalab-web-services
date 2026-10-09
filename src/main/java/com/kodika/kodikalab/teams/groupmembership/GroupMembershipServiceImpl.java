package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMembershipServiceImpl implements GroupMembershipService {
    private final GroupMembershipRepository groupMembershipRepository;

    public GroupMembershipServiceImpl(GroupMembershipRepository groupMembershipRepository) {
        this.groupMembershipRepository = groupMembershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberData> findMembersByTeamId(Integer teamId) {
        return groupMembershipRepository.findMembersByTeamId(teamId);
    }
}
