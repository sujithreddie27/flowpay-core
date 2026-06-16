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

@DisplayName("AccountStatusValidator")
class AccountStatusValidatorTest {

    private AccountStatusValidator validator;
    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        validator = new AccountStatusValidator();

        sender = User.builder()
                .email("sender@test.com")
                .firstName("Sender")
                .lastName("User")
                .passwordHash("hash")
                .build();
        sender.setId(UUID.randomUUID());

        receiver = User.builder()
                .email("receiver@test.com")
                .firstName("Receiver")
                .lastName("User")
                .passwordHash("hash")
                .build();
        receiver.setId(UUID.randomUUID());
    }

    private Account buildAccount(User user, String accountNumber, AccountStatus status) {
        Account account = Account.builder()
                .user(user)
                .accountNumber(accountNumber)
                .balance(new BigDecimal("5000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(status)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        account.setId(UUID.randomUUID());
        return account;
    }

    private PaymentValidationContext buildContext(Account senderAccount, Account receiverAccount) {
        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(receiverAccount.getId())
                .amount(new BigDecimal("100.00"))
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
    @DisplayName("Both accounts active")
    class BothAccountsActive {

        @Test
        @DisplayName("should pass when both accounts are active")
        void shouldPassWhenBothActive() {
            Account senderAccount = buildAccount(sender, "1111111111", AccountStatus.ACTIVE);
            Account receiverAccount = buildAccount(receiver, "2222222222", AccountStatus.ACTIVE);

            ValidationResult result = validator.validate(buildContext(senderAccount, receiverAccount));
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Sender account issues")
    class SenderAccountIssues {

        @Test
        @DisplayName("should fail when sender account is frozen")
        void shouldFailWhenSenderFrozen() {
            Account senderAccount = buildAccount(sender, "1111111111", AccountStatus.FROZEN);
            Account receiverAccount = buildAccount(receiver, "2222222222", AccountStatus.ACTIVE);

            ValidationResult result = validator.validate(buildContext(senderAccount, receiverAccount));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("SENDER_ACCOUNT_FROZEN");
        }

        @Test
        @DisplayName("should fail when sender account is closed")
        void shouldFailWhenSenderClosed() {
            Account senderAccount = buildAccount(sender, "1111111111", AccountStatus.CLOSED);
            Account receiverAccount = buildAccount(receiver, "2222222222", AccountStatus.ACTIVE);

            ValidationResult result = validator.validate(buildContext(senderAccount, receiverAccount));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("SENDER_ACCOUNT_CLOSED");
        }
    }

    @Nested
    @DisplayName("Receiver account issues")
    class ReceiverAccountIssues {

        @Test
        @DisplayName("should fail when receiver account is closed")
        void shouldFailWhenReceiverClosed() {
            Account senderAccount = buildAccount(sender, "1111111111", AccountStatus.ACTIVE);
            Account receiverAccount = buildAccount(receiver, "2222222222", AccountStatus.CLOSED);

            ValidationResult result = validator.validate(buildContext(senderAccount, receiverAccount));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("RECEIVER_ACCOUNT_CLOSED");
        }

        @Test
        @DisplayName("should fail when receiver account is frozen")
        void shouldFailWhenReceiverFrozen() {
            Account senderAccount = buildAccount(sender, "1111111111", AccountStatus.ACTIVE);
            Account receiverAccount = buildAccount(receiver, "2222222222", AccountStatus.FROZEN);

            ValidationResult result = validator.validate(buildContext(senderAccount, receiverAccount));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("RECEIVER_ACCOUNT_FROZEN");
        }
    }

    @Test
    @DisplayName("should have correct order")
    void shouldHaveCorrectOrder() {
        assertThat(validator.getOrder()).isEqualTo(5);
    }
}
