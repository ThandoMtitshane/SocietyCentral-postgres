package com.societycentral.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Outbound representation of a society's bank account.
 *
 * The full account number is never returned. Only the last four
 * digits are exposed (see maskedAccountNumber). Callers that need
 * the full number — currently only the SDO during B300 payout
 * execution — must use a separate, restricted endpoint.
 */
@Data
public class BankAccountResponseDTO {

    private String bankAccountID;
    private String societyID;

    private String bankName;
    private String accountHolderName;

    /** e.g. ****1234 — never the full number. */
    private String maskedAccountNumber;

    private String branchCode;
    private String accountType;

    private String verificationStatus; // UNVERIFIED / VERIFIED / REJECTED
    private String verifiedBy;
    private LocalDateTime verifiedAt;

    private String lastUpdatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Boolean hasProofOfAccountDocument;
}
