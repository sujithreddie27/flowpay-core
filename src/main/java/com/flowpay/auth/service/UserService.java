package com.flowpay.auth.service;

import com.flowpay.auth.dto.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse getUserById(UUID userId);

    UserResponse getUserByEmail(String email);

    void evictUserCache(UUID userId);
}
