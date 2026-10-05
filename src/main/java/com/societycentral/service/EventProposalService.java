package com.societycentral.service;

import com.societycentral.dto.request.PublishEventRequestDTO;
import com.societycentral.dto.request.ReviewEventRequestDTO;
import com.societycentral.dto.response.EventProposalResponseDTO;
import com.societycentral.dto.response.POAEventOptionDTO;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the event proposal lifecycle beyond what EventService handles.
 *
 * Responsibilities:
 * - Submit proposal (DRAFT → PROPOSED): emails SDO with PDF, sends co-host invites
 * - SDO review (PROPOSED → APPROVED/REJECTED): emails executive
 * - Publish (APPROVED → PUBLISHED): adds RSVP config, emails society members
 * - POA event prefill data
 * - Co-host invitation accept/decline
 */
@Service
@Slf4j
public class EventProposalService {

    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SocietyRepository societyRepository;
    private final SDORepository sdoRepository;
    private final VenueRepository venueRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final EventCoHostInvitationRepository coHostRepo;
    private final POAEventRepository poaEventRepository;
    private final POAEventCoHostRepository poaCoHostRepository;
    private final POARepository poaRepository;
    private final BudgetRequestRepository budgetRequestRepository;
    private final RSVPRepository rsvpRepository;
    private final RSVPService rsvpService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final Clock clock;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final AnnouncementService announcementService;
    private final AuditLogRepository auditLogRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    @Autowired
    public EventProposalService(
            EventRepository eventRepository,
            HosterRepository hosterRepository,
            StudentRepository studentRepository,
            ExecutiveRepository executiveRepository,
            SocietyRepository societyRepository,
            SDORepository sdoRepository,
            VenueRepository venueRepository,
            SocietyMemberRepository societyMemberRepository,
            EventCoHostInvitationRepository coHostRepo,
            POAEventRepository poaEventRepository,
            POAEventCoHostRepository poaCoHostRepository,
            POARepository poaRepository,
            BudgetRequestRepository budgetRequestRepository,
            RSVPRepository rsvpRepository,
            RSVPService rsvpService,
            EmailService emailService,
            JwtUtil jwtUtil,
            Clock clock,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            AnnouncementService announcementService,
            AuditLogRepository auditLogRepository) {
        this.eventRepository = eventRepository;
        this.hosterRepository = hosterRepository;
        this.studentRepository = studentRepository;
        this.executiveRepository = executiveRepository;
        this.societyRepository = societyRepository;
        this.sdoRepository = sdoRepository;
        this.venueRepository = venueRepository;
        this.societyMemberRepository = societyMemberRepository;
        this.coHostRepo = coHostRepo;
        this.poaEventRepository = poaEventRepository;
        this.poaCoHostRepository = poaCoHostRepository;
        this.poaRepository = poaRepository;
        this.budgetRequestRepository = budgetRequestRepository;
        this.rsvpRepository = rsvpRepository;
        this.rsvpService = rsvpService;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
        this.clock = clock;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.announcementService = announcementService;
        this.auditLogRepository = auditLogRepository;
    }

