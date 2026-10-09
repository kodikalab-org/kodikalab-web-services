package com.kodika.kodikalab.teams.groupmembership;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link GroupMembershipService} con su repositorio inyectado, sin lógica. */
@Service
public class GroupMembershipServiceImpl implements GroupMembershipService {
    private final GroupMembershipRepository groupMembershipRepository;

    public GroupMembershipServiceImpl(GroupMembershipRepository groupMembershipRepository) {
        this.groupMembershipRepository = groupMembershipRepository;
    }
}
