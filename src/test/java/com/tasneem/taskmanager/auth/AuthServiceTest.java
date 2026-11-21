package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.auth.dto.LoginRequest;
import com.tasneem.taskmanager.auth.dto.RegisterRequest;
import com.tasneem.taskmanager.common.exception.ConflictException;
import com.tasneem.taskmanager.common.exception.InvalidCredentialsException;
import com.tasneem.taskmanager.user.Role;
import com.tasneem.taskmanager.user.User;
import com.tasneem.taskmanager.user.UserRepository;
import com.tasneem.taskmanager.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcy1sb25n";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofHours(24)), Clock.systemUTC());
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    private User storedUser(boolean active) {
        User user = new User("Jane Doe", "jane@example.com", passwordEncoder.encode("secret123"));
        user.setId(7L);
        user.setRole(Role.USER);
        user.setActive(active);
        return user;
    }

    @Test
    void registerStoresLowercaseEmailAndHashedPassword() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest("Jane Doe", "  Jane@Example.COM ", "secret123"));

        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.active()).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsEmailAlreadyUsedInAnyCase() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Jane Doe", "JANE@example.com", "secret123")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenForTheUser() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(storedUser(true)));

        var token = authService.login(new LoginRequest("Jane@Example.com", "secret123"));

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(86_400);
        assertThat(jwtService.extractUserId(token.accessToken())).contains(7L);
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(storedUser(true)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong-password1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginRejectsUnknownEmailWithSameMessage() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "secret123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void loginRejectsDeactivatedUserWithSameMessage() {
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(storedUser(false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "secret123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
