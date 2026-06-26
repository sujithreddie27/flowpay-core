package com.flowpay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.auth.controller.AuthController;
import com.flowpay.auth.dto.*;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.service.AuthService;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserRole;
import com.flowpay.common.enums.UserStatus;
import com.flowpay.common.exception.AuthenticationFailedException;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.common.exception.InvalidTokenException;
import com.flowpay.common.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class,
        JwtTokenProvider.class, JwtProperties.class, GlobalExceptionHandler.class,
        AccessDeniedHandlerImpl.class})
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthService authService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private UserResponse testUserResponse;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@flowpay.com")
                .passwordHash("$2a$12$hashedPassword")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        testUser.setId(UUID.randomUUID());

        testUserResponse = UserResponse.builder()
                .id(testUser.getId())
                .email("test@flowpay.com")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("should register new user successfully")
        void shouldRegisterNewUser() throws Exception {
            AuthResponse authResponse = AuthResponse.of(
                    "access.token.here", "refresh.token.here", 900, testUserResponse);

            when(authService.register(any(CreateUserRequest.class))).thenReturn(authResponse);

            CreateUserRequest request = CreateUserRequest.builder()
                    .email("newuser@flowpay.com")
                    .password("SecurePass123!")
                    .firstName("New")
                    .lastName("User")
                    .phone("+1234567890")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access.token.here"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh.token.here"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.email").value("test@flowpay.com"));
        }

        @Test
        @DisplayName("should reject duplicate email registration")
        void shouldRejectDuplicateEmail() throws Exception {
            when(authService.register(any(CreateUserRequest.class)))
                    .thenThrow(new UserAlreadyExistsException("email", "existing@flowpay.com"));

            CreateUserRequest request = CreateUserRequest.builder()
                    .email("existing@flowpay.com")
                    .password("AnotherPass1!")
                    .firstName("Another")
                    .lastName("User")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("USER_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("should reject invalid email format")
        void shouldRejectInvalidEmail() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("not-an-email")
                    .password("SecurePass123!")
                    .firstName("Test")
                    .lastName("User")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should reject short password")
        void shouldRejectShortPassword() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("test@flowpay.com")
                    .password("short")
                    .firstName("Test")
                    .lastName("User")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("should login with valid credentials")
        void shouldLoginWithValidCredentials() throws Exception {
            AuthResponse authResponse = AuthResponse.of(
                    "access.token.here", "refresh.token.here", 900, testUserResponse);

            when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

            LoginRequest request = LoginRequest.builder()
                    .email("user@flowpay.com")
                    .password("ValidPass123!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access.token.here"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh.token.here"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(900));
        }

        @Test
        @DisplayName("should reject invalid password")
        void shouldRejectInvalidPassword() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthenticationFailedException("Invalid email or password"));

            LoginRequest request = LoginRequest.builder()
                    .email("user@flowpay.com")
                    .password("WrongPassword!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
        }

        @Test
        @DisplayName("should reject non-existent email")
        void shouldRejectNonExistentEmail() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthenticationFailedException("Invalid email or password"));

            LoginRequest request = LoginRequest.builder()
                    .email("nonexistent@flowpay.com")
                    .password("SomePass123!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
        }

        @Test
        @DisplayName("should reject disabled account login")
        void shouldRejectDisabledAccountLogin() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthenticationFailedException("Account is disabled"));

            LoginRequest request = LoginRequest.builder()
                    .email("disabled@flowpay.com")
                    .password("ValidPass123!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Token Refresh")
    class TokenRefresh {

        @Test
        @DisplayName("should refresh token with valid refresh token")
        void shouldRefreshTokenWithValidRefreshToken() throws Exception {
            AuthResponse authResponse = AuthResponse.of(
                    "new.access.token", "new.refresh.token", 900, testUserResponse);

            when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(authResponse);

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid.refresh.token")
                    .build();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("new.access.token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"));
        }

        @Test
        @DisplayName("should reject invalid refresh token")
        void shouldRejectInvalidRefreshToken() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenThrow(new InvalidTokenException("Invalid or expired refresh token"));

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid.refresh.token")
                    .build();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }

        @Test
        @DisplayName("should reject access token used as refresh token")
        void shouldRejectAccessTokenAsRefreshToken() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenThrow(new InvalidTokenException("Token is not a refresh token"));

            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("access.token.as.refresh")
                    .build();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
        }
    }

    @Nested
    @DisplayName("Protected Endpoints")
    class ProtectedEndpoints {

        @Test
        @DisplayName("should deny access without token")
        void shouldDenyAccessWithoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("should deny access with invalid token")
        void shouldDenyAccessWithInvalidToken() throws Exception {
            mockMvc.perform(get("/api/v1/accounts")
                            .header("Authorization", "Bearer invalid.token.value")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should allow access with valid token to protected endpoints")
        void shouldAllowAccessWithValidToken() throws Exception {
            CustomUserDetails userDetails = new CustomUserDetails(testUser);
            String accessToken = jwtTokenProvider.generateAccessToken(userDetails);

            when(customUserDetailsService.loadUserById(testUser.getId()))
                    .thenReturn(userDetails);

            // AuthController doesn't handle /api/v1/transactions, so this returns 404 (not 401)
            mockMvc.perform(get("/api/v1/transactions")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }

        @Test
        @DisplayName("should allow unauthenticated access to auth endpoints")
        void shouldAllowUnauthenticatedAuthAccess() throws Exception {
            // Auth endpoints are public
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(result ->
                            assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
        }
    }
}
