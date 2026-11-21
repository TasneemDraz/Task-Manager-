package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.user.User;
import com.tasneem.taskmanager.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the Bearer token and, when it is valid and the user is still active,
 * authenticates the request. The user is loaded on every request so that a
 * deactivated account's existing tokens are rejected immediately.
 * Requests without a valid token stay anonymous and get 401 on protected endpoints.
 */
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.extractUserId(header.substring(BEARER_PREFIX.length()))
                    .flatMap(userRepository::findById)
                    .filter(User::isActive)
                    .map(CurrentUser::from)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(CurrentUser user) {
        var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
