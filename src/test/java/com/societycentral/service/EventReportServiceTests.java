package com.societycentral.service;

import com.societycentral.dto.response.EventReportSummaryDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventReportService covering:
 * - Report data assembly (buildReportData)
 * - PDF generation (generatePDF / generatePDFFromData)
 * - Edge cases: no feedback, no executive report, no hosters
 * - Report readiness check (7-day rule)
 */
@ExtendWith(MockitoExtension.class)
class EventReportServiceTests {

    private static final String EVENT_ID = "EVT001";
    private static final String SOCIETY_ID = "SOC001";
    private static final String STUDENT_NUM = "s220000001";
    private static final String STUDENT_EMAIL = "student@nmu.ac.za";
    private static final String SDO_STAFF = "NMU001";

    @Mock private EventRepository eventRepository;
    @Mock private EventFeedbackRepository feedbackRepository;
    @Mock private ExecutiveEventReportRepository execReportRepository;
    @Mock private HosterRepository hosterRepository;
    @Mock private RSVPRepository rsvpRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private SocietyRepository societyRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ExecutiveRepository executiveRepository;
    @Mock private UserRepository userRepository;

    private EventReportService service;

    @BeforeEach
    void setUp() {
        service = new EventReportService(
                eventRepository,
                feedbackRepository,
                execReportRepository,
                hosterRepository,
                rsvpRepository,
                venueRepository,
                societyRepository,
                studentRepository,
                executiveRepository,
                userRepository
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // buildReportData TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void buildReportData_assemblesCompleteReport() {
        // Setup event
        Event event = createEvent(LocalDate.now().minusDays(10));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Setup hoster
        Hoster hoster = createHoster();
        when(hosterRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of(hoster));

        // Setup society
        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("CS Society");
        when(societyRepository.findById(SOCIETY_ID)).thenReturn(Optional.of(society));

        // Setup venue
        Venue venue = new Venue();
        venue.setVenueCode("SC001");
        venue.setVenueName("Auditorium");
        when(venueRepository.findById("SC001")).thenReturn(Optional.of(venue));

        // Setup RSVPs (3 total, 2 attended)
        RSVP rsvp1 = createRsvp("s1", true);
        RSVP rsvp2 = createRsvp("s2", true);
        RSVP rsvp3 = createRsvp("s3", false);
        when(rsvpRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of(rsvp1, rsvp2, rsvp3));

        // Setup exec report
        ExecutiveEventReport execReport = createExecReport();
        when(execReportRepository.findAll()).thenReturn(List.of(execReport));

        // Setup student for exec report mapping
        Student student = new Student();
        student.setStudentNumber(STUDENT_NUM);
        student.setEmail(STUDENT_EMAIL);
        when(studentRepository.findById(STUDENT_NUM)).thenReturn(Optional.of(student));

        User user = new User();
        user.setEmail(STUDENT_EMAIL);
        user.setFirstName("John");
        user.setLastName("Doe");
        when(userRepository.findById(STUDENT_EMAIL)).thenReturn(Optional.of(user));

        Executive executive = createExecutive();
        when(executiveRepository.findByIdStudentNumber(STUDENT_NUM)).thenReturn(List.of(executive));

        // Setup feedback
        EventFeedback fb1 = createFeedback("FB001", "s1", 4);
        EventFeedback fb2 = createFeedback("FB002", "s2", 5);
        when(feedbackRepository.findByEventID(EVENT_ID)).thenReturn(List.of(fb1, fb2));

        // Student lookups for feedback mapping
        Student s1 = new Student(); s1.setStudentNumber("s1"); s1.setEmail("s1@nmu.ac.za");
        Student s2 = new Student(); s2.setStudentNumber("s2"); s2.setEmail("s2@nmu.ac.za");
        when(studentRepository.findById("s1")).thenReturn(Optional.of(s1));
        when(studentRepository.findById("s2")).thenReturn(Optional.of(s2));

        User u1 = new User(); u1.setFirstName("Alice"); u1.setLastName("Smith");
        User u2 = new User(); u2.setFirstName("Bob"); u2.setLastName("Jones");
        when(userRepository.findById("s1@nmu.ac.za")).thenReturn(Optional.of(u1));
        when(userRepository.findById("s2@nmu.ac.za")).thenReturn(Optional.of(u2));

        // Execute
        EventReportSummaryDTO result = service.buildReportData(EVENT_ID);

        // Verify event info
        assertEquals(EVENT_ID, result.getEventID());
        assertEquals("Test Event", result.getEventName());
        assertEquals("Auditorium", result.getVenueName());
        assertEquals("CS Society", result.getHostingSocietyName());

        // Verify attendance
        assertEquals(3, result.getTotalRSVPs());
        assertEquals(2, result.getTotalAttendees());
        assertEquals(66.7, result.getAttendanceRate(), 0.001);

        // Verify ratings
        assertEquals(4.5, result.getAverageOverallRating());
        assertEquals(2, result.getTotalFeedbackCount());

        // Verify executive report
        assertEquals(1, result.getExecutiveReports().size());
        assertEquals("John Doe", result.getExecutiveReports().get(0).getExecutiveName());
        assertEquals("President", result.getExecutiveReports().get(0).getExecutivePosition());

        // Verify student feedback
        assertEquals(2, result.getStudentFeedback().size());
        assertEquals("Alice S.", result.getStudentFeedback().get(0).getStudentName());

        // Verify report readiness (10 days ago = ready)
        assertTrue(result.isReportReady());
    }

    @Test
    void buildReportData_eventNotFound_throws() {
        when(eventRepository.findById("NONE")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.buildReportData("NONE"));
    }

    @Test
    void buildReportData_noFeedback_returnsZeroAverages() {
        Event event = createEvent(LocalDate.now().minusDays(10));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(hosterRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of());
        when(rsvpRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of());
        when(execReportRepository.findAll()).thenReturn(List.of());
        when(feedbackRepository.findByEventID(EVENT_ID)).thenReturn(List.of());

        EventReportSummaryDTO result = service.buildReportData(EVENT_ID);

        assertEquals(0.0, result.getAverageOverallRating());
        assertEquals(0, result.getTotalFeedbackCount());
        assertTrue(result.getStudentFeedback().isEmpty());
        assertTrue(result.getExecutiveReports().isEmpty());
    }

    @Test
    void buildReportData_eventLessThan7Days_notReady() {
        Event event = createEvent(LocalDate.now().minusDays(3));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(hosterRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of());
        when(rsvpRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of());
        when(execReportRepository.findAll()).thenReturn(List.of());
        when(feedbackRepository.findByEventID(EVENT_ID)).thenReturn(List.of());

        EventReportSummaryDTO result = service.buildReportData(EVENT_ID);

        assertFalse(result.isReportReady());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PDF GENERATION TESTS
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void generatePDFFromData_producesValidPDF() {
        EventReportSummaryDTO data = EventReportSummaryDTO.builder()
                .eventID(EVENT_ID)
                .eventName("Test Event")
                .eventDate(LocalDate.of(2026, 7, 15))
                .eventStartTime(LocalTime.of(10, 0))
                .eventEndTime(LocalTime.of(12, 0))
                .venueName("Main Hall")
                .campusName("SOUTH_CAMPUS")
                .hostingSocietyName("CS Society")
                .hostingSocietyID(SOCIETY_ID)
                .totalRSVPs(50)
                .totalAttendees(42)
                .averageOverallRating(4.2)
                .averageOrganizationRating(3.8)
                .averageVenueRating(4.5)
                .averageContentRating(4.0)
                .totalFeedbackCount(20)
                .wouldRecommendCount(18)
                .executiveReports(List.of())
                .studentFeedback(List.of())
                .reportReady(true)
                .reportGeneratedMessage("Report generated successfully.")
                .build();

        byte[] pdf = service.generatePDFFromData(data);

        // PDF must start with %PDF header
        assertNotNull(pdf);
        assertTrue(pdf.length > 100, "PDF should have meaningful content");
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generatePDFFromData_withFeedbackAndReport_producesLargerPDF() {
        var feedback = com.societycentral.dto.response.EventFeedbackResponseDTO.builder()
                .feedbackID("FB001")
                .studentName("Alice S.")
                .rating(5)
                .highlights("Great event")
                .improvements("More seating")
                .description("Loved it")
                .build();

        var execReport = com.societycentral.dto.response.ExecutiveEventReportResponseDTO.builder()
                .reportID("RPT001")
                .executiveName("John Doe")
                .executivePosition("President")
                .societyName("CS Society")
                .expectations("High turnout")
                .expectationsMet("YES")
                .successAssessment("SUCCESS")
                .successReason("Great engagement")
                .improvements("Better promotion")
                .advice("Start planning early")
                .attendeeCount(42)
                .overallRating(5)
                .additionalNotes("None")
                .build();

        EventReportSummaryDTO data = EventReportSummaryDTO.builder()
                .eventID(EVENT_ID)
                .eventName("Annual Gala Night")
                .eventDate(LocalDate.of(2026, 7, 1))
                .eventStartTime(LocalTime.of(18, 0))
                .eventEndTime(LocalTime.of(22, 0))
                .venueName("Indoor Sport Centre")
                .campusName("SOUTH_CAMPUS")
                .hostingSocietyName("CS Society")
                .hostingSocietyID(SOCIETY_ID)
                .totalRSVPs(100)
                .totalAttendees(85)
                .averageOverallRating(4.5)
                .averageOrganizationRating(4.2)
                .averageVenueRating(4.8)
                .averageContentRating(4.3)
                .totalFeedbackCount(30)
                .wouldRecommendCount(28)
                .executiveReports(List.of(execReport))
                .studentFeedback(List.of(feedback))
                .reportReady(true)
                .reportGeneratedMessage("Report generated successfully.")
                .build();

        byte[] pdf = service.generatePDFFromData(data);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "PDF with content should be substantial");
        String header = new String(pdf, 0, 4);
        assertEquals("%PDF", header);
    }

    @Test
    void generatePDFFromData_handlesNullFields_gracefully() {
        // All nullable fields are null
        EventReportSummaryDTO data = EventReportSummaryDTO.builder()
                .eventID(EVENT_ID)
                .eventName("Minimal Event")
                .eventDate(null)
                .eventStartTime(null)
                .eventEndTime(null)
                .venueName(null)
                .campusName(null)
                .hostingSocietyName(null)
                .hostingSocietyID(null)
                .totalRSVPs(0)
                .totalAttendees(0)
                .averageOverallRating(0.0)
                .averageOrganizationRating(0.0)
                .averageVenueRating(0.0)
                .averageContentRating(0.0)
                .totalFeedbackCount(0)
                .wouldRecommendCount(0)
                .executiveReports(null)
                .studentFeedback(null)
                .reportReady(false)
                .reportGeneratedMessage("Not ready.")
                .build();

        // Should not throw
        byte[] pdf = service.generatePDFFromData(data);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void generatePDFFromData_handlesSpecialCharacters_inText() {
        var feedback = com.societycentral.dto.response.EventFeedbackResponseDTO.builder()
                .feedbackID("FB001")
                .studentName("Thandeka M.")
                .rating(4)
                .highlights("Great event! The food was amazing \u2014 loved it")
                .improvements("More chairs \u2013 standing for 2hrs is tough")
                .description("Overall good\u2026")
                .build();

        EventReportSummaryDTO data = EventReportSummaryDTO.builder()
                .eventID(EVENT_ID)
                .eventName("Event with unicode \u2014 Special!")
                .eventDate(LocalDate.of(2026, 8, 1))
                .eventStartTime(LocalTime.of(9, 0))
                .eventEndTime(LocalTime.of(11, 0))
                .venueName("Hall A")
                .campusName("NORTH_CAMPUS")
                .hostingSocietyName("Drama Society")
                .hostingSocietyID("SOC002")
                .totalRSVPs(20)
                .totalAttendees(15)
                .averageOverallRating(4.0)
                .averageOrganizationRating(3.5)
                .averageVenueRating(4.0)
                .averageContentRating(4.5)
                .totalFeedbackCount(10)
                .wouldRecommendCount(8)
                .executiveReports(List.of())
                .studentFeedback(List.of(feedback))
                .reportReady(true)
                .reportGeneratedMessage("Generated.")
                .build();

        // Should handle unicode gracefully (sanitizer strips non-ASCII)
        byte[] pdf = service.generatePDFFromData(data);
        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private Event createEvent(LocalDate date) {
        Event e = new Event();
        e.setEventID(EVENT_ID);
        e.setEventName("Test Event");
        e.setEventDate(date);
        e.setEventStartTime(LocalTime.of(10, 0));
        e.setEventEndTime(LocalTime.of(12, 0));
        e.setVenueCode("SC001");
        e.setEventCampus(Campus.SOUTH_CAMPUS);
        e.setEventStatus(EventStatus.PUBLISHED);
        return e;
    }

    private Hoster createHoster() {
        Hoster h = new Hoster();
        HosterId id = new HosterId();
        id.setEventID(EVENT_ID);
        id.setSocietyID(SOCIETY_ID);
        h.setId(id);
        h.setIsPrimary(true);
        return h;
    }

    private RSVP createRsvp(String studentNumber, boolean scanned) {
        RSVP r = new RSVP();
        RsvpId id = new RsvpId(EVENT_ID, studentNumber);
        r.setId(id);
        r.setScannedStatus(scanned);
        return r;
    }

    private ExecutiveEventReport createExecReport() {
        ExecutiveEventReport r = new ExecutiveEventReport();
        r.setReportID("RPT001");
        r.setEventID(EVENT_ID);
        r.setSocietyID(SOCIETY_ID);
        r.setStudentNumber(STUDENT_NUM);
        r.setExpectations("High turnout");
        r.setExpectationsMet("YES");
        r.setSuccessAssessment("SUCCESS");
        r.setSuccessReason("Good engagement");
        r.setImprovements("Better promo");
        r.setAdvice("Plan early");
        r.setAttendeeCount(42);
        r.setOverallRating(5);
        r.setAdditionalNotes("None");
        r.setSubmittedAt(LocalDateTime.now());
        return r;
    }

    private Executive createExecutive() {
        Executive e = new Executive();
        ExecutiveId id = new ExecutiveId();
        id.setStudentNumber(STUDENT_NUM);
        id.setSocietyID(SOCIETY_ID);
        id.setTermStartDate(LocalDate.of(2026, 1, 1));
        e.setId(id);
        e.setTermEndDate(null); // active
        e.setPosition("President");
        return e;
    }

    private EventFeedback createFeedback(String fbID, String studentNum, int rating) {
        EventFeedback fb = new EventFeedback();
        fb.setFeedbackID(fbID);
        fb.setEventID(EVENT_ID);
        fb.setStudentNumber(studentNum);
        fb.setRating(rating);
        fb.setOrganizationRating(rating);
        fb.setVenueRating(rating);
        fb.setContentRating(rating);
        fb.setWouldRecommend(true);
        fb.setHighlights("Great event");
        fb.setImprovements("More food");
        fb.setDescription("Enjoyed it");
        fb.setSubmittedAt(LocalDateTime.now());
        return fb;
    }
}
