package com.kodika.kodikalab.profiles.coach;

import com.kodika.kodikalab.profiles.coach.dto.CoachProfileRequest;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileResponse;
import com.kodika.kodikalab.users.User;

public interface CoachProfileService {
    CoachProfileResponse getCoachProfile(User coach);

    CoachProfileResponse saveCoachProfile(User coach, CoachProfileRequest request);
}
