package com.flowpay.auth.service;

import com.flowpay.auth.dto.UserResponse;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.mapper.UserMapper;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_USER_PROFILE, key = "#userId", unless = "#result == null")
    public UserResponse getUserById(UUID userId) {
        log.debug("Fetching user from database: userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_USER_PROFILE, key = "#email", unless = "#result == null")
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user from database: email={}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return userMapper.toResponse(user);
    }

    @Override
    @CacheEvict(value = RedisConfig.CACHE_USER_PROFILE, key = "#userId")
    public void evictUserCache(UUID userId) {
        log.debug("Evicting user cache: userId={}", userId);
    }
}
