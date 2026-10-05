package com.societycentral.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One budget request line item for a specific (event, society) pair.
 * A single event can have multiple BudgetRequest rows per hosting society
 * (e.g. Meals R1000, Promo R200, Transport R500,  all separate rows).
 *
 * FK to Hoster(eventID, societyID) identifies which society is requesting
 * budget for which event, cleanly resolving the many-to-many between
 * Society and Event via the existing Hoster junction table.
 */
@Entity
@Table(name = "BudgetRequest")
@Getter
@Setter
@NoArgsConstructor
public class BudgetRequest {

    @Id
    @Column(name = "budgetRequestID", length = 20)
    private String budgetRequestID;

    // Composite FK → Hoster(eventID, societyID)
    @Column(name = "eventID", length = 20, nullable = false)
    private String eventID;

    @Column(name = "societyID", length = 20, nullable = false)
    private String societyID;

    // Who submitted this request
    @Column(name = "requestingStudentNumber", length = 20, nullable = false)
    private String requestingStudentNumber;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private BudgetRequestType type;

    // Required when type = OTHER
    @Column(name = "typeSpecification", length = 100)
    private String typeSpecification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BudgetRequestStatus status = BudgetRequestStatus.PENDING;

    @Column(name = "requestDate")
    private LocalDate requestDate;

    @Column(name = "lastUpdatedDate")
    private LocalDate lastUpdatedDate;

    // SDO who reviewed this request
    @Column(name = "reviewedByStaffNumber", length = 20)
    private String reviewedByStaffNumber;

    // SDO notes on approval/rejection/conditions
    @Column(name = "reviewNotes", length = 500)
    private String reviewNotes;

    // Reserved for future POA FK - stored but no FK constraint yet
    @Column(name = "poaID", length = 20)
    private String poaID;

    @Enumerated(EnumType.STRING)
    @Column(name = "payoutStatus", length = 20, nullable = false)
    private BudgetRequestPayoutStatus payoutStatus = BudgetRequestPayoutStatus.NOT_PROCESSED;

    @Column(name = "processedAt")
    private LocalDateTime processedAt;
}