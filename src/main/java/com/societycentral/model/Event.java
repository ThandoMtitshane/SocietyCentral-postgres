package com.societycentral.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "Event")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Event {

    @Id
    @Column(name = "eventID", length = 20)
    private String eventID;

    @Column(name = "eventName", length = 100, nullable = false)
    private String eventName;

    @Column(name = "eventDate")
    private LocalDate eventDate;

    @Column(name = "eventStartTime", columnDefinition = "TIME")
    private LocalTime eventStartTime;

    @Column(name = "eventEndTime", columnDefinition = "TIME")
    private LocalTime eventEndTime;

    /**
     * Legacy single-time value retained for existing records and screens.
     * New event creation mirrors {@link #eventStartTime} into this column.
     */
    @Deprecated
    @Column(name = "eventTime", columnDefinition = "TIME")
    private LocalTime eventTime;

    @Column(name = "venueCode", length = 10)
    private String venueCode;

    /**
     * Legacy display name retained for existing records and screens.
     * New event creation resolves this value from the selected Venue row.
     */
    @Deprecated
    @Column(name = "eventVenue", length = 100)
    private String eventVenue;

    @Enumerated(EnumType.STRING)
    @Column(name = "eventCampus", length = 30)
    private Campus eventCampus;

    @Column(name = "eventDescription", length = 1000)
    private String eventDescription;

    @Column(name = "advertisementVersion")
    private Integer advertisementVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "eventStatus", length = 20)
    private EventStatus eventStatus;

    @Column(name = "rsvpOpenDate")
    private LocalDateTime rsvpOpenDate;

    @Column(name = "rsvpCloseDate")
    private LocalDateTime rsvpCloseDate;

    @Column(name = "eventLimit")
    private Integer eventLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendingType", length = 20)
    private AttendingType attendingType;

    @Column(name = "overallRating")
    private Double overallRating;

    @Deprecated
    @Column(name = "imageUrl", length = 255)
    private String imageUrl;

    @Column(name = "posterUrl", length = 500)
    private String posterUrl;

    @Column(name = "bannerUrl", length = 500)
    private String bannerUrl;

    /**
     * Most recent rejection feedback supplied by the event reviewer.
     */
    @Column(name = "rejectionReason", length = 1000)
    private String rejectionReason;

    // ═══════════════════════════════════════════════════════════════════════════
    // PROPOSAL WORKFLOW FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Student number of the executive who submitted this event proposal.
     */
    @Column(name = "submittedBy", length = 20)
    private String submittedBy;

    /**
     * Links back to a POA event if this proposal was created from one.
     */
    @Column(name = "poaEventID", length = 36)
    private String poaEventID;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUDGET (inline, mirrors POAEvent budget structure)
    // ═══════════════════════════════════════════════════════════════════════════

    @Column(name = "budgetIncomeFromAccount", precision = 10, scale = 2)
    private BigDecimal budgetIncomeFromAccount = BigDecimal.ZERO;

    @Column(name = "budgetIncomeSponsorship", precision = 10, scale = 2)
    private BigDecimal budgetIncomeSponsorship = BigDecimal.ZERO;

    @Column(name = "budgetExpensePromoMaterial", precision = 10, scale = 2)
    private BigDecimal budgetExpensePromoMaterial = BigDecimal.ZERO;

    @Column(name = "budgetExpenseDataAirtime", precision = 10, scale = 2)
    private BigDecimal budgetExpenseDataAirtime = BigDecimal.ZERO;

    @Column(name = "budgetExpenseGifts", precision = 10, scale = 2)
    private BigDecimal budgetExpenseGifts = BigDecimal.ZERO;

    @Column(name = "budgetExpenseVenue", precision = 10, scale = 2)
    private BigDecimal budgetExpenseVenue = BigDecimal.ZERO;

    @Column(name = "budgetExpenseOther", precision = 10, scale = 2)
    private BigDecimal budgetExpenseOther = BigDecimal.ZERO;

    @Column(name = "budgetExpenseOtherSpecification", length = 200)
    private String budgetExpenseOtherSpecification;

    // ═══════════════════════════════════════════════════════════════════════════
    // SDO REVIEW
    // ═══════════════════════════════════════════════════════════════════════════

    @Column(name = "sdoReviewNotes", length = 1000)
    private String sdoReviewNotes;

    @Column(name = "reviewedByStaffNumber", length = 20)
    private String reviewedByStaffNumber;

    @Column(name = "reviewedAt")
    private LocalDateTime reviewedAt;

    @Column(name = "publishedAt")
    private LocalDateTime publishedAt;

    @Column(name = "feedbackEmailSentAt")
    private LocalDateTime feedbackEmailSentAt;

    @Column(name = "advanceNoticeSentAt")
    private LocalDateTime advanceNoticeSentAt;

    @Column(name = "rsvpOpenNoticeSentAt")
    private LocalDateTime rsvpOpenNoticeSentAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════════════════

    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void initializeAuditTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void updateAuditTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
