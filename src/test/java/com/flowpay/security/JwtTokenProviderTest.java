package com.flowpay.security;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserRole;
import com.flowpay.common.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private CustomUserDetails userDetails;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@flowpay.com")
                .passwordHash("hashedpassword")
                .firstName("Test")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .build();
        testUser.setId(UUID.randomUUID());
        userDetails = new CustomUserDetails(testUser);
    }

    @Nested
    @DisplayName("Generate Access Token")
    class GenerateAccessToken {

        @Test
        @DisplayName("should generate valid access token")
        void shouldGenerateValidAccessToken() {
            String token = jwtTokenProvider.generateAccessToken(userDetails);

            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("should contain correct user ID in token")
        void shouldContainCorrectUserId() {
            String token = jwtTokenProvider.generateAccessToken(userDetails);

            UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
            assertThat(extractedUserId).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("should contain correct email in token")
        void shouldContainCorrectEmail() {
            String token = jwtTokenProvider.generateAccessToken(userDetails);

            String extractedEmail = jwtTokenProvider.getEmailFromToken(token);
            assertThat(extractedEmail).isEqualTo(testUser.getEmail());
        }

        @Test
        @DisplayName("access token should not be a refresh token")
        void accessTokenShouldNotBeRefreshToken() {
            String token = jwtTokenProvider.generateAccessToken(userDetails);

            assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("Generate Refresh Token")
    class GenerateRefreshToken {

        @Test
        @DisplayName("should generate valid refresh token")
        void shouldGenerateValidRefreshToken() {
            String token = jwtTokenProvider.generateRefreshToken(userDetails);

            assertThat(token).isNotNull().isNotBlank();
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("refresh token should be identified as refresh type")
        void shouldBeIdentifiedAsRefreshToken() {
            String token = jwtTokenProvider.generateRefreshToken(userDetails);

            assertThat(jwtTokenProvider.isRefreshToken(token)).isTrue();
        }

        @Test
        @DisplayName("should contain correct user ID")
        void shouldContainCorrectUserId() {
            String token = jwtTokenProvider.generateRefreshToken(userDetails);

            UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
            assertThat(extractedUserId).isEqualTo(testUser.getId());
        }
    }

    @Nested
    @DisplayName("Validate Token")
    class ValidateToken {

        @Test
        @DisplayName("should return false for malformed token")
        void shouldReturnFalseForMalformedToken() {
            assertThat(jwtTokenProvider.validateToken("invalid.token.here")).isFalse();
        }

        @Test
        @DisplayName("should return false for empty token")
        void shouldReturnFalseForEmptyToken() {
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("should return false for null-like token")
        void shouldReturnFalseForNullLikeToken() {
            assertThat(jwtTokenProvider.validateToken("null")).isFalse();
        }

        @Test
        @DisplayName("should return true for valid token")
        void shouldReturnTrueForValidToken() {
            String token = jwtTokenProvider.generateAccessToken(userDetails);
            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }
    }
}
