package com.flowpay.transaction.validation;

import com.flowpay.common.exception.PaymentException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.repository.AccountRepository;
import com.flowpay.transaction.validation.PaymentValidator.PaymentValidationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentValidationService {

    private final List<PaymentValidator> validators;
    private final AccountRepository accountRepository;

    public PaymentValidationService(List<PaymentValidator> validators, AccountRepository accountRepository) {
        this.validators = validators.stream()
                .sorted(Comparator.comparingInt(PaymentValidator::getOrder))
                .toList();
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(InitiateTransactionRequest request) {
        Account senderAccount = findAccount(request.getSenderAccountId());
        Account receiverAccount = findAccount(request.getReceiverAccountId());

        return validate(request, senderAccount, receiverAccount);
    }

    public ValidationResult validate(InitiateTransactionRequest request, Account senderAccount, Account receiverAccount) {
        PaymentValidationContext context = PaymentValidationContext.builder()
                .request(request)
                .senderAccount(senderAccount)
                .receiverAccount(receiverAccount)
                .build();

        log.debug("Running {} payment validators for transaction from account {} to account {}",
                validators.size(), senderAccount.getId(), receiverAccount.getId());

        List<ValidationResult> results = validators.stream()
                .map(validator -> {
                    ValidationResult result = validator.validate(context);
                    if (!result.isValid()) {
                        log.warn("Validation failed in {}: {}", validator.getClass().getSimpleName(),
                                result.getErrors().stream()
                                        .map(ValidationResult.ValidationError::getMessage)
                                        .collect(Collectors.joining(", ")));
                    }
                    return result;
                })
                .toList();

        ValidationResult merged = ValidationResult.merge(results);

        if (merged.isValid()) {
            log.debug("All payment validations passed");
        } else {
            log.info("Payment validation failed with {} error(s)", merged.getErrors().size());
        }

        return merged;
    }

    public void validateAndThrow(InitiateTransactionRequest request) {
        ValidationResult result = validate(request);
        if (!result.isValid()) {
            String errorMessages = result.getErrors().stream()
                    .map(ValidationResult.ValidationError::getMessage)
                    .collect(Collectors.joining("; "));
            String errorCode = result.getErrors().get(0).getCode();
            throw new PaymentException(errorMessages, errorCode);
        }
    }

    public void validateAndThrow(InitiateTransactionRequest request, Account senderAccount, Account receiverAccount) {
        ValidationResult result = validate(request, senderAccount, receiverAccount);
        if (!result.isValid()) {
            String errorMessages = result.getErrors().stream()
                    .map(ValidationResult.ValidationError::getMessage)
                    .collect(Collectors.joining("; "));
            String errorCode = result.getErrors().get(0).getCode();
            throw new PaymentException(errorMessages, errorCode);
        }
    }

    private Account findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }
}
