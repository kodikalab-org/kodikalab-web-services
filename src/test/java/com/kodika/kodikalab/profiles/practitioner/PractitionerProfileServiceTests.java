package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileRequest;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUserInfo;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PractitionerProfileServiceTests {
    PractitionerProfileRepository repository;
    CodeforcesClient codeforces;
    PractitionerProfileServiceImpl service;
    User practitioner;

    @BeforeEach
    void setUp() {
        repository = mock(PractitionerProfileRepository.class);
        codeforces = mock(CodeforcesClient.class);
        service = new PractitionerProfileServiceImpl(repository, codeforces, TransactionOperations.withoutTransaction());
        practitioner = new User();
        practitioner.setId(7);
        practitioner.setEmail("test@gmail.com");
        practitioner.setRole(Role.PRACTICANTE);
    }

    @Test
    void confirmedCodeforcesUserIsPersistedWithApiRating() {
        when(repository.existsByStudentCodeAndUserIdNot("20240001", 7)).thenReturn(false);
        when(repository.findByUserId(7)).thenReturn(Optional.empty());
        when(codeforces.findUser("tourist")).thenReturn(Optional.of(new CodeforcesUserInfo("tourist", 3800)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.savePractitionerProfile(practitioner, request("tourist"));

        assertThat(response.message()).isEqualTo("Perfil actualizado correctamente");
        assertThat(response.profile().codeforcesHandle()).isEqualTo("tourist");
        assertThat(response.profile().codeforcesRating()).isEqualTo(3800);
        verify(repository).saveAndFlush(argThat(profile ->
                "tourist".equals(profile.getCodeforcesHandle()) && Integer.valueOf(3800).equals(profile.getCodeforcesRating())));
    }

    @Test
    void unconfirmedCodeforcesUserIsNotPersistedAndOtherProfileDataIsSaved() {
        when(repository.existsByStudentCodeAndUserIdNot("20240001", 7)).thenReturn(false);
        when(repository.findByUserId(7)).thenReturn(Optional.empty());
        when(codeforces.findUser("missing_handle")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.savePractitionerProfile(practitioner, request("missing_handle"));

        assertThat(response.message()).contains("Codeforces no confirmó el identificador informado");
        assertThat(response.profile().codigoEstudiante()).isEqualTo("20240001");
        assertThat(response.profile().codeforcesHandle()).isNull();
        assertThat(response.profile().codeforcesRating()).isNull();
        verify(repository).saveAndFlush(argThat(profile ->
                profile.getCodeforcesHandle() == null && profile.getCodeforcesRating() == null
                        && "Ingeniería de Software".equals(profile.getCareer())));
    }

    @Test
    void unconfirmedCodeforcesUserKeepsPreviousCodeforcesValues() {
        PractitionerProfile existing = new PractitionerProfile();
        existing.setUser(practitioner);
        existing.setStudentCode("20240001");
        existing.setCodeforcesHandle("tourist");
        existing.setCodeforcesRating(3800);
        when(repository.existsByStudentCodeAndUserIdNot("20240001", 7)).thenReturn(false);
        when(repository.findByUserId(7)).thenReturn(Optional.of(existing));
        when(codeforces.findUser("missing_handle")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.savePractitionerProfile(practitioner, request("missing_handle"));

        assertThat(response.profile().codeforcesHandle()).isEqualTo("tourist");
        assertThat(response.profile().codeforcesRating()).isEqualTo(3800);
    }

    @Test
    void blankCodeforcesHandleClearsCodeforcesFieldsWithoutCallingApi() {
        PractitionerProfile existing = new PractitionerProfile();
        existing.setUser(practitioner);
        existing.setStudentCode("20240001");
        existing.setCodeforcesHandle("tourist");
        existing.setCodeforcesRating(3800);
        when(repository.existsByStudentCodeAndUserIdNot("20240001", 7)).thenReturn(false);
        when(repository.findByUserId(7)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.savePractitionerProfile(practitioner, request("   "));

        assertThat(response.profile().codeforcesHandle()).isNull();
        assertThat(response.profile().codeforcesRating()).isNull();
        verifyNoInteractions(codeforces);
    }

    @Test
    void duplicateStudentCodeIsRejectedBeforeCallingCodeforces() {
        when(repository.existsByStudentCodeAndUserIdNot("20240001", 7)).thenReturn(true);

        assertThatThrownBy(() -> service.savePractitionerProfile(practitioner, request("tourist")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("El código de estudiante ya está vinculado a otro practicante");
        verifyNoInteractions(codeforces);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void codeforcesIsQueriedBeforeOpeningTheTransaction() {
        when(repository.findByUserId(7)).thenReturn(Optional.empty());
        when(codeforces.findUser("tourist")).thenReturn(Optional.of(new CodeforcesUserInfo("tourist", 3800)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionOperations transactions = mock(TransactionOperations.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            verify(codeforces).findUser("tourist");
            return TransactionOperations.withoutTransaction().execute(invocation.getArgument(0));
        });
        service = new PractitionerProfileServiceImpl(repository, codeforces, transactions);

        service.savePractitionerProfile(practitioner, request("tourist"));

        verify(transactions).execute(any());
    }

    @Test
    void coachCannotSavePractitionerProfile() {
        User coach = new User();
        coach.setId(8);
        coach.setRole(Role.COACH);

        assertThatThrownBy(() -> service.savePractitionerProfile(coach, request("tourist")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Solo los practicantes pueden gestionar este perfil");
        verifyNoInteractions(repository, codeforces);
    }

    private PractitionerProfileRequest request(String codeforcesHandle) {
        return new PractitionerProfileRequest("20240001", "Ingeniería de Software", 5,
                PractitionerLevel.INTERMEDIO, codeforcesHandle, "tourist_atcoder", "usuario_vjudge");
    }
}
