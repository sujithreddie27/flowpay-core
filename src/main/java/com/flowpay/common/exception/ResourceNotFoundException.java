package com.flowpay.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String resourceType, UUID resourceId) {
        super(
                String.format("%s not found with id: %s", resourceType, resourceId),
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(
                String.format("%s not found with identifier: %s", resourceType, identifier),
                "RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND
        );
    }
}
