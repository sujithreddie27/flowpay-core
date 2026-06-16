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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DuplicateTransactionValidator")
class DuplicateTransactionValidatorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private DuplicateTransactionValidator validator;

    private Account senderAccount;
    private Account receiverAccount;

    @BeforeEach
    void setUp() {
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

    private PaymentValidationContext buildContext(String idempotencyKey) {
        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(receiverAccount.getId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .idempotencyKey(idempotencyKey)
                .build();

        return PaymentValidationContext.builder()
                .request(request)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .build();
    }

    @Test
    @DisplayName("should pass when no idempotency key provided")
    void shouldPassWhenNoIdempotencyKey() {
        ValidationResult result = validator.validate(buildContext(null));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should pass when empty idempotency key provided")
    void shouldPassWhenEmptyIdempotencyKey() {
        ValidationResult result = validator.validate(buildContext(""));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should pass when idempotency key is unique")
    void shouldPassWhenIdempotencyKeyIsUnique() {
        String idempotencyKey = "unique-key-12345";
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);

        ValidationResult result = validator.validate(buildContext(idempotencyKey));
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("should fail when idempotency key already exists")
    void shouldFailWhenIdempotencyKeyExists() {
        String idempotencyKey = "duplicate-key-12345";
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true);

        ValidationResult result = validator.validate(buildContext(idempotencyKey));
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getCode()).isEqualTo("DUPLICATE_TRANSACTION");
    }

    @Test
    @DisplayName("should have correct order")
    void shouldHaveCorrectOrder() {
        assertThat(validator.getOrder()).isEqualTo(1);
    }
}
