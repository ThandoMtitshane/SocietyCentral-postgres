package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of a society's fund transaction history (bank-statement style).
 * Adds a human-readable {@code performedBy} name resolved from the
 * {@code createdBy} email so the UI can show who was behind each movement.
 */
@Data
@Builder
public class FundTransactionResponseDTO {

    private String transactionID;
    private BigDecimal amount;
    private String direction;        // CREDIT or DEBIT
    private String reason;           // FundTransactionReason enum name
    private String description;
    private String reference;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private LocalDateTime transactionDate;

    private String createdBy;        // raw email or "SYSTEM"
    private String performedBy;      // resolved display name (or "System")

    // Populated for SDO portfolio history (which spans multiple societies);
    // null for the single-society executive view.
    private String societyID;
    private String societyName;
}
