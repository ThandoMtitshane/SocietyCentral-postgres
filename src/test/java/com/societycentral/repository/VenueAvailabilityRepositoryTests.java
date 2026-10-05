package com.societycentral.repository;

import com.societycentral.model.Campus;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Venue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:venue-tests;MODE=MSSQLServer;"
                + "DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,EVENT,YEAR,MONTH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VenueAvailabilityRepositoryTests {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 8, 10);
    private static final Set<EventStatus> BLOCKING_STATUSES = EnumSet.of(
            EventStatus.DRAFT,
            EventStatus.PROPOSED,
            EventStatus.APPROVED,
            EventStatus.PUBLISHED);

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void saveCampusVenues() {
        venueRepository.save(southVenue("SC001"));
        venueRepository.save(northVenue("NC001"));
    }

    @AfterEach
    void removeCommittedConcurrencyData() {
        eventRepository.deleteById("CONCURRENT_A");
        eventRepository.deleteById("CONCURRENT_B");
        venueRepository.deleteById("CC001");
    }

    @Test
    void availableVenuesAreFilteredByCampus() {
        List<Venue> available = findAvailable(
                Campus.SOUTH_CAMPUS,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0));

        assertEquals(List.of("SC001"), venueCodes(available));
    }

    @Test
    void southCampusQueryDoesNotReturnNorthCampusVenues() {
        List<Venue> available = findAvailable(
                Campus.SOUTH_CAMPUS,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0));

        assertFalse(venueCodes(available).contains("NC001"));
    }

    @ParameterizedTest(name = "{0}-{1} available={2}")
    @MethodSource("overlapWindows")
    void overlapQueryUsesStrictRangeIntersection(
            LocalTime requestedStart,
            LocalTime requestedEnd,
            boolean expectedAvailable) {
        eventRepository.saveAndFlush(event(
                "EXISTING",
                "SC001",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                EventStatus.DRAFT));

        boolean available = venueCodes(findAvailable(
                Campus.SOUTH_CAMPUS,
                requestedStart,
                requestedEnd)).contains("SC001");

        assertEquals(expectedAvailable, available);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {
            "CANCELLED",
            "REJECTED",
            "COMPLETED"
    })
    void inactiveWorkflowStatusesDoNotBlock(EventStatus status) {
        eventRepository.saveAndFlush(event(
                "NB_" + status.name(),
                "SC001",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                status));

        assertTrue(venueCodes(findAvailable(
                Campus.SOUTH_CAMPUS,
                LocalTime.of(11, 0),
                LocalTime.of(13, 0))).contains("SC001"));
    }

    @Test
    void activeDraftBlocksVenue() {
        eventRepository.saveAndFlush(event(
                "DRAFT_BLOCK",
                "SC001",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                EventStatus.DRAFT));

        assertFalse(venueCodes(findAvailable(
                Campus.SOUTH_CAMPUS,
                LocalTime.of(11, 0),
                LocalTime.of(13, 0))).contains("SC001"));
    }

    @Test
    void availableVenueQueryIgnoresEditedEventsOwnReservation() {
        eventRepository.saveAndFlush(event(
                "EDITED_EVENT",
                "SC001",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                EventStatus.DRAFT));

        assertTrue(venueCodes(findAvailableExcluding(
                Campus.SOUTH_CAMPUS,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                "EDITED_EVENT")).contains("SC001"));
    }

    @Test
    void availableVenueQueryStillDetectsEveryOtherConflict() {
        eventRepository.saveAndFlush(event(
                "EDITED_EVENT",
                "SC001",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                EventStatus.DRAFT));
        eventRepository.saveAndFlush(event(
                "OTHER_EVENT",
                "SC001",
                LocalTime.of(11, 0),
                LocalTime.of(13, 0),
                EventStatus.PROPOSED));

        assertFalse(venueCodes(findAvailableExcluding(
                Campus.SOUTH_CAMPUS,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                "EDITED_EVENT")).contains("SC001"));
    }

    @Test
    void overlapQueryExcludesOnlyTheEventBeingEdited() {
        eventRepository.saveAndFlush(event(
                "EDITED_EVENT",
                "SC001",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                EventStatus.DRAFT));

        assertEquals(0L, eventRepository.countVenueConflictsExcludingEvent(
                "EDITED_EVENT",
                "SC001",
                EVENT_DATE,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                BLOCKING_STATUSES));

        eventRepository.saveAndFlush(event(
                "OTHER_EVENT",
                "SC001",
                LocalTime.of(11, 0),
                LocalTime.of(13, 0),
                EventStatus.PROPOSED));

        assertEquals(1L, eventRepository.countVenueConflictsExcludingEvent(
                "EDITED_EVENT",
                "SC001",
                EVENT_DATE,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                BLOCKING_STATUSES));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTransactionsCannotBothReserveTheSameVenueAndTime()
            throws Exception {
        Venue concurrencyVenue = southVenue("CC001");
        concurrencyVenue.setVenueName("Concurrency Test Venue");
        venueRepository.saveAndFlush(concurrencyVenue);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier startTogether = new CyclicBarrier(2);
        AtomicBoolean firstReservation = new AtomicBoolean(true);
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager);

        try {
            Future<Boolean> first = executor.submit(() -> attemptReservation(
                    "CONCURRENT_A",
                    startTogether,
                    firstReservation,
                    transaction));
            Future<Boolean> second = executor.submit(() -> attemptReservation(
                    "CONCURRENT_B",
                    startTogether,
                    firstReservation,
                    transaction));

            int successfulReservations =
                    (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                            + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successfulReservations);
            assertEquals(1L, eventRepository.countVenueConflicts(
                    "CC001",
                    EVENT_DATE,
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0),
                    BLOCKING_STATUSES));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptReservation(
            String eventID,
            CyclicBarrier startTogether,
            AtomicBoolean firstReservation,
            TransactionTemplate transaction) throws Exception {
        startTogether.await(5, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(transaction.execute(status -> {
            venueRepository.findByVenueCodeForUpdate("CC001")
                    .orElseThrow();

            long conflicts = eventRepository.countVenueConflicts(
                    "CC001",
                    EVENT_DATE,
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0),
                    BLOCKING_STATUSES);
            if (conflicts > 0) {
                return false;
            }

            if (firstReservation.getAndSet(false)) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }

            eventRepository.saveAndFlush(event(
                    eventID,
                    "CC001",
                    LocalTime.of(10, 0),
                    LocalTime.of(12, 0),
                    EventStatus.DRAFT));
            return true;
        }));
    }

    private List<Venue> findAvailable(
            Campus campus,
            LocalTime start,
            LocalTime end) {
        return venueRepository.findAvailableVenues(
                campus,
                EVENT_DATE,
                start,
                end,
                BLOCKING_STATUSES);
    }

    private List<String> venueCodes(List<Venue> venues) {
        return venues.stream()
                .map(Venue::getVenueCode)
                .toList();
    }

    private List<Venue> findAvailableExcluding(
            Campus campus,
            LocalTime start,
            LocalTime end,
            String excludeEventID) {
        return venueRepository.findAvailableVenuesExcludingEvent(
                campus,
                EVENT_DATE,
                start,
                end,
                excludeEventID,
                BLOCKING_STATUSES);
    }

    private Event event(
            String eventID,
            String venueCode,
            LocalTime start,
            LocalTime end,
            EventStatus status) {
        Event event = new Event();
        event.setEventID(eventID);
        event.setEventName("Venue query test");
        event.setEventDate(EVENT_DATE);
        event.setEventStartTime(start);
        event.setEventEndTime(end);
        event.setVenueCode(venueCode);
        event.setEventStatus(status);
        return event;
    }

    private Venue southVenue(String venueCode) {
        Venue venue = new Venue();
        venue.setVenueCode(venueCode);
        venue.setCampus(Campus.SOUTH_CAMPUS);
        venue.setVenueName("South Campus Venue " + venueCode);
        venue.setVenueType("Lecture Hall");
        venue.setCapacity(200);
        venue.setActive(true);
        return venue;
    }

    private Venue northVenue(String venueCode) {
        Venue venue = new Venue();
        venue.setVenueCode(venueCode);
        venue.setCampus(Campus.NORTH_CAMPUS);
        venue.setVenueName("North Campus Venue " + venueCode);
        venue.setVenueType("Lecture Hall");
        venue.setCapacity(200);
        venue.setActive(true);
        return venue;
    }

    private static Stream<Arguments> overlapWindows() {
        return Stream.of(
                Arguments.of(
                        LocalTime.of(11, 0),
                        LocalTime.of(13, 0),
                        false),
                Arguments.of(
                        LocalTime.of(12, 0),
                        LocalTime.of(14, 0),
                        true),
                Arguments.of(
                        LocalTime.of(10, 0),
                        LocalTime.of(12, 0),
                        false),
                Arguments.of(
                        LocalTime.of(10, 30),
                        LocalTime.of(11, 30),
                        false),
                Arguments.of(
                        LocalTime.of(9, 0),
                        LocalTime.of(13, 0),
                        false));
    }
}
