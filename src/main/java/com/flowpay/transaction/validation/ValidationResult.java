package com.flowpay.transaction.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ValidationResult {

    private final boolean valid;
    private final List<ValidationError> errors;

    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    public static ValidationResult failure(String code, String message) {
        List<ValidationError> errors = new ArrayList<>();
        errors.add(new ValidationError(code, message));
        return new ValidationResult(false, Collections.unmodifiableList(errors));
    }

    public static ValidationResult failure(List<ValidationError> errors) {
        return new ValidationResult(false, Collections.unmodifiableList(errors));
    }

    public static ValidationResult merge(List<ValidationResult> results) {
        List<ValidationError> allErrors = new ArrayList<>();
        for (ValidationResult result : results) {
            if (!result.isValid()) {
                allErrors.addAll(result.getErrors());
            }
        }
        if (allErrors.isEmpty()) {
            return success();
        }
        return failure(allErrors);
    }

    @Getter
    @AllArgsConstructor
    public static class ValidationError {
        private final String code;
        private final String message;
    }
}
