package com.flowpay.auth.service;

import com.flowpay.auth.dto.*;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.mapper.UserMapper;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.AuthenticationFailedException;
import com.flowpay.common.exception.InvalidTokenException;
import com.flowpay.common.exception.UserAlreadyExistsException;
import com.flowpay.security.CustomUserDetails;
import com.flowpay.security.JwtProperties;
import com.flowpay.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    private CreateUserRequest createUserRequest;
    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        createUserRequest = CreateUserRequest.builder()
                .email("test@example.com")
                .password("SecurePass123!")
                .firstName("Test")
                .lastName("User")
                .phone("+1234567890")
                .build();

        user = User.builder()
                .email("test@example.com")
                .passwordHash("$2a$12$encodedpassword")
                .firstName("Test")
                .lastName("User")
                .phone("+1234567890")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .role(UserRole.USER)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
        user.setId(UUID.randomUUID());

        userResponse = UserResponse.builder()
                .id(user.getId())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .phone("+1234567890")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .role(UserRole.USER)
                .build();
    }

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("should register user successfully")
        void shouldRegisterSuccessfully() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("+1234567890")).thenReturn(false);
            when(userMapper.toEntity(createUserRequest)).thenReturn(user);
            when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$12$encodedpassword");
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh-token");
            when(jwtProperties.getAccessTokenExpiration()).thenReturn(900000L);
            when(userMapper.toResponse(user)).thenReturn(userResponse);

            AuthResponse response = authService.register(createUserRequest);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getUser()).isEqualTo(userResponse);
            assertThat(response.getTokenType()).isEqualTo("Bearer");

            verify(userRepository).save(any(User.class));
            verify(passwordEncoder).encode("SecurePass123!");
        }

        @Test
        @DisplayName("should throw when email already exists")
        void shouldThrowWhenEmailExists() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(createUserRequest))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when phone already exists")
        void shouldThrowWhenPhoneExists() {
            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("+1234567890")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(createUserRequest))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip phone check when phone is null")
        void shouldSkipPhoneCheckWhenNull() {
            CreateUserRequest noPhoneRequest = CreateUserRequest.builder()
                    .email("nophone@example.com")
                    .password("SecurePass123!")
                    .firstName("No")
                    .lastName("Phone")
                    .build();

            User noPhoneUser = User.builder()
                    .email("nophone@example.com")
                    .passwordHash("encoded")
                    .firstName("No")
                    .lastName("Phone")
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.PENDING)
                    .role(UserRole.USER)
                    .emailVerified(false)
                    .phoneVerified(false)
                    .build();
            noPhoneUser.setId(UUID.randomUUID());

            when(userRepository.existsByEmail("nophone@example.com")).thenReturn(false);
            when(userMapper.toEntity(noPhoneRequest)).thenReturn(noPhoneUser);
            when(passwordEncoder.encode("SecurePass123!")).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(noPhoneUser);
            when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("token");
            when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh");
            when(jwtProperties.getAccessTokenExpiration()).thenReturn(900000L);
            when(userMapper.toResponse(noPhoneUser)).thenReturn(userResponse);

            AuthResponse response = authService.register(noPhoneRequest);

            assertThat(response).isNotNull();
            verify(userRepository, never()).existsByPhone(any());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("should login successfully")
        void shouldLoginSuccessfully() {
            LoginRequest loginRequest = LoginRequest.builder()
                    .email("test@example.com")
                    .password("SecurePass123!")
                    .build();

            CustomUserDetails userDetails = new CustomUserDetails(user);
            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(userDetails);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(userDetails)).thenReturn("access-token");
            when(jwtTokenProvider.generateRefreshToken(userDetails)).thenReturn("refresh-token");
            when(jwtProperties.getAccessTokenExpiration()).thenReturn(900000L);
            when(userMapper.toResponse(user)).thenReturn(userResponse);

            AuthResponse response = authService.login(loginRequest);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            verify(userRepository).updateLastLoginAt(eq(user.getId()), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("should throw on bad credentials")
        void shouldThrowOnBadCredentials() {
            LoginRequest loginRequest = LoginRequest.builder()
                    .email("test@example.com")
                    .password("wrongpass")
                    .build();

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw when account is disabled")
        void shouldThrowWhenDisabled() {
            LoginRequest loginRequest = LoginRequest.builder()
                    .email("disabled@example.com")
                    .password("pass")
                    .build();

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("Disabled"));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("disabled");
        }

        @Test
        @DisplayName("should throw when account is locked")
        void shouldThrowWhenLocked() {
            LoginRequest loginRequest = LoginRequest.builder()
                    .email("locked@example.com")
                    .password("pass")
                    .build();

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new LockedException("Locked"));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessageContaining("locked");
        }
    }

    @Nested
    @DisplayName("RefreshToken")
    class RefreshToken {

        @Test
        @DisplayName("should refresh token successfully")
        void shouldRefreshTokenSuccessfully() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            when(jwtTokenProvider.validateToken("valid-refresh-token")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("valid-refresh-token")).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken("valid-refresh-token")).thenReturn(user.getId());
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("new-access");
            when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("new-refresh");
            when(jwtProperties.getAccessTokenExpiration()).thenReturn(900000L);
            when(userMapper.toResponse(user)).thenReturn(userResponse);

            AuthResponse response = authService.refreshToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("should throw for invalid refresh token")
        void shouldThrowForInvalidToken() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid-token")
                    .build();

            when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("should throw when token is not a refresh token")
        void shouldThrowWhenNotRefreshToken() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("access-token-used-as-refresh")
                    .build();

            when(jwtTokenProvider.validateToken("access-token-used-as-refresh")).thenReturn(true);
            when(jwtTokenProvider.isRefreshToken("access-token-used-as-refresh")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
