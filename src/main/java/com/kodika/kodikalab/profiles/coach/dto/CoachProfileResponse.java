package com.kodika.kodikalab.profiles.coach.dto;

import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.users.Role;

public record CoachProfileResponse(String message, CoachProfileData profile) {
    public static CoachProfileResponse of(String message, CoachProfile profile) {
        return new CoachProfileResponse(message, new CoachProfileData(
                profile.getUser().getEmail(),
                profile.getUser().getRole(),
                profile.getMainSpecialty(),
                profile.getOrganization(),
                profile.getYearsOfExperience(),
                profile.getPresentation()
        ));
    }

    public record CoachProfileData(
            String email,
            Role role,
            String especialidadPrincipal,
            String organizacionClub,
            Integer aniosExperiencia,
            String presentacion
    ) {
    }
}
