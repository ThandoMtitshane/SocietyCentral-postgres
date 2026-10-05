package com.societycentral.service;

import com.societycentral.dto.response.EventFeedbackResponseDTO;
import com.societycentral.dto.response.EventReportSummaryDTO;
import com.societycentral.dto.response.ExecutiveEventReportResponseDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds structured event report data (executive report + student feedback)
 * and generates a formatted PDF.
 *
 * The report becomes "ready" 7 days after the event date.
 */
@Service
@Slf4j
public class EventReportService {

    private final EventRepository eventRepository;
    private final EventFeedbackRepository feedbackRepository;
    private final ExecutiveEventReportRepository execReportRepository;
    private final HosterRepository hosterRepository;
    private final RSVPRepository rsvpRepository;
    private final VenueRepository venueRepository;
    private final SocietyRepository societyRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final UserRepository userRepository;
    private final UserProfilePictureRepository userProfilePictureRepository;

    @Autowired
    public EventReportService(EventRepository eventRepository, EventFeedbackRepository feedbackRepository,
                              ExecutiveEventReportRepository execReportRepository, HosterRepository hosterRepository,
                              RSVPRepository rsvpRepository, VenueRepository venueRepository,
                              SocietyRepository societyRepository, StudentRepository studentRepository,
                              ExecutiveRepository executiveRepository, UserRepository userRepository,
                              UserProfilePictureRepository userProfilePictureRepository) {
        this.eventRepository = eventRepository;
        this.feedbackRepository = feedbackRepository;
        this.execReportRepository = execReportRepository;
        this.hosterRepository = hosterRepository;
        this.rsvpRepository = rsvpRepository;
        this.venueRepository = venueRepository;
        this.societyRepository = societyRepository;
        this.studentRepository = studentRepository;
        this.executiveRepository = executiveRepository;
        this.userRepository = userRepository;
        this.userProfilePictureRepository = userProfilePictureRepository;
    }

    // Kept for existing unit-test/manual construction compatibility.
    public EventReportService(EventRepository eventRepository, EventFeedbackRepository feedbackRepository,
                              ExecutiveEventReportRepository execReportRepository, HosterRepository hosterRepository,
                              RSVPRepository rsvpRepository, VenueRepository venueRepository,
                              SocietyRepository societyRepository, StudentRepository studentRepository,
                              ExecutiveRepository executiveRepository, UserRepository userRepository) {
        this(eventRepository, feedbackRepository, execReportRepository, hosterRepository, rsvpRepository,
                venueRepository, societyRepository, studentRepository, executiveRepository, userRepository, null);
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ══════════════════════════════════════════════════════════════════════════
    // BUILD REPORT DATA
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Assembles the complete report data for a given event.
     */
    @Transactional(readOnly = true)
    public EventReportSummaryDTO buildReportData(String eventID) {
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventID));

        // Resolve hosting society
        Hoster primaryHoster = hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .orElse(hosterRepository.findByIdEventID(eventID).stream().findFirst().orElse(null));

        String societyName = "";
        String societyID = "";
        if (primaryHoster != null) {
            societyID = primaryHoster.getId().getSocietyID();
            societyName = societyRepository.findById(societyID)
                    .map(Society::getSocietyName).orElse("Unknown Society");
        }

        // Resolve venue name
        String venueName = event.getEventVenue() != null ? event.getEventVenue() : "";
        if (event.getVenueCode() != null) {
            venueName = venueRepository.findById(event.getVenueCode())
                    .map(Venue::getVenueName).orElse(venueName);
        }