    /** Compatibility constructor for focused unit tests and existing callers. */
    public EventProposalService(
            EventRepository eventRepository, HosterRepository hosterRepository,
            StudentRepository studentRepository, ExecutiveRepository executiveRepository,
            SocietyRepository societyRepository, SDORepository sdoRepository,
            VenueRepository venueRepository, SocietyMemberRepository societyMemberRepository,
            EventCoHostInvitationRepository coHostRepo, POAEventRepository poaEventRepository,
            POAEventCoHostRepository poaCoHostRepository, POARepository poaRepository,
            BudgetRequestRepository budgetRequestRepository, RSVPRepository rsvpRepository,
            RSVPService rsvpService, EmailService emailService, JwtUtil jwtUtil, Clock clock) {
        this(eventRepository, hosterRepository, studentRepository, executiveRepository,
                societyRepository, sdoRepository, venueRepository, societyMemberRepository,
                coHostRepo, poaEventRepository, poaCoHostRepository, poaRepository,
                budgetRequestRepository, rsvpRepository, rsvpService, emailService, jwtUtil,
                clock, null, null, null, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SUBMIT PROPOSAL (DRAFT → PROPOSED)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Submits an event proposal for SDO review.
     * - Validates budget balance
     * - Sets submittedBy
     * - Sends co-host invitations
     * - Emails SDO with proposal PDF
     */
    @Transactional
    public EventProposalResponseDTO submitProposal(String eventID, String executiveEmail) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);
        Event event = lockEvent(eventID);

        assertEventBelongsToSociety(event, society.getSocietyID());

        if (event.getEventStatus() != EventStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only events in DRAFT status may be submitted for approval.");
        }

        // Validate required fields for submission
        validateProposalForSubmission(event);

        // Validate budget balance
        validateBudgetBalance(event);

        // Set submission metadata
        event.setEventStatus(EventStatus.PROPOSED);
        event.setSubmittedBy(student.getStudentNumber());
        event.setUpdatedAt(LocalDateTime.now(clock));

        Event saved = eventRepository.save(event);

        // Send co-host invitations
        sendCoHostInvitations(saved, student, society);

        EventProposalResponseDTO response = buildProposalResponse(saved, society);
        SDO sdo = sdoRepository.findById(society.getSdoStaffNumber())
                .orElseThrow(() -> new IllegalStateException("Responsible SDO not found."));
        if (eventPublisher == null) {
            emailSDOProposal(saved, society, student);
            return response;
        }
        if (notificationService != null) {
            createProposalNotification(sdo.getEmail(), NotificationType.EVENT_PROPOSAL_SUBMITTED,
                    "New event proposal",
                    "A new event proposal for " + saved.getEventName() + " from "
                            + society.getSocietyName() + " is awaiting review.", saved.getEventID());
        }
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new EventProposalSubmittedEvent(
                    sdo.getEmail(), response, studentDisplayName(student)));
        }
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SDO REVIEW (PROPOSED → APPROVED or REJECTED)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public EventProposalResponseDTO reviewProposal(
            String eventID, String sdoEmail, ReviewEventRequestDTO request) {

        SDO sdo = sdoRepository.findByEmail(sdoEmail).orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        Event event = lockEvent(eventID);

        if (event.getEventStatus() != EventStatus.PROPOSED) {
            throw new IllegalStateException("Only proposed events can be reviewed.");
        }

        // Verify SDO is responsible for this society
        Society society = resolvePrimarySociety(event.getEventID());
        assertResponsibleSdo(society, sdo);

        String action = request.getAction().toUpperCase().trim();
        LocalDateTime now = LocalDateTime.now(clock);

        if ("APPROVE".equals(action)) {event.setEventStatus(EventStatus.APPROVED);
            event.setSdoReviewNotes(request.getReviewNotes());
        }
        else if ("REJECT".equals(action))
        {
            if (request.getReviewNotes() == null || request.getReviewNotes().isBlank()) {
                throw new IllegalArgumentException("Review notes are required when rejecting a proposal.");
            }
            event.setEventStatus(EventStatus.REJECTED);
            String reason = request.getReviewNotes().trim();
            event.setRejectionReason(reason);
            event.setSdoReviewNotes(reason);
        } else {
            throw new IllegalArgumentException("Action must be APPROVE or REJECT.");
        }

        event.setReviewedByStaffNumber(sdo.getStaffNumber());
        event.setReviewedAt(now);
        event.setUpdatedAt(now);

        Event saved = eventRepository.save(event);

        EventProposalResponseDTO response = buildProposalResponse(saved, society);
        Student submitter = studentRepository.findById(saved.getSubmittedBy()).orElse(null);
        if (submitter == null && notificationService == null && eventPublisher == null) {
            emailExecutiveReviewResult(saved, society, sdo);
            return response;
        }
        if (submitter == null) {
            throw new IllegalStateException("Submitting executive not found.");
        }
        if (eventPublisher == null) {
            emailExecutiveReviewResult(saved, society, sdo);
            return response;
        }
        boolean approved = saved.getEventStatus() == EventStatus.APPROVED;
        if (notificationService != null) createProposalNotification(submitter.getEmail(),
                approved ? NotificationType.EVENT_PROPOSAL_APPROVED : NotificationType.EVENT_PROPOSAL_REJECTED,
                approved ? "Event proposal approved" : "Event proposal requires changes",
                approved
                        ? "Your event proposal " + saved.getEventName() + " for " + society.getSocietyName() + " was approved."
                        : limitNotificationMessage("Your event proposal " + saved.getEventName() + " for "
                                + society.getSocietyName() + " was rejected/returned. Reason: "
                                + saved.getSdoReviewNotes()), saved.getEventID());
        if (eventPublisher != null) eventPublisher.publishEvent(new EventProposalReviewedEvent(
                    approved ? EmailType.EVENT_APPROVED : EmailType.EVENT_REJECTED,
                    submitter.getEmail(), saved.getEventName(), society.getSocietyName(),
                    saved.getRejectionReason()));
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLISH EVENT (APPROVED → PUBLISHED)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public EventProposalResponseDTO publishEvent(
            String eventID, String executiveEmail, PublishEventRequestDTO request) {

        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);
        Event event = lockEvent(eventID);

        assertEventBelongsToSociety(event, society.getSocietyID());

        if (event.getEventStatus() != EventStatus.APPROVED) {
            throw new IllegalStateException("Only approved events can be published.");
        }

        if (request == null || request.getRsvpOpenDate() == null
                || request.getRsvpCloseDate() == null || request.getEventLimit() == null) {
            throw new IllegalArgumentException("RSVP dates and event capacity are required.");
        }

        // Validate RSVP dates
        if (request.getRsvpCloseDate().isBefore(request.getRsvpOpenDate())) {
            throw new IllegalArgumentException("RSVP close date must be after open date.");
        }
        LocalDateTime eventStart = event.getEventStartTime() == null
                ? event.getEventDate().atStartOfDay()
                : LocalDateTime.of(event.getEventDate(), event.getEventStartTime());
        if (!request.getRsvpCloseDate().isBefore(eventStart)) {
            throw new IllegalArgumentException("RSVP close date must be before the event starts.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!request.getRsvpCloseDate().isAfter(now)) {
            throw new IllegalArgumentException("RSVP closing date and time must be in the future.");
        }
        Venue venue = venueRepository.findById(event.getVenueCode())
                .orElseThrow(() -> new IllegalArgumentException("Venue not found."));
        if (!venue.isActive()) throw new IllegalArgumentException("Venue is inactive.");
        if (request.getEventLimit() > venue.getCapacity()) {
            throw new IllegalArgumentException("Event capacity exceeds the venue capacity.");
        }

        event.setRsvpOpenDate(request.getRsvpOpenDate());
        event.setRsvpCloseDate(request.getRsvpCloseDate());
        event.setEventLimit(request.getEventLimit());
        event.setEventStatus(EventStatus.PUBLISHED);
        event.setPublishedAt(now);
        event.setUpdatedAt(now);

        Event saved = eventRepository.save(event);

        EventProposalResponseDTO response = buildProposalResponse(saved, society);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new EventPublishedEvent(saved.getEventID(), society.getSocietyID()));
        } else {
            emailMembersWithRSVPLinks(saved, society);
        }
        return response;
    }

    public void sendPublicationInvitations(String eventID, String societyID) {
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException("Society not found."));
        emailMembersWithRSVPLinks(event, society);
    }

    /** Processes the two publication communication stages, safely across restarts. */
    @Transactional
    public synchronized void processPublicationCommunication(String eventID) {
        Event event = eventRepository.findById(eventID).orElse(null);
        if (event == null || event.getEventStatus() != EventStatus.PUBLISHED) return;
        Society society = resolvePrimarySociety(eventID);
        LocalDateTime now = LocalDateTime.now(clock);

        if (event.getRsvpOpenDate() != null && now.isBefore(event.getRsvpOpenDate())) {
            if (event.getAdvanceNoticeSentAt() == null) {
                sendAdvanceNotice(event, society);
                event.setAdvanceNoticeSentAt(now);
                eventRepository.save(event);
            }
            return;
        }

        if (event.getRsvpOpenDate() != null && event.getRsvpOpenNoticeSentAt() == null) {
            sendRsvpOpenNotice(event, society);
            event.setRsvpOpenNoticeSentAt(now);
            eventRepository.save(event);
        }
    }

    private void sendAdvanceNotice(Event event, Society society) {
        for (SocietyMember member : currentMembers(society)) {
            Student student = member.getStudent();
            if (student == null) continue;
            emailService.send(EmailType.EVENT_UPCOMING, student.getEmail(), Map.of(
                    "eventName", event.getEventName(),
                    "societyName", society.getSocietyName(),
                    "eventDate", String.valueOf(event.getEventDate()),
                    "eventTime", String.valueOf(event.getEventStartTime()) + " - " + event.getEventEndTime(),
                    "eventVenue", event.getEventVenue() == null ? "See event details" : event.getEventVenue(),
                    "rsvpOpen", String.valueOf(event.getRsvpOpenDate()),
                    "rsvpClose", String.valueOf(event.getRsvpCloseDate()),
                    "description", event.getEventDescription() == null ? "" : event.getEventDescription()));
        }
    }

    private void sendRsvpOpenNotice(Event event, Society society) {
        for (SocietyMember member : currentMembers(society)) {
            Student student = member.getStudent();
            if (student == null) continue;
            Date expiry = Date.from(event.getEventDate().atStartOfDay().plusDays(1)
                    .toInstant(ZoneOffset.UTC));
            String token = jwtUtil.generateRSVPToken(student.getStudentNumber(), event.getEventID(),
                    event.getAdvertisementVersion(), expiry);
            String link = baseUrl + "/events/" + event.getEventID() + "/rsvp?token=" + token;
            emailService.send(EmailType.EVENT_RSVP_OPEN, student.getEmail(), Map.of(
                    "eventName", event.getEventName(),
                    "societyName", society.getSocietyName(),
                    "eventDate", String.valueOf(event.getEventDate()),
                    "eventTime", String.valueOf(event.getEventStartTime()) + " - " + event.getEventEndTime(),
                    "eventVenue", event.getEventVenue() == null ? "See event details" : event.getEventVenue(),
                    "rsvpClose", String.valueOf(event.getRsvpCloseDate()),
                    "rsvpLink", link));
        }
    }

    private List<SocietyMember> currentMembers(Society society) {
        return societyMemberRepository.findCurrentMembersBySocietyID(
                society.getSocietyID(), LocalDate.now(clock));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CO-HOST INVITATION ACCEPT/DECLINE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public void respondToCoHostInvitation(
            String invitationID, String executiveEmail, String action) {

        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);

        EventCoHostInvitation invitation = coHostRepo
                .findByInvitationIDAndInvitedSocietyID(invitationID, society.getSocietyID())
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found."));

        if (invitation.getStatus() != EventCoHostStatus.PENDING) {
            throw new IllegalStateException("This invitation has already been responded to.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String normalizedAction = action.toUpperCase().trim();

        if ("ACCEPT".equals(normalizedAction)) {
            invitation.setStatus(EventCoHostStatus.ACCEPTED);

            // Add as co-host in Hoster table
            HosterId hosterId = new HosterId(invitation.getEventID(), society.getSocietyID());
            if (!hosterRepository.existsById(hosterId)) {
                Hoster coHost = new Hoster();
                coHost.setId(hosterId);
                coHost.setEvent(eventRepository.findById(invitation.getEventID()).orElse(null));
                coHost.setSociety(society);
                coHost.setIsPrimary(false);
                hosterRepository.save(coHost);
            }
        } else if ("DECLINE".equals(normalizedAction)) {
            invitation.setStatus(EventCoHostStatus.DECLINED);
        } else {
            throw new IllegalArgumentException("Action must be ACCEPT or DECLINE.");
        }

        invitation.setRespondedAt(now);
        invitation.setRespondedByStudentNumber(student.getStudentNumber());
        coHostRepo.save(invitation);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POA EVENT DROPDOWN (prefill data)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<POAEventOptionDTO> getPOAEventsForPrefill(String executiveEmail) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);

        int currentYear = LocalDate.now(clock).getYear();

        // Find the approved POA for this society+year
        Optional<POA> poaOpt = poaRepository.findBySocietyIDAndYear(
                society.getSocietyID(), currentYear);

        if (poaOpt.isEmpty()) return List.of();

        POA poa = poaOpt.get();
        if (poa.getStatus() != POAStatus.APPROVED) return List.of();

        List<POAEvent> poaEvents = poaEventRepository
                .findByPoaIDOrderBySortOrderAsc(poa.getPoaID());

        return poaEvents.stream().map(pe -> {
            List<String> coHosts = poaCoHostRepository
                    .findByPoaEventID(pe.getPoaEventID())
                    .stream()
                    .map(POAEventCoHost::getInvitedSocietyID)
                    .toList();

            return POAEventOptionDTO.builder()
                    .poaEventID(pe.getPoaEventID())
                    .programName(pe.getProgramName())
                    .month(pe.getMonth())
                    .theme(pe.getTheme())
                    .eventDate(pe.getEventDate())
                    .venue(pe.getVenue())
                    .attendance(pe.getAttendance() != null ? pe.getAttendance().name() : null)
                    .purpose(pe.getPurpose())
                    .projectedIncomeFromAccount(pe.getProjectedIncomeFromAccount())
                    .projectedIncomeSponsorship(pe.getProjectedIncomeSponsorship())
                    .expensePromoMaterial(pe.getExpensePromoMaterial())
                    .expenseDataAirtime(pe.getExpenseDataAirtime())
                    .expenseGifts(pe.getExpenseGifts())
                    .expenseOther(pe.getExpenseOther())
                    .expenseOtherSpecification(pe.getExpenseOtherSpecification())
                    .coHostSocietyIDs(coHosts)
                    .build();
        }).toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VIEW PROPOSAL (for executive or SDO)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public EventProposalResponseDTO getProposal(String eventID, String executiveEmail) {
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
        Society society = resolvePrimarySociety(eventID);
        Student student = resolveStudent(executiveEmail);
        Society executiveSociety = resolveActiveExecutiveSociety(student);
        assertEventBelongsToSociety(event, executiveSociety.getSocietyID());
        return buildProposalResponse(event, society);
    }

    @Transactional(readOnly = true)
    public EventProposalResponseDTO getProposalForSDO(String eventID, String sdoEmail) {
        SDO sdo = resolveSdo(sdoEmail);
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
        Society society = resolvePrimarySociety(eventID);
        assertResponsibleSdo(society, sdo);
        return buildProposalResponse(event, society);
    }

    /**
     * Returns all PROPOSED events for societies supervised by the authenticated SDO.
     * Only shows events where the SDO is responsible for the primary hosting society.
     */
    @Transactional(readOnly = true)
    public List<EventProposalResponseDTO> getProposalsForSDO(String sdoEmail) {
        SDO sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        // Find all PROPOSED events hosted by these societies
        List<Event> proposedEvents = eventRepository.findProposalsForResponsibleSdo(
                EventStatus.PROPOSED, sdo.getStaffNumber());

        Map<String, Society> primarySocietiesByEventID = hosterRepository
                .findByIdEventIDInAndIsPrimaryTrue(
                        proposedEvents.stream().map(Event::getEventID).toList())
                .stream()
                .collect(Collectors.toMap(
                        hoster -> hoster.getId().getEventID(),
                        Hoster::getSociety));

        return proposedEvents.stream()
                .map(event -> {
                    Society society = Optional.ofNullable(
                                    primarySocietiesByEventID.get(event.getEventID()))
                            .orElseThrow(() -> new IllegalStateException("Primary host not found."));
                    return buildProposalResponse(event, society);
                })
                .toList();
    }

    /**
     * Returns all pending budget requests for societies supervised by the authenticated SDO.
     */
    @Transactional(readOnly = true)
    public List<BudgetRequest> getPendingBudgetRequestsForSDO(String sdoEmail) {
        SDO sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));

        List<Society> societies = societyRepository.findBySdoStaffNumber(sdo.getStaffNumber());

        return societies.stream()
                .flatMap(society -> budgetRequestRepository
                        .findBySocietyIDAndStatus(society.getSocietyID(), BudgetRequestStatus.PENDING)
                        .stream())
                .toList();
    }

    /**
     * Returns APPROVED + PUBLISHED events for the executive's society.
     * Used by the "Publish & Advertise Event" page.
     */
    @Transactional(readOnly = true)
    public List<EventProposalResponseDTO> getPublishableEventsForExecutive(String executiveEmail) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);

        List<Hoster> hosters = hosterRepository.findByIdSocietyID(society.getSocietyID());

        return hosters.stream()
                .map(Hoster::getEvent)
                .filter(event -> event != null
                        && (event.getEventStatus() == EventStatus.APPROVED
                        || event.getEventStatus() == EventStatus.PUBLISHED))
                .map(event -> buildProposalResponse(event, society))
                .toList();
    }

    /**
     * Returns all RSVPs for an event (executive must own the event).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEventRsvps(String eventID, String executiveEmail) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);

        // Verify ownership
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
        assertEventBelongsToSociety(event, society.getSocietyID());
        Society hostingSociety = resolvePrimarySociety(eventID);

        List<RSVP> rsvps = rsvpRepository.findByIdEventID(eventID);
        Set<String> studentNumbers = rsvps.stream()
                .map(rsvp -> rsvp.getId().getStudentNumber())
                .collect(Collectors.toSet());
        Set<String> activeMemberNumbers = studentNumbers.isEmpty()
                ? Set.of()
                : societyMemberRepository
                        .findActiveStudentNumbersBySocietyIDAndStudentNumbers(
                                hostingSociety.getSocietyID(),
                                studentNumbers,
                                LocalDate.now(clock));
        Map<String, Student> studentsByNumber = studentNumbers.isEmpty()
                ? Map.of()
                : studentRepository.findAllById(studentNumbers).stream()
                        .collect(Collectors.toMap(
                                Student::getStudentNumber,
                                java.util.function.Function.identity()));

        return rsvps.stream().map(rsvp -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("studentNumber", rsvp.getId().getStudentNumber());
            entry.put("qrCodeTicket", rsvp.getQrCodeTicket());
            entry.put("scannedStatus", rsvp.getScannedStatus());
            entry.put("scannedAt", rsvp.getScannedAt());
            entry.put("rsvpCreatedAt", rsvp.getRsvpCreatedAt());
            entry.put("checkInMethod", rsvp.getCheckInMethod());
            entry.put("checkedInBy", rsvp.getCheckedInBy());
            entry.put("rsvpStatus", "CONFIRMED");
            entry.put("societyMember", activeMemberNumbers.contains(
                    rsvp.getId().getStudentNumber()));

            // Resolve student name
            Optional.ofNullable(studentsByNumber.get(
                    rsvp.getId().getStudentNumber())).ifPresent(s -> {
                        if (s.getUser() != null) {
                            entry.put("firstName", s.getUser().getFirstName());
                            entry.put("lastName", s.getUser().getLastName());
                            entry.put("email", s.getEmail());
                            if (s.getUser() != null) {
                                entry.put("firstName", s.getUser().getFirstName());
                                entry.put("lastName", s.getUser().getLastName());
                            }
                        }
                    });
            return entry;
        }).toList();
    }

    @Transactional
    public Map<String, Object> manuallyCheckIn(String eventID, String studentNumber, String executiveEmail) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);
        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
        assertEventBelongsToSociety(event, society.getSocietyID());
        RSVP rsvp = rsvpService.checkInByStudent(eventID, studentNumber, executiveEmail);
        return Map.of("eventID", eventID, "studentNumber", studentNumber, "scannedStatus", true, "scannedAt", rsvp.getScannedAt());
    }

    /**
     * Edit a PUBLISHED event. Requires a change reason.
     * Sends email notifications to all RSVP'd students + society members.
     */
    @Transactional
    public EventProposalResponseDTO editPublishedEvent(
            String eventID, String executiveEmail, Map<String, Object> payload) {

        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);
        Event event = lockEvent(eventID);

        assertEventBelongsToSociety(event, society.getSocietyID());

          if (event.getEventStatus() != EventStatus.PUBLISHED) {
              throw new IllegalStateException("Only published events can be edited this way.");
          }

          if (payload.containsKey("eventDate")
                  || payload.containsKey("eventStartTime")
                  || payload.containsKey("eventEndTime")) {
              throw new IllegalArgumentException(
                      "Event date and time cannot be changed through the normal edit flow. Use the approved postponement or cancellation workflow.");
          }

        String changeReason = (String) payload.get("changeReason");
        if (changeReason == null || changeReason.isBlank()) {
            throw new IllegalArgumentException("A reason for the change is required.");
        }

        // Track what changed
        List<String> changes = new ArrayList<>();

        if (payload.containsKey("eventName") && !payload.get("eventName").toString().isBlank()) {
            String oldVal = event.getEventName();
            String newVal = (String) payload.get("eventName");
            if (!newVal.equals(oldVal)) {
                changes.add("Event Name: \"" + oldVal + "\" → \"" + newVal + "\"");
                event.setEventName(newVal);
            }
        }
        if (payload.containsKey("eventDescription") && !payload.get("eventDescription").toString().isBlank()) {
            String oldVal = event.getEventDescription() != null ? event.getEventDescription() : "";
            String newVal = (String) payload.get("eventDescription");
            if (!newVal.equals(oldVal)) {
                changes.add("Description updated");
                event.setEventDescription(newVal);
            }
        }
        if (payload.containsKey("venueCode") && !payload.get("venueCode").toString().isBlank()) {
            String newVenueCode = (String) payload.get("venueCode");
            if (!newVenueCode.equals(event.getVenueCode())) {
                String oldVenue = event.getEventVenue() != null ? event.getEventVenue() : "Unknown";
                event.setVenueCode(newVenueCode);
                venueRepository.findById(newVenueCode).ifPresent(v -> {
                    event.setEventVenue(v.getVenueName());
                    event.setEventCampus(v.getCampus());
                });
                changes.add("Venue: \"" + oldVenue + "\" → \"" + event.getEventVenue() + "\"");
            }
        }
        if (payload.containsKey("rsvpCloseDate") && !payload.get("rsvpCloseDate").toString().isBlank()) {
            LocalDateTime newVal = LocalDateTime.parse((String) payload.get("rsvpCloseDate"));
            if (!newVal.equals(event.getRsvpCloseDate())) {
                changes.add("RSVP Close Date updated");
                event.setRsvpCloseDate(newVal);
            }
        }
        if (payload.containsKey("eventLimit") && !payload.get("eventLimit").toString().isBlank()) {
            int newVal = Integer.parseInt(payload.get("eventLimit").toString());
            if (event.getEventLimit() == null || newVal != event.getEventLimit()) {
                changes.add("Capacity: " + (event.getEventLimit() != null ? event.getEventLimit() : "Unlimited") + " → " + newVal);
                event.setEventLimit(newVal);
            }
        }

        if (payload.containsKey("posterUrl")
                && payload.get("posterUrl") != null
                && !payload.get("posterUrl").toString().isBlank()
                && !payload.get("posterUrl").toString().equals(event.getPosterUrl())) {
            event.setPosterUrl(payload.get("posterUrl").toString());
            changes.add("Event poster updated");
        }
        if (payload.containsKey("bannerUrl")
                && payload.get("bannerUrl") != null
                && !payload.get("bannerUrl").toString().isBlank()
                && !payload.get("bannerUrl").toString().equals(event.getBannerUrl())) {
            event.setBannerUrl(payload.get("bannerUrl").toString());
            changes.add("Event banner updated");
        }

        if (changes.isEmpty()) {
            throw new IllegalArgumentException("No actual changes detected.");
        }

        event.setUpdatedAt(LocalDateTime.now(clock));
        Event saved = eventRepository.save(event);

        // Notify all RSVP'd students + society members with detailed changes
        notifyEventChange(saved, society, changeReason, changes);

        return buildProposalResponse(saved, society);
    }

    @Transactional
    public EventProposalResponseDTO cancelPublishedEvent(
            String eventID, String executiveEmail, String reason) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);
        Event event = lockEvent(eventID);
        assertEventBelongsToSociety(event, society.getSocietyID());
        if (event.getEventStatus() != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Only published events can be cancelled.");
        }
        String normalizedReason = requireLifecycleReason(reason, "Cancellation");
        EventStatus previousStatus = event.getEventStatus();
        event.setEventStatus(EventStatus.CANCELLED);
        Event saved = eventRepository.save(event);
        recordLifecycleAudit(saved, previousStatus, EventStatus.CANCELLED,
                executiveEmail, normalizedReason);
        communicateLifecycleChange(saved, society, executiveEmail, NotificationType.EVENT_CANCELLED,
                "Event Cancelled", normalizedReason, null, null);
        return buildProposalResponse(saved, society);
    }

    @Transactional
    public EventProposalResponseDTO postponePublishedEvent(
            String eventID, String executiveEmail, Map<String, Object> payload) {
        Student student = resolveStudent(executiveEmail);
        Society society = resolveActiveExecutiveSociety(student);
        Event event = lockEvent(eventID);
        assertEventBelongsToSociety(event, society.getSocietyID());
        if (event.getEventStatus() != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Only published events can be postponed.");
        }

        LocalDate newDate = LocalDate.parse(String.valueOf(payload.get("newEventDate")));
        LocalTime newStart = LocalTime.parse(String.valueOf(payload.get("newStartTime")));
        LocalTime newEnd = LocalTime.parse(String.valueOf(payload.get("newEndTime")));
        String reason = requireLifecycleReason((String) payload.get("reason"), "Postponement");
        LocalDateTime newStartDateTime = LocalDateTime.of(newDate, newStart);
        if (!newEnd.isAfter(newStart) || !newStartDateTime.isAfter(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("The postponed event must have a valid future schedule.");
        }
        if (event.getVenueCode() != null && eventRepository.countVenueConflictsExcludingEvent(
                eventID, event.getVenueCode(), newDate, newStart, newEnd,
                List.of(EventStatus.DRAFT, EventStatus.PROPOSED, EventStatus.APPROVED,
                        EventStatus.PUBLISHED)) > 0) {
            throw new IllegalStateException("The venue is not available for the new schedule.");
        }

        LocalDate oldDate = event.getEventDate();
        LocalTime oldStart = event.getEventStartTime();
        LocalTime oldEnd = event.getEventEndTime();
        event.setEventDate(newDate);
        event.setEventStartTime(newStart);
        event.setEventEndTime(newEnd);
        event.setEventTime(newStart);
        Event saved = eventRepository.save(event);
        recordLifecycleAudit(saved, EventStatus.PUBLISHED, EventStatus.PUBLISHED,
                executiveEmail, reason + " | " + formatSchedule(oldDate, oldStart, oldEnd)
                        + " -> " + formatSchedule(newDate, newStart, newEnd));
        communicateLifecycleChange(saved, society, executiveEmail, NotificationType.EVENT_POSTPONED,
                "Event Postponed", reason, formatSchedule(oldDate, oldStart, oldEnd),
                formatSchedule(newDate, newStart, newEnd));
        return buildProposalResponse(saved, society);
    }

    private String requireLifecycleReason(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(action + " reason is required.");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException(action + " reason must not exceed 500 characters.");
        }
        return normalized;
    }

    private void recordLifecycleAudit(Event event, EventStatus oldStatus,
                                     EventStatus newStatus, String changedBy,
                                     String reason) {
        if (auditLogRepository == null) return;
        AuditLog audit = new AuditLog();
        audit.setStaffNumber(event.getSubmittedBy() == null ? changedBy : event.getSubmittedBy());
        audit.setEntityName("Event");
        audit.setOperation("UPDATE");
        audit.setFieldChanged(newStatus == EventStatus.CANCELLED ? "eventStatus" : "eventSchedule");
        audit.setOldValue(oldStatus.name());
        audit.setNewValue(newStatus.name());
        audit.setChangedBy(changedBy);
        audit.setChangedDate(LocalDateTime.now(clock));
        audit.setReason(reason);
        auditLogRepository.save(audit);
    }

    private void communicateLifecycleChange(Event event, Society society, String senderEmail,
                                            NotificationType type, String title,
                                             String reason, String oldSchedule,
                                             String newSchedule) {
        List<RSVP> attendees = rsvpRepository.findByIdEventID(event.getEventID());
        String schedule = formatSchedule(event.getEventDate(), event.getEventStartTime(), event.getEventEndTime());
        String message = type == NotificationType.EVENT_CANCELLED
                ? event.getEventName() + " has been cancelled. Reason: " + reason
                : event.getEventName() + " has been postponed to " + newSchedule + ". Reason: " + reason;
        for (RSVP rsvp : attendees) {
            Student attendee = rsvp.getStudent() != null ? rsvp.getStudent()
                    : studentRepository.findById(rsvp.getId().getStudentNumber()).orElse(null);
            if (attendee == null || attendee.getEmail() == null) continue;
            if (!notificationService.notificationExists(attendee.getEmail(), type, event.getEventID())) {
                Notification notification = new Notification();
                notification.setNotificationID(generateNotificationID());
                notification.setRecipientEmail(attendee.getEmail());
                notification.setTitle(title);
                notification.setMessage(message);
                notification.setNotifType(type);
                notification.setRelatedID(event.getEventID());
                notificationService.create(notification);
            }
            Map<String, String> vars = new HashMap<>();
            vars.put("announcementTitle", title + ": " + event.getEventName());
            vars.put("announcementContent", message + " Date and time: " + schedule
                    + ". Venue: " + Optional.ofNullable(event.getEventVenue()).orElse("To be confirmed")
                    + ". Society: " + society.getSocietyName());
            if (oldSchedule != null) vars.put("previousSchedule", oldSchedule);
            emailService.send(EmailType.ANNOUNCEMENT, attendee.getEmail(), vars);
        }
        if (announcementService != null) {
            Announcement announcement = new Announcement();
            announcement.setTargetType(TargetType.STUDENTS);
            announcement.setSentBy(senderEmail);
            announcement.setSubject(title + ": " + event.getEventName());
            announcement.setDescription(message + " Date and time: " + schedule
                    + ". Venue: " + Optional.ofNullable(event.getEventVenue()).orElse("To be confirmed")
                    + ". Society: " + society.getSocietyName());
            announcement.setSociety(society);
            announcement.setExpireDate(LocalDateTime.now(clock).plusDays(30));
            announcementService.create(announcement);
        }
    }

    private String formatSchedule(LocalDate date, LocalTime start, LocalTime end) {
        return String.valueOf(date) + " " + String.valueOf(start) + "-" + String.valueOf(end);
    }

    private String generateNotificationID() {
        return "NTF" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 17).toUpperCase(Locale.ROOT);
    }

    private void notifyEventChange(Event event, Society society, String changeReason, List<String> changes) {
        try {
            // Build detailed change summary for email
            StringBuilder changeDetails = new StringBuilder();
            changeDetails.append("<p><strong>The following changes were made to ")
                    .append(event.getEventName()).append(":</strong></p><ul>");
            for (String change : changes) {
                changeDetails.append("<li>").append(change).append("</li>");
            }
            changeDetails.append("</ul>");
            changeDetails.append("<p><strong>Reason:</strong> ").append(changeReason).append("</p>");

            String changeMessage = changeDetails.toString();

            // Email all RSVP'd students
            List<RSVP> rsvps = rsvpRepository.findByIdEventID(event.getEventID());
            for (RSVP rsvp : rsvps) {
                studentRepository.findById(rsvp.getId().getStudentNumber())
                        .ifPresent(s -> {
                            try {
                                emailService.send(EmailType.ANNOUNCEMENT, s.getEmail(), Map.of(
                                        "announcementTitle", "Event Update: " + event.getEventName(),
                                        "societyName", society.getSocietyName(),
                                        "message", "An event you RSVP'd for has been updated." + changeMessage
                                ));
                            } catch (Exception e) {
                                log.error("Failed to notify RSVP'd student {}: {}", s.getEmail(), e.getMessage());
                            }
                        });
            }

            // Email society members who haven't RSVP'd
            Set<String> rsvpStudentNumbers = rsvps.stream()
                    .map(r -> r.getId().getStudentNumber())
                    .collect(Collectors.toSet());

            List<SocietyMember> members = societyMemberRepository.findByIdSocietyID(society.getSocietyID());
            for (SocietyMember member : members) {
                if (rsvpStudentNumbers.contains(member.getId().getStudentNumber())) continue;
                studentRepository.findById(member.getId().getStudentNumber())
                        .ifPresent(s -> {
                            try {
                                emailService.send(EmailType.ANNOUNCEMENT, s.getEmail(), Map.of(
                                        "announcementTitle", "Event Update: " + event.getEventName(),
                                        "societyName", society.getSocietyName(),
                                        "message", "A society event has been updated." + changeMessage
                                ));
                            } catch (Exception e) {
                                log.error("Failed to notify member {}: {}", s.getEmail(), e.getMessage());
                            }
                        });
            }

            log.info("Event change notifications sent for event {} ({} changes, reason: {})",
                    event.getEventID(), changes.size(), changeReason);
        } catch (Exception e) {
            log.error("Failed to send event change notifications: {}", e.getMessage());
        }
    }

    /**
     * Get events for an SDO's supervised societies with status filter
     * Used for the Approve/Reject Events dashboard
     */
    @Transactional(readOnly = true)
    public List<EventProposalResponseDTO> getProposedEventsForSDO(String sdoEmail, String statusFilter) {
        SDO sdo = sdoRepository.findByEmail(sdoEmail)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found"));

        List<Society> supervisedSocieties =
                societyRepository.findBySdoStaffNumber(sdo.getStaffNumber());

        if (supervisedSocieties.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> societyIDs = supervisedSocieties.stream()
                .map(Society::getSocietyID)
                .collect(Collectors.toList());

        // Determine which status to filter by - default to PROPOSED
        EventStatus status = EventStatus.PROPOSED;
        if (statusFilter != null && !statusFilter.isEmpty()) {
            try {
                status = EventStatus.valueOf(statusFilter);
            } catch (IllegalArgumentException e) {
                // If invalid, keep PROPOSED
            }
        }

        // Use the existing repository method
        List<Event> filteredEvents = eventRepository.findEventsByStatusForSocieties(status, societyIDs);

        return filteredEvents.stream()
                .map(event -> {
                    Society society = resolvePrimarySociety(event.getEventID());
                    return buildProposalResponse(event, society);
                })
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private Student resolveStudent(String email) {
        return studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found."));
    }

    private Society resolveActiveExecutiveSociety(Student student) {
        Executive activeRole = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .filter(e -> e.getTermEndDate() == null
                        || !e.getTermEndDate().isBefore(LocalDate.now(clock)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "You are not an active executive."));

        Society society = societyRepository.findById(activeRole.getId().getSocietyID())
                .orElseThrow(() -> new IllegalStateException("Society not found."));
        if (!Boolean.TRUE.equals(society.getActiveStatus())
                || Boolean.TRUE.equals(society.getIsFlagged())) {
            throw new IllegalStateException("This society is not permitted to operate.");
        }
        return society;
    }

    private void createProposalNotification(String recipientEmail, NotificationType type,
                                            String title, String message, String eventID) {
        if (notificationService.notificationExists(recipientEmail, type, eventID)) {
            return;
        }
        Notification notification = new Notification();
        notification.setNotificationID("NTF" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 17).toUpperCase(Locale.ROOT));
        notification.setRecipientEmail(recipientEmail);
        notification.setTitle(title);
        notification.setMessage(limitNotificationMessage(message));
        notification.setNotifType(type);
        notification.setRelatedID(eventID);
        notificationService.create(notification);
    }

    private String limitNotificationMessage(String message) {
        return message.length() <= 500 ? message : message.substring(0, 497) + "...";
    }

    private String studentDisplayName(Student student) {
        if (student.getUser() == null) return student.getStudentNumber();
        return (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim();
    }

    private Event lockEvent(String eventID) {
        return eventRepository.findByIdForUpdate(eventID)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));
    }

    private SDO resolveSdo(String email) {
        return sdoRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("SDO not found."));
    }

    private void assertResponsibleSdo(Society society, SDO sdo) {
        if (society.getSdoStaffNumber() == null
                || !society.getSdoStaffNumber().equals(sdo.getStaffNumber())) {
            throw new ForbiddenOperationException(
                    "You are not authorised to review this event proposal.");
        }
    }

    private void assertEventBelongsToSociety(Event event, String societyID) {
        boolean belongs = hosterRepository.existsByIdEventIDAndIdSocietyID(
                event.getEventID(), societyID);
        if (!belongs) {
            throw new IllegalStateException("This event does not belong to your society.");
        }
    }

    private Society resolvePrimarySociety(String eventID) {
        return hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .map(Hoster::getSociety)
                .orElseThrow(() -> new IllegalStateException("Primary host not found."));
    }

    private void validateProposalForSubmission(Event event) {
        List<String> missing = new ArrayList<>();
        if (event.getEventName() == null || event.getEventName().isBlank()) missing.add("Event name");
        if (event.getEventDate() == null) missing.add("Event date");
        if (event.getEventStartTime() == null) missing.add("Start time");
        if (event.getEventEndTime() == null) missing.add("End time");
        if (event.getVenueCode() == null || event.getVenueCode().isBlank()) missing.add("Venue");
        if (event.getEventCampus() == null) missing.add("Campus");
        if (event.getEventDescription() == null || event.getEventDescription().isBlank()) missing.add("Description");
        if (event.getAttendingType() == null) missing.add("Attending type");

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot submit: missing required fields: " + String.join(", ", missing));
        }

        // Event date must be in the future
        if (!event.getEventDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Event date must be in the future.");
        }
    }

    private void validateBudgetBalance(Event event) {
        BigDecimal income = safeDecimal(event.getBudgetIncomeFromAccount())
                .add(safeDecimal(event.getBudgetIncomeSponsorship()));
        BigDecimal expenses = safeDecimal(event.getBudgetExpensePromoMaterial())
                .add(safeDecimal(event.getBudgetExpenseDataAirtime()))
                .add(safeDecimal(event.getBudgetExpenseGifts()))
                .add(safeDecimal(event.getBudgetExpenseVenue()))
                .add(safeDecimal(event.getBudgetExpenseOther()));

        if (income.compareTo(expenses) != 0) {
            throw new IllegalArgumentException(
                    "Budget must balance. Income (R" + income + ") ≠ Expenses (R" + expenses + ").");
        }
    }

    private BigDecimal safeDecimal(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private void sendCoHostInvitations(Event event, Student inviter, Society hostSociety) {
        List<EventCoHostInvitation> existing = coHostRepo.findByEventID(event.getEventID());

        // Get co-host society IDs from existing invitations that are not declined
        Set<String> alreadyInvited = existing.stream()
                .filter(i -> i.getStatus() != EventCoHostStatus.DECLINED)
                .map(EventCoHostInvitation::getInvitedSocietyID)
                .collect(Collectors.toSet());

        // Find societies that need new invitations from the Hoster table
        // (co-hosts added during draft creation)
        List<Hoster> coHosts = hosterRepository.findByIdEventID(event.getEventID()).stream()
                .filter(h -> !Boolean.TRUE.equals(h.getIsPrimary()))
                .filter(h -> !alreadyInvited.contains(h.getId().getSocietyID()))
                .toList();

        for (Hoster coHost : coHosts) {
            EventCoHostInvitation invitation = new EventCoHostInvitation();
            invitation.setInvitationID(EventCoHostInvitation.newID());
            invitation.setEventID(event.getEventID());
            invitation.setInvitedSocietyID(coHost.getId().getSocietyID());
            invitation.setInvitedByStudentNumber(inviter.getStudentNumber());
            invitation.setStatus(EventCoHostStatus.PENDING);
            coHostRepo.save(invitation);

            // Email executives of the invited society
            try {
                Society invitedSociety = coHost.getSociety();
                emailService.send(EmailType.POA_COHOST_INVITE,
                        invitedSociety.getEmail(),
                        Map.of(
                                "eventName", event.getEventName(),
                                "societyName", hostSociety.getSocietyName(),
                                "invitedSocietyName", invitedSociety.getSocietyName()
                        ));
            } catch (Exception e) {
                log.error("Failed to send co-host invite email: {}", e.getMessage());
            }
        }
    }

    private void emailSDOProposal(Event event, Society society, Student submitter) {
        try {
            SDO sdo = sdoRepository.findById(society.getSdoStaffNumber()).orElse(null);
            if (sdo == null) return;

            String sdoEmail = sdo.getEmail();
            emailService.send(EmailType.EVENT_PROPOSAL_SUBMITTED, sdoEmail, Map.of(
                    "eventName", event.getEventName(),
                    "societyName", society.getSocietyName(),
                    "submittedBy", submitter.getUser() != null
                            ? submitter.getUser().getFirstName() + " " + submitter.getUser().getLastName()
                            : submitter.getStudentNumber()
            ));
        } catch (Exception e) {
            log.error("Failed to email SDO about event proposal: {}", e.getMessage());
        }
    }

    private void emailExecutiveReviewResult(Event event, Society society, SDO sdo) {
        try {
            if (event.getSubmittedBy() == null) return;

            Student submitter = studentRepository.findById(event.getSubmittedBy()).orElse(null);
            if (submitter == null) return;

            EmailType type = event.getEventStatus() == EventStatus.APPROVED
                    ? EmailType.EVENT_APPROVED
                    : EmailType.EVENT_REJECTED;

            Map<String, String> vars = new HashMap<>();
            vars.put("eventName", event.getEventName());
            vars.put("societyName", society.getSocietyName());
            if (event.getRejectionReason() != null) {
                vars.put("reason", event.getRejectionReason());
            }

            emailService.send(type, submitter.getEmail(), vars);
        } catch (Exception e) {
            log.error("Failed to email executive about review result: {}", e.getMessage());
        }
    }

    private void emailMembersWithRSVPLinks(Event event, Society society) {
        try {
            // Get all active members of this society
            List<SocietyMember> members = societyMemberRepository
                    .findByIdSocietyID(society.getSocietyID());

            for (SocietyMember member : members) {
                try {
                    Student student = member.getStudent();
                    if (student == null) {
                        student = studentRepository.findById(member.getId().getStudentNumber())
                                .orElse(null);
                    }
                    if (student == null) continue;

                    // Generate unique RSVP link
                    Date expiry = Date.from(
                            event.getEventDate().atStartOfDay()
                                    .plusDays(1)
                                    .toInstant(ZoneOffset.UTC));

                    String token = jwtUtil.generateRSVPToken(
                            student.getStudentNumber(),
                            event.getEventID(),
                            event.getAdvertisementVersion(),
                            expiry);

                    String rsvpLink = baseUrl + "/events/" + event.getEventID() + "/rsvp?token=" + token;

                    emailService.send(EmailType.EVENT_RSVP_INVITE, student.getEmail(), Map.of(
                            "eventName", event.getEventName(),
                            "eventDate", event.getEventDate().toString(),
                            "eventVenue", event.getEventVenue() != null ? event.getEventVenue() : "See event details",
                            "rsvpLink", rsvpLink,
                            "societyName", society.getSocietyName()
                    ));
                } catch (Exception e) {
                    log.error("Failed to send RSVP email to member {}: {}",
                            member.getId().getStudentNumber(), e.getMessage());
                }
            }

            log.info("RSVP emails sent to {} members for event {}",
                    members.size(), event.getEventID());
        } catch (Exception e) {
            log.error("Failed to email members with RSVP links: {}", e.getMessage());
        }
    }

    private EventProposalResponseDTO buildProposalResponse(Event event, Society society) {
        // Resolve venue
        String venueName = event.getEventVenue();
        String venueType = null;
        Integer venueCapacity = null;
        if (event.getVenueCode() != null) {
            Venue venue = venueRepository.findById(event.getVenueCode()).orElse(null);
            if (venue != null) {
                venueName = venue.getVenueName();
                venueType = venue.getVenueType();
                venueCapacity = venue.getCapacity();
            }
        }

        // Resolve submitter name
        String submittedByName = null;
        if (event.getSubmittedBy() != null) {
            studentRepository.findById(event.getSubmittedBy())
                    .ifPresent(s -> { /* handled below */ });
            Student submitter = studentRepository.findById(event.getSubmittedBy()).orElse(null);
            if (submitter != null && submitter.getUser() != null) {
                submittedByName = submitter.getUser().getFirstName() + " "
                        + submitter.getUser().getLastName();
            }
        }

        // Resolve reviewer name
        String reviewedByName = null;
        if (event.getReviewedByStaffNumber() != null) {
            sdoRepository.findById(event.getReviewedByStaffNumber())
                    .ifPresent(sdo -> { /* handled via user lookup */ });
        }

        // Co-hosts
        List<EventCoHostInvitation> invitations = coHostRepo.findByEventID(event.getEventID());
        List<EventProposalResponseDTO.CoHostDTO> coHosts = invitations.stream()
                .map(inv -> {
                    String sName = societyRepository.findById(inv.getInvitedSocietyID())
                            .map(Society::getSocietyName).orElse("Unknown");
                    return EventProposalResponseDTO.CoHostDTO.builder()
                            .invitationID(inv.getInvitationID())
                            .societyID(inv.getInvitedSocietyID())
                            .societyName(sName)
                            .status(inv.getStatus().name())
                            .respondedAt(inv.getRespondedAt())
                            .build();
                }).toList();

        // Budget totals
        BigDecimal totalIncome = safeDecimal(event.getBudgetIncomeFromAccount())
                .add(safeDecimal(event.getBudgetIncomeSponsorship()));
        BigDecimal totalExpenses = safeDecimal(event.getBudgetExpensePromoMaterial())
                .add(safeDecimal(event.getBudgetExpenseDataAirtime()))
                .add(safeDecimal(event.getBudgetExpenseGifts()))
                .add(safeDecimal(event.getBudgetExpenseVenue()))
                .add(safeDecimal(event.getBudgetExpenseOther()));

        // POA event name
        String poaEventName = null;
        if (event.getPoaEventID() != null) {
            poaEventRepository.findById(event.getPoaEventID())
                    .ifPresent(pe -> { /* resolved below */ });
            POAEvent poaEvent = poaEventRepository.findById(event.getPoaEventID()).orElse(null);
            if (poaEvent != null) {
                poaEventName = poaEvent.getProgramName();
            }
        }

        return EventProposalResponseDTO.builder()
                .eventID(event.getEventID())
                .eventName(event.getEventName())
                .eventStatus(event.getEventStatus().name())
                .eventDate(event.getEventDate())
                .eventStartTime(event.getEventStartTime())
                .eventEndTime(event.getEventEndTime())
                .venueCode(event.getVenueCode())
                .venueName(venueName)
                .venueType(venueType)
                .venueCapacity(venueCapacity)
                .campus(event.getEventCampus() != null ? event.getEventCampus().name() : null)
                .eventDescription(event.getEventDescription())
                .attendingType(event.getAttendingType() != null ? event.getAttendingType().name() : null)
                .posterUrl(event.getPosterUrl())
                .bannerUrl(event.getBannerUrl())
                .societyID(society.getSocietyID())
                .societyName(society.getSocietyName())
                .submittedByStudentNumber(event.getSubmittedBy())
                .submittedByName(submittedByName)
                .poaEventID(event.getPoaEventID())
                .poaEventName(poaEventName)
                .budgetIncomeFromAccount(event.getBudgetIncomeFromAccount())
                .budgetIncomeSponsorship(event.getBudgetIncomeSponsorship())
                .budgetTotalIncome(totalIncome)
                .budgetExpensePromoMaterial(event.getBudgetExpensePromoMaterial())
                .budgetExpenseDataAirtime(event.getBudgetExpenseDataAirtime())
                .budgetExpenseGifts(event.getBudgetExpenseGifts())
                .budgetExpenseVenue(event.getBudgetExpenseVenue())
                .budgetExpenseOther(event.getBudgetExpenseOther())
                .budgetExpenseOtherSpecification(event.getBudgetExpenseOtherSpecification())
                .budgetTotalExpenses(totalExpenses)
                .coHosts(coHosts)
                .sdoReviewNotes(event.getSdoReviewNotes())
                .rejectionReason(event.getRejectionReason())
                .reviewedByStaffNumber(event.getReviewedByStaffNumber())
                .reviewedByName(reviewedByName)
                .reviewedAt(event.getReviewedAt())
                .rsvpOpenDate(event.getRsvpOpenDate())
                .rsvpCloseDate(event.getRsvpCloseDate())
                .eventLimit(event.getEventLimit())
                .publishedAt(event.getPublishedAt())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
