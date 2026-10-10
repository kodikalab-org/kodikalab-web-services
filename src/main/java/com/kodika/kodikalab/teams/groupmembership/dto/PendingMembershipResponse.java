package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.profiles.practitioner.PractitionerLevel;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import java.time.OffsetDateTime;

/** Solicitud de ingreso pendiente, con los datos del postulante que el coach necesita para decidir. */
public record PendingMembershipResponse(
        Integer membershipId,
        Integer groupId,
        Integer practitionerId,
        MembershipStatus status,
        OffsetDateTime requestedAt,
        Applicant practitioner
) {
    /** Perfil académico y competitivo del postulante; no incluye el correo. */
    public record Applicant(
            String fullName,
            String studentCode,
            String career,
            Integer academicCycle,
            PractitionerLevel competitiveLevel,
            String codeforcesHandle,
            Integer codeforcesRating,
            String atcoderHandle,
            String vjudgeHandle
    ) {
    }

    public static PendingMembershipResponse from(GroupMembership membership) {
        PractitionerProfile profile = membership.getPractitioner();
        return new PendingMembershipResponse(
                membership.getId(),
                membership.getGroup().getId(),
                profile.getUserId(),
                membership.getStatus(),
                membership.getJoinedAt(),
                new Applicant(
                        profile.getUser().getFullName(),
                        profile.getStudentCode(),
                        profile.getCareer(),
                        profile.getAcademicCycle(),
                        profile.getCompetitiveLevel(),
                        profile.getCodeforcesHandle(),
                        profile.getCodeforcesRating(),
                        profile.getAtcoderHandle(),
                        profile.getVjudgeHandle()
                )
        );
    }
}
