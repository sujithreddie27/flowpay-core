package com.flowpay.auth.service;

import com.flowpay.auth.dto.UserResponse;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.mapper.UserMapper;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserResponse userResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        user = User.builder()
                .email("user@example.com")
                .passwordHash("hashed")
                .firstName("Test")
                .lastName("User")
                .phone("+1234567890")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        user.setId(userId);

        userResponse = UserResponse.builder()
                .id(userId)
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .build();
    }

    @Nested
    @DisplayName("GetUserById")
    class GetUserById {

        @Test
        @DisplayName("should return user response when user exists")
        void shouldReturnUserWhenExists() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userMapper.toResponse(user)).thenReturn(userResponse);

            UserResponse result = userService.getUserById(userId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getEmail()).isEqualTo("user@example.com");
            verify(userRepository).findById(userId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user not found")
        void shouldThrowWhenNotFound() {
            UUID nonExistentId = UUID.randomUUID();
            when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("GetUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("should return user response when email exists")
        void shouldReturnUserWhenEmailExists() {
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(userMapper.toResponse(user)).thenReturn(userResponse);

            UserResponse result = userService.getUserByEmail("user@example.com");

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("user@example.com");
            verify(userRepository).findByEmail("user@example.com");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when email not found")
        void shouldThrowWhenEmailNotFound() {
            when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserByEmail("unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("EvictUserCache")
    class EvictUserCache {

        @Test
        @DisplayName("should evict cache without errors")
        void shouldEvictCacheSuccessfully() {
            // This is a cache eviction method - just verify it doesn't throw
            userService.evictUserCache(userId);
            // Method has no side effects other than cache eviction
        }
    }
}
