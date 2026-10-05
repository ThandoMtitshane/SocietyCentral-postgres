package com.societycentral.repository;

import com.societycentral.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenAndUsedFalse(String token);

    /** Clean up old tokens for a user before issuing a new one */
    void deleteByEmail(String email);

    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.email = :newEmail where t.email = :oldEmail")
    int reassignEmail(@Param("oldEmail") String oldEmail,
                      @Param("newEmail") String newEmail);
}
