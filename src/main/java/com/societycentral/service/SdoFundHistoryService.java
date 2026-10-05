package com.societycentral.service;

import com.societycentral.dto.response.FundTransactionResponseDTO;
import com.societycentral.model.FundTransaction;
import com.societycentral.model.Society;
import com.societycentral.repository.FundTransactionRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fund transaction history across every society an SDO supervises
 * (bank-statement style), newest first, each row carrying its society name
 * and the resolved name of the person behind the transaction. Supports an
 * optional single-society filter for drill-down.
 */
@Service
@RequiredArgsConstructor
public class SdoFundHistoryService {

    private final SocietyRepository societyRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<FundTransactionResponseDTO> getHistoryForSdo(String sdoEmail,
                                                             String societyIDFilter) {
        List<Society> societies = societyRepository.findAllBySdo_Email(sdoEmail);

        // Optional drill-down to one supervised society.
        if (societyIDFilter != null && !societyIDFilter.isBlank()) {
            String target = societyIDFilter.trim();
            societies = societies.stream()
                    .filter(s -> s.getSocietyID().equalsIgnoreCase(target))
                    .toList();
        }

        Map<String, String> nameCache = new HashMap<>();
        List<FundTransactionResponseDTO> all = new ArrayList<>();

        for (Society society : societies) {
            List<FundTransaction> txns = fundTransactionRepository
                    .findBySocietyIDOrderByTransactionDateDesc(society.getSocietyID());
            for (FundTransaction tx : txns) {
                all.add(FundTransactionResponseDTO.builder()
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
                        .societyID(society.getSocietyID())
                        .societyName(society.getSocietyName())
                        .build());
            }
        }

        // Portfolio-wide newest-first ordering.
        all.sort(Comparator.comparing(
                FundTransactionResponseDTO::getTransactionDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return all;
    }

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
}
