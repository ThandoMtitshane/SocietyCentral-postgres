package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.EventReportSummaryDTO;
import com.societycentral.dto.response.EventFeedbackResponseDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.service.EmailService;
import com.societycentral.service.EmailType;
import com.societycentral.service.EventReportService;
import com.societycentral.service.EventFeedbackSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Endpoints for viewing event reports and downloading PDFs.
 *
 * SDO:
 *   GET /api/sdo/events/{eventID}/report         → view report data (JSON)
 *   GET /api/sdo/events/{eventID}/report/pdf      → download PDF
 *   GET /api/sdo/events/reports                   → list events with available reports
 *
 * Executive:
 *   GET /api/executive/events/{eventID}/report/view  → view report data (JSON)
 *   GET /api/executive/events/{eventID}/report/pdf   → download PDF
 *   GET /api/executive/events/pending-reports        → events needing a report
 *
 * Student:
 *   GET /api/student/events/feedback-pending        → attended events with open feedback
 */
@RestController
@RequiredArgsConstructor
public class EventReportController {

    private final EventReportService eventReportService;
    private final EmailService emailService;
    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final RSVPRepository rsvpRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SDORepository sdoRepository;
    private final SocietyRepository societyRepository;
    private final EventFeedbackRepository feedbackRepository;
    private final ExecutiveEventReportRepository execReportRepository;
    private final VenueRepository venueRepository;
    private final EventFeedbackSubmissionService feedbackSubmissionService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    // ══════════════════════════════════════════════════════════════════════════
    // SDO ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * SDO views the structured report data for an event (JSON).
     */
    @GetMapping("/api/sdo/events/{eventID}/report")
    public ResponseEntity<ApiResponse<EventReportSummaryDTO>> sdoViewReport(
            @PathVariable String eventID,
            Authentication auth) {

        // Verify SDO has access (supervises the hosting society)
        verifySdoAccess(eventID, auth.getName());

        EventReportSummaryDTO report = eventReportService.buildReportData(eventID);
        return ResponseEntity.ok(ApiResponse.success("Event report loaded.", report));
    }

