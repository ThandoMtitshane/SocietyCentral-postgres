package com.societycentral.repository;

import com.societycentral.model.BankAccount;
import com.societycentral.model.BankAccountVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for society bank accounts.
 *
 * The UNIQUE(societyID) constraint guarantees at most one row
 * per society, so findBySocietyID returns an Optional.
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, String> {

    /** The bank account (if any) belonging to a society. */
    Optional<BankAccount> findBySocietyID(String societyID);

    /** Whether a society has bank details on file. */
    boolean existsBySocietyID(String societyID);

    /**
     * All bank accounts in a given verification state.
     * Used by the SDO's verification queue (B300 sidebar).
     */
    List<BankAccount> findByVerificationStatus(BankAccountVerificationStatus status);
}
