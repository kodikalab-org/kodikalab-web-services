package com.kodika.kodikalab.users;

import com.kodika.kodikalab.common.exception.ConflictException;
import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTests {
    @Test
    void savesActiveAccountWithRoleAndUtcTimestamp() {
        UserRepository repository = mock(UserRepository.class);
        new UserService(repository).createUser("Matias", "Test", "matias@upc.edu.pe", "HASH", Role.PRACTITIONER);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        User user = captor.getValue();
        assertThat(user.getEmail()).isEqualTo("matias@upc.edu.pe");
        assertThat(user.getPasswordHash()).isEqualTo("HASH");
        assertThat(user.getRole()).isEqualTo(Role.PRACTITIONER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void existingEmailBlocksCreationWithRequiredMessage() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByEmailIgnoreCase("matias@upc.edu.pe")).thenReturn(true);
        assertThatThrownBy(() -> new UserService(repository).createUser("Matias", "Test", "matias@upc.edu.pe", "HASH", Role.COACH))
                .isInstanceOf(ConflictException.class).hasMessage(UserService.EMAIL_ALREADY_REGISTERED);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void databaseUniqueConstraintIsTranslatedForConcurrentRegistrations() {
        for (String name : new String[]{"uq_users_email", "users_email_key"}) {
            UserRepository repository = mock(UserRepository.class);
            var violation = new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"), name);
            when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("SQL", violation));
            assertThatThrownBy(() -> new UserService(repository).createUser("Matias", "Test", "matias@upc.edu.pe", "HASH", Role.COACH))
                    .isInstanceOf(ConflictException.class).hasMessage(UserService.EMAIL_ALREADY_REGISTERED);
        }
    }

    @Test
    void unrelatedDatabaseErrorIsNotMisreportedAsDuplicateEmail() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Other constraint"));
        assertThatThrownBy(() -> new UserService(repository).createUser("Matias", "Test", "matias@upc.edu.pe", "HASH", Role.COACH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
