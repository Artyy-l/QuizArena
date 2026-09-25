package org.example.dto.request.auth;

public record UpdateProfileRequest(Long userId, String newUsername, String newPassword) {}
