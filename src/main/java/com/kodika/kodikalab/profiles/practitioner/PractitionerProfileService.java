package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileRequest;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileResponse;
import com.kodika.kodikalab.users.User;

public interface PractitionerProfileService {

    PractitionerProfileResponse getPractitionerProfile(User practitioner);

    PractitionerProfileResponse savePractitionerProfile(
            User practitioner,
            PractitionerProfileRequest request
    );

    PractitionerProfile requirePractitionerProfile(Integer userId);
}