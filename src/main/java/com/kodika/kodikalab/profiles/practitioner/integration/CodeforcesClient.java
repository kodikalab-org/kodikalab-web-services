package com.kodika.kodikalab.profiles.practitioner.integration;

import com.kodika.kodikalab.common.exception.NotFoundException;
import java.util.List;
import java.util.Optional;

public interface CodeforcesClient {
    Optional<CodeforcesUserInfo> findUser(String handle);

    default CodeforcesUserInfo requireUser(String handle) {
        return findUser(handle).orElseThrow(() -> new NotFoundException(
                "El usuario de Codeforces no existe"));
    }

    default List<CodeforcesSubmission> findSubmissions(String handle) {
        throw new CodeforcesUnavailableException();
    }
}
