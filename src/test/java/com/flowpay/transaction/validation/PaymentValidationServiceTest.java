package com.flowpay.transaction.validation;

import com.flowpay.auth.entity.User;
import com.flowpay.common.enums.AccountStatus;
import com.flowpay.common.enums.AccountType;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.exception.PaymentException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentValidationService")
class PaymentValidationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private PaymentValidationService validationService;

    private Account senderAccount;
    private Account receiverAccount;
    private UUID senderAccountId;
    private UUID receiverAccountId;

    @BeforeEach
    void setUp() {
        senderAccountId = UUID.randomUUID();
        receiverAccountId = UUID.randomUUID();

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
        senderAccount.setId(senderAccountId);

        receiverAccount = Account.builder()
                .user(receiver)
                .accountNumber("2222222222")
                .balance(new BigDecimal("3000.00"))
                .currency("USD")
                .accountType(AccountType.SAVINGS)
                .status(AccountStatus.ACTIVE)
                .dailyLimit(new BigDecimal("10000.0000"))
                .build();
        receiverAccount.setId(receiverAccountId);
    }

    private InitiateTransactionRequest buildRequest() {
        return InitiateTransactionRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .build();
    }

    @Nested
    @DisplayName("validate with accounts")
    class ValidateWithAccounts {

        @Test
        @DisplayName("should pass when all validators pass")
        void shouldPassWhenAllValidatorsPass() {
            // Use only a simple always-pass validator
            PaymentValidator alwaysPass = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.success();
                }

                @Override
                public int getOrder() {
                    return 1;
                }
            };

            validationService = new PaymentValidationService(List.of(alwaysPass), accountRepository);

            ValidationResult result = validationService.validate(buildRequest(), senderAccount, receiverAccount);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should fail when any validator fails")
        void shouldFailWhenAnyValidatorFails() {
            PaymentValidator alwaysFail = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.failure("TEST_FAILURE", "Test validation failed");
                }

                @Override
                public int getOrder() {
                    return 1;
                }
            };

            validationService = new PaymentValidationService(List.of(alwaysFail), accountRepository);

            ValidationResult result = validationService.validate(buildRequest(), senderAccount, receiverAccount);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("TEST_FAILURE");
        }

        @Test
        @DisplayName("should aggregate errors from multiple validators")
        void shouldAggregateErrorsFromMultipleValidators() {
            PaymentValidator fail1 = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.failure("ERROR_1", "First error");
                }

                @Override
                public int getOrder() {
                    return 1;
                }
            };

            PaymentValidator fail2 = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.failure("ERROR_2", "Second error");
                }

                @Override
                public int getOrder() {
                    return 2;
                }
            };

            validationService = new PaymentValidationService(List.of(fail1, fail2), accountRepository);

            ValidationResult result = validationService.validate(buildRequest(), senderAccount, receiverAccount);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
        }

        @Test
        @DisplayName("should execute validators in order")
        void shouldExecuteValidatorsInOrder() {
            PaymentValidator high = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.failure("HIGH_PRIORITY", "High priority error");
                }

                @Override
                public int getOrder() {
                    return 1;
                }
            };

            PaymentValidator low = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.failure("LOW_PRIORITY", "Low priority error");
                }

                @Override
                public int getOrder() {
                    return 100;
                }
            };

            // Pass low first to verify ordering
            validationService = new PaymentValidationService(List.of(low, high), accountRepository);

            ValidationResult result = validationService.validate(buildRequest(), senderAccount, receiverAccount);
            assertThat(result.getErrors().get(0).getCode()).isEqualTo("HIGH_PRIORITY");
            assertThat(result.getErrors().get(1).getCode()).isEqualTo("LOW_PRIORITY");
        }
    }

    @Nested
    @DisplayName("validate with request only")
    class ValidateWithRequestOnly {

        @Test
        @DisplayName("should throw when sender account not found")
        void shouldThrowWhenSenderAccountNotFound() {
            validationService = new PaymentValidationService(List.of(), accountRepository);
            when(accountRepository.findById(senderAccountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validationService.validate(buildRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when receiver account not found")
        void shouldThrowWhenReceiverAccountNotFound() {
            validationService = new PaymentValidationService(List.of(), accountRepository);
            when(accountRepository.findById(senderAccountId)).thenReturn(Optional.of(senderAccount));
            when(accountRepository.findById(receiverAccountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> validationService.validate(buildRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("validateAndThrow")
    class ValidateAndThrow {

        @Test
        @DisplayName("should not throw when validation passes")
        void shouldNotThrowWhenValid() {
            PaymentValidator alwaysPass = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.success();
                }

                @Override
                public int getOrder() {
                    return 1;
                }
            };

            validationService = new PaymentValidationService(List.of(alwaysPass), accountRepository);
            validationService.validateAndThrow(buildRequest(), senderAccount, receiverAccount);
        }

        @Test
        @DisplayName("should throw PaymentException when validation fails")
        void shouldThrowPaymentExceptionWhenInvalid() {
            PaymentValidator alwaysFail = new PaymentValidator() {
                @Override
                public ValidationResult validate(PaymentValidator.PaymentValidationContext context) {
                    return ValidationResult.failure("VALIDATION_FAILED", "Something went wrong");
                }

                @Override
                public int getOrder() {
                    return 1;
                }
            };

            validationService = new PaymentValidationService(List.of(alwaysFail), accountRepository);

            assertThatThrownBy(() -> validationService.validateAndThrow(buildRequest(), senderAccount, receiverAccount))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Something went wrong");
        }
    }
}
