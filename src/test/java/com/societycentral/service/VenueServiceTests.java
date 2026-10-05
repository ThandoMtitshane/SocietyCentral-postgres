package com.societycentral.service;

import com.societycentral.dto.response.VenueAvailabilityDTO;
import com.societycentral.exception.VenueUnavailableException;
import com.societycentral.model.Campus;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Venue;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VenueServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);
    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 10);
    private static final LocalTime START = LocalTime.of(10, 0);
    private static final LocalTime END = LocalTime.of(12, 0);

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private EventRepository eventRepository;

    private VenueService venueService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T10:00:00Z"),
                ZoneOffset.UTC);
        venueService = new VenueService(
                venueRepository,
                eventRepository,
                clock);
    }

    @Test
    void availableVenuesExposeOnlyDatabaseValues() {
        Venue venue = venue(Campus.SOUTH_CAMPUS, 850);
        when(venueRepository.findAvailableVenues(
                eq(Campus.SOUTH_CAMPUS),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                anyCollection()))
                .thenReturn(List.of(venue));

        List<VenueAvailabilityDTO> available =
                venueService.getAvailableVenues(
                        Campus.SOUTH_CAMPUS,
                        EVENT_DATE,
                        START,
                        END);

        assertEquals(1, available.size());
        assertEquals("SC001", available.getFirst().getVenueCode());
        assertEquals("Auditorium", available.getFirst().getVenueName());
        assertEquals(850, available.getFirst().getCapacity());
    }

    @Test
    void existingVenueRemainsAvailableWhenCurrentEventIsExcluded() {
        Venue venue = venue(Campus.SOUTH_CAMPUS, 850);
        when(venueRepository.findAvailableVenuesExcludingEvent(
                eq(Campus.SOUTH_CAMPUS),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                eq("EVT002"),
                anyCollection()))
                .thenReturn(List.of(venue));

        List<VenueAvailabilityDTO> available =
                venueService.getAvailableVenues(
                        Campus.SOUTH_CAMPUS,
                        EVENT_DATE,
                        START,
                        END,
                        " EVT002 ");

        assertEquals(List.of("SC001"), available.stream()
                .map(VenueAvailabilityDTO::getVenueCode)
                .toList());
        verify(venueRepository).findAvailableVenuesExcludingEvent(
                eq(Campus.SOUTH_CAMPUS),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                eq("EVT002"),
                anyCollection());
    }

    @Test
    void rejectsAvailabilityDateBeforeToday() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.getAvailableVenues(
                        Campus.SOUTH_CAMPUS,
                        TODAY.minusDays(1),
                        START,
                        END));

        assertEquals(
                "Event date cannot be before today.",
                exception.getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void rejectsReversedAvailabilityTimeOrderBeforeRepositoryQuery() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.getAvailableVenues(
                        Campus.SOUTH_CAMPUS,
                        EVENT_DATE,
                        LocalTime.of(23, 0),
                        LocalTime.of(22, 30)));

        assertEquals(
                "Event end time must be after the start time.",
                exception.getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void rejectsEqualAvailabilityTimesBeforeRepositoryQuery() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.getAvailableVenues(
                        Campus.SOUTH_CAMPUS,
                        EVENT_DATE,
                        START,
                        START));

        assertEquals(
                "Event end time must be after the start time.",
                exception.getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void validAvailabilityTimeOrderReachesRepository() {
        LocalTime endTime = LocalTime.of(11, 0);
        when(venueRepository.findAvailableVenues(
                eq(Campus.SOUTH_CAMPUS),
                eq(EVENT_DATE),
                eq(START),
                eq(endTime),
                anyCollection()))
                .thenReturn(List.of());

        venueService.getAvailableVenues(
                Campus.SOUTH_CAMPUS,
                EVENT_DATE,
                START,
                endTime);

        verify(venueRepository).findAvailableVenues(
                eq(Campus.SOUTH_CAMPUS),
                eq(EVENT_DATE),
                eq(START),
                eq(endTime),
                anyCollection());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsTodayStartThatIsNotInTheFuture() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.getAvailableVenues(
                        Campus.SOUTH_CAMPUS,
                        TODAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0)));

        assertEquals(
                "Event start time must be in the future.",
                exception.getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void validatesRequiredAvailabilityParametersInOrder() {
        assertEquals(
                "Campus is required.",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> venueService.getAvailableVenues(
                                null, null, null, null))
                        .getMessage());
        assertEquals(
                "Event date is required.",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> venueService.getAvailableVenues(
                                Campus.SOUTH_CAMPUS, null, null, null))
                        .getMessage());
        assertEquals(
                "Event start time is required.",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> venueService.getAvailableVenues(
                                Campus.SOUTH_CAMPUS,
                                EVENT_DATE,
                                null,
                                null))
                        .getMessage());
        assertEquals(
                "Event end time is required.",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> venueService.getAvailableVenues(
                                Campus.SOUTH_CAMPUS,
                                EVENT_DATE,
                                START,
                                null))
                        .getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void attendanceAboveDatabaseCapacityIsRejected() {
        when(venueRepository.findByVenueCodeForUpdate("SC001"))
                .thenReturn(Optional.of(venue(
                        Campus.SOUTH_CAMPUS,
                        120)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.lockAndValidateForEventCreation(
                        "sc001",
                        Campus.SOUTH_CAMPUS,
                        121));

        assertEquals(
                "Expected attendance cannot exceed the selected venue capacity of 120.",
                exception.getMessage());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void attendanceEqualToDatabaseCapacityIsAccepted() {
        Venue venue = venue(Campus.SOUTH_CAMPUS, 120);
        when(venueRepository.findByVenueCodeForUpdate("SC001"))
                .thenReturn(Optional.of(venue));

        Venue selected = venueService.lockAndValidateForEventCreation(
                "SC001",
                Campus.SOUTH_CAMPUS,
                120);

        assertSame(venue, selected);
    }

    @Test
    void eventUpdateReloadsExistingVenueByCode() {
        Venue venue = venue(Campus.SOUTH_CAMPUS, 120);
        when(venueRepository.findByVenueCodeForUpdate("SC001"))
                .thenReturn(Optional.of(venue));

        Venue selected = venueService.lockAndValidateForEventUpdate(
                "sc001",
                Campus.SOUTH_CAMPUS,
                100);

        assertSame(venue, selected);
        verify(venueRepository).findByVenueCodeForUpdate("SC001");
    }

    @Test
    void removedVenueReturnsEditSpecificConflict() {
        when(venueRepository.findByVenueCodeForUpdate("SC001"))
                .thenReturn(Optional.empty());

        VenueUnavailableException exception = assertThrows(
                VenueUnavailableException.class,
                () -> venueService.lockAndValidateForEventUpdate(
                        "SC001",
                        Campus.SOUTH_CAMPUS,
                        100));

        assertEquals(
                "The selected venue is no longer available.",
                exception.getMessage());
    }

    @Test
    void missingVenueCodeReturnsRequiredSelectionMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.lockAndValidateForEventUpdate(
                        " ",
                        Campus.SOUTH_CAMPUS,
                        100));

        assertEquals("Venue selection is required.", exception.getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void venueCampusMismatchIsRejected() {
        when(venueRepository.findByVenueCodeForUpdate("SC001"))
                .thenReturn(Optional.of(venue(
                        Campus.SOUTH_CAMPUS,
                        850)));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.lockAndValidateForEventCreation(
                        "SC001",
                        Campus.NORTH_CAMPUS,
                        100));

        assertEquals(
                "Selected venue does not belong to the selected campus.",
                exception.getMessage());
    }

    @Test
    void overlapRecheckUsesOnlyActiveWorkflowStatuses() {
        when(eventRepository.countVenueConflicts(
                eq("SC001"),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                anyCollection())).thenReturn(1L);

        assertThrows(
                VenueUnavailableException.class,
                () -> venueService.assertAvailableForEventCreation(
                        "SC001",
                        EVENT_DATE,
                        START,
                        END));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<EventStatus>> statuses =
                ArgumentCaptor.forClass(Collection.class);
        verify(eventRepository).countVenueConflicts(
                org.mockito.ArgumentMatchers.eq("SC001"),
                org.mockito.ArgumentMatchers.eq(EVENT_DATE),
                org.mockito.ArgumentMatchers.eq(START),
                org.mockito.ArgumentMatchers.eq(END),
                statuses.capture());

        assertTrue(statuses.getValue().contains(EventStatus.DRAFT));
        assertTrue(statuses.getValue().contains(EventStatus.PROPOSED));
        assertTrue(statuses.getValue().contains(EventStatus.APPROVED));
        assertTrue(statuses.getValue().contains(EventStatus.PUBLISHED));
        assertFalse(statuses.getValue().contains(EventStatus.CANCELLED));
        assertFalse(statuses.getValue().contains(EventStatus.REJECTED));
        assertFalse(statuses.getValue().contains(EventStatus.COMPLETED));
    }

    @Test
    void invalidRangeNeverExecutesVenueConflictQuery() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> venueService.assertAvailableForEventCreation(
                        "SC001",
                        EVENT_DATE,
                        LocalTime.of(23, 0),
                        LocalTime.of(22, 30)));

        assertEquals(
                "Event end time must be after the start time.",
                exception.getMessage());
        verifyNoInteractions(venueRepository, eventRepository);
    }

    @Test
    void editAvailabilityExcludesCurrentEventIdentifier() {
        when(eventRepository.countVenueConflictsExcludingEvent(
                eq("EVT100"),
                eq("SC001"),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                anyCollection())).thenReturn(0L);

        venueService.assertAvailableForEventUpdate(
                "EVT100",
                "sc001",
                EVENT_DATE,
                START,
                END);

        verify(eventRepository).countVenueConflictsExcludingEvent(
                eq("EVT100"),
                eq("SC001"),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                anyCollection());
    }

    @Test
    void editConflictWithAnotherEventReturnsConflictException() {
        when(eventRepository.countVenueConflictsExcludingEvent(
                eq("EVT100"),
                eq("SC001"),
                eq(EVENT_DATE),
                eq(START),
                eq(END),
                anyCollection())).thenReturn(1L);

        VenueUnavailableException exception = assertThrows(
                VenueUnavailableException.class,
                () -> venueService.assertAvailableForEventUpdate(
                        "EVT100",
                        "SC001",
                        EVENT_DATE,
                        START,
                        END));

        assertEquals(
                "The selected venue is already booked for this date and time.",
                exception.getMessage());
    }

    private Venue venue(Campus campus, int capacity) {
        Venue venue = new Venue();
        venue.setVenueCode("SC001");
        venue.setCampus(campus);
        venue.setVenueName("Auditorium");
        venue.setVenueType("Auditorium");
        venue.setCapacity(capacity);
        venue.setActive(true);
        return venue;
    }
}
