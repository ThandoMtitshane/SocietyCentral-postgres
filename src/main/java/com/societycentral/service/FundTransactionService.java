package com.societycentral.service;

import com.societycentral.dto.response.FundTransactionResponseDTO;
import com.societycentral.model.*;
import com.societycentral.repository.FundTransactionRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.UserRepository;
import com.societycentral.service.ExecutiveSocietyResolver.ActiveExecutiveSociety;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central service for all currentBalance changes.
 * Every change to Society.currentBalance must go through here
 * so the FundTransaction audit trail is never skipped.
 *
 * Callers:
 *  - SocietyManagementService.registerSociety()  -> CREDIT ANNUAL_ALLOCATION
 *  - SocietyManagementService.updateSociety()    -> CREDIT/DEBIT ANNUAL_ALLOCATION (if effectiveImmediately)
 *  - SocietyMemberService.joinSociety()          -> CREDIT MEMBERSHIP_FEE
 *  - BudgetRequestService (future)               -> DEBIT BUDGET_APPROVED
 */
@Service
@RequiredArgsConstructor
public class FundTransactionService {

    private final SocietyRepository societyRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final UserRepository userRepository;
    private final ExecutiveSocietyResolver executiveSocietyResolver;

    /**
     * Credits or debits a society's currentBalance and records the
     * transaction. This is the single entry point for all balance changes.
     *
     * @param societyID   the society whose balance changes
     * @param amount      always positive,  direction controls sign
     * @param direction   CREDIT (+) or DEBIT (-)
     * @param reason      why this happened
     * @param description human-readable explanation
     * @param reference   contextual reference (student number, budgetRequestID, etc.)
     * @param createdBy   email of user who triggered this, or "SYSTEM"
     */
    @Transactional
    public FundTransaction recordTransaction(String societyID,
                                              BigDecimal amount,
                                              FundTransactionDirection direction,
                                              FundTransactionReason reason,
                                              String description,
                                              String reference,
                                              String createdBy) {
        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not found: " + societyID));

        BigDecimal balanceBefore = society.getCurrentBalance() != null
                ? society.getCurrentBalance()
                : BigDecimal.ZERO;

        BigDecimal balanceAfter = direction == FundTransactionDirection.CREDIT
                ? balanceBefore.add(amount)
                : balanceBefore.subtract(amount);

        // Prevent negative balance on debit
        if (direction == FundTransactionDirection.DEBIT
                && balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Insufficient funds. Available balance: R "
                    + balanceBefore + ", requested: R " + amount);
        }

        // Update society balance
        society.setCurrentBalance(balanceAfter);
        societyRepository.save(society);

        // Record transaction
        FundTransaction tx = new FundTransaction();
        tx.setTransactionID(generateTransactionID());
        tx.setSocietyID(societyID);
        tx.setAmount(amount);
        tx.setDirection(direction);
        tx.setReason(reason);
        tx.setDescription(description);
        tx.setReference(reference);
        tx.setBalanceBefore(balanceBefore);
        tx.setBalanceAfter(balanceAfter);
        tx.setTransactionDate(LocalDateTime.now());
        tx.setCreatedBy(createdBy);

        return fundTransactionRepository.save(tx);
    }

    /**
     * Full fund transaction history for the authenticated executive's own
     * society, newest first, with the {@code createdBy} email resolved to a
     * human-readable name for display.
     */
    @Transactional(readOnly = true)
    public List<FundTransactionResponseDTO> getHistoryForExecutive(String executiveEmail) {
        ActiveExecutiveSociety context = executiveSocietyResolver.resolve(executiveEmail);
        String societyID = context.society().getSocietyID();

        List<FundTransaction> transactions =
                fundTransactionRepository.findBySocietyIDOrderByTransactionDateDesc(societyID);

        // Cache email -> display name so we resolve each user only once
        Map<String, String> nameCache = new HashMap<>();

        return transactions.stream()
                .map(tx -> FundTransactionResponseDTO.builder()
                        .transactionID(tx.getTransactionID())
                        .amount(tx.getAmount())
                        .direction(tx.getDirection() == null ? null : tx.getDirection().name())
                        .reason(tx.getReason() == null ? null : tx.getReason().name())
                        .description(tx.getDescription())
                        .reference(tx.getReference())
                        .balanceBefore(tx.getBalanceBefore())
                        .balanceAfter(tx.getBalanceAfter())
                        .transactionDate(tx.getTransactionDate())
                        .createdBy(tx.getCreatedBy())
                        .performedBy(resolvePerformerName(tx.getCreatedBy(), nameCache))
                        .build())
                .toList();
    }

    /**
     * Resolves a createdBy email into a display name. "SYSTEM" (or blank)
     * maps to "System". Unknown emails fall back to the raw value.
     */
    private String resolvePerformerName(String createdBy, Map<String, String> cache) {
        if (createdBy == null || createdBy.isBlank()
                || "SYSTEM".equalsIgnoreCase(createdBy)) {
            return "System";
        }
        return cache.computeIfAbsent(createdBy, email ->
                userRepository.findById(email)
                        .map(user -> {
                            String full = ((user.getFirstName() == null ? "" : user.getFirstName())
                                    + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
                            return full.isEmpty() ? email : full;
                        })
                        .orElse(email));
    }

    /**
     * Formats an amount for display: +R1 000.00 (CREDIT) or -R500.00 (DEBIT).
     */
    public static String formatAmount(BigDecimal amount, FundTransactionDirection direction) {
        String prefix = direction == FundTransactionDirection.CREDIT ? "+R" : "-R";
        return prefix + String.format("%,.2f", amount);
    }

    private String generateTransactionID() {
        long count = fundTransactionRepository.count() + 1;
        return String.format("TXN%06d", count);
    }
}