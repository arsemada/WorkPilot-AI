package com.workpilot.ai.security;

import com.workpilot.ai.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UUID currentUserId() {
        return currentUser().getId();
    }

    public static CustomUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new UnauthorizedException("Authentication required");
        }
        return details;
    }
}
