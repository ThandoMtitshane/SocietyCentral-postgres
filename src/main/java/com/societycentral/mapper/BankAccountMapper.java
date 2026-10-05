package com.societycentral.mapper;

import com.societycentral.dto.response.BankAccountResponseDTO;
import com.societycentral.model.BankAccount;
import org.springframework.stereotype.Component;

/**
 * Converts BankAccount entities into response DTOs.
 *
 * Kept out of the service so that masking logic lives in exactly
 * one place and can be reused by any future caller (e.g. a B300
 * sidebar projection).
 */
@Component
public class BankAccountMapper {

    /**
     * Masks an account number for display, keeping only the last
     * four digits. Numbers shorter than four characters are fully
     * masked. Null is returned as "****".
     */
    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public BankAccountResponseDTO toResponseDTO(BankAccount entity) {
        BankAccountResponseDTO dto = new BankAccountResponseDTO();
        dto.setBankAccountID(entity.getBankAccountID());
        dto.setSocietyID(entity.getSocietyID());
        dto.setBankName(entity.getBankName());
        dto.setAccountHolderName(entity.getAccountHolderName());
        dto.setMaskedAccountNumber(maskAccountNumber(entity.getAccountNumber()));
        dto.setBranchCode(entity.getBranchCode());
        dto.setAccountType(entity.getAccountType());
        dto.setVerificationStatus(
                entity.getVerificationStatus() != null
                        ? entity.getVerificationStatus().name()
                        : null
        );
        dto.setVerifiedBy(entity.getVerifiedBy());
        dto.setVerifiedAt(entity.getVerifiedAt());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setHasProofOfAccountDocument(entity.getProofOfAccountDocument() != null);
        return dto;
    }
}
