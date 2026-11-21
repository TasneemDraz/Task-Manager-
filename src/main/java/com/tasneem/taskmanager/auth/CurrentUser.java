package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.user.Role;
import com.tasneem.taskmanager.user.User;

/** The authenticated caller, stored as the principal of the security context. */
public record CurrentUser(Long id, String email, Role role) {

    public static CurrentUser from(User user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }
}
