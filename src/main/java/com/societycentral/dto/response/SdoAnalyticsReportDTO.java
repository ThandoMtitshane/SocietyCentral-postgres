package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Portfolio-wide Analytics & Reports payload for an SDO, aggregated across
 * every society they supervise over an optional date range. Contains a
 * portfolio summary, per-society breakdown rows (for the drill-down table),
 * and an event-report overview with per-event ratings.
 */
@Data
@Builder
public class SdoAnalyticsReportDTO {

    private LocalDate fromDate;
    private LocalDate toDate;
    private String generatedAt;

    private PortfolioSummary summary;
    private List<SocietyRow> societies;        // per-society breakdown (drill-down table)
    private EventReportOverview eventReports;  // generated-report overview with ratings

    // ── Portfolio summary ────────────────────────────────────────────────────
    @Data
    @Builder
    public static class PortfolioSummary {
        private int totalSocieties;
        private int activeSocieties;
        private int flaggedSocieties;
        private long totalMembers;
        private long totalEvents;
        private long totalRSVPs;
        private long totalAttendance;
        private double averageSocietyRating;    // mean of society ratings
        private double averageEventRating;       // mean event feedback rating across portfolio

        private BigDecimal totalAllocated;        // sum of annualBudgetAllocation
        private BigDecimal totalCurrentBalance;   // sum of currentBalance
        private BigDecimal totalCredits;          // credits in range across societies
        private BigDecimal totalDebits;           // debits in range across societies
    }

    // ── Per-society breakdown row ──────────────────────────────────────────────
    @Data
    @Builder
    public static class SocietyRow {
        private String societyID;
        private String societyName;
        private String acronym;
        private boolean active;
        private boolean flagged;
        private double societyRating;        // Society.rating
        private long memberCount;
        private long eventCount;
        private long totalRSVPs;
        private long totalAttendance;
        private double averageEventRating;    // mean event feedback rating for this society
        private BigDecimal currentBalance;
        private BigDecimal totalCredits;      // in range
        private BigDecimal totalDebits;       // in range
    }

    // ── Event report overview (generated reports + ratings) ────────────────────
    @Data
    @Builder
    public static class EventReportOverview {
        private int totalReportableEvents;    // completed/past events in range
        private double averageOverallRating;
        private double averageOrganizationRating;
        private double averageVenueRating;
        private double averageContentRating;
        private long totalFeedbackCount;
        private Map<String, Long> eventsByStatus;
        private List<EventReportRow> rows;    // per-event report summaries
    }

    @Data
    @Builder
    public static class EventReportRow {
        private String eventID;
        private String eventName;
        private LocalDate eventDate;
        private String status;
        private String societyID;
        private String societyName;
        private long rsvpCount;
        private long attendanceCount;
        private double attendanceRate;
        private double averageOverallRating;
        private long feedbackCount;
        private boolean reportReady;          // 7+ days since event
    }
}
