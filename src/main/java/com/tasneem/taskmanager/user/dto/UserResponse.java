package com.tasneem.taskmanager.user.dto;

import com.tasneem.taskmanager.user.Role;
import com.tasneem.taskmanager.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
