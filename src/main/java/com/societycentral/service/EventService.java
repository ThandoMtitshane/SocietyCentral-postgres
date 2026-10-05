package com.societycentral.service;

import com.societycentral.dto.request.CreateEventRequestDTO;
import com.societycentral.dto.response.EventListItemDTO;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.ExecutiveEventPageDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.mapper.EventMapper;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.repository.projection.ExecutiveEventListProjection;
import com.societycentral.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Service responsible for event-related business operations.
 *
 * This service supports event creation, draft management, proposal
 * submission and withdrawal, approval, publication, retrieval, deletion
 * and RSVP-link generation.
 */
@Service
public class EventService {

    private static final int MAX_EXECUTIVE_EVENT_PAGE_SIZE = 100;
    private static final int EVENT_DESCRIPTION_SUMMARY_LENGTH = 180;

    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final SocietyRepository societyRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final AuditLogRepository auditLogRepository;
    private final VenueService venueService;
    private final JwtUtil jwtUtil;
    private final EventMapper eventMapper;

    private Clock clock = Clock.systemDefaultZone();

    @Value("${app.base-url}")
    private String baseUrl;

    @Autowired
    public EventService(
            EventRepository eventRepository,
            HosterRepository hosterRepository,
            SocietyRepository societyRepository,
            StudentRepository studentRepository,
            ExecutiveRepository executiveRepository,
            AuditLogRepository auditLogRepository,
            VenueService venueService,
            JwtUtil jwtUtil,
            EventMapper eventMapper) {

        this.eventRepository = eventRepository;
        this.hosterRepository = hosterRepository;
        this.societyRepository = societyRepository;
        this.studentRepository = studentRepository;
        this.executiveRepository = executiveRepository;
        this.auditLogRepository = auditLogRepository;
        this.venueService = venueService;
        this.jwtUtil = jwtUtil;
        this.eventMapper = eventMapper;
    }

    @Autowired
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public List<Event> findAll() {
        return eventRepository.findAll();
    }

    public Optional<Event> findById(String eventID) {
        return eventRepository.findById(eventID);
    }

    public List<Event> findPublishedUpcoming() {
        return eventRepository.findByEventDateGreaterThanEqualAndEventStatus(
                LocalDate.now(clock),
                EventStatus.PUBLISHED
        );
    }

    public List<Event> findByStatus(EventStatus status) {
        return eventRepository.findByEventStatus(status);
    }

    public List<Event> findByCampus(String eventCampus) {
        return eventRepository.findByEventCampus(eventCampus);
    }

