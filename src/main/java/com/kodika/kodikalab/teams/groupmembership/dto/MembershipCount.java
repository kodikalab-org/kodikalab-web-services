package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;

/** Cantidad de membresías de un grupo en un estado. */
public record MembershipCount(Integer groupId, MembershipStatus status, Long total) {
}
