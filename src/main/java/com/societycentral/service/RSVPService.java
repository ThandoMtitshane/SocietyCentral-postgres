package com.societycentral.service;

import com.societycentral.dto.response.RSVPResponseDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the full RSVP lifecycle:
 * - Resolving student identity from token or authentication
 * - Capacity checking with pessimistic locking
 * - QR code ticket generation
 * - PDF ticket generation via RSVPTicketService
 * - Email confirmation via EmailService
 */
@Service
@Slf4j
public class RSVPService {

    private final RSVPRepository rsvpRepository;
    private final EventRepository eventRepository;
    private final StudentRepository studentRepository;
    private final HosterRepository hosterRepository;
    private final VenueRepository venueRepository;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final RSVPTicketService rsvpTicketService;
    private final ExecutiveSocietyResolver executiveSocietyResolver;
    private final Clock clock;
    private final ExecutiveRepository executiveRepository;
    private final EventAttendanceEligibility attendanceEligibility;

    @Autowired
    public RSVPService(RSVPRepository rsvpRepository,
                       EventRepository eventRepository,
                       StudentRepository studentRepository,
                       HosterRepository hosterRepository,
                       VenueRepository venueRepository,
                       JwtUtil jwtUtil,
                       EmailService emailService,
                       RSVPTicketService rsvpTicketService,
                       ExecutiveSocietyResolver executiveSocietyResolver,
                       Clock clock,
                       ExecutiveRepository executiveRepository,
                       EventAttendanceEligibility attendanceEligibility) {
        this.rsvpRepository = rsvpRepository;
        this.eventRepository = eventRepository;
        this.studentRepository = studentRepository;
        this.hosterRepository = hosterRepository;
        this.venueRepository = venueRepository;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
        this.rsvpTicketService = rsvpTicketService;
        this.executiveSocietyResolver = executiveSocietyResolver;
        this.clock = clock;
        this.executiveRepository = executiveRepository;
        this.attendanceEligibility = attendanceEligibility;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIRM RSVP
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Confirms a student's RSVP for an event.
     * Supports both token-based (email link) and authenticated (logged-in) flows.
     *
     * Uses pessimistic locking on RSVP count to prevent race conditions
     * when multiple students RSVP simultaneously near capacity.
     */
    @Transactional
    public RSVPResponseDTO confirmRsvp(String eventID, String token, Authentication authentication) {
        // 1. Resolve student identity
        Student student = resolveStudent(token, authentication);

        // 2. Load and validate the event
        Event event = eventRepository.findByIdForUpdate(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        validateEventForRsvp(event, token);

        if (token == null || token.isBlank()) {
            boolean ownsEventSociety = hosterRepository.findByIdEventID(eventID).stream()
                    .filter(hoster -> Boolean.TRUE.equals(hoster.getIsPrimary()))
                    .map(hoster -> hoster.getId().getSocietyID())
                    .anyMatch(societyID -> executiveRepository.existsActiveExecutiveRole(
                            student.getStudentNumber(), societyID, java.time.LocalDate.now(clock)));
            if (ownsEventSociety) {
                throw new IllegalStateException("Executives cannot RSVP to their own society events.");
            }
        }

        attendanceEligibility.requireEligibleStudent(event, student.getStudentNumber());

        // 3. Check for duplicate RSVP
        RsvpId rsvpId = new RsvpId(eventID, student.getStudentNumber());
        if (rsvpRepository.existsById(rsvpId)) {
            throw new IllegalStateException("You have already RSVP'd for this event.");
        }

        // 4. Capacity check with pessimistic lock
        long currentCount = rsvpRepository.countByEventIDWithLock(eventID);
        Integer eventLimit = event.getEventLimit();

        if (eventLimit != null && eventLimit > 0 && currentCount >= eventLimit) {
            throw new IllegalStateException(
                    "This event has reached full capacity. No more RSVPs can be accepted.");
        }

        // 5. Generate unique QR code ticket reference
        String rsvpReference = generateRsvpReference(eventID);
        String qrCodeTicket = rsvpReference;

        // 6. Create and save the RSVP
        RSVP rsvp = new RSVP();
        rsvp.setId(rsvpId);
        rsvp.setEvent(event);
        rsvp.setStudent(student);
        rsvp.setQrCodeTicket(qrCodeTicket);
        rsvp.setScannedStatus(false);
        rsvp.setRsvpCreatedAt(LocalDateTime.now(clock));

        rsvpRepository.save(rsvp);

        // 7. Build the response DTO
        RSVPResponseDTO responseDTO = buildResponseDTO(rsvp, event, student, currentCount + 1);

        // 8. Send confirmation email with PDF ticket (async-safe: failures are swallowed)
        sendConfirmationEmail(rsvp, event, student, responseDTO);

        log.info("RSVP confirmed: student={} event={} ref={}",
                student.getStudentNumber(), eventID, rsvpReference);

        return responseDTO;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET RSVP STATUS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks whether the student already has an RSVP for the given event.
     * Returns null if no RSVP exists.
     */
    @Transactional(readOnly = true)
    public RSVPResponseDTO getRsvpStatus(String eventID, String token, Authentication authentication) {
        Student student = resolveStudent(token, authentication);

        RsvpId rsvpId = new RsvpId(eventID, student.getStudentNumber());
        Optional<RSVP> existingRsvp = rsvpRepository.findById(rsvpId);

        if (existingRsvp.isEmpty()) {
            return null;
        }

        RSVP rsvp = existingRsvp.get();
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        long currentCount = rsvpRepository.countByIdEventID(eventID);
        return buildResponseDTO(rsvp, event, student, currentCount);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GENERATE TICKET PDF
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generates the branded PDF ticket for an existing RSVP.
     */
    @Transactional(readOnly = true)
    public byte[] generateTicketPdf(String eventID, String token, Authentication authentication) {
        Student student = resolveStudent(token, authentication);

        RsvpId rsvpId = new RsvpId(eventID, student.getStudentNumber());
        RSVP rsvp = rsvpRepository.findById(rsvpId)
                .orElseThrow(() -> new IllegalStateException(
                        "No RSVP found. Please confirm your attendance first."));

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        long currentCount = rsvpRepository.countByIdEventID(eventID);
        RSVPResponseDTO dto = buildResponseDTO(rsvp, event, student, currentCount);

        return rsvpTicketService.generateTicketPdf(dto);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LEGACY METHODS (kept for backward compatibility)
    // ═══════════════════════════════════════════════════════════════════════════

    public List<RSVP> findAll() {
        return rsvpRepository.findAll();
    }

    public Optional<RSVP> findById(RsvpId id) {
        return rsvpRepository.findById(id);
    }

    public List<RSVP> findByEventID(String eventID) {
        return rsvpRepository.findByIdEventID(eventID);
    }

    public List<RSVP> findByStudentNumber(String studentNumber) {
        return rsvpRepository.findByIdStudentNumber(studentNumber);
    }

    public long countAttendees(String eventID) {
        return rsvpRepository.countByIdEventIDAndScannedStatusTrue(eventID);
    }

    public RSVP scanQrCode(String qrCodeTicket) {
        return scanQrCode(qrCodeTicket, null);
    }

    public RSVP scanQrCode(String qrCodeTicket, String checkedInBy) {
        RSVP rsvp = rsvpRepository.findByQrCodeTicket(qrCodeTicket)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR code"));

        if (Boolean.TRUE.equals(rsvp.getScannedStatus())) {
            throw new IllegalStateException("QR code has already been scanned");
        }

        return checkIn(rsvp, "QR", checkedInBy);
    }

    @Transactional
    public RSVP checkInByStudent(String eventID, String studentNumber,
                                 String authenticatedExecutiveEmail) {
        var executiveContext = executiveSocietyResolver.resolve(
                authenticatedExecutiveEmail);

        eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        if (!hosterRepository.existsByIdEventIDAndIdSocietyID(
                eventID, executiveContext.society().getSocietyID())) {
            throw new ForbiddenOperationException(
                    "Executive is not authorised to manage this event.");
        }

        RSVP rsvp = rsvpRepository.findById(new RsvpId(eventID, studentNumber))
                .orElseThrow(() -> new IllegalArgumentException("Confirmed RSVP not found."));
        return checkIn(rsvp, "MANUAL", authenticatedExecutiveEmail);
    }

    private RSVP checkIn(RSVP rsvp, String method, String checkedInBy) {
        if (Boolean.TRUE.equals(rsvp.getScannedStatus())) {
            throw new IllegalStateException("Guest has already been checked in.");
        }
        rsvp.setScannedStatus(true);
        rsvp.setScannedAt(LocalDateTime.now());
        rsvp.setCheckInMethod(method);
        rsvp.setCheckedInBy(checkedInBy);
        return rsvpRepository.save(rsvp);
    }

    public Optional<RSVP> findByQrCodeTicket(String qrCodeTicket) {
        return rsvpRepository.findByQrCodeTicket(qrCodeTicket);
    }

    public void deleteById(RsvpId id) {
        rsvpRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves the student from either the RSVP token or the authentication context.
     * Token takes priority (allows unauthenticated RSVP via email link).
     */
    private Student resolveStudent(String token, Authentication authentication) {
        if (token != null && !token.isBlank()) {
            return resolveStudentFromToken(token);
        }

        if (authentication != null && authentication.isAuthenticated()) {
            return resolveStudentFromAuth(authentication);
        }

        throw new IllegalArgumentException(
                "Authentication is required. Please log in or use the RSVP link from your email.");
    }

    private Student resolveStudentFromToken(String token) {
        try {
            Claims claims = jwtUtil.parseRSVPToken(token);
            String studentNumber = claims.getSubject();

            return studentRepository.findById(studentNumber)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Student account not found. Please contact administration."));
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new IllegalArgumentException("This RSVP link has expired.");
        } catch (io.jsonwebtoken.JwtException e) {
            throw new IllegalArgumentException("Invalid RSVP link. Please request a new one.");
        }
    }

    private Student resolveStudentFromAuth(Authentication authentication) {
        String email = authentication.getName();

        return studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Only students can RSVP for events."));
    }

    /**
     * Validates that the event is in a valid state for accepting RSVPs.
     */
    private void validateEventForRsvp(Event event, String token) {
        // Event must be PUBLISHED
        if (event.getEventStatus() != EventStatus.PUBLISHED) {
            throw new IllegalStateException("This event is not currently accepting RSVPs.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (event.getRsvpOpenDate() != null && now.isBefore(event.getRsvpOpenDate())) {
            throw new IllegalStateException("RSVP opens on "
                    + event.getRsvpOpenDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy 'at' HH:mm")) + ".");
        }
        if (event.getRsvpCloseDate() != null && !now.isBefore(event.getRsvpCloseDate())) {
            throw new IllegalStateException("RSVP for this event is closed.");
        }
    }

    /**
     * Generates a unique, human-readable RSVP reference.
     * Format: RSVP-EVT{year}-{monthDay}-{random8digits}
     * Example: RSVP-EVT2026-0805-42917361
     */
    private String generateRsvpReference(String eventID) {
        LocalDateTime now = LocalDateTime.now(clock);
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.valueOf((long) (Math.random() * 90000000L) + 10000000L);
        return "RSVP-" + eventID + "-" + datePart + "-" + randomPart;
    }

    /**
     * Builds the response DTO with all event, student, venue, and society info.
     */
    private RSVPResponseDTO buildResponseDTO(RSVP rsvp, Event event, Student student, long currentCount) {
        // Resolve venue name
        String venueName = resolveVenueName(event);

        // Resolve primary society
        String societyName = "";
        String societyID = "";
        List<Hoster> hosters = hosterRepository.findByIdEventID(event.getEventID());
        Optional<Hoster> primaryHoster = hosters.stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst();

        if (primaryHoster.isPresent()) {
            societyName = primaryHoster.get().getSociety().getSocietyName();
            societyID = primaryHoster.get().getSociety().getSocietyID();
        } else if (!hosters.isEmpty()) {
            societyName = hosters.getFirst().getSociety().getSocietyName();
            societyID = hosters.getFirst().getSociety().getSocietyID();
        }

        // Resolve campus label
        String campus = event.getEventCampus() != null
                ? event.getEventCampus().name()
                : "";

        // Resolve student display name
        String studentName = "";
        if (student.getUser() != null) {
            studentName = student.getUser().getFirstName() + " " + student.getUser().getLastName();
        }

        // Calculate remaining spaces
        Integer eventLimit = event.getEventLimit();
        Integer remainingSpaces = null;
        if (eventLimit != null && eventLimit > 0) {
            remainingSpaces = (int) Math.max(0, eventLimit - currentCount);
        }

        return RSVPResponseDTO.builder()
                .rsvpReference(rsvp.getQrCodeTicket())
                .qrCodeTicket(rsvp.getQrCodeTicket())
                .studentNumber(student.getStudentNumber())
                .studentName(studentName)
                .eventID(event.getEventID())
                .eventName(event.getEventName())
                .eventDescription(event.getEventDescription())
                .eventDate(event.getEventDate())
                .eventStartTime(event.getEventStartTime())
                .eventEndTime(event.getEventEndTime())
                .venueName(venueName)
                .campus(campus)
                .societyName(societyName)
                .societyID(societyID)
                .eventLimit(eventLimit)
                .confirmedRSVPs((int) currentCount)
                .remainingSpaces(remainingSpaces)
                .posterUrl(event.getPosterUrl())
                .bannerUrl(event.getBannerUrl())
                .build();
    }

    /**
     * Resolves the human-readable venue name from either the Venue table
     * or the legacy eventVenue column.
     */
    private String resolveVenueName(Event event) {
        if (event.getVenueCode() != null && !event.getVenueCode().isBlank()) {
            return venueRepository.findById(event.getVenueCode())
                    .map(v -> v.getVenueName() + ", " + getCampusLabel(v.getCampus()))
                    .orElse(event.getEventVenue() != null ? event.getEventVenue() : "Venue to be confirmed");
        }

        return event.getEventVenue() != null ? event.getEventVenue() : "Venue to be confirmed";
    }

    private String getCampusLabel(Campus campus) {
        if (campus == null) return "";
        return switch (campus) {
            case SOUTH_CAMPUS -> "South Campus";
            case NORTH_CAMPUS -> "North Campus";
            case SECOND_AVENUE_CAMPUS -> "Second Avenue Campus";
            case MISSIONVALE_CAMPUS -> "Missionvale Campus";
            case GEORGE_CAMPUS -> "George Campus";
            case BIRD_STREET_CAMPUS -> "Bird Street Campus";
            case OCEAN_SCIENCES_CAMPUS -> "Ocean Sciences Campus";
        };
    }

    /**
     * Sends the RSVP confirmation email with the PDF ticket attached.
     * Failures are logged and swallowed — never crash the main RSVP flow.
     */
    private void sendConfirmationEmail(RSVP rsvp, Event event, Student student, RSVPResponseDTO dto) {
        try {
            String recipientEmail = student.getEmail();

            // Generate the PDF ticket
            byte[] pdfTicket = rsvpTicketService.generateTicketPdf(dto);

            // Send email with attachment
            emailService.sendWithAttachment(
                    EmailType.RSVP_CONFIRMATION,
                    recipientEmail,
                    Map.of(
                            "eventName", event.getEventName() != null ? event.getEventName() : "Event",
                            "eventDate", event.getEventDate() != null ? event.getEventDate().toString() : "TBC",
                            "eventVenue", resolveVenueName(event),
                            "qrCode", rsvp.getQrCodeTicket(),
                            "studentName", dto.getStudentName()
                    ),
                    pdfTicket,
                    "SocietyCentral_Ticket_" + event.getEventID() + ".pdf"
            );

            log.info("RSVP confirmation email sent to {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send RSVP confirmation email for student={} event={}: {}",
                    student.getStudentNumber(), event.getEventID(), e.getMessage());
        }
    }
}
