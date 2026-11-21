package com.tasneem.taskmanager.user;

import com.tasneem.taskmanager.auth.CurrentUser;
import com.tasneem.taskmanager.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        return userService.getProfile(currentUser.id());
    }
}
