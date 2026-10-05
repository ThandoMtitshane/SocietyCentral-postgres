package com.societycentral.service;

import com.societycentral.dto.response.VenueAvailabilityDTO;
import com.societycentral.exception.VenueUnavailableException;
import com.societycentral.model.Campus;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Venue;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.VenueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Applies authoritative venue availability, campus and capacity rules.
 */
@Service
public class VenueService {

    private static final Set<EventStatus> BLOCKING_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(
                    EventStatus.DRAFT,
                    EventStatus.PROPOSED,
                    EventStatus.APPROVED,
                    EventStatus.PUBLISHED));

    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final Clock clock;

    /**
     * Creates the venue service.
     *
     * @param venueRepository venue persistence and locking access
     * @param eventRepository event overlap access
     * @param clock server-local clock used for date validation
     */
    @Autowired
    public VenueService(
            VenueRepository venueRepository,
            EventRepository eventRepository,
            Clock clock) {
        this.venueRepository = venueRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * Returns active venues available for an event window on one campus.
     *
     * @param campus requested campus
     * @param eventDate requested date
     * @param startTime requested start time
     * @param endTime requested end time
     * @return matching available venues
     */
    @Transactional(readOnly = true)
    public List<VenueAvailabilityDTO> getAvailableVenues(
            Campus campus,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime) {
        return getAvailableVenues(
                campus,
                eventDate,
                startTime,
                endTime,
                null);
    }

    /**
     * Returns active venues available for an event edit, optionally excluding
     * the event's own reservation.
     *
     * @param campus requested campus
     * @param eventDate requested date
     * @param startTime requested start time
     * @param endTime requested end time
     * @param excludeEventID event currently being edited, or null
     * @return matching available venues
     */
    @Transactional(readOnly = true)
    public List<VenueAvailabilityDTO> getAvailableVenues(
            Campus campus,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            String excludeEventID) {
        validateAvailabilityPeriod(campus, eventDate, startTime, endTime);

        String normalizedExcludedEventID =
                normalizeOptionalEventID(excludeEventID);
        List<Venue> availableVenues = normalizedExcludedEventID == null
                ? venueRepository.findAvailableVenues(
                        campus,
                        eventDate,
                        startTime,
                        endTime,
                        BLOCKING_STATUSES)
                : venueRepository.findAvailableVenuesExcludingEvent(
                        campus,
                        eventDate,
                        startTime,
                        endTime,
                        normalizedExcludedEventID,
                        BLOCKING_STATUSES);

        return availableVenues
                .stream()
                .map(this::mapToAvailability)
                .toList();
    }

    /**
     * Finds a venue by its normalized code for response enrichment.
     *
     * @param venueCode venue identifier
     * @return the venue when the code is present and exists
     */
    @Transactional(readOnly = true)
    public Optional<Venue> findByVenueCode(String venueCode) {
        if (venueCode == null || venueCode.isBlank()) {
            return Optional.empty();
        }
        return venueRepository.findById(venueCode.trim().toUpperCase());
    }

    /**
     * Locks and validates a venue before a new Event is inserted.
     *
     * This method must join the event-creation transaction. Locking the stable
     * Venue row serializes reservations for the same venue: a second
     * transaction waits, then repeats the overlap query after the first
     * transaction commits. A normal unique constraint cannot represent
     * arbitrary time-range overlaps.
     *
     * @param venueCode selected venue identifier
     * @param selectedCampus campus supplied with the event request
     * @param eventLimit expected attendance
     * @return the locked, authoritative Venue entity
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Venue lockAndValidateForEventCreation(
            String venueCode,
            Campus selectedCampus,
            int eventLimit) {
        return lockAndValidateVenue(
                venueCode,
                selectedCampus,
                eventLimit,
                false);
    }

    /**
     * Reloads and locks the authoritative venue before an Event edit.
     *
     * <p>The request supplies only the venue code, campus and attendance
     * limit. Venue name, type, capacity and active state always come from the
     * locked database row.</p>
     *
     * @param venueCode selected venue identifier
     * @param selectedCampus campus supplied with the event request
     * @param eventLimit expected attendance
     * @return the locked, authoritative Venue entity
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Venue lockAndValidateForEventUpdate(
            String venueCode,
            Campus selectedCampus,
            int eventLimit) {
        return lockAndValidateVenue(
                venueCode,
                selectedCampus,
                eventLimit,
                true);
    }

    private Venue lockAndValidateVenue(
            String venueCode,
            Campus selectedCampus,
            int eventLimit,
            boolean editingExistingEvent) {
        String normalizedVenueCode = normalizeVenueCode(venueCode);

        Venue venue = venueRepository.findByVenueCodeForUpdate(normalizedVenueCode)
                .orElseThrow(() -> editingExistingEvent
                        ? new VenueUnavailableException(
                                "The selected venue is no longer available.")
                        : new IllegalArgumentException(
                                "Venue not found: " + normalizedVenueCode));

        return validateLockedVenue(venue, selectedCampus, eventLimit);
    }

    /**
     * Rechecks overlap availability after the selected venue row is locked.
     *
     * @param venueCode locked venue identifier
     * @param eventDate event date
     * @param startTime event start time
     * @param endTime event end time
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertAvailableForEventCreation(
            String venueCode,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime) {
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required.");
        }

        // BUSINESS RULE: Never execute an overlap query for an equal or
        // reversed period, even if this recheck is called outside the current
        // EventService creation flow.
        validateTimeOrder(startTime, endTime);

        // BUSINESS RULE: Drafts reserve venues immediately so another
        // executive cannot create a competing draft. REJECTED, CANCELLED and
        // COMPLETED events do not block. Draft expiry is a future enhancement.
        if (eventRepository.countVenueConflicts(
                normalizeVenueCode(venueCode),
                eventDate,
                startTime,
                endTime,
                BLOCKING_STATUSES) > 0) {
            throw new VenueUnavailableException();
        }
    }

    /**
     * Rechecks overlap availability while excluding the Event row currently
     * being edited or submitted.
     *
     * <p>This method must run after the same authoritative Venue row has been
     * locked by {@link #lockAndValidateForEventUpdate(String, Campus, int)}.
     * Excluding the current identifier prevents an unchanged draft from
     * conflicting with its own reservation while still detecting every other
     * blocking event.</p>
     *
     * @param currentEventID event being edited or submitted
     * @param venueCode locked venue identifier
     * @param eventDate event date
     * @param startTime event start time
     * @param endTime event end time
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertAvailableForEventUpdate(
            String currentEventID,
            String venueCode,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime) {
        if (currentEventID == null || currentEventID.isBlank()) {
            throw new IllegalArgumentException("Event identifier is required.");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required.");
        }

        validateTimeOrder(startTime, endTime);

        if (eventRepository.countVenueConflictsExcludingEvent(
                currentEventID,
                normalizeVenueCode(venueCode),
                eventDate,
                startTime,
                endTime,
                BLOCKING_STATUSES) > 0) {
            throw new VenueUnavailableException(
                    "The selected venue is already booked for this date and time.");
        }
    }

    private Venue validateLockedVenue(
            Venue venue,
            Campus selectedCampus,
            int eventLimit) {
        // BUSINESS RULE: Inactive venues cannot be selected or reserved.
        if (!venue.isActive()) {
            throw new IllegalStateException("Selected venue is not active.");
        }

        // BUSINESS RULE: The venue row is authoritative for campus identity.
        if (venue.getCampus() != selectedCampus) {
            throw new IllegalArgumentException(
                    "Selected venue does not belong to the selected campus.");
        }

        // BUSINESS RULE: Capacity always comes from Venue; the request can
        // specify only the desired event attendance limit.
        if (eventLimit > venue.getCapacity()) {
            throw new IllegalArgumentException(
                    "Expected attendance cannot exceed the selected venue capacity of "
                            + venue.getCapacity()
                            + ".");
        }

        return venue;
    }

    private void validateAvailabilityPeriod(
            Campus campus,
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime) {
        if (campus == null) {
            throw new IllegalArgumentException("Campus is required.");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required.");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Event start time is required.");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Event end time is required.");
        }
        LocalDateTime validationTime = LocalDateTime.now(clock);
        LocalDate today = validationTime.toLocalDate();
        if (eventDate.isBefore(today)) {
            throw new IllegalArgumentException(
                    "Event date cannot be before today.");
        }

        validateTimeOrder(startTime, endTime);

        // BUSINESS RULE: A venue cannot be selected for a same-day event that
        // has already started (or starts at the current instant).
        if (eventDate.equals(today)
                && !LocalDateTime.of(eventDate, startTime)
                .isAfter(validationTime)) {
            throw new IllegalArgumentException(
                    "Event start time must be in the future.");
        }
    }

    private void validateTimeOrder(
            LocalTime startTime,
            LocalTime endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException(
                    "Event start time is required.");
        }
        if (endTime == null) {
            throw new IllegalArgumentException(
                    "Event end time is required.");
        }

        // BUSINESS RULE: Availability is for one calendar date. Equal or
        // reversed times are invalid; an earlier end is never treated as the
        // following day because overnight events are not supported.
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException(
                    "Event end time must be after the start time.");
        }
    }

    private String normalizeVenueCode(String venueCode) {
        if (venueCode == null || venueCode.isBlank()) {
            throw new IllegalArgumentException("Venue selection is required.");
        }
        return venueCode.trim().toUpperCase();
    }

    private String normalizeOptionalEventID(String eventID) {
        if (eventID == null || eventID.isBlank()) {
            return null;
        }
        return eventID.trim();
    }

    private VenueAvailabilityDTO mapToAvailability(Venue venue) {
        return VenueAvailabilityDTO.builder()
                .venueCode(venue.getVenueCode())
                .campus(venue.getCampus())
                .venueName(venue.getVenueName())
                .venueType(venue.getVenueType())
                .capacity(venue.getCapacity())
                .build();
    }
}
