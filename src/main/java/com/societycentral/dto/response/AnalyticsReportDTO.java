package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated Analytics & Reports payload for one society over an optional
 * date range. Contains three sections: financial, event performance and
 * membership. All figures are computed server-side and scoped to the
 * authenticated executive's active society.
 */
@Data
@Builder
public class AnalyticsReportDTO {

    private String societyID;
    private String societyName;
    private LocalDate fromDate;      // null = no lower bound
    private LocalDate toDate;        // null = no upper bound
    private String generatedAt;      // ISO timestamp when this report was built

    private FinancialReport financial;
    private EventPerformanceReport events;
    private MembershipReport membership;

    // ── Financial ──────────────────────────────────────────────────────────
    @Data
    @Builder
    public static class FinancialReport {
        private BigDecimal openingBalance;   // balanceBefore of the first tx in range
        private BigDecimal closingBalance;   // balanceAfter of the last tx in range
        private BigDecimal currentBalance;    // society's live balance
        private BigDecimal totalCredits;      // sum of all CREDIT amounts in range
        private BigDecimal totalDebits;       // sum of all DEBIT amounts in range
        private int transactionCount;
        private List<CategoryTotal> byReason;  // credits/debits grouped by reason
    }

    @Data
    @Builder
    public static class CategoryTotal {
        private String reason;
        private String direction;   // CREDIT or DEBIT
        private BigDecimal total;
        private int count;
    }

    // ── Event Performance ──────────────────────────────────────────────────
    @Data
    @Builder
    public static class EventPerformanceReport {
        private int totalEvents;
        private Map<String, Long> byStatus;     // eventStatus -> count
        private long totalRSVPs;
        private long totalAttendance;
        private double averageAttendanceRate;    // % across events that had RSVPs
        private double averageRating;             // across events with feedback
        private List<EventPerformanceRow> rows;
    }

    @Data
    @Builder
    public static class EventPerformanceRow {
        private String eventID;
        private String eventName;
        private LocalDate eventDate;
        private String status;
        private String proposedAt;    // createdAt
        private String reviewedAt;
        private String publishedAt;
        private long rsvpCount;
        private long attendanceCount;
        private double attendanceRate;    // %
        private double averageRating;      // 0 if no feedback
        private long feedbackCount;
    }

    // ── Membership ─────────────────────────────────────────────────────────
    @Data
    @Builder
    public static class MembershipReport {
        private long totalActiveMembers;
        private long newMembersInRange;
        private Map<String, Long> byCampus;
        private Map<String, Long> byLevel;
        private Map<String, Long> byCourse;
    }
}
