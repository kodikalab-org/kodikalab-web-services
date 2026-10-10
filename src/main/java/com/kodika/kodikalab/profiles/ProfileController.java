package com.kodika.kodikalab.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.profiles.coach.CoachProfileService;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileRequest;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileRequest;
import com.kodika.kodikalab.users.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * "Mi perfil": el rol de la sesión decide si se gestiona {@code practicante} o {@code coach}.
 * El body se lee como JSON genérico porque su forma depende de ese rol.
 */
@RestController
@RequestMapping("/users/me")
@Tag(name = "Perfil", description = "Perfil de la cuenta autenticada: practicante o coach (US-03).")
public class ProfileController {
    private final CurrentUserResolver currentUserResolver;
    private final ProfileRequestReader requestReader;
    private final PractitionerProfileService practitionerProfileService;
    private final CoachProfileService coachProfileService;

    public ProfileController(CurrentUserResolver currentUserResolver, ProfileRequestReader requestReader,
                             PractitionerProfileService practitionerProfileService, CoachProfileService coachProfileService) {
        this.currentUserResolver = currentUserResolver;
        this.requestReader = requestReader;
        this.practitionerProfileService = practitionerProfileService;
        this.coachProfileService = coachProfileService;
    }

    @GetMapping
    public ResponseEntity<?> getCurrentProfile() {
        User user = currentUserResolver.currentUser();
        return switch (user.getRole()) {
            case PRACTICANTE -> ResponseEntity.ok(practitionerProfileService.getPractitionerProfile(user));
            case COACH -> ResponseEntity.ok(coachProfileService.getCoachProfile(user));
        };
    }

    @PutMapping
    public ResponseEntity<?> updateCurrentProfile(@RequestBody JsonNode body) {
        User user = currentUserResolver.currentUser();
        return switch (user.getRole()) {
            case PRACTICANTE -> ResponseEntity.ok(practitionerProfileService.savePractitionerProfile(
                    user, requestReader.read(body, PractitionerProfileRequest.class)));
            case COACH -> ResponseEntity.ok(coachProfileService.saveCoachProfile(
                    user, requestReader.read(body, CoachProfileRequest.class)));
        };
    }
}
