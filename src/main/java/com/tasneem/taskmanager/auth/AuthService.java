package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.auth.dto.LoginRequest;
import com.tasneem.taskmanager.auth.dto.RegisterRequest;
import com.tasneem.taskmanager.auth.dto.TokenResponse;
import com.tasneem.taskmanager.common.exception.ConflictException;
import com.tasneem.taskmanager.common.exception.InvalidCredentialsException;
import com.tasneem.taskmanager.user.User;
import com.tasneem.taskmanager.user.UserRepository;
import com.tasneem.taskmanager.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already in use");
        }
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()));
        return UserResponse.from(userRepository.save(user));
    }

    /** Unknown email, wrong password and deactivated account all get the same answer. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(InvalidCredentialsException::new);
        return TokenResponse.bearer(jwtService.generateToken(user), jwtService.getExpirationSeconds());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
