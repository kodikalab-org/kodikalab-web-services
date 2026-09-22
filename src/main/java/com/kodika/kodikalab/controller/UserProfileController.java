package com.kodika.kodikalab.controller;

import com.kodika.kodikalab.dto.LinkHandleRequest;
import com.kodika.kodikalab.dto.ProfileResponse;
import com.kodika.kodikalab.dto.UpdateProfileRequest;
import com.kodika.kodikalab.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(userProfileService.getProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(request));
    }

    @PostMapping("/me/handles")
    public ResponseEntity<ProfileResponse> linkHandle(@RequestBody LinkHandleRequest request) {
        return ResponseEntity.ok(userProfileService.linkHandle(request));
    }
}
