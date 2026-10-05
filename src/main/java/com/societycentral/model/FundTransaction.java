package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit trail of every movement of funds in/out of a society's
 * currentBalance. Think of it as a bank statement per society.
 *
 * Direction:
 *   CREDIT → currentBalance increases  (displayed as +R amount)
 *   DEBIT  → currentBalance decreases  (displayed as -R amount)
 *
 * balanceBefore and balanceAfter are snapshots at the time of the
 * transaction,  they never change even if currentBalance is later
 * adjusted, giving a true audit trail.
 */
@Entity
@Table(name = "FundTransaction")
@Getter
@Setter
@NoArgsConstructor
public class FundTransaction {

    @Id
    @Column(name = "transactionID", length = 20)
    private String transactionID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10, nullable = false)
    private FundTransactionDirection direction; // CREDIT or DEBIT

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 30, nullable = false)
    private FundTransactionReason reason;

    // Human-readable explanation of why this transaction happened
    @Column(name = "description", length = 500)
    private String description;

    // Contextual reference:
    // - MEMBERSHIP_FEE     → studentNumber of new member
    // - BUDGET_APPROVED    → budgetRequestID
    // - ANNUAL_ALLOCATION  → "Year YYYY allocation" or "Immediate adjustment"
    // - MANUAL_ADJUSTMENT  → staffNumber of SDO who made the adjustment
    @Column(name = "reference", length = 100)
    private String reference;

    // Snapshots for audit trail integrity
    @Column(name = "balanceBefore", precision = 10, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balanceAfter", precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "transactionDate", nullable = false)
    private LocalDateTime transactionDate;

    // User (email) who triggered this - "SYSTEM" for automatic triggers
    @Column(name = "createdBy", length = 100)
    private String createdBy;
}