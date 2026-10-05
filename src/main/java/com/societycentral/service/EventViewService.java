package com.societycentral.service;

import com.societycentral.dto.response.EventSummaryDTO;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.StudentEventSummaryDTO;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.mapper.EventMapper;
import com.societycentral.model.Event;
import com.societycentral.model.Hoster;
import com.societycentral.model.RsvpId;
import com.societycentral.model.Student;
import com.societycentral.model.Venue;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Resolves which published events a user can see (A400 - View Events).
 *
 * Student: sees PUBLISHED events hosted by their active societies, or open to every student.
 * Active executive and SDO: see all PUBLISHED events.
 */
@Service
@RequiredArgsConstructor
public class EventViewService {

    private final EventRepository eventRepository;
    private final StudentRepository studentRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final ExecutiveRepository executiveRepository;
    private final HosterRepository hosterRepository;
    private final RSVPRepository rsvpRepository;
    private final VenueService venueService;
    private final EventMapper eventMapper;
    private final Clock clock;

    /**
     * Returns the list of visible events for the logged-in student.
     *
     * @param email the authenticated user's email (from JWT)
     * @return the list of published events visible to the student
     */
    @Transactional(readOnly = true)
    public List<StudentEventSummaryDTO> getVisibleEventsForStudent(
            String email) {

        // 1. Validate the authenticated email
        validateEmail(email);

        // 2. Find the student
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student not found."
                        ));

        // 3. Give active executives unrestricted published-event visibility
        LocalDate today = LocalDate.now(clock);
        if (isActiveExecutive(student, today)) {
            return getAllPublishedEvents();
        }

        // 4. Get only their active society memberships
        List<String> societyIDs = societyMemberRepository
                .findActiveSocietyIDsForStudent(
                        student.getStudentNumber(),
                        today
                );

        LocalDateTime now = LocalDateTime.now(clock);

        // 5. Opening time is enforced by the repository query.
        List<Event> events =
                eventRepository.findVisibleEventsForStudent(
                        societyIDs, now
                );

        String studentNumber = student.getStudentNumber();

        // 6. Map to DTO
        return events.stream()
                .map(event -> mapToDTO(event, studentNumber))
                .toList();
    }

    /**
     * Returns every published event for an authenticated executive or Student
     * Development Officer.
     *
     * @return all published events in chronological order
     */
    @Transactional(readOnly = true)
    public List<StudentEventSummaryDTO> getAllPublishedEvents() {

        // 1. Load all published events in chronological order
        List<Event> events = eventRepository.findAllVisiblePublishedEvents(
                LocalDateTime.now(clock));

        // 2. Map to the lightweight event-card DTO
        List<StudentEventSummaryDTO> summaries = events.stream()
                .map(event -> mapToDTO(event, null))
                .toList();

        // 3. Return chronological results
        return summaries;
    }

    /**
     * Returns the events that the authenticated Executive may
     * request a budget for.
     *
     * Business Rule:
     * An Executive may request a budget only for events hosted
     * by their own society.
     *
     * @param email authenticated Executive email
     * @return list of eligible events
     */
    @Transactional(readOnly = true)
    public List<EventSummaryDTO> getEligibleBudgetEvents(String email) {

        validateEmail(email);

        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Student not found."));

        var executive = executiveRepository
                .findActiveExecutiveRoles(student.getStudentNumber(), LocalDate.now(clock))
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Only Executives may request budgets."));

        List<Hoster> hostedEvents = hosterRepository
                .findByIdSocietyID(executive.getId().getSocietyID());

        return hostedEvents.stream()
                .map(Hoster::getEvent)
                .filter(event -> event != null)
                .filter(event -> event.getEventStatus() != null && "APPROVED".equals(event.getEventStatus().name()))
                .map(event -> {
                    EventSummaryDTO dto = EventSummaryDTO.builder()
                            .eventID(event.getEventID())
                            .eventName(event.getEventName())
                            .eventDate(event.getEventDate())
                            .eventTime(event.getEventStartTime() != null
                                    ? event.getEventStartTime() : event.getEventTime())
                            .eventCampus(event.getEventCampus() != null
                                    ? event.getEventCampus().name() : null)
                            .eventVenue(event.getEventVenue())
                            .eventStatus(event.getEventStatus() != null
                                    ? event.getEventStatus().name() : null)
                            .build();
                    dto.setEventLimit(event.getEventLimit());
                    dto.setAttendingType(event.getAttendingType() != null ? event.getAttendingType().name() : null);

                    findPrimaryHost(event.getEventID())
                            .ifPresent(hoster -> {
                                if (hoster.getSociety() != null) {
                                    dto.setPrimarySocietyName(hoster.getSociety().getSocietyName());
                                }
                            });
                    return dto;

                })
                .toList();
    }

    /**
     * Returns complete details for one published event visible to the
     * authenticated student.
     *
     * @param eventID requested event identifier
     * @param email authenticated student's email
     * @return complete visible event details
     */
    @Transactional(readOnly = true)
    public EventResponseDTO getVisibleEventForStudent(
            String eventID,
            String email) {

        // 1. Validate email and event identifier
        validateEmail(email);
        validateEventID(eventID);

        // 2. Find student
        Student student = studentRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student not found."
                        ));

        // 3. Give active executives unrestricted published-event visibility
        LocalDate today = LocalDate.now(clock);
        if (isActiveExecutive(student, today)) {
            return getPublishedEvent(eventID);
        }

        // 4. Determine authorised active societies
        List<String> societyIDs = societyMemberRepository
                .findActiveSocietyIDsForStudent(
                        student.getStudentNumber(),
                        today
                );

        // 5. Retrieve the visible event
        Event event = eventRepository.findVisibleEventForStudent(
                        eventID,
                        societyIDs, LocalDateTime.now(clock)
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Event not found."
                        ));

        // 6. Retrieve the primary host
        Hoster primaryHost = requirePrimaryHost(eventID);

        // 7. Retrieve the authoritative venue
        Venue venue = resolveEventVenue(event);

        // 8. Map the complete event response
        return eventMapper.mapToResponse(
                event,
                primaryHost.getSociety(),
                venue
        );
    }

    /**
     * Returns complete details for one published event to an authenticated
     * executive or Student Development Officer.
     *
     * @param eventID requested event identifier
     * @return complete published event details
     */
    @Transactional(readOnly = true)
    public EventResponseDTO getPublishedEvent(
            String eventID) {

        // 1. Validate event identifier
        validateEventID(eventID);

        // 2. Retrieve the published event
        Event event = eventRepository.findVisiblePublishedEvent(
                        eventID, LocalDateTime.now(clock))
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Event not found."
                        ));

        // 3. Retrieve the primary host
        Hoster primaryHost = requirePrimaryHost(eventID);

        // 4. Retrieve the authoritative venue
        Venue venue = resolveEventVenue(event);

        // 5. Map the complete event response
        return eventMapper.mapToResponse(
                event,
                primaryHost.getSociety(),
                venue
        );
    }

    /**
     * Maps an Event entity to the summary DTO shown in the events list.
     * Primary society name is resolved from the primary Hoster row.
     *
     * @param event the event being mapped
     * @return the event summary DTO
     */
    private StudentEventSummaryDTO mapToDTO(Event event) {
        return mapToDTO(event, null);
    }

    private StudentEventSummaryDTO mapToDTO(Event event, String studentNumber) {

        StudentEventSummaryDTO dto = new StudentEventSummaryDTO();

        dto.setEventID(
                event.getEventID()
        );

        dto.setEventName(event.getEventName());

        dto.setEventDescription(
                event.getEventDescription()
        );

        dto.setEventDate(
                event.getEventDate()
        );

        dto.setEventStartTime(event.getEventStartTime() != null
                        ? event.getEventStartTime()
                        : event.getEventTime()
        );

        dto.setEventEndTime(event.getEventEndTime()
        );

        dto.setVenueName(event.getEventVenue());

        dto.setCampus(
                event.getEventCampus() != null
                        ? event.getEventCampus().name()
                        : null
        );

        dto.setAttendingType(event.getAttendingType() != null ? event.getAttendingType().name() : null);

        dto.setPosterUrl(
                event.getPosterUrl() != null
                        && !event.getPosterUrl().isBlank()
                        ? event.getPosterUrl()
                        : event.getImageUrl()
        );

        dto.setBannerUrl(event.getBannerUrl());

        dto.setEventLimit(event.getEventLimit());
        dto.setRsvpOpenDate(event.getRsvpOpenDate());
        dto.setRsvpCloseDate(event.getRsvpCloseDate());
        if (studentNumber != null) {
            if (rsvpRepository.existsById(new RsvpId(event.getEventID(), studentNumber))) {
                dto.setStudentRsvpStatus("CONFIRMED");
            }
        }

        // Resolve primary society name from Hoster
        findPrimaryHost(event.getEventID())
                .ifPresent(hoster -> {

                    if (hoster.getSociety() != null) {
                        dto.setPrimarySocietyID(
                                hoster.getSociety().getSocietyID()
                        );
                        dto.setPrimarySocietyName(
                                hoster.getSociety()
                                        .getSocietyName()
                        );
                    }
                });

        return dto;
    }

    /**
     * Checks the persistence-backed executive role used by authentication.
     * Executives remain STUDENT users in {@code UserPrincipal}, so a synthetic
     * {@code ROLE_EXECUTIVE} authority cannot be used for this decision.
     *
     * @param student authenticated student record
     * @param currentDate server-local current date
     * @return {@code true} when an executive term includes the current date
     */
    private boolean isActiveExecutive(
            Student student,
            LocalDate currentDate) {

        return !executiveRepository.findActiveExecutiveRoles(
                student.getStudentNumber(),
                currentDate
        ).isEmpty();
    }

    /**
     * Finds the primary Hoster record for an event.
     *
     * @param eventID the event identifier
     * @return the primary Hoster record when available
     */
    private Optional<Hoster> findPrimaryHost(
            String eventID) {

        return hosterRepository.findByIdEventID(eventID)
                .stream()
                .filter(hoster ->
                        Boolean.TRUE.equals(
                                hoster.getIsPrimary()
                        )
                )
                .findFirst();
    }

    /**
     * Returns the primary host required for a complete event response.
     *
     * @param eventID event identifier
     * @return primary host with its society
     */
    private Hoster requirePrimaryHost(
            String eventID) {

        Hoster primaryHost = findPrimaryHost(eventID)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Primary host not found for event: "
                                        + eventID
                        ));

        if (primaryHost.getSociety() == null) {
            throw new ResourceNotFoundException(
                    "Primary host society not found for event: "
                            + eventID
            );
        }

        return primaryHost;
    }

    /**
     * Resolves authoritative venue data while supporting legacy events that
     * only retain their venue display fields.
     *
     * @param event event being returned
     * @return authoritative venue, or {@code null} for a legacy event
     */
    private Venue resolveEventVenue(
            Event event) {

        return venueService.findByVenueCode(
                        event.getVenueCode()
                )
                .orElse(null);
    }

    /**
     * Validates the authenticated user's email.
     *
     * @param email the authenticated user's email
     */
    private void validateEmail(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated user email is required."
            );
        }
    }

    /**
     * Validates the requested event identifier.
     *
     * @param eventID requested event identifier
     */
    private void validateEventID(String eventID) {

        if (eventID == null || eventID.isBlank()) {
            throw new IllegalArgumentException(
                    "Event ID is required."
            );
        }
    }


}
