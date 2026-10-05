package com.societycentral.dto.response;

import com.societycentral.model.BudgetRequestPayoutStatus;
import java.time.LocalDateTime;

import com.societycentral.model.BudgetRequestStatus;
import com.societycentral.model.BudgetRequestType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class BudgetRequestResponseDTO {

    private String budgetRequestID;
    private String eventID;
    private String societyID;
    private String requestingStudentNumber;

    private String name;
    private String description;
    private BigDecimal amount;

    private BudgetRequestType type;
    private String typeSpecification;

    private BudgetRequestStatus status;

    private LocalDate requestDate;
    private LocalDate lastUpdatedDate;

    private String reviewedByStaffNumber;
    private String reviewNotes;

    private String poaID;

    private BudgetRequestPayoutStatus payoutStatus;
    private LocalDateTime processedAt;

}
