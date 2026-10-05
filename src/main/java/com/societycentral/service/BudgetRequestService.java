package com.societycentral.service;

import com.societycentral.model.*;
import com.societycentral.dto.request.BudgetRequestRequestDTO;
import com.societycentral.dto.response.BudgetRequestResponseDTO;
import com.societycentral.model.BudgetRequest;
import com.societycentral.model.BudgetRequestStatus;
import com.societycentral.model.FundTransactionDirection;
import com.societycentral.model.FundTransactionReason;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetRequestService {

    private final SocietyRepository societyRepository;
    private final BudgetRequestRepository budgetRequestRepository;
    private final HosterRepository hosterRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SDORepository sdoRepository;
    private final FundTransactionService fundTransactionService;
    private final BankAccountRepository bankAccountRepository;

    public BudgetRequestResponseDTO requestBudget(BudgetRequestRequestDTO request,
                                                  String studentNumber) {
        Student student = studentRepository.findByEmail(studentNumber)
                .orElseThrow(() ->
                        new IllegalArgumentException("Student not found."));

        boolean executive = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .anyMatch(e ->
                        e.getId().getSocietyID()
                                .equals(request.getSocietyID()));

        if (!executive) {
            throw new IllegalArgumentException(
                    "Only executives of this society may request budgets.");
        }

        if (!hosterRepository.existsByIdEventIDAndIdSocietyID(
                request.getEventID(),
                request.getSocietyID())) {

            throw new IllegalArgumentException(
                    "The selected society is not hosting this event.");
        }

        // A society cannot request more than its available balance.
        Society society = societyRepository.findById(request.getSocietyID())
                .orElseThrow(() -> new IllegalArgumentException("Society not found."));
        BigDecimal balance = society.getCurrentBalance() != null
                ? society.getCurrentBalance()
                : BigDecimal.ZERO;
        BigDecimal requested = request.getAmount() != null
                ? request.getAmount()
                : BigDecimal.ZERO;

        if (requested.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Budget request amount must be greater than zero.");
        }
        if (requested.compareTo(balance) > 0) {
            throw new IllegalStateException(
                    "Requested amount (R " + requested
                    + ") exceeds your society's available balance (R " + balance + ").");
        }

        BudgetRequest budgetRequest = new BudgetRequest();

        budgetRequest.setBudgetRequestID(generateID());
        budgetRequest.setEventID(request.getEventID());
        budgetRequest.setSocietyID(request.getSocietyID());
        budgetRequest.setRequestingStudentNumber(student.getStudentNumber());

        budgetRequest.setName(request.getName());
        budgetRequest.setDescription(request.getDescription());
        budgetRequest.setAmount(request.getAmount());

        budgetRequest.setType(request.getType());
        budgetRequest.setTypeSpecification(request.getTypeSpecification());

        budgetRequest.setStatus(BudgetRequestStatus.PENDING);
        budgetRequest.setRequestDate(java.time.LocalDate.now());
        budgetRequest.setLastUpdatedDate(java.time.LocalDate.now());

        BudgetRequest saved = budgetRequestRepository.save(budgetRequest);

        return mapToResponse(saved);
    }

    private String generateID() {
        long count = budgetRequestRepository.count() + 1;
        return String.format("BR%03d", count);
    }

    private BudgetRequestResponseDTO mapToResponse(BudgetRequest budgetRequest) {

        return BudgetRequestResponseDTO.builder()
                .budgetRequestID(budgetRequest.getBudgetRequestID())
                .eventID(budgetRequest.getEventID())
                .societyID(budgetRequest.getSocietyID())
                .requestingStudentNumber(budgetRequest.getRequestingStudentNumber())
                .name(budgetRequest.getName())
                .description(budgetRequest.getDescription())
                .amount(budgetRequest.getAmount())
                .type(budgetRequest.getType())
                .typeSpecification(budgetRequest.getTypeSpecification())
                .status(budgetRequest.getStatus())
                .requestDate(budgetRequest.getRequestDate())
                .lastUpdatedDate(budgetRequest.getLastUpdatedDate())
                .reviewedByStaffNumber(budgetRequest.getReviewedByStaffNumber())
                .reviewNotes(budgetRequest.getReviewNotes())
                .poaID(budgetRequest.getPoaID())
                .payoutStatus(budgetRequest.getPayoutStatus())
                .processedAt(budgetRequest.getProcessedAt())
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    public BudgetRequestResponseDTO reviewBudgetRequest(
            String budgetRequestID,
            BudgetRequestStatus status,
            String staffNumber,
            String reviewNotes) {

        BudgetRequest budgetRequest = budgetRequestRepository
                .findById(budgetRequestID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Budget request not found."));

        if (budgetRequest.getStatus() != BudgetRequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Only pending budget requests can be reviewed.");
        }

        if (status != BudgetRequestStatus.APPROVED
                && status != BudgetRequestStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Budget request can only be approved or rejected.");
        }

        if (status == BudgetRequestStatus.REJECTED
                && (reviewNotes == null || reviewNotes.isBlank())) {
            throw new IllegalArgumentException(
                    "A reason is required when rejecting a budget request.");
        }

        SDO sdo = sdoRepository.findByEmail(staffNumber)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        // On approval: validate the society can afford it, then debit the
        // balance and record the movement in the fund history.
        if (status == BudgetRequestStatus.APPROVED) {
            Society society = societyRepository.findById(budgetRequest.getSocietyID())
                    .orElseThrow(() -> new IllegalArgumentException("Society not found."));

            BigDecimal balance = society.getCurrentBalance() != null
                    ? society.getCurrentBalance()
                    : BigDecimal.ZERO;
            BigDecimal amount = budgetRequest.getAmount() != null
                    ? budgetRequest.getAmount()
                    : BigDecimal.ZERO;

            if (amount.compareTo(balance) > 0) {
                throw new IllegalStateException(
                        "Cannot approve: requested amount (R " + amount
                        + ") exceeds the society's available balance (R " + balance + ").");
            }

            // recordTransaction updates currentBalance and writes the
            // FundTransaction audit row (also re-checks for negative balance).
            fundTransactionService.recordTransaction(
                    society.getSocietyID(),
                    amount,
                    FundTransactionDirection.DEBIT,
                    FundTransactionReason.BUDGET_APPROVED,
                    "Budget approved: " + budgetRequest.getName()
                            + " (event " + budgetRequest.getEventID() + ")",
                    budgetRequest.getBudgetRequestID(),
                    staffNumber
            );
        }

        budgetRequest.setStatus(status);
        budgetRequest.setReviewedByStaffNumber(sdo.getStaffNumber());
        budgetRequest.setReviewNotes(reviewNotes);
        budgetRequest.setLastUpdatedDate(java.time.LocalDate.now());

        BudgetRequest saved = budgetRequestRepository.save(budgetRequest);

        return mapToResponse(saved);
    }

    /**
     * Returns budget requests for all societies supervised by the given SDO.
     * Optionally filtered by status (PENDING, APPROVED, REJECTED).
     *
     * @param email authenticated SDO's email
     * @param status optional status filter; null returns all statuses
     * @return matching budget requests
     */
    public List<BudgetRequestResponseDTO> getBudgetRequestsForSDO(
            String email,
            BudgetRequestStatus status) {

        SDO sdo = sdoRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        List<String> societyIDs = societyRepository
                .findBySdoStaffNumber(sdo.getStaffNumber())
                .stream()
                .map(society -> society.getSocietyID())
                .toList();

        List<BudgetRequest> requests = (status != null)
                ? budgetRequestRepository.findBySocietyIDInAndStatus(societyIDs, status)
                : budgetRequestRepository.findBySocietyIDIn(societyIDs);

        return requests.stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * B300 — Process Funds.
     *
     * Marks an APPROVED budget request as paid out. Assumes the money
     * already left the society balance at approval time, so this method
     * only records that the bank payout was executed.
     *
     * Validations:
     *   • the request exists
     *   • status is APPROVED
     *   • payoutStatus is NOT_PROCESSED (no double-processing)
     *   • the SDO supervises the society
     *   • the society's bank account exists and is VERIFIED
     */
    @org.springframework.transaction.annotation.Transactional
    public BudgetRequestResponseDTO processFunds(
            String budgetRequestID,
            String sdoEmail) {

        BudgetRequest budgetRequest = budgetRequestRepository
                .findById(budgetRequestID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Budget request not found."));

        if (budgetRequest.getStatus() != BudgetRequestStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only approved budget requests can be processed.");
        }

        if (budgetRequest.getPayoutStatus() == BudgetRequestPayoutStatus.PROCESSED) {
            throw new IllegalStateException(
                    "This budget request has already been processed.");
        }

        SDO sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        Society society = societyRepository.findById(budgetRequest.getSocietyID())
                .orElseThrow(() -> new IllegalArgumentException("Society not found."));

        if (!society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new IllegalStateException(
                    "You do not supervise this society.");
        }

        // Bank account must exist and be VERIFIED before payout.
        BankAccount bankAccount = bankAccountRepository
                .findBySocietyID(society.getSocietyID())
                .orElseThrow(() -> new IllegalStateException(
                        "This society has no bank account on file. " +
                                "The executive must add one before funds can be processed."));

        if (bankAccount.getVerificationStatus() != BankAccountVerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "The society's bank account has not been verified. " +
                            "Verify the proof-of-account document first.");
        }

        budgetRequest.setPayoutStatus(BudgetRequestPayoutStatus.PROCESSED);
        budgetRequest.setProcessedAt(java.time.LocalDateTime.now());
        budgetRequest.setLastUpdatedDate(java.time.LocalDate.now());

        BudgetRequest saved = budgetRequestRepository.save(budgetRequest);
        return mapToResponse(saved);
    }

    /**
     * B300 — the SDO's funds processing queue.
     * Returns APPROVED budget requests with payoutStatus NOT_PROCESSED
     * across all societies the SDO supervises.
     */
    public List<BudgetRequestResponseDTO> getPayoutQueueForSDO(String sdoEmail) {

        SDO sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        List<String> societyIDs = societyRepository
                .findBySdoStaffNumber(sdo.getStaffNumber())
                .stream()
                .map(Society::getSocietyID)
                .toList();

        if (societyIDs.isEmpty()) {
            return List.of();
        }

        return budgetRequestRepository
                .findBySocietyIDInAndStatusAndPayoutStatus(
                        societyIDs,
                        BudgetRequestStatus.APPROVED,
                        BudgetRequestPayoutStatus.NOT_PROCESSED)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

}