    @Transactional(readOnly = true)
    public ExecutiveEventPageDTO getExecutiveEvents(
            String executiveEmail,
            EventStatus status,
            int page,
            int size,
            String search) {

        validateExecutiveEventPage(page, size);

        Society society = resolveActiveExecutiveSociety(executiveEmail);
        String searchPattern = normalizeSearchPattern(search);

        Page<ExecutiveEventListProjection> result =
                eventRepository.findExecutiveEvents(
                        society.getSocietyID(),
                        status,
                        searchPattern,
                        PageRequest.of(page, size)
                );

        List<EventListItemDTO> content = result.getContent()
                .stream()
                .map(this::mapToListItem)
                .toList();

        return ExecutiveEventPageDTO.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    @Transactional(readOnly = true)
    public EventResponseDTO getExecutiveEvent(
            String eventID,
            String executiveEmail) {

        Society society = resolveActiveExecutiveSociety(executiveEmail);

        Hoster hoster = requireHostedEvent(
                eventID,
                society.getSocietyID()
        );

        Event event = hoster.getEvent();
        Venue venue = resolveEventVenue(event);

        return eventMapper.mapToResponse(
                event,
                society,
                venue
        );
    }

    /**
     * Updates an existing DRAFT or REJECTED event.
     *
     * Drafts may remain incomplete. Saving changes to a rejected event moves
     * it back to DRAFT but does not submit it for approval.
     */
    @Transactional
    public EventResponseDTO updateExecutiveEvent(
            String eventID,
            String executiveEmail,
            CreateEventRequestDTO request) {

        Society society = resolveActiveExecutiveSociety(executiveEmail);
        Event event = lockEvent(eventID);

        assertEventBelongsToSociety(
                eventID,
                society.getSocietyID()
        );

        if (event.getEventStatus() != EventStatus.DRAFT
                && event.getEventStatus() != EventStatus.REJECTED) {

            throw new IllegalStateException(
                    "Only draft or rejected events may be edited."
            );
        }

        if (!java.util.Objects.equals(event.getEventDate(), request.getEventDate())
                || !java.util.Objects.equals(event.getEventStartTime(), request.getEventStartTime())
                || !java.util.Objects.equals(event.getEventEndTime(), request.getEventEndTime())) {
            throw new IllegalArgumentException(
                    "Event date and time cannot be changed through the normal edit flow. Use the approved postponement or cancellation workflow.");
        }

        /*
         * Draft validation permits incomplete details.
         *
         * Validation is performed before the managed Event entity is changed.
         */
        Venue venue = validateDraftRequest(request, eventID);

        boolean revisingRejectedEvent =
                event.getEventStatus() == EventStatus.REJECTED;

        applyEditableFields(event, request, venue);

        if (revisingRejectedEvent) {
            event.setEventStatus(EventStatus.DRAFT);
        }

        event.setUpdatedAt(LocalDateTime.now(clock));

        Event savedEvent = eventRepository.save(event);

        return eventMapper.mapToResponse(
                savedEvent,
                society,
                venue
        );
    }

    /**
     * Creates a new event in DRAFT status.
     *
     * Drafts may contain incomplete event details.
     */
    @Transactional
    public EventResponseDTO createEvent(
            String societyID,
            String executiveEmail,
            CreateEventRequestDTO request) {

        Student student = studentRepository.findByEmail(executiveEmail)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Authenticated user is not an active society executive."
                ));

        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not found: " + societyID
                ));

        validateSocietyForEventCreation(society);

        boolean authorisedExecutive = executiveRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .anyMatch(executive ->
                        executive.getId()
                                .getSocietyID()
                                .equals(societyID)
                                && isExecutiveTermActive(executive)
                );

        if (!authorisedExecutive) {
            throw new ForbiddenOperationException(
                    "You are not an active executive of this society."
            );
        }

        /*
         * Draft creation uses partial validation.
         *
         * Missing fields are permitted while the event remains DRAFT.
         */
        Venue venue = validateDraftRequest(request, null);

        Event event = mapToEvent(request, venue);

        event.setEventID(generateEventID());
        event.setEventStatus(EventStatus.DRAFT);
        event.setAdvertisementVersion(1);
        event.setOverallRating(null);

        LocalDateTime createdAt = LocalDateTime.now(clock);

        event.setCreatedAt(createdAt);
        event.setUpdatedAt(createdAt);

        Event savedEvent = eventRepository.save(event);

        Hoster primaryHost = new Hoster();

        primaryHost.setId(new HosterId(
                savedEvent.getEventID(),
                society.getSocietyID()
        ));

        primaryHost.setEvent(savedEvent);
        primaryHost.setSociety(society);
        primaryHost.setIsPrimary(true);

        hosterRepository.save(primaryHost);

        return eventMapper.mapToResponse(
                savedEvent,
                society,
                venue
        );
    }

    /**
     * Submits a complete DRAFT event for approval.
     *
     * Unlike draft saving, this method requires all mandatory details.
     */
    @Transactional
    public EventResponseDTO submitEventForApproval(
            String eventID,
            String executiveEmail) {

        Society society = resolveActiveExecutiveSociety(executiveEmail);
        Event event = lockEvent(eventID);

        assertEventBelongsToSociety(
                eventID,
                society.getSocietyID()
        );

        if (event.getEventStatus() != EventStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only events in DRAFT status may be submitted for approval."
            );
        }

        validateSocietyForEventCreation(society);

        /*
         * Submission performs complete validation.
         */
        Venue venue = validateEventForSubmission(event);

        event.setEventStatus(EventStatus.PROPOSED);
        event.setUpdatedAt(LocalDateTime.now(clock));

        Event submittedEvent = eventRepository.save(event);

        return eventMapper.mapToResponse(
                submittedEvent,
                society,
                venue
        );
    }

    /**
     * Withdraws an event proposal while it is awaiting SDO review.
     *
     * <p>The authenticated executive's active society is resolved from
     * persistence. The event must belong to that society through its primary
     * Hoster record and must currently be in PROPOSED status.</p>
     *
     * @param eventID identifier of the proposed event
     * @param executiveEmail authenticated executive email from Spring Security
     * @return the existing event with WITHDRAWN status
     * @throws IllegalArgumentException when an input is blank
     * @throws ForbiddenOperationException when the executive's society is not
     * the event's primary host
     * @throws IllegalStateException when the event is not PROPOSED
     */
    @Transactional
    public EventResponseDTO withdrawEventProposal(
            String eventID,
            String executiveEmail) {

        validateWithdrawalInput(
                eventID,
                executiveEmail
        );

        Society society = resolveActiveExecutiveSociety(executiveEmail);
        Event event = lockEvent(eventID);

        assertEventBelongsToPrimarySociety(
                eventID,
                society.getSocietyID()
        );

        if (event.getEventStatus() != EventStatus.PROPOSED) {
            throw new IllegalStateException(
                    "Only proposed events may be withdrawn."
            );
        }

        LocalDateTime withdrawnAt = LocalDateTime.now(clock);

        event.setEventStatus(EventStatus.WITHDRAWN);
        event.setUpdatedAt(withdrawnAt);

        Event withdrawnEvent = eventRepository.save(event);

        recordEventProposalWithdrawal(
                withdrawnEvent,
                society,
                executiveEmail,
                withdrawnAt
        );

        /*
         * TODO C200 email:
         * Once responsible-SDO event workflow notifications are implemented,
         * add EVENT_PROPOSAL_WITHDRAWN and send it through EmailService.
         */

        return eventMapper.mapToResponse(
                withdrawnEvent,
                society,
                resolveEventVenue(withdrawnEvent)
        );
    }

    @Transactional
    public Event proposeEvent(Event event) {
        event.setEventStatus(EventStatus.PROPOSED);
        event.setUpdatedAt(LocalDateTime.now(clock));

        return eventRepository.save(event);
    }

    @Transactional
    public Event approveEvent(

            // TODO B200:
            // Verify that approvingSdoEmail belongs to the SDO responsible for
            // the society hosting this event.

            String eventID,
            String approvingSdoEmail) {

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event not found: " + eventID
                ));

        event.setEventStatus(EventStatus.APPROVED);
        event.setUpdatedAt(LocalDateTime.now(clock));

        return eventRepository.save(event);
    }

    @Transactional
    public Event publishEvent(String eventID) {

        Event event = eventRepository.findById(eventID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event not found: " + eventID
                ));

        if (event.getEventStatus() != EventStatus.APPROVED) {
            throw new IllegalStateException(
                    "Event must be APPROVED before it can be published."
            );
        }

        event.setEventStatus(EventStatus.PUBLISHED);
        event.setUpdatedAt(LocalDateTime.now(clock));

        return eventRepository.save(event);
    }

    public Event save(Event event) {
        return eventRepository.save(event);
    }

    /**
     * Permanently deletes a society-owned event while it is still a draft.
     *
     * <p>The executive identity and society ownership are resolved from
     * persistence. The browser supplies only the event identifier.</p>
     *
     * @param eventID identifier of the draft event
     * @param executiveEmail authenticated executive email from Spring Security
     * @throws IllegalArgumentException when the input, executive, event, or
     * ownership is invalid
     * @throws IllegalStateException when the event is no longer a draft
     */
    @Transactional
    public void deleteDraftEvent(
            String eventID,
            String executiveEmail) {

        validateDraftDeletionInput(
                eventID,
                executiveEmail
        );

        Society society =
                resolveExecutiveSocietyForDraftDeletion(
                        executiveEmail
                );

        Event event = eventRepository.findByIdForUpdate(eventID)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Event not found."
                        ));

        boolean ownedByExecutiveSociety =
                hosterRepository
                        .existsByIdEventIDAndIdSocietyID(
                                eventID,
                                society.getSocietyID()
                        );

        if (!ownedByExecutiveSociety) {
            throw new IllegalArgumentException(
                    "You are not authorised to delete this draft."
            );
        }

        if (event.getEventStatus() != EventStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only draft events may be deleted."
            );
        }

        /*
         * Remove every database child before deleting Hoster and Event rows.
         * Although normal drafts do not yet have RSVPs, feedback, or outcomes,
         * cleaning them here keeps the aggregate deletion safe if legacy data
         * violates that workflow invariant.
         *
         * Event media is stored as URL fields on Event; this project has no
         * EventMedia entity or EventMediaRepository.
         */
        eventRepository.deleteBudgetRequestsByEventID(
                eventID
        );
        eventRepository.deleteEventOutcomesByEventID(
                eventID
        );
        eventRepository.deleteRsvpsByEventID(
                eventID
        );
        eventRepository.deleteEventFeedbackByEventID(
                eventID
        );

        List<Hoster> eventHosters =
                hosterRepository.findByIdEventID(eventID);

        if (!eventHosters.isEmpty()) {
            hosterRepository.deleteAll(eventHosters);
        }

        eventRepository.delete(event);
    }

    /**
     * Compatibility alias retained for existing internal callers.
     *
     * @deprecated use {@link #deleteDraftEvent(String, String)}
     */
    @Deprecated
    @Transactional
    public void deleteDraft(
            String eventID,
            String executiveEmail) {

        deleteDraftEvent(eventID, executiveEmail);
    }

    /**
     * Retained only for older internal functionality.
     *
     * New controller endpoints should use deleteDraftEvent so status and
     * ownership are enforced.
     */
    @Deprecated
    public void deleteById(String eventID) {
        eventRepository.deleteById(eventID);
    }

    public String generateRSVPLink(
            Event event,
            Student student) {

        Date expiry = Date.from(
                event.getEventDate()
                        .atStartOfDay()
                        .plusDays(1)
                        .toInstant(ZoneOffset.UTC)
        );

        String token = jwtUtil.generateRSVPToken(
                student.getStudentNumber(),
                event.getEventID(),
                event.getAdvertisementVersion(),
                expiry
        );

        return baseUrl
                + "/events/"
                + event.getEventID()
                + "/rsvp?token="
                + token;
    }

    private void validateSocietyForEventCreation(Society society) {

        if (!Boolean.TRUE.equals(society.getActiveStatus())) {
            throw new IllegalStateException(
                    "Only active societies may create events."
            );
        }

        if (Boolean.TRUE.equals(society.getIsFlagged())) {
            throw new IllegalStateException(
                    "Flagged societies may not create events."
            );
        }
    }

    /**
     * Performs partial validation for draft creation and draft updates.
     *
     * Missing values are accepted. Values that are supplied must be valid.
     *
     * @param request draft form values
     * @param currentEventID existing Event ID during updates, otherwise null
     * @return authoritative Venue when enough venue information exists,
     * otherwise null
     */
    private Venue validateDraftRequest(
            CreateEventRequestDTO request,
            String currentEventID) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Event details are required."
            );
        }

        LocalDateTime validationTime = LocalDateTime.now(clock);

        validatePartialEventSchedule(
                request,
                validationTime
        );

        validatePartialRsvpWindow(
                request,
                validationTime
        );

        if (request.getEventLimit() != null
                && request.getEventLimit() < 1) {

            throw new IllegalArgumentException(
                    "Event attendance limit must be at least 1."
            );
        }

        boolean venueSelected =
                request.getVenueCode() != null
                        && !request.getVenueCode().isBlank();

        if (!venueSelected) {
            return null;
        }

        /*
         * Campus and attendance limit are needed to perform authoritative
         * venue validation.
         *
         * When they have not yet been entered, the draft may still be saved.
         */
        if (request.getEventCampus() == null
                || request.getEventLimit() == null) {

            return venueService.findByVenueCode(
                            request.getVenueCode()
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Selected venue was not found."
                    ));
        }

        Venue venue;

        if (currentEventID == null) {
            venue = venueService.lockAndValidateForEventCreation(
                    request.getVenueCode(),
                    request.getEventCampus(),
                    request.getEventLimit()
            );
        } else {
            venue = venueService.lockAndValidateForEventUpdate(
                    request.getVenueCode(),
                    request.getEventCampus(),
                    request.getEventLimit()
            );
        }

        /*
         * Venue conflict validation requires a complete event schedule.
         */
        boolean completeSchedule =
                request.getEventDate() != null
                        && request.getEventStartTime() != null
                        && request.getEventEndTime() != null;

        if (completeSchedule) {
            if (currentEventID == null) {
                venueService.assertAvailableForEventCreation(
                        venue.getVenueCode(),
                        request.getEventDate(),
                        request.getEventStartTime(),
                        request.getEventEndTime()
                );
            } else {
                venueService.assertAvailableForEventUpdate(
                        currentEventID,
                        venue.getVenueCode(),
                        request.getEventDate(),
                        request.getEventStartTime(),
                        request.getEventEndTime()
                );
            }
        }

        return venue;
    }

    /**
     * Performs complete validation before an Event enters the approval
     * workflow.
     */
    private Venue validateSubmissionRequest(
            CreateEventRequestDTO request,
            String currentEventID) {

        validateRequiredEventFields(request);

        LocalDateTime validationTime = LocalDateTime.now(clock);

        LocalDateTime eventStart = validateEventSchedule(
                request,
                validationTime
        );

        validateRsvpWindow(
                request,
                eventStart,
                validationTime
        );

        Venue venue = venueService.lockAndValidateForEventUpdate(
                request.getVenueCode(),
                request.getEventCampus(),
                request.getEventLimit()
        );

        venueService.assertAvailableForEventUpdate(
                currentEventID,
                venue.getVenueCode(),
                request.getEventDate(),
                request.getEventStartTime(),
                request.getEventEndTime()
        );

        validateRequiredEventMedia(request);

        return venue;
    }

    /**
     * Validates schedule fields supplied in a draft.
     *
     * Missing schedule fields are accepted.
     */
    private void validatePartialEventSchedule(
            CreateEventRequestDTO request,
            LocalDateTime validationTime) {

        LocalDate eventDate = request.getEventDate();
        LocalTime startTime = request.getEventStartTime();
        LocalTime endTime = request.getEventEndTime();

        if (eventDate != null
                && eventDate.isBefore(validationTime.toLocalDate())) {

            throw new IllegalArgumentException(
                    "Event date cannot be before today."
            );
        }

        if (startTime != null
                && endTime != null
                && !startTime.isBefore(endTime)) {

            throw new IllegalArgumentException(
                    "Event end time must be after the start time."
            );
        }

        if (eventDate != null
                && startTime != null
                && eventDate.equals(validationTime.toLocalDate())) {

            LocalDateTime eventStart = LocalDateTime.of(
                    eventDate,
                    startTime
            );

            if (!eventStart.isAfter(validationTime)) {
                throw new IllegalArgumentException(
                        "Event start time must be in the future."
                );
            }
        }
    }

    /**
     * Validates RSVP values in a draft only when enough information exists.
     *
     * A partially entered RSVP configuration may remain in a draft.
     * Complete validation runs during submission.
     */
    private void validatePartialRsvpWindow(
            CreateEventRequestDTO request,
            LocalDateTime validationTime) {

        LocalDateTime opening = request.getRsvpOpenDate();
        LocalDateTime closing = request.getRsvpCloseDate();

        /*
         * Drafts may have an incomplete RSVP pair.
         */
        if (opening == null || closing == null) {
            return;
        }

        if (opening.isBefore(validationTime)) {
            throw new IllegalArgumentException(
                    "RSVP opening cannot be in the past."
            );
        }

        if (!opening.isBefore(closing)) {
            throw new IllegalArgumentException(
                    "RSVP opening must be before RSVP closing."
            );
        }

        if (request.getEventDate() == null
                || request.getEventStartTime() == null) {

            return;
        }

        LocalDateTime eventStart = LocalDateTime.of(
                request.getEventDate(),
                request.getEventStartTime()
        );

        LocalDateTime latestAllowedClosingTime =
                eventStart.minusHours(2);

        if (closing.isAfter(latestAllowedClosingTime)) {
            throw new IllegalArgumentException(
                    "RSVP closing must be at least 2 hours before the event starts."
            );
        }
    }

    private LocalDateTime validateEventSchedule(
            CreateEventRequestDTO request,
            LocalDateTime validationTime) {

        LocalDate today = validationTime.toLocalDate();

        if (request.getEventDate().isBefore(today)) {
            throw new IllegalArgumentException(
                    "Event date cannot be before today."
            );
        }

        if (!request.getEventStartTime()
                .isBefore(request.getEventEndTime())) {

            throw new IllegalArgumentException(
                    "Event end time must be after the start time."
            );
        }

        LocalDateTime eventStart = LocalDateTime.of(
                request.getEventDate(),
                request.getEventStartTime()
        );

        if (request.getEventDate().equals(today)
                && !eventStart.isAfter(validationTime)) {

            throw new IllegalArgumentException(
                    "Event start time must be in the future."
            );
        }

        return eventStart;
    }

    private void validateRsvpWindow(
            CreateEventRequestDTO request,
            LocalDateTime eventStart,
            LocalDateTime now) {

        LocalDateTime opening = request.getRsvpOpenDate();
        LocalDateTime closing = request.getRsvpCloseDate();

        if (opening == null && closing == null) {
            return;
        }

        if (opening == null || closing == null) {
            throw new IllegalArgumentException(
                    "Provide both RSVP opening and closing times, or leave both empty."
            );
        }

        if (opening.isBefore(now)) {
            throw new IllegalArgumentException(
                    "RSVP opening cannot be in the past."
            );
        }

        if (!opening.isBefore(closing)) {
            throw new IllegalArgumentException(
                    "RSVP opening must be before RSVP closing."
            );
        }

        LocalDateTime latestAllowedClosingTime =
                eventStart.minusHours(2);

        if (closing.isAfter(latestAllowedClosingTime)) {
            throw new IllegalArgumentException(
                    "RSVP closing must be at least 2 hours before the event starts."
            );
        }
    }

    private void validateRequiredEventFields(
            CreateEventRequestDTO request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Event details are required."
            );
        }

        if (request.getEventName() == null
                || request.getEventName().isBlank()) {

            throw new IllegalArgumentException(
                    "Event name is required."
            );
        }

        if (request.getEventDate() == null) {
            throw new IllegalArgumentException(
                    "Event date is required."
            );
        }

        if (request.getEventStartTime() == null) {
            throw new IllegalArgumentException(
                    "Event start time is required."
            );
        }

        if (request.getEventEndTime() == null) {
            throw new IllegalArgumentException(
                    "Event end time is required."
            );
        }

        if (request.getVenueCode() == null
                || request.getVenueCode().isBlank()) {

            throw new IllegalArgumentException(
                    "Venue selection is required."
            );
        }

        if (request.getEventCampus() == null) {
            throw new IllegalArgumentException(
                    "Event campus is required."
            );
        }

        if (request.getEventDescription() == null
                || request.getEventDescription().isBlank()) {

            throw new IllegalArgumentException(
                    "Event description is required."
            );
        }

        if (request.getEventLimit() == null
                || request.getEventLimit() < 1) {

            throw new IllegalArgumentException(
                    "Event attendance limit must be at least 1."
            );
        }

        if (request.getAttendingType() == null) {
            throw new IllegalArgumentException(
                    "Attending type is required."
            );
        }
    }

    private void validateRequiredEventMedia(
            CreateEventRequestDTO request) {

        if (request.getPosterUrl() == null
                || request.getPosterUrl().isBlank()) {

            throw new IllegalArgumentException(
                    "Event poster is required."
            );
        }

        if (request.getBannerUrl() == null
                || request.getBannerUrl().isBlank()) {

            throw new IllegalArgumentException(
                    "Event banner is required."
            );
        }
    }

    @SuppressWarnings("deprecation")
    private Venue validateEventForSubmission(Event event) {

        CreateEventRequestDTO request =
                new CreateEventRequestDTO();

        request.setEventName(event.getEventName());
        request.setEventDescription(event.getEventDescription());
        request.setEventDate(event.getEventDate());

        request.setEventStartTime(
                event.getEventStartTime() != null
                        ? event.getEventStartTime()
                        : event.getEventTime()
        );

        request.setEventEndTime(event.getEventEndTime());
        request.setVenueCode(event.getVenueCode());
        request.setEventCampus(event.getEventCampus());
        request.setEventLimit(event.getEventLimit());
        request.setAttendingType(event.getAttendingType());
        request.setRsvpOpenDate(event.getRsvpOpenDate());
        request.setRsvpCloseDate(event.getRsvpCloseDate());
        request.setPosterUrl(event.getPosterUrl());
        request.setBannerUrl(event.getBannerUrl());

        return validateSubmissionRequest(
                request,
                event.getEventID()
        );
    }

    private boolean isExecutiveTermActive(
            Executive executive) {

        return executive.getTermEndDate() == null
                || !executive.getTermEndDate()
                .isBefore(LocalDate.now(clock));
    }

    private Event mapToEvent(
            CreateEventRequestDTO request,
            Venue venue) {

        Event event = new Event();

        applyEditableFields(
                event,
                request,
                venue
        );

        return event;
    }

    /**
     * Applies draft fields safely.
     *
     * Missing values remain null while the event is in DRAFT status.
     */
    private void applyEditableFields(
            Event event,
            CreateEventRequestDTO request,
            Venue venue) {

        event.setEventName(
                trimToNull(request.getEventName())
        );

        event.setEventDate(
                request.getEventDate()
        );

        event.setEventStartTime(
                request.getEventStartTime()
        );

        event.setEventEndTime(
                request.getEventEndTime()
        );

        /*
         * Legacy compatibility field.
         */
        event.setEventTime(
                request.getEventStartTime()
        );

        if (venue != null) {
            event.setVenueCode(
                    venue.getVenueCode()
            );

            event.setEventVenue(
                    venue.getVenueName()
            );

            event.setEventCampus(
                    venue.getCampus()
            );
        } else {
            event.setVenueCode(
                    trimToNull(request.getVenueCode())
            );

            event.setEventVenue(null);
            event.setEventCampus(
                    request.getEventCampus()
            );
        }

        event.setEventDescription(
                trimToNull(request.getEventDescription())
        );

        event.setRsvpOpenDate(
                request.getRsvpOpenDate()
        );

        event.setRsvpCloseDate(
                request.getRsvpCloseDate()
        );

        event.setEventLimit(
                request.getEventLimit()
        );

        event.setAttendingType(
                request.getAttendingType()
        );

        event.setPosterUrl(
                trimToNull(request.getPosterUrl())
        );

        event.setBannerUrl(
                trimToNull(request.getBannerUrl())
        );
    }

    private String trimToNull(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    private Venue resolveEventVenue(Event event) {

        if (event.getVenueCode() == null
                || event.getVenueCode().isBlank()) {

            return null;
        }

        return venueService.findByVenueCode(
                        event.getVenueCode()
                )
                .orElse(null);
    }

    private Society resolveActiveExecutiveSociety(
            String executiveEmail) {

        Student student = studentRepository.findByEmail(
                        executiveEmail
                )
                .orElseThrow(() ->
                        new ForbiddenOperationException(
                                "Authenticated user is not an active society executive."
                        ));

        Executive activeRole = executiveRepository
                .findByIdStudentNumber(
                        student.getStudentNumber()
                )
                .stream()
                .filter(executive ->
                        executive.getTermEndDate() == null
                                || executive.getTermEndDate()
                                .isAfter(LocalDate.now(clock))
                )
                .findFirst()
                .orElseThrow(() ->
                        new ForbiddenOperationException(
                                "Authenticated user is not an active society executive."
                        ));

        String societyID =
                activeRole.getId().getSocietyID();

        return societyRepository.findById(societyID)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Society not found: " + societyID
                        ));
    }

    private Hoster requireHostedEvent(
            String eventID,
            String societyID) {

        return requireHostedEvent(
                eventID,
                societyID,
                "The event does not belong to your society."
        );
    }

    private Hoster requireHostedEvent(
            String eventID,
            String societyID,
            String unauthorisedMessage) {

        return hosterRepository.findEventHostedBySociety(
                        eventID,
                        societyID
                )
                .orElseGet(() -> {

                    if (eventRepository.existsById(eventID)) {
                        throw new ForbiddenOperationException(
                                unauthorisedMessage
                        );
                    }

                    throw new ResourceNotFoundException(
                            "Event not found: " + eventID
                    );
                });
    }

    private Event lockEvent(String eventID) {

        return eventRepository.findByIdForUpdate(eventID)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Event not found: " + eventID
                        ));
    }

    private void assertEventBelongsToSociety(
            String eventID,
            String societyID) {

        if (!hosterRepository
                .existsByIdEventIDAndIdSocietyID(
                        eventID,
                        societyID
                )) {

            throw new ForbiddenOperationException(
                    "The event does not belong to your society."
            );
        }
    }

    private void assertEventBelongsToPrimarySociety(
            String eventID,
            String societyID) {

        Hoster hoster = requireHostedEvent(
                eventID,
                societyID,
                "You are not authorised to withdraw this event proposal."
        );

        if (!Boolean.TRUE.equals(hoster.getIsPrimary())) {
            throw new ForbiddenOperationException(
                    "You are not authorised to withdraw this event proposal."
            );
        }
    }

    private void validateDraftDeletionInput(
            String eventID,
            String executiveEmail) {

        if (eventID == null || eventID.isBlank()) {
            throw new IllegalArgumentException(
                    "Event ID is required."
            );
        }

        if (executiveEmail == null
                || executiveEmail.isBlank()) {

            throw new IllegalArgumentException(
                    "Executive email is required."
            );
        }
    }

    private Society resolveExecutiveSocietyForDraftDeletion(
            String executiveEmail) {

        try {
            return resolveActiveExecutiveSociety(
                    executiveEmail
            );
        } catch (ForbiddenOperationException
                 | ResourceNotFoundException exception) {

            throw new IllegalArgumentException(
                    "Invalid executive.",
                    exception
            );
        }
    }

    private void validateWithdrawalInput(
            String eventID,
            String executiveEmail) {

        if (eventID == null || eventID.isBlank()) {
            throw new IllegalArgumentException(
                    "Event ID is required."
            );
        }

        if (executiveEmail == null || executiveEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Executive email is required."
            );
        }
    }

    private void recordEventProposalWithdrawal(
            Event event,
            Society society,
            String executiveEmail,
            LocalDateTime withdrawnAt) {

        AuditLog auditLog = new AuditLog();
        auditLog.setStaffNumber(society.getSdoStaffNumber());
        auditLog.setEntityName("Event");
        auditLog.setOperation("UPDATE");
        auditLog.setFieldChanged("eventStatus");
        auditLog.setOldValue(EventStatus.PROPOSED.name());
        auditLog.setNewValue(EventStatus.WITHDRAWN.name());
        auditLog.setChangedBy(executiveEmail);
        auditLog.setChangedDate(withdrawnAt);
        auditLog.setReason(
                "EVENT_PROPOSAL_WITHDRAWN: " + event.getEventID()
        );

        auditLogRepository.save(auditLog);
    }

    private void validateExecutiveEventPage(
            int page,
            int size) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative."
            );
        }

        if (size < 1
                || size > MAX_EXECUTIVE_EVENT_PAGE_SIZE) {

            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_EXECUTIVE_EVENT_PAGE_SIZE
                            + "."
            );
        }
    }

    private String normalizeSearchPattern(
            String search) {

        if (search == null || search.isBlank()) {
            return null;
        }

        return "%"
                + search.trim()
                .toLowerCase(Locale.ROOT)
                + "%";
    }

    private EventListItemDTO mapToListItem(
            ExecutiveEventListProjection event) {

        return EventListItemDTO.builder()
                .eventID(event.getEventID())
                .eventName(event.getEventName())
                .eventDescriptionSummary(
                        summarizeDescription(
                                event.getEventDescription()
                        )
                )
                .eventDate(event.getEventDate())
                .eventStartTime(event.getEventStartTime())
                .eventEndTime(event.getEventEndTime())
                .eventStatus(event.getEventStatus())
                .campus(event.getCampus())
                .venueCode(event.getVenueCode())
                .venueName(event.getVenueName())
                .venueCapacity(event.getVenueCapacity())
                .eventLimit(event.getEventLimit())
                .posterUrl(event.getPosterUrl())
                .bannerUrl(event.getBannerUrl())
                .societyID(event.getSocietyID())
                .societyName(event.getSocietyName())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .rejectionReason(event.getRejectionReason())
                .build();
    }

    private String summarizeDescription(
            String description) {

        if (description == null) {
            return null;
        }

        String normalized = description
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.length()
                <= EVENT_DESCRIPTION_SUMMARY_LENGTH) {

            return normalized;
        }

        return normalized.substring(
                0,
                EVENT_DESCRIPTION_SUMMARY_LENGTH - 1
        ) + "…";
    }

    private String generateEventID() {

        String eventID;

        do {
            long sequence =
                    eventRepository.count() + 1;

            eventID = String.format(
                    "EVT%03d",
                    sequence
            );

            if (eventRepository.existsById(eventID)) {
                sequence++;

                while (eventRepository.existsById(
                        String.format(
                                "EVT%03d",
                                sequence
                        ))) {

                    sequence++;
                }

                eventID = String.format(
                        "EVT%03d",
                        sequence
                );
            }

        } while (eventRepository.existsById(eventID));

        return eventID;
    }


    // TODO:
    // totalRSVP and eventAttendance are derived values. They should be
    // exposed through response DTOs rather than persisted as Event columns.

    // TODO:
    // overallRating should later be calculated from EventFeedback records
    // instead of being manually assigned.
}
