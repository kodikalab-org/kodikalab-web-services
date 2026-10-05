package com.kodika.kodikalab.users;

import com.kodika.kodikalab.common.exception.ConflictException;
import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTests {
    private static final String EMAIL = "test@gmail.com";

    @Test
    void savesFullNameActiveAccountWithRoleAndUtcTimestamp() {
        UserRepository repository = mock(UserRepository.class);
        new UserService(repository).createUser("Usuario Prueba", EMAIL, "HASH", Role.PRACTICANTE);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        User user = captor.getValue();
        assertThat(user.getFullName()).isEqualTo("Usuario Prueba");
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo("HASH");
        assertThat(user.getRole()).isEqualTo(Role.PRACTICANTE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVO);
        assertThat(user.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void existingEmailBlocksCreationWithRequiredMessage() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
        assertThatThrownBy(() -> new UserService(repository).createUser("Usuario Prueba", EMAIL, "HASH", Role.COACH))
                .isInstanceOf(ConflictException.class).hasMessage(UserService.EMAIL_ALREADY_REGISTERED);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void databaseUniqueConstraintIsTranslatedForConcurrentRegistrations() {
        for (String name : new String[]{"uq_usuario_correo", "usuario_correo_key"}) {
            UserRepository repository = mock(UserRepository.class);
            var violation = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"), name);
            when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL", violation));
            assertThatThrownBy(() -> new UserService(repository).createUser("Usuario Prueba", EMAIL, "HASH", Role.COACH))
                    .isInstanceOf(ConflictException.class).hasMessage(UserService.EMAIL_ALREADY_REGISTERED);
        }
    }

    @Test
    void lookupUsesOwnRepositoryAndPreservesEmptyResult() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        assertThat(new UserService(repository).findByEmail(EMAIL)).isEmpty();
        verify(repository).findByEmailIgnoreCase(EMAIL);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void lookupReturnsStoredAccountWithoutChangingStatus() {
        UserRepository repository = mock(UserRepository.class);
        User user = new User();
        user.setStatus(UserStatus.SUSPENDIDO);
        when(repository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        assertThat(new UserService(repository).findByEmail(EMAIL)).contains(user);
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDIDO);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedDatabaseConstraintIsNotMisreportedAsDuplicateEmail() {
        UserRepository repository = mock(UserRepository.class);
        var violation = new ConstraintViolationException("other", new SQLException("other", "23505"), "uq_other_column");
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL", violation));
        assertThatThrownBy(() -> new UserService(repository).createUser("Usuario Prueba", EMAIL, "HASH", Role.COACH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
