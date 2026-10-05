package com.societycentral.service;

import com.societycentral.dto.request.BankAccountRequestDTO;
import com.societycentral.dto.response.BankAccountResponseDTO;
import com.societycentral.mapper.BankAccountMapper;
import com.societycentral.model.BankAccount;
import com.societycentral.model.BankAccountVerificationStatus;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.repository.BankAccountRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Business logic for society bank accounts.
 *
 * ------------------------------------------------------------------
 * AUTHORISATION MODEL
 * ------------------------------------------------------------------
 * Editing bank details is restricted to the Executive who is
 * submitting the Budget Request. That identity check is the
 * responsibility of the BudgetRequest service — this service
 * accepts an actor email and trusts that the caller has already
 * authenticated the request.
 *
 * Verification is an SDO-only action (B300).
 *
 * ------------------------------------------------------------------
 * UPSERT SEMANTICS
 * ------------------------------------------------------------------
 * There is at most one BankAccount per society (enforced by
 * UQ_BankAccount_Society).
 *   • If no row exists → INSERT.
 *   • If a row exists   → UPDATE in place (createdAt is preserved).
 *
 * On any edit, verificationStatus is reset to UNVERIFIED and the
 * verifier fields are cleared. This forces the SDO to re-verify
 * any account whose details have changed.
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final SocietyRepository societyRepository;
    private final SDORepository sdoRepository;
    private final BankAccountMapper mapper;

    // ────────────────────────────────────────────────────────────
    // READ
    // ────────────────────────────────────────────────────────────

    /**
     * Returns the society's bank account, or null if none exists.
     * Used by the Budget Request form to decide whether to prompt
     * for bank details.
     */
    @Transactional(readOnly = true)
    public BankAccountResponseDTO getBankAccount(String societyID) {
        return bankAccountRepository.findBySocietyID(societyID)
                .map(mapper::toResponseDTO)
                .orElse(null);
    }

    /**
     * Convenience for the BudgetRequest service — avoids loading
     * the full DTO just to check for existence.
     */
    @Transactional(readOnly = true)
    public boolean societyHasBankAccount(String societyID) {
        return bankAccountRepository.existsBySocietyID(societyID);
    }

    // ────────────────────────────────────────────────────────────
    // UPSERT (Executive action)
    // ────────────────────────────────────────────────────────────

    /**
     * Creates or updates the society's bank account.
     *
     * @param societyID   the owning society
     * @param request     the new details
     * @param actorEmail  email of the Executive making the change
     */
    @Transactional
    public BankAccountResponseDTO upsertBankAccount(
            String societyID,
            BankAccountRequestDTO request,
            String actorEmail
    ) {
        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not found: " + societyID));

        // Ensure the society is active — inactive societies cannot
        // be having budget requests submitted for them anyway.
        if (Boolean.FALSE.equals(society.getActiveStatus())) {
            throw new IllegalStateException(
                    "Society " + societyID + " is not active.");
        }

        LocalDateTime now = LocalDateTime.now();

        BankAccount bankAccount = bankAccountRepository
                .findBySocietyID(societyID)
                .orElseGet(() -> {
                    BankAccount fresh = new BankAccount();
                    fresh.setBankAccountID(UUID.randomUUID().toString());
                    fresh.setSocietyID(societyID);
                    fresh.setCreatedAt(now);
                    return fresh;
                });

        // Overwrite editable fields
        bankAccount.setBankName(request.getBankName().trim());
        bankAccount.setAccountHolderName(request.getAccountHolderName().trim());
        bankAccount.setAccountNumber(request.getAccountNumber().trim());
        bankAccount.setBranchCode(request.getBranchCode().trim());
        bankAccount.setAccountType(request.getAccountType().trim());

        // Any edit invalidates prior verification.
        bankAccount.setVerificationStatus(BankAccountVerificationStatus.UNVERIFIED);
        bankAccount.setVerifiedBy(null);
        bankAccount.setVerifiedAt(null);

        bankAccount.setLastUpdatedBy(actorEmail);
        bankAccount.setUpdatedAt(now);

        BankAccount saved = bankAccountRepository.save(bankAccount);
        return mapper.toResponseDTO(saved);
    }

    // ────────────────────────────────────────────────────────────
    // VERIFY / REJECT (SDO action during B300)
    // ────────────────────────────────────────────────────────────

    /**
     * Records an SDO's verification decision.
     *
     * @param societyID       the society whose bank is being reviewed
     * @param sdoEmail        email of the SDO making the decision
     * @param newStatus       VERIFIED or REJECTED
     */
    @Transactional
    public BankAccountResponseDTO setVerificationStatus(
            String societyID,
            String sdoEmail,
            BankAccountVerificationStatus newStatus
    ) {
        if (newStatus == null || newStatus == BankAccountVerificationStatus.UNVERIFIED) {
            throw new IllegalArgumentException(
                    "Status must be VERIFIED or REJECTED.");
        }

        SDO sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SDO not found: " + sdoEmail));

        BankAccount bankAccount = bankAccountRepository
                .findBySocietyID(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No bank account on file for society " + societyID));

        bankAccount.setVerificationStatus(newStatus);
        bankAccount.setVerifiedBy(sdo.getStaffNumber());
        bankAccount.setVerifiedAt(LocalDateTime.now());
        bankAccount.setUpdatedAt(LocalDateTime.now());

        BankAccount saved = bankAccountRepository.save(bankAccount);
        return mapper.toResponseDTO(saved);
    }

    @Transactional
    public void uploadProofOfAccountDocument(
            String societyID, MultipartFile file, String actorEmail) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("Only PDF files are allowed.");
        }

        BankAccount ba = bankAccountRepository.findBySocietyID(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No bank account on file for society " + societyID));

        try {
            ba.setProofOfAccountDocument(file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file.", e);
        }

        // Any edit invalidates prior verification
        ba.setVerificationStatus(BankAccountVerificationStatus.UNVERIFIED);
        ba.setVerifiedBy(null);
        ba.setVerifiedAt(null);
        ba.setLastUpdatedBy(actorEmail);
        ba.setUpdatedAt(LocalDateTime.now());

        bankAccountRepository.save(ba);
    }

    @Transactional(readOnly = true)
    public BankAccount getEntityForSociety(String societyID) {
        return bankAccountRepository.findBySocietyID(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No bank account on file for society " + societyID));
    }
}
