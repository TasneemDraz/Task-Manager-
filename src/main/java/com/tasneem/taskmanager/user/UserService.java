package com.tasneem.taskmanager.user;

import com.tasneem.taskmanager.common.PageResponse;
import com.tasneem.taskmanager.common.exception.ConflictException;
import com.tasneem.taskmanager.common.exception.NotFoundException;
import com.tasneem.taskmanager.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    public PageResponse<UserResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id"));
        return PageResponse.from(userRepository.findAll(pageable), UserResponse::from);
    }

    /**
     * Idempotent. The user's existing tokens stop working because JwtFilter checks `active` on every request.
     * Admin accounts cannot be deactivated, so the system can never be left without an admin.
     */
    @Transactional
    public void deactivate(Long userId) {
        User user = findUser(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new ConflictException("Admin accounts cannot be deactivated");
        }
        user.setActive(false);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