        // RSVP stats
        List<RSVP> rsvps = rsvpRepository.findByIdEventID(eventID);
        long totalRSVPs = rsvps.size();
        long totalAttendees = rsvps.stream()
                .filter(r -> Boolean.TRUE.equals(r.getScannedStatus())).count();
        List<Map<String, Object>> attendanceAudit = rsvps.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentNumber", r.getId().getStudentNumber());
            row.put("rsvpCreatedAt", r.getRsvpCreatedAt());
            row.put("rsvpStatus", "CONFIRMED");
            row.put("attendanceStatus", Boolean.TRUE.equals(r.getScannedStatus()) ? "CHECKED_IN" : "NOT_CHECKED_IN");
            row.put("checkedInAt", r.getScannedAt());
            row.put("checkInMethod", r.getCheckInMethod());
            row.put("checkedInBy", r.getCheckedInBy());
            studentRepository.findById(r.getId().getStudentNumber()).ifPresent(s -> {
                row.put("email", s.getEmail());
                if (s.getUser() != null) row.put("name", s.getUser().getFirstName() + " " + s.getUser().getLastName());
            });
            return row;
        }).toList();

        // Executive reports
        List<ExecutiveEventReport> execReports = execReportRepository.findAll().stream()
                .filter(r -> r.getEventID().equals(eventID))
                .toList();

        List<ExecutiveEventReportResponseDTO> execDTOs = execReports.stream()
                .map(this::mapExecReport).toList();

        // Student feedback
        List<EventFeedback> feedbacks = feedbackRepository.findByEventID(eventID);
        List<EventFeedbackResponseDTO> feedbackDTOs = feedbacks.stream()
                .map(this::mapFeedback).toList();

        // Compute averages
        double avgOverall = feedbacks.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(EventFeedback::getRating).average().orElse(0.0);
        double avgOrg = feedbacks.stream()
                .filter(f -> f.getOrganizationRating() != null)
                .mapToInt(EventFeedback::getOrganizationRating).average().orElse(0.0);
        double avgVenue = feedbacks.stream()
                .filter(f -> f.getVenueRating() != null)
                .mapToInt(EventFeedback::getVenueRating).average().orElse(0.0);
        double avgContent = feedbacks.stream()
                .filter(f -> f.getContentRating() != null)
                .mapToInt(EventFeedback::getContentRating).average().orElse(0.0);
        long wouldRecommend = feedbacks.stream()
                .filter(f -> Boolean.TRUE.equals(f.getWouldRecommend())).count();

        // Check if report is ready (7 days after event)
        boolean reportReady = event.getEventDate() != null
                && LocalDate.now().isAfter(event.getEventDate().plusDays(6));

        String message = reportReady
                ? "Report generated successfully."
                : "Report will be available 7 days after the event (" +
                  (event.getEventDate() != null ? event.getEventDate().plusDays(7).format(DATE_FMT) : "TBD") + ").";

        return EventReportSummaryDTO.builder()
                .eventID(eventID)
                .eventName(event.getEventName())
                .eventDate(event.getEventDate())
                .eventStartTime(event.getEventStartTime())
                .eventEndTime(event.getEventEndTime())
                .venueName(venueName)
                .campusName(event.getEventCampus() != null ? event.getEventCampus().name() : "")
                .hostingSocietyName(societyName)
                .hostingSocietyID(societyID)
                .posterUrl(event.getPosterUrl())
                .bannerUrl(event.getBannerUrl())
                .totalRSVPs(totalRSVPs)
                .totalAttendees(totalAttendees)
                .attendanceAudit(attendanceAudit)
                .attendanceRate(totalRSVPs == 0 ? 0.0
                        : Math.round((double) totalAttendees / totalRSVPs * 1000.0) / 10.0)
                .averageOverallRating(Math.round(avgOverall * 10.0) / 10.0)
                .averageOrganizationRating(Math.round(avgOrg * 10.0) / 10.0)
                .averageVenueRating(Math.round(avgVenue * 10.0) / 10.0)
                .averageContentRating(Math.round(avgContent * 10.0) / 10.0)
                .totalFeedbackCount(feedbacks.size())
                .wouldRecommendCount(wouldRecommend)
                .executiveReports(execDTOs)
                .studentFeedback(feedbackDTOs)
                .reportReady(reportReady)
                .reportGeneratedMessage(message)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GENERATE PDF
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generates a structured PDF report for the given event.
     * Returns the PDF as a byte array.
     */
    public byte[] generatePDF(String eventID) {
        EventReportSummaryDTO data = buildReportData(eventID);
        return generatePDFFromData(data);
    }

    /**
     * Generates PDF from pre-built report data. Useful for testing.
     */
    public byte[] generatePDFFromData(EventReportSummaryDTO data) {
        try (PDDocument doc = new PDDocument()) {
            BrandedPDFWriter w = new BrandedPDFWriter(doc);

            // ═══════════ PAGE 1: Header + Event Details + Attendance ═══════════
            w.drawHeader("EVENT REPORT");
            w.moveDown(30);

            // Event Name (large)
            w.drawText(sanitize(data.getEventName()), w.FONT_BOLD, 16, w.TEXT_DARK);
            w.moveDown(24);

            // Event details card
            w.drawCardStart();
            w.drawLabelValue("DATE", data.getEventDate() != null
                    ? data.getEventDate().format(DATE_FMT) : "Not specified");
            w.drawLabelValue("TIME", formatTimeRange(data.getEventStartTime(), data.getEventEndTime()));
            w.drawLabelValue("VENUE", sanitize(data.getVenueName()));
            w.drawLabelValue("CAMPUS", sanitize(data.getCampusName()));
            w.drawLabelValue("HOSTED BY", sanitize(data.getHostingSocietyName()));
            w.drawCardEnd();
            w.moveDown(24);

            // Attendance summary card
            w.drawSectionTitle("ATTENDANCE SUMMARY");
            w.drawCardStart();
            w.drawLabelValue("TOTAL RSVPs", String.valueOf(data.getTotalRSVPs()));
            w.drawLabelValue("ACTUAL ATTENDEES", String.valueOf(data.getTotalAttendees()));
            w.drawLabelValue("ATTENDANCE RATE", String.format("%.1f%%", data.getAttendanceRate()));
            w.drawCardEnd();
            w.moveDown(24);

            // ═══════════ EXECUTIVE REPORT SECTION ═══════════
            w.drawSectionTitle("EXECUTIVE REPORT");
            if (data.getExecutiveReports() == null || data.getExecutiveReports().isEmpty()) {
                w.drawText("No executive report has been submitted for this event.", w.FONT_OBLIQUE, 10, w.TEXT_MUTED);
                w.moveDown(16);
            } else {
                for (ExecutiveEventReportResponseDTO report : data.getExecutiveReports()) {
                    w.drawCardStart();
                    w.drawLabelValue("SUBMITTED BY",
                            sanitize(report.getExecutiveName()) + " (" + sanitize(report.getExecutivePosition()) + ")");
                    w.drawLabelValue("SOCIETY", sanitize(report.getSocietyName()));
                    w.drawSeparator();
                    w.drawLabelValue("EXPECTATIONS", sanitize(report.getExpectations()));
                    w.drawLabelValue("EXPECTATIONS MET", sanitize(report.getExpectationsMet()));
                    w.drawLabelValue("SUCCESS ASSESSMENT", sanitize(report.getSuccessAssessment()));
                    w.drawLabelValue("REASON", sanitize(report.getSuccessReason()));
                    w.drawLabelValue("IMPROVEMENTS", sanitize(report.getImprovements()));
                    w.drawLabelValue("ADVICE", sanitize(report.getAdvice()));
                    w.drawLabelValue("ATTENDEE COUNT",
                            report.getAttendeeCount() != null ? String.valueOf(report.getAttendeeCount()) : "-");
                    w.drawLabelValue("OVERALL RATING",
                            report.getOverallRating() != null ? report.getOverallRating() + " / 5" : "-");
                    w.drawLabelValue("ADDITIONAL NOTES", sanitize(report.getAdditionalNotes()));
                    w.drawCardEnd();
                    w.moveDown(16);
                }
            }

            // ═══════════ STUDENT FEEDBACK SUMMARY ═══════════
            w.drawSectionTitle("STUDENT FEEDBACK SUMMARY");
            w.drawCardStart();
            w.drawLabelValue("TOTAL RESPONSES", String.valueOf(data.getTotalFeedbackCount()));
            w.drawLabelValue("AVERAGE OVERALL", data.getAverageOverallRating() + " / 5");
            w.drawLabelValue("ORGANIZATION", data.getAverageOrganizationRating() + " / 5");
            w.drawLabelValue("VENUE", data.getAverageVenueRating() + " / 5");
            w.drawLabelValue("CONTENT", data.getAverageContentRating() + " / 5");
            w.drawLabelValue("WOULD RECOMMEND",
                    data.getWouldRecommendCount() + " of " + data.getTotalFeedbackCount() + " students");
            w.drawCardEnd();
            w.moveDown(24);

            // ═══════════ INDIVIDUAL STUDENT REVIEWS ═══════════
            w.drawSectionTitle("STUDENT REVIEWS");
            if (data.getStudentFeedback() == null || data.getStudentFeedback().isEmpty()) {
                w.drawText("No student feedback submitted.", w.FONT_OBLIQUE, 10, w.TEXT_MUTED);
            } else {
                int num = 1;
                for (EventFeedbackResponseDTO fb : data.getStudentFeedback()) {
                    w.drawReviewCard(num, fb);
                    num++;
                }
            }

            // ═══════════ FOOTER ═══════════
            w.moveDown(30);
            w.drawFooter();

            return w.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate PDF for event {}: {}", data.getEventID(), e.getMessage());
            throw new RuntimeException("Failed to generate event report PDF", e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAPPING HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private ExecutiveEventReportResponseDTO mapExecReport(ExecutiveEventReport report) {
        String execName = "Unknown Executive";
        String execPosition = "";

        Optional<Student> studentOpt = studentRepository.findById(report.getStudentNumber());
        if (studentOpt.isPresent()) {
            Student s = studentOpt.get();
            Optional<User> userOpt = userRepository.findById(s.getEmail());
            if (userOpt.isPresent()) {
                execName = userOpt.get().getFirstName() + " " + userOpt.get().getLastName();
            }
            // Find their executive position
            execPosition = executiveRepository.findByIdStudentNumber(s.getStudentNumber())
                    .stream()
                    .filter(e -> e.getId().getSocietyID().equals(report.getSocietyID()))
                    .filter(e -> e.getTermEndDate() == null || e.getTermEndDate().isAfter(LocalDate.now()))
                    .findFirst()
                    .map(Executive::getPosition)
                    .orElse("");
        }

        String societyName = societyRepository.findById(report.getSocietyID())
                .map(Society::getSocietyName).orElse("Unknown Society");

        return ExecutiveEventReportResponseDTO.builder()
                .reportID(report.getReportID())
                .eventID(report.getEventID())
                .societyID(report.getSocietyID())
                .societyName(societyName)
                .studentNumber(report.getStudentNumber())
                .executiveName(execName)
                .executivePosition(execPosition)
                .expectations(report.getExpectations())
                .expectationsMet(report.getExpectationsMet())
                .successAssessment(report.getSuccessAssessment())
                .successReason(report.getSuccessReason())
                .improvements(report.getImprovements())
                .advice(report.getAdvice())
                .attendeeCount(report.getAttendeeCount())
                .overallRating(report.getOverallRating())
                .additionalNotes(report.getAdditionalNotes())
                .submittedAt(report.getSubmittedAt())
                .build();
    }

    private EventFeedbackResponseDTO mapFeedback(EventFeedback fb) {
        String studentName = "Anonymous";
        Optional<Student> studentOpt = studentRepository.findById(fb.getStudentNumber());
        Optional<User> userOpt = Optional.empty();
        if (studentOpt.isPresent()) {
            userOpt = userRepository.findById(studentOpt.get().getEmail());
            if (userOpt.isPresent()) {
                User u = userOpt.get();
                studentName = (u.getFirstName() + " " + u.getLastName()).trim();
            }
        }

        return EventFeedbackResponseDTO.builder()
                .feedbackID(fb.getFeedbackID())
                .studentNumber(fb.getStudentNumber())
                .studentName(studentName)
                .hasProfilePicture(userOpt.isPresent() && userProfilePictureRepository != null
                        && userProfilePictureRepository.existsById(studentOpt.get().getEmail()))
                .rating(fb.getRating())
                .organizationRating(fb.getOrganizationRating())
                .venueRating(fb.getVenueRating())
                .contentRating(fb.getContentRating())
                .wouldRecommend(fb.getWouldRecommend())
                .highlights(fb.getHighlights())
                .improvements(fb.getImprovements())
                .description(fb.getDescription())
                .submittedAt(fb.getSubmittedAt())
                .build();
    }

    private String formatTimeRange(java.time.LocalTime start, java.time.LocalTime end) {
        if (start == null && end == null) return "Not specified";
        if (start != null && end != null) return start.format(TIME_FMT) + " - " + end.format(TIME_FMT);
        if (start != null) return start.format(TIME_FMT);
        return end.format(TIME_FMT);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replaceAll("[^\\x20-\\x7E]", " ").trim();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BRANDED PDF WRITER (matches ticket design)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Branded PDF writer that produces reports matching the SocietyCentral
     * ticket design: navy header, gold accents, card-based sections.
     */
    static class BrandedPDFWriter {
        private final PDDocument doc;
        private PDPageContentStream cs;
        private float y;

        // Brand colours (RGB 0-1)
        private static final float[] NAVY = {0.106f, 0.169f, 0.294f};
        private static final float[] NAVY_DARK = {0.071f, 0.122f, 0.220f};
        private static final float[] GOLD = {0.961f, 0.651f, 0.137f};
        private static final float[] WHITE = {1f, 1f, 1f};
        final float[] TEXT_DARK = {0.102f, 0.102f, 0.180f};
        final float[] TEXT_MUTED = {0.420f, 0.447f, 0.502f};
        private static final float[] BORDER = {0.898f, 0.906f, 0.922f};
        private static final float[] CARD_BG = {0.969f, 0.973f, 0.980f};
        private static final float[] SUCCESS = {0.086f, 0.639f, 0.290f};

        private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
        private static final float MARGIN = 50f;
        private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

        final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font FONT_OBLIQUE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        BrandedPDFWriter(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_HEIGHT;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < 60) {
                newPage();
                y -= 40; // top margin on continuation pages
            }
        }

        void moveDown(float amount) throws IOException {
            y -= amount;
            ensureSpace(0);
        }

        // ── Header (navy strip with branding) ──
        void drawHeader(String badgeText) throws IOException {
            float headerH = 80f;
            y -= headerH;

            // Navy background
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
            cs.addRect(0, y, PAGE_WIDTH, headerH);
            cs.fill();

            // Dark top accent
            cs.setNonStrokingColor(NAVY_DARK[0], NAVY_DARK[1], NAVY_DARK[2]);
            cs.addRect(0, y + headerH - 4, PAGE_WIDTH, 4);
            cs.fill();

            // "SOCIETY CENTRAL"
            cs.beginText();
            cs.setFont(FONT_BOLD, 22);
            cs.setNonStrokingColor(WHITE[0], WHITE[1], WHITE[2]);
            cs.newLineAtOffset(MARGIN, y + 38);
            cs.showText("SOCIETY CENTRAL");
            cs.endText();

            // "NELSON MANDELA UNIVERSITY"
            cs.beginText();
            cs.setFont(FONT_REGULAR, 10);
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.newLineAtOffset(MARGIN, y + 18);
            cs.showText("NELSON MANDELA UNIVERSITY");
            cs.endText();

            // Badge (gold rounded rect on right)
            float badgeW = 120f, badgeH = 30f;
            float badgeX = PAGE_WIDTH - MARGIN - badgeW - 10;
            float badgeY = y + 25;
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            drawRoundedRect(badgeX, badgeY, badgeW, badgeH, 15f);
            cs.fill();

            cs.beginText();
            cs.setFont(FONT_BOLD, 10);
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
            cs.newLineAtOffset(badgeX + 14, badgeY + 10);
            cs.showText(badgeText);
            cs.endText();

            y -= 20;
        }

        // ── Section title (gold accent bar + navy text) ──
        void drawSectionTitle(String text) throws IOException {
            ensureSpace(30);
            y -= 14;

            // Gold accent bar
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.addRect(MARGIN, y + 1, 35, 3);
            cs.fill();

            // Title text (next to the bar)
            cs.beginText();
            cs.setFont(FONT_BOLD, 11);
            cs.setNonStrokingColor(NAVY[0], NAVY[1], NAVY[2]);
            cs.newLineAtOffset(MARGIN + 42, y);
            cs.showText(text);
            cs.endText();
            y -= 16;
        }

        // ── Card container (light background + border + gold left accent) ──
        private float cardStartY;

        void drawCardStart() throws IOException {
            ensureSpace(40);
            cardStartY = y;
            y -= 12; // inner padding top
        }

        void drawCardEnd() throws IOException {
            y -= 8; // inner padding bottom
            float cardH = cardStartY - y;

            // Border only (no background fill - it would cover text)
            cs.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
            cs.setLineWidth(0.5f);
            cs.addRect(MARGIN, y, CONTENT_WIDTH, cardH);
            cs.stroke();

            // Gold left accent bar
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.addRect(MARGIN, y, 4, cardH);
            cs.fill();

            y -= 8;
        }

        void drawSeparator() throws IOException {
            y -= 4;
            cs.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
            cs.setLineWidth(0.3f);
            cs.moveTo(MARGIN + 16, y);
            cs.lineTo(PAGE_WIDTH - MARGIN - 16, y);
            cs.stroke();
            y -= 8;
        }

        // ── Label: Value pair ──
        void drawLabelValue(String label, String value) throws IOException {
            ensureSpace(30);
            String safeValue = (value == null || value.isBlank()) ? "-" : value.replaceAll("[^\\x20-\\x7E]", " ").trim();
            java.util.List<String> lines = wrapText(safeValue, 65);

            // Label (gold, small)
            cs.beginText();
            cs.setFont(FONT_BOLD, 8);
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.newLineAtOffset(MARGIN + 16, y);
            cs.showText(label);
            cs.endText();
            y -= 13;

            // Value (dark, regular)
            for (String line : lines) {
                ensureSpace(14);
                cs.beginText();
                cs.setFont(FONT_REGULAR, 10);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                cs.newLineAtOffset(MARGIN + 16, y);
                cs.showText(line);
                cs.endText();
                y -= 14;
            }
            y -= 4;
        }

        // ── Generic text ──
        void drawText(String text, PDType1Font font, int size, float[] color) throws IOException {
            ensureSpace(20);
            String safe = (text == null) ? "" : text.replaceAll("[^\\x20-\\x7E]", " ").trim();
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(safe);
            cs.endText();
            y -= size + 4;
        }

        // ── Student review card (styled like online store review) ──
        void drawReviewCard(int num, EventFeedbackResponseDTO fb) throws IOException {
            ensureSpace(80);
            float reviewStart = y;
            y -= 8;

            // Reviewer name + stars
            String stars = fb.getRating() != null
                    ? " ".repeat(0) + fb.getRating() + "/5"
                    : "";
            cs.beginText();
            cs.setFont(FONT_BOLD, 10);
            cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
            cs.newLineAtOffset(MARGIN + 16, y);
            cs.showText("#" + num + "  " + (fb.getStudentName() != null
                    ? fb.getStudentName().replaceAll("[^\\x20-\\x7E]", "") : "Anonymous"));
            cs.endText();

            // Rating on the right
            if (fb.getRating() != null) {
                String ratingText = ratingStars(fb.getRating()) + "  " + fb.getRating() + "/5";
                cs.beginText();
                cs.setFont(FONT_BOLD, 9);
                cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - 80, y);
                cs.showText(ratingText);
                cs.endText();
            }
            y -= 16;

            // Highlights
            if (fb.getHighlights() != null && !fb.getHighlights().isBlank()) {
                drawReviewField("Highlights", fb.getHighlights(), SUCCESS);
            }
            // Improvements
            if (fb.getImprovements() != null && !fb.getImprovements().isBlank()) {
                drawReviewField("Could improve", fb.getImprovements(), TEXT_MUTED);
            }
            // Comment
            if (fb.getDescription() != null && !fb.getDescription().isBlank()) {
                drawReviewField("Comment", fb.getDescription(), TEXT_MUTED);
            }

            y -= 6;

            // Draw background for the review card
            float reviewH = reviewStart - y;
            // Light border at bottom
            cs.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
            cs.setLineWidth(0.3f);
            cs.moveTo(MARGIN + 16, y);
            cs.lineTo(PAGE_WIDTH - MARGIN - 16, y);
            cs.stroke();
            y -= 10;
        }

        private void drawReviewField(String label, String value, float[] color) throws IOException {
            String safe = value.replaceAll("[^\\x20-\\x7E]", " ").trim();
            java.util.List<String> lines = wrapText(safe, 70);
            ensureSpace(14 * (lines.size() + 1));

            cs.beginText();
            cs.setFont(FONT_OBLIQUE, 8);
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.newLineAtOffset(MARGIN + 16, y);
            cs.showText(label + ":");
            cs.endText();
            y -= 12;

            for (String line : lines) {
                cs.beginText();
                cs.setFont(FONT_REGULAR, 9);
                cs.setNonStrokingColor(TEXT_DARK[0], TEXT_DARK[1], TEXT_DARK[2]);
                cs.newLineAtOffset(MARGIN + 24, y);
                cs.showText(line);
                cs.endText();
                y -= 12;
            }
            y -= 4;
        }

        // ── Footer ──
        void drawFooter() throws IOException {
            // Separator line
            cs.setStrokingColor(BORDER[0], BORDER[1], BORDER[2]);
            cs.setLineWidth(0.5f);
            cs.moveTo(MARGIN, y);
            cs.lineTo(PAGE_WIDTH - MARGIN, y);
            cs.stroke();
            y -= 16;

            String timestamp = "Report generated on " + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd MMMM yyyy 'at' HH:mm"));

            cs.beginText();
            cs.setFont(FONT_REGULAR, 7);
            cs.setNonStrokingColor(TEXT_MUTED[0], TEXT_MUTED[1], TEXT_MUTED[2]);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Society Central  |  Nelson Mandela University  |  " + timestamp);
            cs.endText();
        }

        // ── Helpers ──
        private String ratingStars(int rating) {
            return "*".repeat(Math.max(0, rating)) + ".".repeat(Math.max(0, 5 - rating));
        }

        byte[] toByteArray() throws IOException {
            if (cs != null) cs.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }

        private java.util.List<String> wrapText(String text, int maxChars) {
            if (text == null || text.isBlank()) return java.util.List.of("-");
            java.util.List<String> lines = new java.util.ArrayList<>();
            String[] words = text.split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                if (current.length() + word.length() + 1 > maxChars) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    if (!current.isEmpty()) current.append(" ");
                    current.append(word);
                }
            }
            if (!current.isEmpty()) lines.add(current.toString());
            return lines;
        }

        private void drawRoundedRect(float x, float ry, float w, float h, float r) throws IOException {
            float k = 0.5523f;
            float kr = k * r;
            cs.moveTo(x + r, ry);
            cs.lineTo(x + w - r, ry);
            cs.curveTo(x + w - r + kr, ry, x + w, ry + r - kr, x + w, ry + r);
            cs.lineTo(x + w, ry + h - r);
            cs.curveTo(x + w, ry + h - r + kr, x + w - r + kr, ry + h, x + w - r, ry + h);
            cs.lineTo(x + r, ry + h);
            cs.curveTo(x + r - kr, ry + h, x, ry + h - r + kr, x, ry + h - r);
            cs.lineTo(x, ry + r);
            cs.curveTo(x, ry + r - kr, x + r - kr, ry, x + r, ry);
            cs.closePath();
        }
    }
}
