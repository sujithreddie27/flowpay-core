package com.flowpay.transaction.validation;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.repository.TransactionRepository;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLimitValidator")
class DailyLimitValidatorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private DailyLimitValidator validator;

    private Account senderAccount;
    private Account receiverAccount;
    private UUID userId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validator, "dailyLimitMultiplier", 1.0);

        userId = UUID.randomUUID();

        User sender = User.builder()
                .email("sender@test.com")
                .firstName("Sender")
                .lastName("User")
                .passwordHash("hash")
                .build();
        sender.setId(userId);

        User receiver = User.builder()
                .email("receiver@test.com")
                .firstName("Receiver")
                .lastName("User")
                .passwordHash("hash")
                .build();
        receiver.setId(UUID.randomUUID());

        senderAccount = Account.builder()
                .user(sender)
                .accountNumber("1111111111")
                .balance(new BigDecimal("50000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        senderAccount.setId(UUID.randomUUID());

        receiverAccount = Account.builder()
                .user(receiver)
                .accountNumber("2222222222")
                .balance(new BigDecimal("3000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        receiverAccount.setId(UUID.randomUUID());
    }

    private PaymentValidationContext buildContext(BigDecimal amount) {
        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(receiverAccount.getId())
                .amount(amount)
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .build();

        return PaymentValidationContext.builder()
                .request(request)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .build();
    }

    @Test
    @DisplayName("should pass when under daily limit")
    void shouldPassWhenUnderDailyLimit() {
        when(transactionRepository.getTotalAmountBySenderIdAndDateRange(
                eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("2000.00"));

        ValidationResult result = validator.validate(buildContext(new BigDecimal("5000.00")));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should pass when exactly at daily limit")
    void shouldPassWhenExactlyAtDailyLimit() {
        when(transactionRepository.getTotalAmountBySenderIdAndDateRange(
                eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("5000.00"));

        ValidationResult result = validator.validate(buildContext(new BigDecimal("5000.00")));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail when exceeding daily limit")
    void shouldFailWhenExceedingDailyLimit() {
        when(transactionRepository.getTotalAmountBySenderIdAndDateRange(
                eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("8000.00"));

        ValidationResult result = validator.validate(buildContext(new BigDecimal("3000.00")));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("should fail when already at daily limit")
    void shouldFailWhenAlreadyAtDailyLimit() {
        when(transactionRepository.getTotalAmountBySenderIdAndDateRange(
                eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new BigDecimal("10000.00"));

        ValidationResult result = validator.validate(buildContext(new BigDecimal("1.00")));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("should pass when no transactions today")
    void shouldPassWhenNoTransactionsToday() {
        when(transactionRepository.getTotalAmountBySenderIdAndDateRange(
                eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(BigDecimal.ZERO);

        ValidationResult result = validator.validate(buildContext(new BigDecimal("9999.99")));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should have correct order")
    void shouldHaveCorrectOrder() {
        assertThat(validator.getOrder()).isEqualTo(20);
    }
}
