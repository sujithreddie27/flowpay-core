package com.flowpay.auth.service;

import com.flowpay.auth.dto.*;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.mapper.UserMapper;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.exception.AuthenticationFailedException;
import com.flowpay.common.exception.InvalidTokenException;
import com.flowpay.common.exception.UserAlreadyExistsException;
import com.flowpay.security.CustomUserDetails;
import com.flowpay.security.JwtProperties;
import com.flowpay.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public AuthResponse register(CreateUserRequest request) {
        log.info("Processing registration for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("email", request.getEmail());
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new UserAlreadyExistsException("phone", request.getPhone());
        }

        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        log.info("User registered successfully: userId={}", user.getId());

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpiration() / 1000,
                userMapper.toResponse(user)
        );
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Processing login for email: {}", request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            userRepository.updateLastLoginAt(userDetails.getUserId(), OffsetDateTime.now());

            String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
            String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

            User user = userRepository.findById(userDetails.getUserId())
                    .orElseThrow(() -> new AuthenticationFailedException("User not found"));

            log.info("User logged in successfully: userId={}", userDetails.getUserId());

            return AuthResponse.of(
                    accessToken,
                    refreshToken,
                    jwtProperties.getAccessTokenExpiration() / 1000,
                    userMapper.toResponse(user)
            );
        } catch (BadCredentialsException ex) {
            log.warn("Login failed - bad credentials for email: {}", request.getEmail());
            throw new AuthenticationFailedException("Invalid email or password");
        } catch (DisabledException ex) {
            log.warn("Login failed - account disabled for email: {}", request.getEmail());
            throw new AuthenticationFailedException("Account is disabled");
        } catch (LockedException ex) {
            log.warn("Login failed - account locked for email: {}", request.getEmail());
            throw new AuthenticationFailedException("Account is locked");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Token is not a refresh token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found for refresh token"));

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        log.info("Token refreshed successfully: userId={}", userId);

        return AuthResponse.of(
                newAccessToken,
                newRefreshToken,
                jwtProperties.getAccessTokenExpiration() / 1000,
                userMapper.toResponse(user)
        );
    }

    @Override
    public void logout(LogoutRequest request) {
        String refreshToken = request.getRefreshToken();

        if (jwtTokenProvider.validateToken(refreshToken)) {
            String key = "blacklist:token:" + refreshToken;
            long expiration = jwtProperties.getRefreshTokenExpiration() / 1000;
            stringRedisTemplate.opsForValue().set(key, "blacklisted", expiration, TimeUnit.SECONDS);
            log.info("Refresh token blacklisted successfully");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AuthenticationFailedException("Not authenticated");
        }

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));
        return userMapper.toResponse(user);
    }

    @Override
    public TokenVerifyResponse verifyToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        boolean valid = jwtTokenProvider.validateToken(token);
        if (!valid) {
            return TokenVerifyResponse.builder().valid(false).build();
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return TokenVerifyResponse.builder()
                .valid(true)
                .userId(userId)
                .email(email)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void requestPasswordReset(PasswordResetRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            String key = "password-reset:" + resetToken;
            stringRedisTemplate.opsForValue().set(key, user.getId().toString(), 1, TimeUnit.HOURS);
            log.info("Password reset token generated for userId={}", user.getId());
        });
        // Always return success to prevent email enumeration
    }
}
