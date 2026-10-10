package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;

public record GroupMemberData(Integer membershipId, Integer teamId, Integer userId,
                              String fullName, MembershipStatus status) {
}
