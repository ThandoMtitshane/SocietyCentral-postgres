package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores a one-time password reset token for a user.
 * Token is a UUID, expires after 1 hour.
 * Used only once,  deleted after successful password reset.
 */
@Entity
@Table(name = "PasswordResetToken")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @Column(name = "token", length = 36)
    private String token;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @Column(name = "expiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    public static PasswordResetToken create(String email) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(UUID.randomUUID().toString());
        t.setEmail(email);
        t.setExpiresAt(LocalDateTime.now().plusHours(1));
        t.setUsed(false);
        return t;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}