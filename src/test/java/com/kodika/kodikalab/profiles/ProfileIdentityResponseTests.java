package com.kodika.kodikalab.profiles;

import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileResponse;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileResponse;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileIdentityResponseTests {
    @Test
    void practitionerNameComesFromTheAccountNotTheEmail() {
        User account = account(Role.PRACTICANTE);
        PractitionerProfile profile = new PractitionerProfile();
        profile.setUser(account);
        var data = PractitionerProfileResponse.of("OK", profile).profile();
        assertThat(data.fullName()).isEqualTo("Ángela Prueba");
        assertThat(data.email()).isEqualTo("test@example.invalid");
    }

    @Test
    void coachNameComesFromTheAccountNotTheEmail() {
        User account = account(Role.COACH);
        CoachProfile profile = new CoachProfile();
        profile.setUser(account);
        var data = CoachProfileResponse.of("OK", profile).profile();
        assertThat(data.fullName()).isEqualTo("Ángela Prueba");
        assertThat(data.email()).isEqualTo("test@example.invalid");
    }

    private User account(Role role) {
        User user = new User();
        user.setFullName("Ángela Prueba");
        user.setEmail("test@example.invalid");
        user.setRole(role);
        return user;
    }
}
