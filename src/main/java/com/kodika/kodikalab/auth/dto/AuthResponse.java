package com.kodika.kodikalab.auth.dto;

import com.kodika.kodikalab.users.Role;

public record AuthResponse(String message, String email, Role role) {
}
