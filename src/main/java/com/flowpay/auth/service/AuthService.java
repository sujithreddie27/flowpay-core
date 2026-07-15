package com.flowpay.auth.service;

import com.flowpay.auth.dto.*;

public interface AuthService {

    AuthResponse register(CreateUserRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request);

    UserResponse getCurrentUser();

    TokenVerifyResponse verifyToken(String token);

    void requestPasswordReset(PasswordResetRequest request);
}
