package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Combined event report data used to generate the PDF and serve the
 * viewing endpoint. Contains event details, executive report(s), and
 * all student feedback entries.
 */
@Data
@Builder
public class EventReportSummaryDTO {

    // ── Event info ─────────────────────────────────────────────────────────
    private String eventID;
    private String eventName;
    private LocalDate eventDate;
    private LocalTime eventStartTime;
    private LocalTime eventEndTime;
    private String venueName;
    private String campusName;
    private String hostingSocietyName;
    private String hostingSocietyID;
    private String posterUrl;
    private String bannerUrl;

    // ── Attendance summary ─────────────────────────────────────────────────
    private long totalRSVPs;
    private long totalAttendees;
    private double attendanceRate;
    private List<Map<String, Object>> attendanceAudit;

    // ── Ratings summary ───────────────────────────────────────────────────
    private double averageOverallRating;
    private double averageOrganizationRating;
    private double averageVenueRating;
    private double averageContentRating;
    private long totalFeedbackCount;
    private long wouldRecommendCount;

    // ── Executive report(s) ───────────────────────────────────────────────
    private List<ExecutiveEventReportResponseDTO> executiveReports;

    // ── Student feedback (individual reviews) ─────────────────────────────
    private List<EventFeedbackResponseDTO> studentFeedback;

    // ── Report metadata ───────────────────────────────────────────────────
    private boolean reportReady;  // true if 7 days have passed since event
    private String reportGeneratedMessage;
}
