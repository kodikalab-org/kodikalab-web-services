package com.kodika.kodikalab.service;

import com.kodika.kodikalab.dto.RegisterRequest;
import com.kodika.kodikalab.dto.LoginRequest;
import com.kodika.kodikalab.dto.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
