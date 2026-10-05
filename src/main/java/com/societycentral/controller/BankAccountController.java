package com.societycentral.controller;

import com.societycentral.dto.request.BankAccountRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.BankAccountResponseDTO;
import com.societycentral.model.BankAccount;
import com.societycentral.model.BankAccountVerificationStatus;
import com.societycentral.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bank account endpoints.
 *
 * URL is scoped to the society because a bank account has no
 * meaning outside its owner. The UNIQUE(societyID) constraint
 * means there is never an ambiguity about "which one".
 *
 * Authorisation (Executive vs SDO) is enforced by the URL prefix
 * and by the role checks in SecurityConfig:
 *
 *   /api/executive/... → Executive only (edit + read)
 *   /api/sdo/...       → SDO only       (read + verify)
 */
@RestController
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    // ────────────────────────────────────────────────────────────
    // EXECUTIVE — read + edit
    // ────────────────────────────────────────────────────────────

    /** Read the society's bank account (Executive view). */
    @GetMapping("/api/executive/societies/{societyID}/bank-account")
    public ResponseEntity<ApiResponse<BankAccountResponseDTO>> getForExecutive(
            @PathVariable String societyID
    ) {
        BankAccountResponseDTO dto = bankAccountService.getBankAccount(societyID);
        return ResponseEntity.ok(ApiResponse.success(
                dto == null
                        ? "No bank account on file for this society."
                        : "Bank account retrieved.",
                dto));
    }

    /** Create or update the society's bank account (Executive only). */
    @PutMapping("/api/executive/societies/{societyID}/bank-account")
    public ResponseEntity<ApiResponse<BankAccountResponseDTO>> upsert(
            @PathVariable String societyID,
            @Valid @RequestBody BankAccountRequestDTO request,
            Authentication authentication
    ) {
        BankAccountResponseDTO dto = bankAccountService.upsertBankAccount(
                societyID,
                request,
                authentication.getName()
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Bank account saved. Verification has been reset.", dto));
    }

    // ────────────────────────────────────────────────────────────
    // SDO — read + verify
    // ────────────────────────────────────────────────────────────

    /** Read the society's bank account (SDO view). */
    @GetMapping("/api/sdo/societies/{societyID}/bank-account")
    public ResponseEntity<ApiResponse<BankAccountResponseDTO>> getForSdo(
            @PathVariable String societyID
    ) {
        BankAccountResponseDTO dto = bankAccountService.getBankAccount(societyID);
        return ResponseEntity.ok(ApiResponse.success(
                dto == null
                        ? "No bank account on file for this society."
                        : "Bank account retrieved.",
                dto));
    }

    /** SDO marks the society's bank account VERIFIED or REJECTED. */
    @PatchMapping("/api/sdo/societies/{societyID}/bank-account/verify")
    public ResponseEntity<ApiResponse<BankAccountResponseDTO>> verify(
            @PathVariable String societyID,
            @RequestParam("status") BankAccountVerificationStatus status,
            Authentication authentication
    ) {
        BankAccountResponseDTO dto = bankAccountService.setVerificationStatus(
                societyID,
                authentication.getName(),
                status
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Bank account verification updated.", dto));
    }

    @PostMapping(
            value = "/api/executive/societies/{societyID}/bank-account/document",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> uploadDocument(
            @PathVariable String societyID,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        bankAccountService.uploadProofOfAccountDocument(societyID, file, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Document uploaded.", null));
    }

    @GetMapping("/api/sdo/societies/{societyID}/bank-account/document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String societyID) {
        BankAccount ba = bankAccountService.getEntityForSociety(societyID);
        if (ba.getProofOfAccountDocument() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"proof-of-account.pdf\"")
                .body(ba.getProofOfAccountDocument());
    }
}
