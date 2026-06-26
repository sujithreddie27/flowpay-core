package com.flowpay.common.ratelimit;

import com.flowpay.common.exception.RateLimitExceededException;
import com.flowpay.config.RateLimiterProperties;
import com.flowpay.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flowpay.rate-limiter.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAspect {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties rateLimiterProperties;

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        if (!rateLimiterProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        String key = resolveKey(rateLimited, joinPoint);
        int limit = rateLimited.limit() > 0 ? rateLimited.limit() : rateLimiterProperties.getDefaultLimit();
        int windowSeconds = rateLimited.windowSeconds() > 0 ? rateLimited.windowSeconds() : rateLimiterProperties.getDefaultWindowSeconds();

        if (!rateLimiterService.isAllowed(key, limit, windowSeconds)) {
            throw new RateLimitExceededException(key);
        }

        return joinPoint.proceed();
    }

    private String resolveKey(RateLimited rateLimited, ProceedingJoinPoint joinPoint) {
        if (!rateLimited.key().isEmpty()) {
            return rateLimited.key();
        }

        String methodName = joinPoint.getSignature().toShortString();
        String identifier = resolveUserIdentifier();
        return identifier + ":" + methodName;
    }

    private String resolveUserIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return "user:" + userDetails.getUserId().toString();
        }
        return "ip:" + getClientIp();
    }

    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
