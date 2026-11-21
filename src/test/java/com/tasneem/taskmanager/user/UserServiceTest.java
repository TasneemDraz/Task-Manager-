package com.tasneem.taskmanager.user;

import com.tasneem.taskmanager.common.exception.ConflictException;
import com.tasneem.taskmanager.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user() {
        User user = new User("Jane Doe", "jane@example.com", "hash");
        user.setId(7L);
        return user;
    }

    @Test
    void profileNeverExposesPassword() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));

        var profile = userService.getProfile(7L);

        assertThat(profile.email()).isEqualTo("jane@example.com");
        assertThat(profile.toString()).doesNotContain("hash");
    }

    @Test
    void deactivateMarksUserInactive() {
        User user = user();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        userService.deactivate(7L);

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void adminCannotBeDeactivated() {
        User admin = user();
        admin.setRole(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deactivate(7L)).isInstanceOf(ConflictException.class);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void deactivateUnknownUserReturnsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivate(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listReturnsPage() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(user()), inv.getArgument(0), 1));

        var page = userService.list(0, 10);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
