package com.kodika.kodikalab.profiles.practitioner.dto;

import com.kodika.kodikalab.profiles.practitioner.PractitionerLevel;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.users.Role;

public record PractitionerProfileResponse(String message, ProfileData profile) {
    public static PractitionerProfileResponse of(String message, PractitionerProfile profile) {
        return new PractitionerProfileResponse(message, new ProfileData(
                profile.getUser().getEmail(),
                profile.getUser().getRole(),
                profile.getStudentCode(),
                profile.getCareer(),
                profile.getAcademicCycle(),
                profile.getCompetitiveLevel(),
                profile.getCodeforcesHandle(),
                profile.getCodeforcesRating(),
                profile.getAtcoderHandle(),
                profile.getVjudgeHandle()
        ));
    }

    public record ProfileData(
            String email,
            Role role,
            String codigoEstudiante,
            String carrera,
            Integer cicloAcademico,
            PractitionerLevel nivelCompetitivo,
            String codeforcesHandle,
            Integer codeforcesRating,
            String atcoderHandle,
            String vjudgeHandle
    ) {
    }
}
