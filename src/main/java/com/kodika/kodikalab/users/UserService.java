package com.kodika.kodikalab.users;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ConstraintViolations;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
    public void createUser(String fullName, String email, String passwordHash, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(EMAIL_ALREADY_REGISTERED);
        }
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVO);
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // The database constraint is authoritative even for concurrent registrations.
            if (ConstraintViolations.isUniqueViolationOf(exception, "uq_usuario_correo", "usuario_correo_key")) {
                throw new ConflictException(EMAIL_ALREADY_REGISTERED);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }
}
