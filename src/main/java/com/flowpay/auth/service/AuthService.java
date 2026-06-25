package com.flowpay.auth.service;

import com.flowpay.auth.dto.AuthResponse;
import com.flowpay.auth.dto.CreateUserRequest;
import com.flowpay.auth.dto.LoginRequest;
import com.flowpay.auth.dto.RefreshTokenRequest;

public interface AuthService {

    AuthResponse register(CreateUserRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);
}
