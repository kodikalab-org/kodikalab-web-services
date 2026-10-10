package com.kodika.kodikalab.profiles.coach;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileRequest;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoachProfileServiceTests {
    CoachProfileRepository repository;
    CoachProfileServiceImpl service;
    User coach;

    @BeforeEach
    void setUp() {
        repository = mock(CoachProfileRepository.class);
        service = new CoachProfileServiceImpl(repository);
        coach = new User();
        coach.setId(8);
        coach.setEmail("coach@gmail.com");
        coach.setRole(Role.COACH);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsProfileLinkedToAuthenticatedCoach() {
        when(repository.findByUserId(8)).thenReturn(Optional.empty());

        var response = service.saveCoachProfile(coach, request("Club de Programación", "Entrenador ICPC."));

        assertThat(response.message()).isEqualTo("Perfil actualizado correctamente");
        assertThat(response.profile().email()).isEqualTo("coach@gmail.com");
        assertThat(response.profile().role()).isEqualTo(Role.COACH);
        assertThat(response.profile().especialidadPrincipal()).isEqualTo("Grafos");
        assertThat(response.profile().aniosExperiencia()).isEqualTo(4);
        verify(repository).saveAndFlush(argThat(profile ->
                profile.getUser() == coach && profile.getUserId() == null
                        && "Club de Programación".equals(profile.getOrganization())));
    }

    @Test
    void updatesExistingProfileInPlace() {
        CoachProfile existing = new CoachProfile();
        existing.setUser(coach);
        existing.setMainSpecialty("DP");
        existing.setYearsOfExperience(1);
        when(repository.findByUserId(8)).thenReturn(Optional.of(existing));

        service.saveCoachProfile(coach, request("Club", "Bio"));

        verify(repository).saveAndFlush(same(existing));
        assertThat(existing.getMainSpecialty()).isEqualTo("Grafos");
        assertThat(existing.getYearsOfExperience()).isEqualTo(4);
    }

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        when(repository.findByUserId(8)).thenReturn(Optional.empty());

        var response = service.saveCoachProfile(coach, request("   ", ""));

        assertThat(response.profile().organizacionClub()).isNull();
        assertThat(response.profile().presentacion()).isNull();
    }

    @Test
    void missingProfileReturnsNotFound() {
        when(repository.findByUserId(8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCoachProfile(coach))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("El perfil de coach aún no está registrado");
    }

    @Test
    void practitionerCannotManageCoachProfile() {
        User practitioner = new User();
        practitioner.setId(7);
        practitioner.setRole(Role.PRACTICANTE);

        assertThatThrownBy(() -> service.saveCoachProfile(practitioner, request("Club", "Bio")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo los coaches pueden gestionar este perfil");
        assertThatThrownBy(() -> service.getCoachProfile(practitioner))
                .isInstanceOf(ForbiddenException.class);
        verify(repository, never()).findByUserId(any());
        verify(repository, never()).saveAndFlush(any());
    }

    private CoachProfileRequest request(String organization, String presentation) {
        return new CoachProfileRequest("  Grafos ", organization, 4, presentation);
    }
}
