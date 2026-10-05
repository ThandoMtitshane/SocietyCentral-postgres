package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for creating or updating a society's bank account.
 *
 * Field-level validation here covers format. The "only the Executive
 * may edit" rule is enforced in the service layer via the authenticated
 * user's identity.
 */
@Data
public class BankAccountRequestDTO {

    @NotBlank(message = "Bank name is required.")
    @Size(max = 100, message = "Bank name must be at most 100 characters.")
    private String bankName;

    @NotBlank(message = "Account holder name is required.")
    @Size(max = 150, message = "Account holder name must be at most 150 characters.")
    private String accountHolderName;

    @NotBlank(message = "Account number is required.")
    @Pattern(
            regexp = "\\d{9,11}",
            message = "Account number must be 9 to 11 digits."
    )
    private String accountNumber;

    @NotBlank(message = "Branch code is required.")
    @Pattern(
            regexp = "\\d{6}",
            message = "Branch code must be exactly 6 digits."
    )
    private String branchCode;

    @NotBlank(message = "Account type is required.")
    @Pattern(
            regexp = "CHEQUE|SAVINGS|TRANSMISSION",
            message = "Account type must be CHEQUE, SAVINGS, or TRANSMISSION."
    )
    private String accountType;
}
