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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrencyMismatchValidator")
class CurrencyMismatchValidatorTest {

    private CurrencyMismatchValidator validator;
    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        validator = new CurrencyMismatchValidator();

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

    private Account buildAccount(User user, String accountNumber, String currency) {
        Account account = Account.builder()
                .user(user)
                .accountNumber(accountNumber)
                .balance(new BigDecimal("5000.00"))
                .currency(currency)
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        account.setId(UUID.randomUUID());
        return account;
    }

    private PaymentValidationContext buildContext(String requestCurrency, Account senderAccount, Account receiverAccount) {
        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(receiverAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency(requestCurrency)
                .type(TransactionType.TRANSFER)
                .build();

        return PaymentValidationContext.builder()
                .request(request)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .build();
    }

    @Test
    @DisplayName("should pass when all currencies match")
    void shouldPassWhenAllCurrenciesMatch() {
        Account senderAccount = buildAccount(sender, "1111111111", "USD");
        Account receiverAccount = buildAccount(receiver, "2222222222", "USD");

        ValidationResult result = validator.validate(buildContext("USD", senderAccount, receiverAccount));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should pass with case-insensitive currency matching")
    void shouldPassWithCaseInsensitiveMatching() {
        Account senderAccount = buildAccount(sender, "1111111111", "USD");
        Account receiverAccount = buildAccount(receiver, "2222222222", "USD");

        ValidationResult result = validator.validate(buildContext("usd", senderAccount, receiverAccount));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail when sender currency mismatches")
    void shouldFailWhenSenderCurrencyMismatches() {
        Account senderAccount = buildAccount(sender, "1111111111", "EUR");
        Account receiverAccount = buildAccount(receiver, "2222222222", "USD");

        ValidationResult result = validator.validate(buildContext("USD", senderAccount, receiverAccount));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("SENDER_CURRENCY_MISMATCH");
    }

    @Test
    @DisplayName("should fail when receiver currency mismatches")
    void shouldFailWhenReceiverCurrencyMismatches() {
        Account senderAccount = buildAccount(sender, "1111111111", "USD");
        Account receiverAccount = buildAccount(receiver, "2222222222", "EUR");

        ValidationResult result = validator.validate(buildContext("USD", senderAccount, receiverAccount));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("RECEIVER_CURRENCY_MISMATCH");
    }

    @Test
    @DisplayName("should fail when request currency differs from both accounts")
    void shouldFailWhenRequestCurrencyDiffersFromBothAccounts() {
        Account senderAccount = buildAccount(sender, "1111111111", "EUR");
        Account receiverAccount = buildAccount(receiver, "2222222222", "GBP");

        ValidationResult result = validator.validate(buildContext("USD", senderAccount, receiverAccount));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("SENDER_CURRENCY_MISMATCH");
    }

    @Test
    @DisplayName("should have correct order")
    void shouldHaveCorrectOrder() {
        assertThat(validator.getOrder()).isEqualTo(15);
    }
}
