package com.societycentral.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Small helper for pulling info about the currently authenticated user
 * out of Spring Security's context, without every service needing to
 * know about SecurityContextHolder / UserPrincipal casting directly.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // static-only utility class - no instances
    }

    /**
     * Returns the email (username) of the currently authenticated user.
     *
     * @throws IllegalStateException if there is no authenticated user
     *                                in the current security context.
     */
    public static String getCurrentUserEmail() {
        return getCurrentUserPrincipal().getEmail();
    }

    /**
     * Returns the full UserPrincipal of the currently authenticated user,
     * in case a service needs more than just the email (e.g. authorities).
     *
     * @throws IllegalStateException if there is no authenticated user
     *                                in the current security context.
     */
    public static UserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in security context");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }

        throw new IllegalStateException(
                "Unexpected principal type: " + principal.getClass());
    }
}