package com.tasneem.taskmanager.user;

import com.tasneem.taskmanager.common.exception.NotFoundException;
import com.tasneem.taskmanager.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getProfile(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
