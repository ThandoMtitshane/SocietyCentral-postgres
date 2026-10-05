package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A society's banking details, used for fund payouts in B300.
 *
 * Relationship:
 *   Society 1 ──── 0..1 BankAccount
 *
 * Enforced at the database level by:
 *   - FK_BankAccount_Society        societyID references Society
 *   - UQ_BankAccount_Society        UNIQUE(societyID)
 *
 * The Society entity is not aware of this class. Navigate from
 * BankAccount → Society (many-to-one) when the owning society is
 * needed; do not add a reverse field to Society.
 *
 * Only an Executive may create or edit these details. The SDO may
 * view them and update verificationStatus during B300.
 */
@Getter
@Setter
@Entity
@Table(name = "BankAccount")
public class BankAccount {

    // ── Identity ────────────────────────────────────────────────
    @Id
    @Column(name = "bankAccountID", length = 36)
    private String bankAccountID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "societyID",
            referencedColumnName = "societyID",
            insertable = false,
            updatable = false
    )
    private Society society;

    // ── Banking details ─────────────────────────────────────────
    @Column(name = "bankName", length = 100, nullable = false)
    private String bankName;

    @Column(name = "accountHolderName", length = 150, nullable = false)
    private String accountHolderName;

    @Column(name = "accountNumber", length = 30, nullable = false)
    private String accountNumber;

    @Column(name = "branchCode", length = 20, nullable = false)
    private String branchCode;

    @Column(name = "accountType", length = 30, nullable = false)
    private String accountType; // CHEQUE, SAVINGS, TRANSMISSION

    // ── Verification ────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(
            name = "verificationStatus",
            length = 20,
            nullable = false
    )
    private BankAccountVerificationStatus verificationStatus =
            BankAccountVerificationStatus.UNVERIFIED;

    @Column(name = "verifiedBy", length = 20)
    private String verifiedBy;

    @Column(name = "verifiedAt")
    private LocalDateTime verifiedAt;

    // ── Audit ───────────────────────────────────────────────────
    @Column(name = "lastUpdatedBy", length = 100)
    private String lastUpdatedBy;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "proofOfAccountDocument", columnDefinition = "bytea")
    private byte[] proofOfAccountDocument;
}