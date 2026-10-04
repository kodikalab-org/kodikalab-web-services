package com.kodika.kodikalab.users;

import com.kodika.kodikalab.common.exception.ConflictException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public module boundary for account creation; users owns its repository. */
@Service
public class UserService {
    public static final String EMAIL_ALREADY_REGISTERED =
            "El correo institucional ya está vinculado a una cuenta existente";
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void createUser(String firstName, String lastName, String email, String passwordHash, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(EMAIL_ALREADY_REGISTERED);
        }
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // The database constraint is authoritative even for concurrent registrations.
            if (isEmailConflict(exception)) {
                throw new ConflictException(EMAIL_ALREADY_REGISTERED);
            }
            throw exception;
        }
    }

    private boolean isEmailConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if ("uq_users_email".equals(name) || "users_email_key".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
