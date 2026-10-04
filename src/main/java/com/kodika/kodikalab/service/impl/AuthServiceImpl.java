package com.kodika.kodikalab.service.impl;

import com.kodika.kodikalab.dto.RegisterRequest;
import com.kodika.kodikalab.dto.LoginRequest;
import com.kodika.kodikalab.dto.AuthResponse;
import com.kodika.kodikalab.service.AuthService;
import org.springframework.stereotype.Service;

// Retained for the legacy login scaffold (US-02); registration lives in auth/.
@Service("legacyAuthService")
public class AuthServiceImpl implements AuthService {

    @Override
    public AuthResponse register(RegisterRequest request) {
        return null;
    }
    @Override
    public AuthResponse login(LoginRequest request) {
        return null;
    }
}
