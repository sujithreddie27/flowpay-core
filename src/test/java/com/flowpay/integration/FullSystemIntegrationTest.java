package com.flowpay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.auth.dto.*;
import com.flowpay.auth.entity.User;
import com.flowpay.auth.repository.UserRepository;
import com.flowpay.common.dto.ApiResponse;
import com.flowpay.common.enums.*;
import com.flowpay.transaction.dto.*;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.service.AccountService;
import com.flowpay.transaction.service.TransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full system integration test that verifies all modules working together:
 * Auth → Account → Transaction → Health Checks.
 *
 * Tests the complete user journey from registration to payment processing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullSystemIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("flowpay_full_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("flowpay.alerting.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    private String senderAccessToken;
    private String receiverAccessToken;
    private UUID senderUserId;
    private UUID receiverUserId;
    private UUID senderAccountId;
    private UUID receiverAccountId;

    @BeforeAll
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("1. Authentication Module Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AuthModuleIntegration {

        @Test
        @Order(1)
        @DisplayName("should register sender user via REST API")
        void shouldRegisterSenderUser() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("sender-system@flowpay.com")
                    .password("SecurePass123!")
                    .firstName("System")
                    .lastName("Sender")
                    .phone("+1234567001")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.user.email").value("sender-system@flowpay.com"))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            var response = objectMapper.readTree(responseBody);
            senderAccessToken = response.get("data").get("accessToken").asText();
            senderUserId = UUID.fromString(response.get("data").get("user").get("id").asText());

            assertThat(senderAccessToken).isNotBlank();
            assertThat(senderUserId).isNotNull();
        }

        @Test
        @Order(2)
        @DisplayName("should register receiver user via REST API")
        void shouldRegisterReceiverUser() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("receiver-system@flowpay.com")
                    .password("SecurePass123!")
                    .firstName("System")
                    .lastName("Receiver")
                    .phone("+1234567002")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            var response = objectMapper.readTree(responseBody);
            receiverAccessToken = response.get("data").get("accessToken").asText();
            receiverUserId = UUID.fromString(response.get("data").get("user").get("id").asText());
        }

        @Test
        @Order(3)
        @DisplayName("should reject duplicate registration")
        void shouldRejectDuplicateRegistration() throws Exception {
            CreateUserRequest request = CreateUserRequest.builder()
                    .email("sender-system@flowpay.com")
                    .password("AnotherPass123!")
                    .firstName("Duplicate")
                    .lastName("User")
                    .phone("+1234567099")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @Order(4)
        @DisplayName("should login with valid credentials")
        void shouldLoginWithValidCredentials() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("sender-system@flowpay.com")
                    .password("SecurePass123!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        }

        @Test
        @Order(5)
        @DisplayName("should reject login with invalid password")
        void shouldRejectInvalidPassword() throws Exception {
            LoginRequest request = LoginRequest.builder()
                    .email("sender-system@flowpay.com")
                    .password("WrongPassword!")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(6)
        @DisplayName("should refresh access token")
        void shouldRefreshAccessToken() throws Exception {
            // First login to get a refresh token
            LoginRequest loginRequest = LoginRequest.builder()
                    .email("sender-system@flowpay.com")
                    .password("SecurePass123!")
                    .build();

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String loginBody = loginResult.getResponse().getContentAsString();
            String refreshToken = objectMapper.readTree(loginBody).get("data").get("refreshToken").asText();

            RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                    .refreshToken(refreshToken)
                    .build();

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("2. Account Module Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class AccountModuleIntegration {

        @Test
        @Order(1)
        @DisplayName("should create sender account with authenticated user")
        void shouldCreateSenderAccount() throws Exception {
            // Ensure users exist
            if (senderUserId == null) {
                User sender = userRepository.save(User.builder()
                        .email("sender-acct@flowpay.com")
                        .passwordHash("$2a$12$hashedpassword")
                        .firstName("Sender")
                        .lastName("Account")
                        .phone("+1234567011")
                        .status(UserStatus.ACTIVE)
                        .kycStatus(KycStatus.VERIFIED)
                        .role(UserRole.USER)
                        .emailVerified(true)
                        .phoneVerified(true)
                        .build());
                senderUserId = sender.getId();
            }

            // Create account directly via service (no auth required for service layer test)
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .userId(senderUserId)
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .dailyLimit(new BigDecimal("50000.00"))
                    .build();

            AccountResponse response = accountService.createAccount(request);
            senderAccountId = response.getId();

            assertThat(response).isNotNull();
            assertThat(response.getAccountNumber()).isNotBlank();
            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getCurrency()).isEqualTo("USD");
            assertThat(response.getAccountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @Order(2)
        @DisplayName("should create receiver account")
        void shouldCreateReceiverAccount() throws Exception {
            if (receiverUserId == null) {
                User receiver = userRepository.save(User.builder()
                        .email("receiver-acct@flowpay.com")
                        .passwordHash("$2a$12$hashedpassword")
                        .firstName("Receiver")
                        .lastName("Account")
                        .phone("+1234567012")
                        .status(UserStatus.ACTIVE)
                        .kycStatus(KycStatus.VERIFIED)
                        .role(UserRole.USER)
                        .emailVerified(true)
                        .phoneVerified(true)
                        .build());
                receiverUserId = receiver.getId();
            }

            CreateAccountRequest request = CreateAccountRequest.builder()
                    .userId(receiverUserId)
                    .currency("USD")
                    .accountType(AccountType.CURRENT)
                    .dailyLimit(new BigDecimal("100000.00"))
                    .build();

            AccountResponse response = accountService.createAccount(request);
            receiverAccountId = response.getId();

            assertThat(response.getAccountType()).isEqualTo(AccountType.CURRENT);
            assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @Order(3)
        @DisplayName("should credit sender account with initial balance")
        void shouldCreditSenderAccount() {
            AccountResponse response = accountService.creditAccount(senderAccountId, new BigDecimal("25000.00"));

            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("25000.0000"));
        }

        @Test
        @Order(4)
        @DisplayName("should get account by ID")
        void shouldGetAccountById() {
            AccountResponse response = accountService.getAccountById(senderAccountId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(senderAccountId);
            assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("25000.0000"));
        }

        @Test
        @Order(5)
        @DisplayName("should get accounts by user ID")
        void shouldGetAccountsByUserId() {
            var accounts = accountService.getAccountsByUserId(senderUserId);

            assertThat(accounts).isNotEmpty();
            assertThat(accounts).anyMatch(a -> a.getId().equals(senderAccountId));
        }
    }

    @Nested
    @DisplayName("3. Transaction Module Integration")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class TransactionModuleIntegration {

        @BeforeEach
        void ensureAccountsExist() {
            if (senderAccountId == null || receiverAccountId == null) {
                setUpAccountsDirectly();
            }
        }

        @Test
        @Order(1)
        @DisplayName("should initiate and complete payment successfully")
        void shouldInitiateAndCompletePayment() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("1000.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Full system test payment")
                    .idempotencyKey("full-system-" + UUID.randomUUID())
                    .build();

            TransactionResponse response = transactionService.initiatePayment(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            assertThat(response.getReferenceId()).isNotBlank();
            assertThat(response.getCurrency()).isEqualTo("USD");
            assertThat(response.getType()).isEqualTo(TransactionType.TRANSFER);

            // Verify balances updated
            AccountResponse sender = accountService.getAccountById(senderAccountId);
            AccountResponse receiver = accountService.getAccountById(receiverAccountId);
            assertThat(sender.getBalance()).isLessThan(new BigDecimal("25000.0000"));
            assertThat(receiver.getBalance()).isGreaterThan(BigDecimal.ZERO);
        }

        @Test
        @Order(2)
        @DisplayName("should get transaction by ID")
        void shouldGetTransactionById() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Lookup test")
                    .idempotencyKey("lookup-" + UUID.randomUUID())
                    .build();

            TransactionResponse created = transactionService.initiatePayment(request);
            TransactionResponse fetched = transactionService.getTransactionById(created.getId());

            assertThat(fetched.getId()).isEqualTo(created.getId());
            assertThat(fetched.getReferenceId()).isEqualTo(created.getReferenceId());
            assertThat(fetched.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @Order(3)
        @DisplayName("should get transaction by reference ID")
        void shouldGetTransactionByReferenceId() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("250.00"))
                    .currency("USD")
                    .type(TransactionType.PAYMENT)
                    .description("Reference lookup")
                    .idempotencyKey("ref-lookup-" + UUID.randomUUID())
                    .build();

            TransactionResponse created = transactionService.initiatePayment(request);
            TransactionResponse fetched = transactionService.getTransactionByReferenceId(created.getReferenceId());

            assertThat(fetched.getId()).isEqualTo(created.getId());
        }

        @Test
        @Order(4)
        @DisplayName("should enforce idempotency on duplicate requests")
        void shouldEnforceIdempotency() {
            String idempotencyKey = "idempotent-full-" + UUID.randomUUID();

            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Idempotency check")
                    .idempotencyKey(idempotencyKey)
                    .build();

            TransactionResponse first = transactionService.initiatePayment(request);
            TransactionResponse second = transactionService.initiatePayment(request);

            assertThat(first.getId()).isEqualTo(second.getId());
            assertThat(first.getReferenceId()).isEqualTo(second.getReferenceId());
        }

        @Test
        @Order(5)
        @DisplayName("should fail transaction with insufficient funds")
        void shouldFailWithInsufficientFunds() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("999999.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Should fail - insufficient funds")
                    .idempotencyKey("insuf-" + UUID.randomUUID())
                    .build();

            Assertions.assertThrows(Exception.class, () -> transactionService.initiatePayment(request));
        }

        @Test
        @Order(6)
        @DisplayName("should fail with currency mismatch")
        void shouldFailWithCurrencyMismatch() {
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAccountId)
                    .receiverAccountId(receiverAccountId)
                    .amount(new BigDecimal("100.00"))
                    .currency("EUR")
                    .type(TransactionType.TRANSFER)
                    .description("Currency mismatch test")
                    .idempotencyKey("currency-" + UUID.randomUUID())
                    .build();

            Assertions.assertThrows(Exception.class, () -> transactionService.initiatePayment(request));
        }

        @Test
        @Order(7)
        @DisplayName("should get transaction history for user")
        void shouldGetTransactionHistory() {
            TransactionFilterRequest filter = TransactionFilterRequest.builder()
                    .page(0)
                    .size(10)
                    .build();

            var transactions = transactionService.getTransactionsByUserId(senderUserId, filter);

            assertThat(transactions).isNotNull();
            assertThat(transactions.getContent()).isNotEmpty();
        }

        private void setUpAccountsDirectly() {
            User sender = userRepository.save(User.builder()
                    .email("sender-tx-" + UUID.randomUUID().toString().substring(0, 8) + "@flowpay.com")
                    .passwordHash("$2a$12$hashedpassword")
                    .firstName("Tx")
                    .lastName("Sender")
                    .phone("+1" + System.nanoTime() % 1000000000)
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .role(UserRole.USER)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build());
            senderUserId = sender.getId();

            User receiver = userRepository.save(User.builder()
                    .email("receiver-tx-" + UUID.randomUUID().toString().substring(0, 8) + "@flowpay.com")
                    .passwordHash("$2a$12$hashedpassword")
                    .firstName("Tx")
                    .lastName("Receiver")
                    .phone("+2" + System.nanoTime() % 1000000000)
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .role(UserRole.USER)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build());
            receiverUserId = receiver.getId();

            Account senderAcct = accountRepository.save(Account.builder()
                    .user(sender)
                    .accountNumber("SYS-SEND-" + UUID.randomUUID().toString().substring(0, 8))
                    .balance(new BigDecimal("25000.0000"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .dailyLimit(new BigDecimal("50000.0000"))
                    .build());
            senderAccountId = senderAcct.getId();

            Account receiverAcct = accountRepository.save(Account.builder()
                    .user(receiver)
                    .accountNumber("SYS-RECV-" + UUID.randomUUID().toString().substring(0, 8))
                    .balance(new BigDecimal("5000.0000"))
                    .currency("USD")
                    .accountType(AccountType.CURRENT)
                    .status(AccountStatus.ACTIVE)
                    .dailyLimit(new BigDecimal("100000.0000"))
                    .build());
            receiverAccountId = receiverAcct.getId();
        }
    }

    @Nested
    @DisplayName("4. Health Check & Monitoring Integration")
    class HealthCheckIntegration {

        @Test
        @DisplayName("should return healthy status from actuator")
        void shouldReturnHealthyStatus() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("should return liveness probe status")
        void shouldReturnLivenessProbe() throws Exception {
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("should return readiness probe status")
        void shouldReturnReadinessProbe() throws Exception {
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("should return application info")
        void shouldReturnApplicationInfo() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("5. API Security Integration")
    class SecurityIntegration {

        @Test
        @DisplayName("should reject unauthenticated requests to protected endpoints")
        void shouldRejectUnauthenticatedRequests() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject requests with invalid JWT")
        void shouldRejectInvalidJwt() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/" + UUID.randomUUID())
                            .header("Authorization", "Bearer invalid.jwt.token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should allow public endpoints without authentication")
        void shouldAllowPublicEndpoints() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/actuator/info"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("should reject malformed registration request")
        void shouldRejectMalformedRegistration() throws Exception {
            CreateUserRequest badRequest = CreateUserRequest.builder()
                    .email("not-an-email")
                    .password("short")
                    .firstName("")
                    .lastName("")
                    .build();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("6. Cross-Module Data Integrity")
    class DataIntegrity {

        @Test
        @DisplayName("should maintain referential integrity across modules")
        void shouldMaintainReferentialIntegrity() {
            // Create user
            User user = userRepository.save(User.builder()
                    .email("integrity-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                    .passwordHash("$2a$12$hash")
                    .firstName("Integrity")
                    .lastName("Test")
                    .phone("+3" + System.nanoTime() % 1000000000)
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .role(UserRole.USER)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build());

            // Create account linked to user
            Account account = accountRepository.save(Account.builder()
                    .user(user)
                    .accountNumber("INT-" + UUID.randomUUID().toString().substring(0, 8))
                    .balance(new BigDecimal("1000.0000"))
                    .currency("USD")
                    .accountType(AccountType.WALLET)
                    .status(AccountStatus.ACTIVE)
                    .dailyLimit(new BigDecimal("10000.0000"))
                    .build());

            // Verify referential link
            Account fetched = accountRepository.findById(account.getId()).orElseThrow();
            assertThat(fetched.getUser().getId()).isEqualTo(user.getId());

            // Verify user can be found via account
            var userAccounts = accountRepository.findByUserId(user.getId());
            assertThat(userAccounts).isNotEmpty();
            assertThat(userAccounts.get(0).getId()).isEqualTo(account.getId());
        }

        @Test
        @DisplayName("should record transactions with correct entity references")
        void shouldRecordTransactionsWithCorrectReferences() {
            // Setup fresh users and accounts
            User sender = userRepository.save(User.builder()
                    .email("ref-sender-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                    .passwordHash("$2a$12$hash")
                    .firstName("Ref")
                    .lastName("Sender")
                    .phone("+4" + System.nanoTime() % 1000000000)
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .role(UserRole.USER)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build());

            User receiver = userRepository.save(User.builder()
                    .email("ref-receiver-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                    .passwordHash("$2a$12$hash")
                    .firstName("Ref")
                    .lastName("Receiver")
                    .phone("+5" + System.nanoTime() % 1000000000)
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .role(UserRole.USER)
                    .emailVerified(true)
                    .phoneVerified(true)
                    .build());

            Account senderAcct = accountRepository.save(Account.builder()
                    .user(sender)
                    .accountNumber("REF-S-" + UUID.randomUUID().toString().substring(0, 8))
                    .balance(new BigDecimal("5000.0000"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .dailyLimit(new BigDecimal("50000.0000"))
                    .build());

            Account receiverAcct = accountRepository.save(Account.builder()
                    .user(receiver)
                    .accountNumber("REF-R-" + UUID.randomUUID().toString().substring(0, 8))
                    .balance(new BigDecimal("1000.0000"))
                    .currency("USD")
                    .accountType(AccountType.SAVINGS)
                    .status(AccountStatus.ACTIVE)
                    .dailyLimit(new BigDecimal("50000.0000"))
                    .build());

            // Execute transaction
            InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                    .senderAccountId(senderAcct.getId())
                    .receiverAccountId(receiverAcct.getId())
                    .amount(new BigDecimal("200.00"))
                    .currency("USD")
                    .type(TransactionType.TRANSFER)
                    .description("Referential integrity test")
                    .idempotencyKey("ref-integrity-" + UUID.randomUUID())
                    .build();

            TransactionResponse txResponse = transactionService.initiatePayment(request);

            // Verify transaction references
            Transaction tx = transactionRepository.findById(txResponse.getId()).orElseThrow();
            assertThat(tx.getSender().getId()).isEqualTo(sender.getId());
            assertThat(tx.getReceiver().getId()).isEqualTo(receiver.getId());
            assertThat(tx.getSenderAccount().getId()).isEqualTo(senderAcct.getId());
            assertThat(tx.getReceiverAccount().getId()).isEqualTo(receiverAcct.getId());
        }
    }
}
