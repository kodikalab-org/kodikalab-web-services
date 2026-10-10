package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileRequest;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileResponse;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUserInfo;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class PractitionerProfileServiceImpl implements PractitionerProfileService {
    private static final String ONLY_PRACTITIONERS = "Solo los practicantes pueden gestionar este perfil";
    private static final String PROFILE_NOT_FOUND = "El perfil de practicante aún no está registrado";
    private static final String DUPLICATE_STUDENT_CODE = "El código de estudiante ya está vinculado a otro practicante";
    private static final String PROFILE_UPDATED = "Perfil actualizado correctamente";
    private static final String CODEFORCES_NOT_CONFIRMED = "Perfil actualizado correctamente, pero Codeforces no "
            + "confirmó el identificador informado; se conservaron los datos previos de Codeforces";

    private final PractitionerProfileRepository profileRepository;
    private final CodeforcesClient codeforcesClient;
    private final TransactionOperations transactionOperations;

    public PractitionerProfileServiceImpl(PractitionerProfileRepository profileRepository, CodeforcesClient codeforcesClient,
                              TransactionOperations transactionOperations) {
        this.profileRepository = profileRepository;
        this.codeforcesClient = codeforcesClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    @Transactional(readOnly = true)
    public PractitionerProfileResponse getPractitionerProfile(User user) {
        requirePractitioner(user);
        PractitionerProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
        return PractitionerProfileResponse.of("Perfil obtenido correctamente", profile);
    }
    @Override
    @Transactional(readOnly = true)
    public PractitionerProfile requirePractitionerProfile(Integer userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(PROFILE_NOT_FOUND));
    }

    /**
     * La consulta a Codeforces se hace antes de abrir la transacción para no retener
     * una conexión de base de datos mientras se espera a la API externa.
     */
    @Override
    public PractitionerProfileResponse savePractitionerProfile(User user, PractitionerProfileRequest request) {
        requirePractitioner(user);
        String studentCode = trimRequired(request.codigoEstudiante());
        ensureStudentCodeAvailable(studentCode, user.getId());
        String codeforcesHandle = trimOptional(request.codeforcesHandle());
        Optional<CodeforcesUserInfo> codeforcesUser = codeforcesHandle == null
                ? Optional.empty() : codeforcesClient.findUser(codeforcesHandle);
        boolean codeforcesNotConfirmed = codeforcesHandle != null && codeforcesUser.isEmpty();

        return transactionOperations.execute(status -> {
            ensureStudentCodeAvailable(studentCode, user.getId());
            PractitionerProfile profile = profileRepository.findByUserId(user.getId()).orElseGet(() -> {
                PractitionerProfile created = new PractitionerProfile();
                created.setUser(user);
                return created;
            });
            profile.setStudentCode(studentCode);
            profile.setCareer(trimRequired(request.carrera()));
            profile.setAcademicCycle(request.cicloAcademico());
            profile.setCompetitiveLevel(request.nivelCompetitivo());
            applyCodeforces(profile, codeforcesHandle, codeforcesUser);
            profile.setAtcoderHandle(trimOptional(request.atcoderHandle()));
            profile.setVjudgeHandle(trimOptional(request.vjudgeHandle()));
            PractitionerProfile saved = profileRepository.saveAndFlush(profile);
            return PractitionerProfileResponse.of(
                    codeforcesNotConfirmed ? CODEFORCES_NOT_CONFIRMED : PROFILE_UPDATED, saved);
        });
    }

    private void ensureStudentCodeAvailable(String studentCode, Integer userId) {
        if (profileRepository.existsByStudentCodeAndUserIdNot(studentCode, userId)) {
            throw new ConflictException(DUPLICATE_STUDENT_CODE);
        }
    }

    private void applyCodeforces(PractitionerProfile profile, String requestedHandle,
                                 Optional<CodeforcesUserInfo> confirmedUser) {
        if (requestedHandle == null) {
            profile.setCodeforcesHandle(null);
            profile.setCodeforcesRating(null);
            return;
        }
        confirmedUser.ifPresent(userInfo -> {
            profile.setCodeforcesHandle(userInfo.handle());
            profile.setCodeforcesRating(userInfo.rating());
        });
    }

    private void requirePractitioner(User user) {
        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException(ONLY_PRACTITIONERS);
        }
    }

    private String trimRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String trimOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
