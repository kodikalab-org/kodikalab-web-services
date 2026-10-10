package com.kodika.kodikalab.profiles.coach;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileRequest;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileResponse;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachProfileServiceImpl implements CoachProfileService {
    private static final String ONLY_COACHES = "Solo los coaches pueden gestionar este perfil";
    private static final String PROFILE_NOT_FOUND = "El perfil de coach aún no está registrado";

    private final CoachProfileRepository profileRepository;

    public CoachProfileServiceImpl(CoachProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CoachProfileResponse getCoachProfile(User coach) {
        requireCoach(coach);
        CoachProfile profile = profileRepository.findByUserId(coach.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        return CoachProfileResponse.of("Perfil obtenido correctamente", profile);
    }

    @Override
    @Transactional(readOnly = true)
    public CoachProfile requireCoachProfile(Integer userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
    }

    @Override
    @Transactional
    public CoachProfileResponse saveCoachProfile(User coach, CoachProfileRequest request) {
        requireCoach(coach);
        CoachProfile profile = profileRepository.findByUserId(coach.getId()).orElseGet(() -> {
            CoachProfile created = new CoachProfile();
            created.setUser(coach);
            return created;
        });
        profile.setMainSpecialty(request.especialidadPrincipal());
        profile.setOrganization(blankToNull(request.organizacionClub()));
        profile.setYearsOfExperience(request.aniosExperiencia());
        profile.setPresentation(blankToNull(request.presentacion()));
        CoachProfile saved = profileRepository.saveAndFlush(profile);
        return CoachProfileResponse.of("Perfil actualizado correctamente", saved);
    }

    private void requireCoach(User user) {
        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException(ONLY_COACHES);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
