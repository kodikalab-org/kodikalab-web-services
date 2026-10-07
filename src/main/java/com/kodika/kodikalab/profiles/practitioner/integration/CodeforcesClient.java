package com.kodika.kodikalab.profiles.practitioner.integration;

import java.util.Optional;

public interface CodeforcesClient {
    Optional<CodeforcesUserInfo> findUser(String handle);
}
