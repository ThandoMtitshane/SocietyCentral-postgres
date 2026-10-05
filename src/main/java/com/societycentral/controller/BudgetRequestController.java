package com.societycentral.controller;

import com.societycentral.model.BudgetRequestStatus;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

import com.societycentral.dto.request.BudgetRequestReviewDTO;

import com.societycentral.dto.request.BudgetRequestRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.BudgetRequestResponseDTO;
import com.societycentral.model.BudgetRequest;
import com.societycentral.service.BudgetRequestService;
import com.societycentral.service.EventProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budget-requests")
@RequiredArgsConstructor
public class BudgetRequestController {

    private final BudgetRequestService budgetRequestService;
    private final EventProposalService eventProposalService;

    @PostMapping
    public ResponseEntity<ApiResponse<BudgetRequestResponseDTO>> requestBudget(
            @Valid @RequestBody BudgetRequestRequestDTO request,
            Authentication authentication) {

        BudgetRequestResponseDTO response =
                budgetRequestService.requestBudget(request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Budget request submitted successfully.", response));
    }

    /** Submit multiple budget items for one event in a single request. */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<BudgetRequestResponseDTO>>> requestBudgetBatch(
            @Valid @RequestBody List<BudgetRequestRequestDTO> requests,
            Authentication authentication) {

        List<BudgetRequestResponseDTO> responses = requests.stream()
                .map(request -> budgetRequestService.requestBudget(request, authentication.getName()))
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        responses.size() + " budget item(s) submitted successfully.", responses));
    }

    @PatchMapping("/{budgetRequestID}/review")
    public ResponseEntity<ApiResponse<BudgetRequestResponseDTO>> reviewBudgetRequest(
            @PathVariable String budgetRequestID,
            @Valid @RequestBody BudgetRequestReviewDTO request,
            Authentication authentication) {

        BudgetRequestResponseDTO response =
                budgetRequestService.reviewBudgetRequest(
                        budgetRequestID,
                        request.getStatus(),
                        authentication.getName(),
                        request.getReviewNotes());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Budget request reviewed successfully.",
                        response));
    }

    /** SDO: Get all pending budget requests for their supervised societies. */
    @GetMapping("/sdo/pending")
    public ResponseEntity<ApiResponse<List<BudgetRequestResponseDTO>>> getSDOPendingRequests(
            Authentication authentication) {

        List<BudgetRequest> pending = eventProposalService
                .getPendingBudgetRequestsForSDO(authentication.getName());

        List<BudgetRequestResponseDTO> responses = pending.stream()
                .map(br -> BudgetRequestResponseDTO.builder()
                        .budgetRequestID(br.getBudgetRequestID())
                        .eventID(br.getEventID())
                        .societyID(br.getSocietyID())
                        .requestingStudentNumber(br.getRequestingStudentNumber())
                        .name(br.getName())
                        .description(br.getDescription())
                        .amount(br.getAmount())
                        .type(br.getType())
                        .typeSpecification(br.getTypeSpecification())
                        .status(br.getStatus())
                        .requestDate(br.getRequestDate())
                        .lastUpdatedDate(br.getLastUpdatedDate())
                        .reviewedByStaffNumber(br.getReviewedByStaffNumber())
                        .reviewNotes(br.getReviewNotes())
                        .poaID(br.getPoaID())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                "Pending budget requests retrieved.", responses));
    }

    /**
     * B100 — Approve/Reject Budget
     *
     * Returns budget requests for all societies the authenticated SDO
     * supervises, optionally filtered by status.
     *
     * Example: GET /api/budget-requests?status=PENDING
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BudgetRequestResponseDTO>>> getBudgetRequests(
            @RequestParam(required = false) BudgetRequestStatus status,
            Authentication authentication) {

        List<BudgetRequestResponseDTO> requests =
                budgetRequestService.getBudgetRequestsForSDO(authentication.getName(), status);

        return ResponseEntity.ok(
                ApiResponse.success("Budget requests retrieved successfully.", requests));
    }

    /**
     * B300 — Process Funds.
     * The SDO marks an approved budget request as paid out.
     *
     * POST /api/sdo/budget-requests/{budgetRequestID}/process-funds
     */
    @PostMapping("/sdo/{budgetRequestID}/process-funds")
    public ResponseEntity<ApiResponse<BudgetRequestResponseDTO>> processFunds(
            @PathVariable String budgetRequestID,
            Authentication authentication) {

        BudgetRequestResponseDTO response =
                budgetRequestService.processFunds(
                        budgetRequestID, authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                "Funds processed successfully.", response));
    }

    /**
     * B300 — Funds processing queue.
     * Returns approved, unpaid budget requests for the SDO's societies.
     *
     * GET /api/budget-requests/sdo/payout-queue
     */
    @GetMapping("/sdo/payout-queue")
    public ResponseEntity<ApiResponse<List<BudgetRequestResponseDTO>>> getPayoutQueue(
            Authentication authentication) {

        List<BudgetRequestResponseDTO> queue =
                budgetRequestService.getPayoutQueueForSDO(authentication.getName());

        return ResponseEntity.ok(ApiResponse.success(
                "Payout queue retrieved.", queue));
    }
}