    /**
     * SDO downloads the event report as a PDF.
     */
    @GetMapping("/api/sdo/events/{eventID}/report/pdf")
    public ResponseEntity<byte[]> sdoDownloadPDF(
            @PathVariable String eventID,
            Authentication auth) {

        verifySdoAccess(eventID, auth.getName());

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        byte[] pdf = eventReportService.generatePDF(eventID);
        String filename = "Event_Report_" + event.getEventName()
                .replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * SDO lists all events that have reports available (7+ days past).
     */
    @GetMapping("/api/sdo/events/reports")
    public ResponseEntity<ApiResponse<List<EventReportListItem>>> sdoListReports(
            Authentication auth) {

        SDO sdo = sdoRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("SDO not found."));

        List<Society> societies = societyRepository.findBySdoStaffNumber(sdo.getStaffNumber());
        List<String> societyIDs = societies.stream()
                .map(Society::getSocietyID).toList();

        // Find all events hosted by supervised societies that are 7+ days old
        List<EventReportListItem> items = eventRepository.findAll().stream()
                .filter(e -> e.getEventDate() != null)
                .filter(e -> e.getEventDate().plusDays(7).isBefore(LocalDate.now())
                        || e.getEventDate().plusDays(7).isEqual(LocalDate.now()))
                .filter(e -> e.getEventStatus() == EventStatus.PUBLISHED
                        || e.getEventStatus() == EventStatus.COMPLETED)
                .filter(e -> hosterRepository.findByIdEventID(e.getEventID()).stream()
                        .anyMatch(h -> societyIDs.contains(h.getId().getSocietyID())))
                .map(e -> mapToListItem(e, societyIDs))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Reports loaded.", items));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXECUTIVE ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Executive views the structured report data for their society's event.
     */
    @GetMapping("/api/executive/events/{eventID}/report/view")
    public ResponseEntity<ApiResponse<EventReportSummaryDTO>> executiveViewReport(
            @PathVariable String eventID,
            Authentication auth) {

        verifyExecutiveAccess(eventID, auth.getName());

        EventReportSummaryDTO report = eventReportService.buildReportData(eventID);
        return ResponseEntity.ok(ApiResponse.success("Event report loaded.", report));
    }

    /**
     * Executive downloads the event report as a PDF.
     */
    @GetMapping("/api/executive/events/{eventID}/report/pdf")
    public ResponseEntity<byte[]> executiveDownloadPDF(
            @PathVariable String eventID,
            Authentication auth) {

        verifyExecutiveAccess(eventID, auth.getName());

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        byte[] pdf = eventReportService.generatePDF(eventID);
        String filename = "Event_Report_" + event.getEventName()
                .replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Executive gets a list of their society's events that need a report
     * (event ended, no report submitted yet by their society).
     */
    @GetMapping("/api/executive/events/pending-reports")
    public ResponseEntity<ApiResponse<List<EventReportListItem>>> executivePendingReports(
            Authentication auth) {

        Student student = studentRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        Executive activeRole = executiveRepository.findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null
                        || e.getTermEndDate().isAfter(LocalDate.now())
                        || e.getTermEndDate().isEqual(LocalDate.now()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("You are not an active executive."));

        String societyID = activeRole.getId().getSocietyID();

        // Find events hosted by the society that have a past or today's date
        List<EventReportListItem> items = hosterRepository.findByIdSocietyID(societyID).stream()
                .map(h -> eventRepository.findById(h.getId().getEventID()).orElse(null))
                .filter(e -> e != null)
                .filter(e -> feedbackSubmissionService.isEventFinished(e))
                .map(e -> {
                    boolean reportSubmitted = execReportRepository
                            .existsByEventIDAndSocietyID(e.getEventID(), societyID);
                    boolean reportReady = e.getEventDate().plusDays(7).isBefore(LocalDate.now())
                            || e.getEventDate().plusDays(7).isEqual(LocalDate.now());
                    return new EventReportListItem(
                            e.getEventID(),
                            e.getEventName(),
                            e.getEventDate(),
                            getSocietyNameForEvent(e.getEventID()),
                            reportSubmitted,
                            reportReady
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Pending reports loaded.", items));
    }

    /**
     * Lists completed events owned by the authenticated executive's society
     * for the Executive Past Events & Feedback page.
     */
    @GetMapping("/api/executive/events/past-feedback")
    public ResponseEntity<ApiResponse<List<ExecutivePastEventItem>>> executivePastFeedback(
            Authentication auth) {

        Student student = studentRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        String societyID = executiveRepository.findByIdStudentNumber(student.getStudentNumber()).stream()
                .filter(e -> e.getTermEndDate() == null
                        || !e.getTermEndDate().isBefore(LocalDate.now()))
                .map(e -> e.getId().getSocietyID())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("You are not an active executive."));

        List<ExecutivePastEventItem> items = hosterRepository.findByIdSocietyID(societyID).stream()
                .map(h -> eventRepository.findById(h.getId().getEventID()).orElse(null))
                .filter(e -> e != null && feedbackSubmissionService.isEventFinished(e))
                .map(e -> new ExecutivePastEventItem(
                        e.getEventID(), e.getEventName(), e.getEventDate(), e.getEventStartTime(),
                        e.getEventEndTime(), getSocietyNameForEvent(e.getEventID()),
                        e.getEventVenue(), e.getPosterUrl(), e.getBannerUrl(),
                        rsvpRepository.countByIdEventIDAndScannedStatusTrue(e.getEventID()),
                        feedbackRepository.findByEventID(e.getEventID()).size()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Past events loaded.", items));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STUDENT ENDPOINT
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Student gets a list of attended events where they can still submit feedback.
     * Shows past events the student RSVP'd to.
     * Feedback submission still requires scannedStatus=true (enforced at submit time),
     * but we show all RSVP'd past events so the student can see what's available.
     */
    @GetMapping("/api/student/events/feedback-pending")
    public ResponseEntity<ApiResponse<List<FeedbackPendingItem>>> studentFeedbackPending(
            Authentication auth) {

        Student student = studentRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        String studentNumber = student.getStudentNumber();

        // Find ALL RSVPs for this student (not just scanned ones)
        List<RSVP> allRsvps = rsvpRepository.findByIdStudentNumber(studentNumber);

        List<FeedbackPendingItem> items = allRsvps.stream()
                .map(rsvp -> {
                    Event event = eventRepository.findById(rsvp.getId().getEventID()).orElse(null);
                    if (event == null) return null;
                    if (!feedbackSubmissionService.isEventFinished(event)) return null;
                    boolean alreadySubmitted = feedbackRepository
                            .existsByEventIDAndStudentNumber(event.getEventID(), studentNumber);
                    boolean attended = Boolean.TRUE.equals(rsvp.getScannedStatus());
                    return feedbackPendingItem(event, studentNumber, alreadySubmitted, attended);
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Feedback status loaded.", items));
    }

    /**
     * Executive A700 feedback entry point. Executives use the same
     * attendance-based eligibility and feedback record as students.
     */
    @GetMapping("/api/executive/events/feedback-pending")
    public ResponseEntity<ApiResponse<List<FeedbackPendingItem>>> executiveFeedbackPending(
            Authentication auth) {

        Student student = studentRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
        String studentNumber = student.getStudentNumber();

        if (executiveRepository.findByIdStudentNumber(studentNumber).stream()
                .noneMatch(e -> e.getTermEndDate() == null
                        || !e.getTermEndDate().isBefore(LocalDate.now()))) {
            throw new IllegalStateException("You are not an active executive.");
        }

        List<RSVP> allRsvps = rsvpRepository.findByIdStudentNumber(studentNumber);
        List<FeedbackPendingItem> items = allRsvps.stream()
                .map(rsvp -> {
                    Event event = eventRepository.findById(rsvp.getId().getEventID()).orElse(null);
                    if (event == null || !feedbackSubmissionService.isEventFinished(event)) return null;
                    boolean submitted = feedbackRepository.existsByEventIDAndStudentNumber(
                            event.getEventID(), studentNumber);
                    return feedbackPendingItem(event, studentNumber, submitted,
                            Boolean.TRUE.equals(rsvp.getScannedStatus()));
                })
                .filter(item -> item != null)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Feedback status loaded.", items));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INNER DTOs (simple data carriers for list endpoints)
    // ══════════════════════════════════════════════════════════════════════════

    public record EventReportListItem(
            String eventID,
            String eventName,
            LocalDate eventDate,
            String societyName,
            boolean reportSubmitted,
            boolean reportReady
    ) {}

    public record FeedbackPendingItem(
            String eventID,
            String eventName,
            LocalDate eventDate,
            java.time.LocalTime eventStartTime,
            java.time.LocalTime eventEndTime,
            String societyName,
            String venueName,
            String posterUrl,
            String bannerUrl,
            boolean feedbackSubmitted,
            boolean attended,
            EventFeedbackResponseDTO submittedFeedback,
            java.time.LocalDateTime feedbackDeadline,
            boolean feedbackWindowOpen,
            boolean feedbackEditable
    ) {}

    private FeedbackPendingItem feedbackPendingItem(Event event, String studentNumber,
                                                    boolean submitted, boolean attended) {
        EventFeedbackResponseDTO response = submitted
                ? feedbackRepository.findByEventID(event.getEventID()).stream()
                .filter(feedback -> studentNumber.equals(feedback.getStudentNumber()))
                .findFirst()
                .map(feedback -> EventFeedbackResponseDTO.builder()
                        .feedbackID(feedback.getFeedbackID())
                        .studentNumber(feedback.getStudentNumber())
                        .rating(feedback.getRating())
                        .organizationRating(feedback.getOrganizationRating())
                        .venueRating(feedback.getVenueRating())
                        .contentRating(feedback.getContentRating())
                        .wouldRecommend(feedback.getWouldRecommend())
                        .highlights(feedback.getHighlights())
                        .improvements(feedback.getImprovements())
                        .description(feedback.getDescription())
                        .submittedAt(feedback.getSubmittedAt())
                        .build())
                .orElse(null)
                : null;
        String venueName = event.getEventVenue();
        if ((venueName == null || venueName.isBlank()) && event.getVenueCode() != null) {
            venueName = venueRepository.findById(event.getVenueCode())
                    .map(Venue::getVenueName).orElse(venueName);
        }
        java.time.LocalDateTime deadline = feedbackSubmissionService.feedbackDeadline(event);
        boolean windowOpen = feedbackSubmissionService.isFeedbackWindowOpen(event);
        return new FeedbackPendingItem(event.getEventID(), event.getEventName(), event.getEventDate(),
                event.getEventStartTime(), event.getEventEndTime(), getSocietyNameForEvent(event.getEventID()),
                venueName, event.getPosterUrl(), event.getBannerUrl(), submitted, attended, response,
                deadline, windowOpen, response != null && windowOpen);
    }

    public record ExecutivePastEventItem(
            String eventID,
            String eventName,
            LocalDate eventDate,
            java.time.LocalTime eventStartTime,
            java.time.LocalTime eventEndTime,
            String societyName,
            String venueName,
            String posterUrl,
            String bannerUrl,
            long checkedInCount,
            long feedbackCount
    ) {}

    // ══════════════════════════════════════════════════════════════════════════
    // ACCESS VERIFICATION
    // ══════════════════════════════════════════════════════════════════════════

    private void verifySdoAccess(String eventID, String email) {
        SDO sdo = sdoRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("SDO not found."));

        List<String> supervisedSocietyIDs = societyRepository
                .findBySdoStaffNumber(sdo.getStaffNumber()).stream()
                .map(Society::getSocietyID).toList();

        boolean hasAccess = hosterRepository.findByIdEventID(eventID).stream()
                .anyMatch(h -> supervisedSocietyIDs.contains(h.getId().getSocietyID()));

        if (!hasAccess) {
            throw new IllegalStateException("You do not have access to this event's report.");
        }
    }

    private void verifyExecutiveAccess(String eventID, String email) {
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));

        List<String> execSocietyIDs = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber()).stream()
                .filter(e -> e.getTermEndDate() == null || e.getTermEndDate().isAfter(LocalDate.now()))
                .map(e -> e.getId().getSocietyID())
                .toList();

        boolean hasAccess = hosterRepository.findByIdEventID(eventID).stream()
                .anyMatch(h -> execSocietyIDs.contains(h.getId().getSocietyID()));

        if (!hasAccess) {
            throw new IllegalStateException("Your society does not host this event.");
        }
    }

    private String getSocietyNameForEvent(String eventID) {
        return hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .or(() -> hosterRepository.findByIdEventID(eventID).stream().findFirst())
                .map(h -> societyRepository.findById(h.getId().getSocietyID())
                        .map(Society::getSocietyName).orElse("Unknown"))
                .orElse("Unknown Society");
    }

    private EventReportListItem mapToListItem(Event e, List<String> societyIDs) {
        String societyName = getSocietyNameForEvent(e.getEventID());
        String societyID = hosterRepository.findByIdEventID(e.getEventID()).stream()
                .filter(h -> societyIDs.contains(h.getId().getSocietyID()))
                .findFirst()
                .map(h -> h.getId().getSocietyID())
                .orElse("");
        boolean reportSubmitted = !societyID.isEmpty()
                && execReportRepository.existsByEventIDAndSocietyID(e.getEventID(), societyID);
        return new EventReportListItem(
                e.getEventID(),
                e.getEventName(),
                e.getEventDate(),
                societyName,
                reportSubmitted,
                true // already filtered to 7+ days
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SDO FEEDBACK OVERVIEW + REQUEST
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * SDO gets a full overview of past events with feedback/report status.
     * Returns event details + color coding info (red/yellow/green).
     */
    @GetMapping("/api/sdo/events/feedback-overview")
    public ResponseEntity<ApiResponse<List<FeedbackOverviewItem>>> sdoFeedbackOverview(
            Authentication auth) {

        SDO sdo = sdoRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("SDO not found."));

        List<Society> societies = societyRepository.findBySdoStaffNumber(sdo.getStaffNumber());
        List<String> societyIDs = societies.stream().map(Society::getSocietyID).toList();

        // Find all past events hosted by supervised societies
        List<FeedbackOverviewItem> items = eventRepository.findAll().stream()
                .filter(e -> e.getEventDate() != null && !e.getEventDate().isAfter(LocalDate.now()))
                .filter(e -> hosterRepository.findByIdEventID(e.getEventID()).stream()
                        .anyMatch(h -> societyIDs.contains(h.getId().getSocietyID())))
                .map(e -> {
                    String eventSocietyID = hosterRepository.findByIdEventID(e.getEventID()).stream()
                            .filter(h -> societyIDs.contains(h.getId().getSocietyID()))
                            .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                            .findFirst()
                            .or(() -> hosterRepository.findByIdEventID(e.getEventID()).stream()
                                    .filter(h -> societyIDs.contains(h.getId().getSocietyID()))
                                    .findFirst())
                            .map(h -> h.getId().getSocietyID())
                            .orElse("");

                    String societyName = societyRepository.findById(eventSocietyID)
                            .map(Society::getSocietyName).orElse("Unknown");

                    boolean reportSubmitted = !eventSocietyID.isEmpty()
                            && execReportRepository.existsByEventIDAndSocietyID(e.getEventID(), eventSocietyID);

                    long daysSinceEvent = java.time.temporal.ChronoUnit.DAYS.between(
                            e.getEventDate(), LocalDate.now());

                    long attendeeCount = rsvpRepository.countByIdEventIDAndScannedStatusTrue(e.getEventID());
                    long rsvpCount = rsvpRepository.countByIdEventID(e.getEventID());
                    long feedbackCount = feedbackRepository.findByEventID(e.getEventID()).size();

                    // Color: GREEN = report submitted, RED = 7+ days no report, YELLOW = <7 days no report
                    String status;
                    if (reportSubmitted) {
                        status = "GREEN";
                    } else if (daysSinceEvent >= 7) {
                        status = "RED";
                    } else {
                        status = "YELLOW";
                    }

                    String venueName = e.getEventVenue() != null ? e.getEventVenue() : "";
                    if (e.getVenueCode() != null) {
                        venueName = venueRepository.findById(e.getVenueCode())
                                .map(Venue::getVenueName).orElse(venueName);
                    }

                    return new FeedbackOverviewItem(
                            e.getEventID(),
                            e.getEventName(),
                            e.getEventDescription(),
                            e.getEventDate(),
                            venueName,
                            societyName,
                            eventSocietyID,
                            attendeeCount,
                            rsvpCount,
                            feedbackCount,
                            reportSubmitted,
                            daysSinceEvent,
                            status
                    );
                })
                .sorted((a, b) -> {
                    // Sort: RED first, then YELLOW, then GREEN
                    int order = statusOrder(a.status()) - statusOrder(b.status());
                    if (order != 0) return order;
                    return b.eventDate().compareTo(a.eventDate()); // newest first within group
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Feedback overview loaded.", items));
    }

    /**
     * SDO requests feedback/report from executives of a specific event's hosting society.
     * Sends an email reminder to all active executives.
     */
    @PostMapping("/api/sdo/events/{eventID}/request-feedback")
    public ResponseEntity<ApiResponse<Void>> sdoRequestFeedback(
            @PathVariable String eventID,
            Authentication auth) {

        verifySdoAccess(eventID, auth.getName());

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        // Find hosting society
        Hoster primaryHoster = hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .orElse(hosterRepository.findByIdEventID(eventID).stream().findFirst().orElse(null));

        if (primaryHoster == null) {
            throw new IllegalStateException("No hosting society found for this event.");
        }

        String societyID = primaryHoster.getId().getSocietyID();
        String societyName = societyRepository.findById(societyID)
                .map(Society::getSocietyName).orElse("your society");

        // Send email to all active executives
        List<String> execEmails = executiveRepository.findCurrentExecutiveEmailsBySocietyID(
                societyID, LocalDate.now());

        String reportLink = baseUrl + "/executive/events/" + eventID + "/report";

        for (String execEmail : execEmails) {
            emailService.send(EmailType.EVENT_REPORT_REQUEST, execEmail, java.util.Map.of(
                    "eventName", event.getEventName(),
                    "societyName", societyName,
                    "reportLink", reportLink
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Feedback request sent to " + execEmails.size() + " executive(s).", null));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXECUTIVE MANUAL REPORT GENERATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Executive manually triggers report PDF generation and email delivery
     * (even before 7 days have passed).
     */
    @PostMapping("/api/executive/events/{eventID}/report/generate")
    public ResponseEntity<ApiResponse<Void>> executiveGenerateReport(
            @PathVariable String eventID,
            Authentication auth) {

        verifyExecutiveAccess(eventID, auth.getName());

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        // Generate PDF
        byte[] pdf = eventReportService.generatePDF(eventID);
        String filename = "Event_Report_" + event.getEventName()
                .replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        // Find hosting society + SDO
        Hoster primaryHoster = hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .orElse(hosterRepository.findByIdEventID(eventID).stream().findFirst().orElse(null));

        if (primaryHoster != null) {
            String societyID = primaryHoster.getId().getSocietyID();
            Society society = societyRepository.findById(societyID).orElse(null);

            java.util.Map<String, String> emailVars = java.util.Map.of(
                    "eventName", event.getEventName(),
                    "societyName", society != null ? society.getSocietyName() : "Society",
                    "reportLink", baseUrl + "/sdo/events/" + eventID + "/report"
            );

            // Send to SDO
            if (society != null) {
                sdoRepository.findById(society.getSdoStaffNumber()).ifPresent(sdo ->
                        emailService.sendWithAttachment(
                                EmailType.EVENT_REPORT_GENERATED, sdo.getEmail(),
                                emailVars, pdf, filename));
            }

            // Send to the executive who triggered it
            emailService.sendWithAttachment(
                    EmailType.EVENT_REPORT_GENERATED, auth.getName(),
                    emailVars, pdf, filename);
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Report generated and sent to your SDO.", null));
    }

    public record FeedbackOverviewItem(
            String eventID,
            String eventName,
            String eventDescription,
            LocalDate eventDate,
            String venueName,
            String societyName,
            String societyID,
            long attendeeCount,
            long rsvpCount,
            long feedbackCount,
            boolean reportSubmitted,
            long daysSinceEvent,
            String status  // "RED", "YELLOW", "GREEN"
    ) {}

    private int statusOrder(String status) {
        return switch (status) {
            case "RED" -> 0;
            case "YELLOW" -> 1;
            case "GREEN" -> 2;
            default -> 3;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TEST TRIGGER — sends a demo report PDF to a specified email
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Test endpoint: generates a demo event report PDF with sample data
     * and sends it to the specified email address.
     *
     * Usage: POST /api/test/event-report/send?email=s229878873@mandela.ac.za
     *
     * This is for development/testing purposes only.
     */
    @PostMapping("/api/test/event-report/send")
    public ResponseEntity<ApiResponse<String>> triggerTestReport(
            @RequestParam String email) {

        // Build demo data
        var feedback1 = com.societycentral.dto.response.EventFeedbackResponseDTO.builder()
                .feedbackID("FB-DEMO-001")
                .studentName("Thabo M.")
                .rating(5)
                .organizationRating(4)
                .venueRating(5)
                .contentRating(5)
                .wouldRecommend(true)
                .highlights("The guest speaker was incredibly inspiring. The networking session was the highlight for me.")
                .improvements("More chairs would have been nice, some of us had to stand.")
                .description("One of the best events I have attended this year. Well organised and informative.")
                .submittedAt(java.time.LocalDateTime.now().minusDays(2))
                .build();

        var feedback2 = com.societycentral.dto.response.EventFeedbackResponseDTO.builder()
                .feedbackID("FB-DEMO-002")
                .studentName("Naledi K.")
                .rating(4)
                .organizationRating(4)
                .venueRating(3)
                .contentRating(5)
                .wouldRecommend(true)
                .highlights("Content was very relevant to my field of study.")
                .improvements("The venue was a bit small for the number of attendees. Consider a bigger venue next time.")
                .description("Great event overall. Looking forward to the next one.")
                .submittedAt(java.time.LocalDateTime.now().minusDays(1))
                .build();

        var feedback3 = com.societycentral.dto.response.EventFeedbackResponseDTO.builder()
                .feedbackID("FB-DEMO-003")
                .studentName("Sipho N.")
                .rating(4)
                .organizationRating(5)
                .venueRating(4)
                .contentRating(4)
                .wouldRecommend(true)
                .highlights("The food was amazing and the event started on time!")
                .improvements("Maybe add more interactive Q&A sessions.")
                .description("Enjoyed the event, learnt a lot.")
                .submittedAt(java.time.LocalDateTime.now().minusHours(18))
                .build();

        var execReport = com.societycentral.dto.response.ExecutiveEventReportResponseDTO.builder()
                .reportID("RPT-DEMO-001")
                .eventID("EVT-DEMO")
                .societyID("SOC-DEMO")
                .societyName("Computer Science Society")
                .studentNumber("s220000001")
                .executiveName("Masego Madisha")
                .executivePosition("President")
                .expectations("We expected a turnout of around 80 students, with strong engagement during the Q&A session and positive feedback on the workshop content.")
                .expectationsMet("YES")
                .successAssessment("SUCCESS")
                .successReason("We exceeded our attendance target (92 attendees). The speaker was well-received and the interactive workshop had full participation. Social media engagement was also high with 200+ shares.")
                .improvements("We underestimated the seating capacity needed. For the next event we will book a larger venue and add overflow seating. We also need a better sound system for the back rows.")
                .advice("Start promoting at least 2 weeks in advance. Having a backup plan for AV issues saved us when the projector failed initially.")
                .attendeeCount(92)
                .overallRating(5)
                .additionalNotes("Special thanks to the SDO for the budget support. The leftover promotional material will be used for our next event.")
                .submittedAt(java.time.LocalDateTime.now().minusDays(3))
                .build();

        var reportData = com.societycentral.dto.response.EventReportSummaryDTO.builder()
                .eventID("EVT-DEMO")
                .eventName("Tech Talk: AI in South Africa 2026")
                .eventDate(java.time.LocalDate.now().minusDays(10))
                .eventStartTime(java.time.LocalTime.of(14, 0))
                .eventEndTime(java.time.LocalTime.of(17, 0))
                .venueName("Nomhle Nkonyeni Lecture Halls")
                .campusName("SOUTH_CAMPUS")
                .hostingSocietyName("Computer Science Society")
                .hostingSocietyID("SOC-DEMO")
                .totalRSVPs(120)
                .totalAttendees(92)
                .averageOverallRating(4.3)
                .averageOrganizationRating(4.3)
                .averageVenueRating(4.0)
                .averageContentRating(4.7)
                .totalFeedbackCount(3)
                .wouldRecommendCount(3)
                .executiveReports(java.util.List.of(execReport))
                .studentFeedback(java.util.List.of(feedback1, feedback2, feedback3))
                .reportReady(true)
                .reportGeneratedMessage("Report generated successfully.")
                .build();

        // Generate PDF
        byte[] pdf = eventReportService.generatePDFFromData(reportData);
        String filename = "Event_Report_Tech_Talk_AI_2026.pdf";

        // Send to specified email
        emailService.sendWithAttachment(
                com.societycentral.service.EmailType.EVENT_REPORT_GENERATED,
                email,
                java.util.Map.of(
                        "eventName", "Tech Talk: AI in South Africa 2026",
                        "societyName", "Computer Science Society",
                        "reportLink", baseUrl + "/sdo/events/EVT-DEMO/report"
                ),
                pdf,
                filename
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Demo report PDF generated and sent to " + email, null));
    }
}
