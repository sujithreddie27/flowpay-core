package com.flowpay.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.auth.dto.*;
import com.flowpay.auth.service.AuthService;
import com.flowpay.common.enums.*;
import com.flowpay.common.exception.AuthenticationFailedException;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.common.exception.UserAlreadyExistsException;
import com.flowpay.security.CustomUserDetailsService;
import com.flowpay.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        UserResponse userResponse = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .role(UserRole.USER)
                .build();

        authResponse = AuthResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-456")
                .tokenType("Bearer")
                .expiresIn(900)
                .user(userResponse)
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        @Test
        @DisplayName("should register user and return 201")
        void shouldRegisterSuccessfully() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("new@example.com")
                    .password("SecurePass123!")
                    .name("New User")
                    .phone("+1234567890")
                    .build();

            when(authService.register(any(CreateUserRequest.class))).thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-456"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.message").value("User registered successfully"));
        }

        @Test
        @DisplayName("should return 400 for invalid email")
        void shouldReturn400ForInvalidEmail() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("invalid-email")
                    .password("SecurePass123!")
                    .name("Test User")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for short password")
        void shouldReturn400ForShortPassword() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("valid@example.com")
                    .password("short")
                    .name("Test User")
                    .build();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            String requestJson = "{}";

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 409 for duplicate email")
        void shouldReturn409ForDuplicateEmail() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("existing@example.com")
                    .password("SecurePass123!")
                    .name("Existing User")
                    .build();

            when(authService.register(any(CreateUserRequest.class)))
                    .thenThrow(new UserAlreadyExistsException("email", "existing@example.com"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("should login successfully and return 200")
        void shouldLoginSuccessfully() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("user@example.com")
                    .password("SecurePass123!")
                    .build();

            when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                    .andExpect(jsonPath("$.message").value("Login successful"));
        }

        @Test
        @DisplayName("should return 401 for bad credentials")
        void shouldReturn401ForBadCredentials() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("user@example.com")
                    .password("wrongpassword1")
                    .build();

            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new AuthenticationFailedException("Invalid email or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should return 400 for missing email")
        void shouldReturn400ForMissingEmail() throws Exception {
            String requestJson = "{\"password\": \"SecurePass123!\"}";

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshToken {

        @Test
        @DisplayName("should refresh token successfully")
        void shouldRefreshSuccessfully() throws Exception {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
                    .andExpect(jsonPath("$.message").value("Token refreshed successfully"));
        }

        @Test
        @DisplayName("should return 400 for missing refresh token")
        void shouldReturn400ForMissingToken() throws Exception {
            String requestJson = "{}";

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }
}