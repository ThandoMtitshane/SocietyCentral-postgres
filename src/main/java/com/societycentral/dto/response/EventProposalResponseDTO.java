package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Complete event proposal response used by both executives (to view their
 * submission) and SDOs (to review and approve/reject).
 *
 * Includes all proposal fields, budget breakdown, co-host invitations,
 * and SDO review feedback.
 */
@Data
@Builder
public class EventProposalResponseDTO {

    // ─── Event Identity ──────────────────────────────────────────────────────
    private String eventID;
    private String eventName;
    private String eventStatus;

    // ─── Schedule ────────────────────────────────────────────────────────────
    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private LocalTime eventEndTime;

    // ─── Venue ───────────────────────────────────────────────────────────────
    private String venueCode;
    private String venueName;
    private String venueType;
    private Integer venueCapacity;
    private String campus;

    // ─── Details ─────────────────────────────────────────────────────────────
    private String eventDescription;
    private String attendingType;
    private String posterUrl;
    private String bannerUrl;

    // ─── Society ─────────────────────────────────────────────────────────────
    private String societyID;
    private String societyName;
    private String submittedByStudentNumber;
    private String submittedByName;

    // ─── POA Link ────────────────────────────────────────────────────────────
    private String poaEventID;
    private String poaEventName;

    // ─── Budget ──────────────────────────────────────────────────────────────
    private BigDecimal budgetIncomeFromAccount;
    private BigDecimal budgetIncomeSponsorship;
    private BigDecimal budgetTotalIncome;
    private BigDecimal budgetExpensePromoMaterial;
    private BigDecimal budgetExpenseDataAirtime;
    private BigDecimal budgetExpenseGifts;
    private BigDecimal budgetExpenseVenue;
    private BigDecimal budgetExpenseOther;
    private String budgetExpenseOtherSpecification;
    private BigDecimal budgetTotalExpenses;

    // ─── Co-Hosts ────────────────────────────────────────────────────────────
    private List<CoHostDTO> coHosts;

    // ─── SDO Review ──────────────────────────────────────────────────────────
    private String sdoReviewNotes;
    private String rejectionReason;
    private String reviewedByStaffNumber;
    private String reviewedByName;
    private LocalDateTime reviewedAt;

    // ─── Publishing (Phase 2, nullable until published) ──────────────────────
    private LocalDateTime rsvpOpenDate;
    private LocalDateTime rsvpCloseDate;
    private Integer eventLimit;
    private LocalDateTime publishedAt;

    // ─── Audit ───────────────────────────────────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Nested DTO for co-host invitation details.
     */
    @Data
    @Builder
    public static class CoHostDTO {
        private String invitationID;
        private String societyID;
        private String societyName;
        private String status;
        private LocalDateTime respondedAt;
    }
}
