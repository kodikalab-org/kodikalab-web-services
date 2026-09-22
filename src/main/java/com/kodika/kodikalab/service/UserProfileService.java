package com.kodika.kodikalab.service;

import com.kodika.kodikalab.dto.ProfileResponse;
import com.kodika.kodikalab.dto.UpdateProfileRequest;
import com.kodika.kodikalab.dto.LinkHandleRequest;

public interface UserProfileService {

    ProfileResponse getProfile();

    ProfileResponse updateProfile(UpdateProfileRequest request);

    ProfileResponse linkHandle(LinkHandleRequest request);
}
