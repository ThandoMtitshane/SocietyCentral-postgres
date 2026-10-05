package com.societycentral.security;

import com.societycentral.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security's view of an authenticated user, backed by our
 * {@link User} entity.
 * <p>
 * The "username" in Spring Security terms is the user's email (our
 * natural primary key for User). The single authority granted is derived
 * from {@code User.userType} (e.g. "ROLE_STUDENT", "ROLE_SDO" or "ROLE_ADMIN").
 * <p>
 * NOTE: This only reflects {@code User.userType} (STUDENT / SDO / ADMIN). It does
 * NOT currently reflect whether a STUDENT is also a current Executive of
 * a society - that is a separate, more granular authorisation concern
 * (see ExecutiveService TODOs) and should be checked at the service layer
 * for endpoints that require "must be an executive of society X", not via
 * a blanket role on the principal.
 */
public class UserPrincipal implements UserDetails {

    private final String email;
    private final String passwordHash;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getUserType().name())
        );
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}