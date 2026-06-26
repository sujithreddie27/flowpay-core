package com.flowpay.security;

import com.flowpay.common.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component("resourceOwnershipValidator")
public class ResourceOwnershipValidator {

    public boolean isOwnerOrAdmin(UUID resourceOwnerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            return false;
        }

        if (hasRole(userDetails, UserRole.ADMIN)) {
            return true;
        }

        boolean isOwner = userDetails.getUserId().equals(resourceOwnerId);
        if (!isOwner) {
            log.warn("Access denied: userId={} attempted to access resource owned by userId={}",
                    userDetails.getUserId(), resourceOwnerId);
        }
        return isOwner;
    }

    public boolean isOwner(UUID resourceOwnerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            return false;
        }

        return userDetails.getUserId().equals(resourceOwnerId);
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            return false;
        }

        return hasRole(userDetails, UserRole.ADMIN);
    }

    private boolean hasRole(CustomUserDetails userDetails, UserRole role) {
        return userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role.name()));
    }
}
