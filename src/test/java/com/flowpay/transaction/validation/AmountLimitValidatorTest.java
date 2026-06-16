package com.flowpay.transaction.validation;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AmountLimitValidator")
class AmountLimitValidatorTest {

    private AmountLimitValidator validator;
    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
        validator = new AmountLimitValidator(new BigDecimal("0.01"), new BigDecimal("1000000.00"));

        User sender = User.builder()
                .email("sender@test.com")
                .firstName("Sender")
                .lastName("User")
                .passwordHash("hash")
                .build();
        sender.setId(UUID.randomUUID());

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
                .balance(new BigDecimal("5000.00"))
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

    @Nested
    @DisplayName("Valid amounts")
    class ValidAmounts {

        @Test
        @DisplayName("should pass for minimum amount")
        void shouldPassForMinimumAmount() {
            ValidationResult result = validator.validate(buildContext(new BigDecimal("0.01")));
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("should pass for normal amount")
        void shouldPassForNormalAmount() {
            ValidationResult result = validator.validate(buildContext(new BigDecimal("500.00")));
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should pass for maximum amount")
        void shouldPassForMaximumAmount() {
            ValidationResult result = validator.validate(buildContext(new BigDecimal("1000000.00")));
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid amounts")
    class InvalidAmounts {

        @Test
        @DisplayName("should fail for amount below minimum")
        void shouldFailForAmountBelowMinimum() {
            ValidationResult result = validator.validate(buildContext(new BigDecimal("0.001")));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("AMOUNT_BELOW_MINIMUM");
        }

        @Test
        @DisplayName("should fail for amount above maximum")
        void shouldFailForAmountAboveMaximum() {
            ValidationResult result = validator.validate(buildContext(new BigDecimal("1000000.01")));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("AMOUNT_ABOVE_MAXIMUM");
        }
    }

    @Test
    @DisplayName("should have correct order")
    void shouldHaveCorrectOrder() {
        assertThat(validator.getOrder()).isEqualTo(10);
    }
}
