package com.kodika.kodikalab.teams.groupmembership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        @JsonInclude(JsonInclude.Include.NON_NULL) Clarification clarification,
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

    /** Aclaración pedida por el coach y, si ya existe, la respuesta del postulante. */
    public record Clarification(
            String request,
            OffsetDateTime requestedAt,
            String response,
            OffsetDateTime respondedAt
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
                clarificationOf(membership),
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

    private static Clarification clarificationOf(GroupMembership membership) {
        if (membership.getClarificationRequest() == null) {
            return null;
        }
        return new Clarification(
                membership.getClarificationRequest(),
                membership.getClarificationRequestedAt(),
                membership.getClarificationResponse(),
                membership.getClarificationRespondedAt()
        );
    }
}
