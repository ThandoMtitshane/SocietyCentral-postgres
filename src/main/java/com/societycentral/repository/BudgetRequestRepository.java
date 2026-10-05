package com.societycentral.repository;

import com.societycentral.model.BudgetRequestPayoutStatus;
import com.societycentral.model.BudgetRequest;
import com.societycentral.model.BudgetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetRequestRepository extends JpaRepository<BudgetRequest, String> {

    // All budget requests for a specific (event, society) pair
    List<BudgetRequest> findByEventIDAndSocietyID(String eventID, String societyID);

    // All budget requests for a society (across all events)
    List<BudgetRequest> findBySocietyID(String societyID);

    // All budget requests for an event (across all hosting societies)
    List<BudgetRequest> findByEventID(String eventID);

    // Pending requests for a society - used to check if new requests can be made
    List<BudgetRequest> findBySocietyIDAndStatus(String societyID, BudgetRequestStatus status);

    // Count pending requests for a society - used in exec dashboard stat card
    long countBySocietyIDAndStatus(String societyID, BudgetRequestStatus status);

    // All requests by a specific executive
    List<BudgetRequest> findByRequestingStudentNumber(String studentNumber);

    // All budget requests across a set of societies (used for SDO review lists)
    List<BudgetRequest> findBySocietyIDIn(List<String> societyIDs);

    // All budget requests across a set of societies, filtered by status
    List<BudgetRequest> findBySocietyIDInAndStatus(List<String> societyIDs, BudgetRequestStatus status);

    // B300 — the SDO's funds processing queue
    List<BudgetRequest> findBySocietyIDInAndStatusAndPayoutStatus(
            List<String> societyIDs,
            BudgetRequestStatus status,
            BudgetRequestPayoutStatus payoutStatus
    );
}