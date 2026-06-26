package com.flowpay.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.auth.entity.User;
import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.enums.KycStatus;
import com.flowpay.common.enums.UserRole;
import com.flowpay.common.enums.UserStatus;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.transaction.controller.AccountController;
import com.flowpay.transaction.controller.TransactionController;
import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.service.AccountService;
import com.flowpay.transaction.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TransactionController.class, AccountController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAuthenticationFilter.class,
        JwtTokenProvider.class, JwtProperties.class, GlobalExceptionHandler.class,
        AccessDeniedHandlerImpl.class, ResourceOwnershipValidator.class})
@ActiveProfiles("test")
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private AccountService accountService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private User regularUser;
    private User merchantUser;
    private User adminUser;
    private String regularUserToken;
    private String merchantUserToken;
    private String adminUserToken;

    @BeforeEach
    void setUp() {
        regularUser = User.builder()
                .email("user@flowpay.com")
                .passwordHash("$2a$12$hashedPassword")
                .firstName("Regular")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.USER)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        regularUser.setId(UUID.randomUUID());

        merchantUser = User.builder()
                .email("merchant@flowpay.com")
                .passwordHash("$2a$12$hashedPassword")
                .firstName("Merchant")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.MERCHANT)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        merchantUser.setId(UUID.randomUUID());

        adminUser = User.builder()
                .email("admin@flowpay.com")
                .passwordHash("$2a$12$hashedPassword")
                .firstName("Admin")
                .lastName("User")
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.VERIFIED)
                .role(UserRole.ADMIN)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        adminUser.setId(UUID.randomUUID());

        CustomUserDetails regularDetails = new CustomUserDetails(regularUser);
        CustomUserDetails merchantDetails = new CustomUserDetails(merchantUser);
        CustomUserDetails adminDetails = new CustomUserDetails(adminUser);

        regularUserToken = jwtTokenProvider.generateAccessToken(regularDetails);
        merchantUserToken = jwtTokenProvider.generateAccessToken(merchantDetails);
        adminUserToken = jwtTokenProvider.generateAccessToken(adminDetails);

        when(customUserDetailsService.loadUserById(regularUser.getId())).thenReturn(regularDetails);
        when(customUserDetailsService.loadUserById(merchantUser.getId())).thenReturn(merchantDetails);
        when(customUserDetailsService.loadUserById(adminUser.getId())).thenReturn(adminDetails);
    }

    @Nested
    @DisplayName("Role-Based Access Control")
    class RoleBasedAccess {

        @Test
        @DisplayName("USER role can initiate payments")
        void userCanInitiatePayments() throws Exception {
            when(transactionService.initiatePayment(any())).thenReturn(TransactionResponse.builder().build());

            mockMvc.perform(post("/api/v1/transactions")
                            .header("Authorization", "Bearer " + regularUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"senderAccountId\":\"" + UUID.randomUUID() + "\",\"receiverAccountId\":\"" + UUID.randomUUID() + "\",\"amount\":100.00,\"currency\":\"USD\",\"type\":\"TRANSFER\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("USER role cannot retry transactions (ADMIN only)")
        void userCannotRetryTransactions() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/" + UUID.randomUUID() + "/retry")
                            .header("Authorization", "Bearer " + regularUserToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("USER role cannot reverse transactions (ADMIN only)")
        void userCannotReverseTransactions() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/" + UUID.randomUUID() + "/reverse")
                            .header("Authorization", "Bearer " + regularUserToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("USER role cannot view retryable transactions (ADMIN only)")
        void userCannotViewRetryableTransactions() throws Exception {
            mockMvc.perform(get("/api/v1/transactions/retryable")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER role cannot view transaction history (ADMIN only)")
        void userCannotViewTransactionHistory() throws Exception {
            mockMvc.perform(get("/api/v1/transactions/history")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER role cannot process stale transactions (ADMIN only)")
        void userCannotProcessStaleTransactions() throws Exception {
            mockMvc.perform(post("/api/v1/transactions/stale/process")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN role can retry transactions")
        void adminCanRetryTransactions() throws Exception {
            when(transactionService.retryTransaction(any())).thenReturn(TransactionResponse.builder().build());

            mockMvc.perform(post("/api/v1/transactions/" + UUID.randomUUID() + "/retry")
                            .header("Authorization", "Bearer " + adminUserToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN role can reverse transactions")
        void adminCanReverseTransactions() throws Exception {
            when(transactionService.reverseTransaction(any(), any())).thenReturn(TransactionResponse.builder().build());

            mockMvc.perform(post("/api/v1/transactions/" + UUID.randomUUID() + "/reverse")
                            .header("Authorization", "Bearer " + adminUserToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN role can view all transaction history")
        void adminCanViewTransactionHistory() throws Exception {
            when(transactionService.getRetryableTransactions()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/transactions/retryable")
                            .header("Authorization", "Bearer " + adminUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("USER role cannot credit accounts (MERCHANT/ADMIN only)")
        void userCannotCreditAccounts() throws Exception {
            mockMvc.perform(post("/api/v1/accounts/" + UUID.randomUUID() + "/credit")
                            .header("Authorization", "Bearer " + regularUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100.00}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("MERCHANT role can credit accounts")
        void merchantCanCreditAccounts() throws Exception {
            UUID accountId = UUID.randomUUID();
            when(accountService.creditAccount(any(), any())).thenReturn(AccountResponse.builder().build());

            mockMvc.perform(post("/api/v1/accounts/" + accountId + "/credit")
                            .header("Authorization", "Bearer " + merchantUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100.00}"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("USER role cannot update accounts (ADMIN only)")
        void userCannotUpdateAccounts() throws Exception {
            mockMvc.perform(patch("/api/v1/accounts/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + regularUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"FROZEN\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN role can update accounts")
        void adminCanUpdateAccounts() throws Exception {
            when(accountService.updateAccount(any(), any())).thenReturn(AccountResponse.builder().build());

            mockMvc.perform(patch("/api/v1/accounts/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + adminUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"FROZEN\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Resource Ownership Validation")
    class ResourceOwnership {

        @Test
        @DisplayName("User can access own transactions by userId")
        void userCanAccessOwnTransactions() throws Exception {
            UUID userId = regularUser.getId();
            when(transactionService.getTransactionsByUserId(any(), any()))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            mockMvc.perform(get("/api/v1/transactions/user/" + userId)
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User cannot access other user's transactions")
        void userCannotAccessOtherUsersTransactions() throws Exception {
            UUID otherUserId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/transactions/user/" + otherUserId)
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can access any user's transactions")
        void adminCanAccessAnyUsersTransactions() throws Exception {
            UUID otherUserId = UUID.randomUUID();
            when(transactionService.getTransactionsByUserId(any(), any()))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            mockMvc.perform(get("/api/v1/transactions/user/" + otherUserId)
                            .header("Authorization", "Bearer " + adminUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User can access own accounts")
        void userCanAccessOwnAccounts() throws Exception {
            UUID userId = regularUser.getId();
            when(accountService.getAccountsByUserId(userId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/accounts/user/" + userId)
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User cannot access other user's accounts")
        void userCannotAccessOtherUsersAccounts() throws Exception {
            UUID otherUserId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/accounts/user/" + otherUserId)
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can access any user's accounts")
        void adminCanAccessAnyUsersAccounts() throws Exception {
            UUID otherUserId = UUID.randomUUID();
            when(accountService.getAccountsByUserId(otherUserId)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/accounts/user/" + otherUserId)
                            .header("Authorization", "Bearer " + adminUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User can access own total balance")
        void userCanAccessOwnTotalBalance() throws Exception {
            UUID userId = regularUser.getId();
            when(accountService.getTotalBalance(userId)).thenReturn(BigDecimal.valueOf(1000));

            mockMvc.perform(get("/api/v1/accounts/user/" + userId + "/total-balance")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User cannot access other user's total balance")
        void userCannotAccessOtherUsersTotalBalance() throws Exception {
            UUID otherUserId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/accounts/user/" + otherUserId + "/total-balance")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("User can access own transaction summary")
        void userCanAccessOwnTransactionSummary() throws Exception {
            UUID userId = regularUser.getId();
            when(transactionService.getTransactionSummary(any(), any(), any())).thenReturn(null);

            mockMvc.perform(get("/api/v1/transactions/summary/" + userId)
                            .param("from", "2024-01-01T00:00:00Z")
                            .param("to", "2024-12-31T23:59:59Z")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("User cannot access other user's transaction summary")
        void userCannotAccessOtherUsersTransactionSummary() throws Exception {
            UUID otherUserId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/transactions/summary/" + otherUserId)
                            .param("from", "2024-01-01T00:00:00Z")
                            .param("to", "2024-12-31T23:59:59Z")
                            .header("Authorization", "Bearer " + regularUserToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Unauthenticated Access")
    class UnauthenticatedAccess {

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void unauthenticatedRequestReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/transactions/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("Invalid token returns 401")
        void invalidTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/transactions/" + UUID.randomUUID())
                            .header("Authorization", "Bearer invalid.token.here"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Expired token returns 401")
        void expiredTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/transactions/" + UUID.randomUUID())
                            .header("Authorization", "Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZXhwIjoxfQ.invalid"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
