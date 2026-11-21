package com.tasneem.taskmanager.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 100, message = "must be between 2 and 100 characters")
        String fullName,

        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt only uses the first 72 bytes, hence the upper bound
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "must contain at least one letter and one number")
        String password) {
}
