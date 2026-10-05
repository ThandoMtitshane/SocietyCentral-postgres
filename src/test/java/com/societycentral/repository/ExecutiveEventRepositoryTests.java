package com.societycentral.repository;

import com.societycentral.model.Campus;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.User;
import com.societycentral.model.UserType;
import com.societycentral.model.Venue;
import com.societycentral.repository.projection.ExecutiveEventListProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:executive-event-tests;"
                + "MODE=MSSQLServer;DB_CLOSE_DELAY=-1;"
                + "NON_KEYWORDS=USER,EVENT,YEAR,MONTH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExecutiveEventRepositoryTests {

    private static final String SOCIETY_ID = "SOC001";
    private static final String OTHER_SOCIETY_ID = "SOC002";
    private static final LocalDate EVENT_DATE = LocalDate.of(2030, 8, 10);
    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 7, 20, 9, 0);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SDORepository sdoRepository;
    @Autowired
    private SocietyRepository societyRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private HosterRepository hosterRepository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedEvents() {
        User sdoUser = new User();
        sdoUser.setEmail("sdo@nmu.ac.za");
        sdoUser.setFirstName("Sam");
        sdoUser.setLastName("Officer");
        sdoUser.setUserType(UserType.SDO);
        sdoUser.setPasswordHash("hash");
        userRepository.save(sdoUser);

        SDO sdo = new SDO();
        sdo.setStaffNumber("SDO001");
        sdo.setEmail(sdoUser.getEmail());
        sdoRepository.save(sdo);

        Society society = society(SOCIETY_ID, "Computing Society");
        Society otherSociety =
                society(OTHER_SOCIETY_ID, "Robotics Society");
        societyRepository.saveAll(List.of(society, otherSociety));

        Venue auditorium = venue(
                "SC001",
                "South Campus Auditorium",
                850);
        Venue lab = venue(
                "SC002",
                "Innovation Laboratory",
                120);
        venueRepository.saveAll(List.of(auditorium, lab));

        Event olderDraft = event(
                "EVT_DRAFT",
                "Welcome Evening",
                "SC001",
                EventStatus.DRAFT,
                CREATED_AT.plusHours(1));
        Event newerRejected = event(
                "EVT_REJECTED",
                "Leadership Workshop",
                "SC002",
                EventStatus.REJECTED,
                CREATED_AT.plusDays(1));
        newerRejected.setRejectionReason("Clarify the programme.");
        Event otherSocietyDraft = event(
                "EVT_OTHER",
                "Private Robotics Draft",
                "SC001",
                EventStatus.DRAFT,
                CREATED_AT.plusDays(2));
        eventRepository.saveAll(List.of(
                olderDraft,
                newerRejected,
                otherSocietyDraft));

        hosterRepository.save(hoster(olderDraft, society));
        hosterRepository.save(hoster(newerRejected, society));
        hosterRepository.save(hoster(otherSocietyDraft, otherSociety));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void societyListNeverReturnsAnotherSocietyPrivateEvent() {
        Page<ExecutiveEventListProjection> page = find(
                SOCIETY_ID,
                null,
                null);

        assertEquals(
                List.of("EVT_REJECTED", "EVT_DRAFT"),
                page.getContent().stream()
                        .map(ExecutiveEventListProjection::getEventID)
                        .toList());
        assertFalse(page.getContent().stream()
                .anyMatch(event -> "EVT_OTHER".equals(event.getEventID())));
    }

    @Test
    void statusFilterReturnsOnlyMatchingStatus() {
        Page<ExecutiveEventListProjection> page = find(
                SOCIETY_ID,
                EventStatus.REJECTED,
                null);

        assertEquals(1, page.getTotalElements());
        assertEquals(
                EventStatus.REJECTED,
                page.getContent().getFirst().getEventStatus());
        assertEquals(
                "Clarify the programme.",
                page.getContent().getFirst().getRejectionReason());
    }

    @Test
    void withdrawnStatusProjectsAndIsExcludedFromProposedResults() {
        Society eventSociety = societyRepository.findById(SOCIETY_ID)
                .orElseThrow();
        Event withdrawn = event(
                "EVT_WITHDRAWN",
                "Withdrawn Proposal",
                "SC001",
                EventStatus.WITHDRAWN,
                CREATED_AT.plusDays(3));
        eventRepository.save(withdrawn);
        hosterRepository.save(hoster(withdrawn, eventSociety));
        entityManager.flush();
        entityManager.clear();

        Page<ExecutiveEventListProjection> withdrawnPage = find(
                SOCIETY_ID,
                EventStatus.WITHDRAWN,
                null);
        Page<ExecutiveEventListProjection> proposedPage = find(
                SOCIETY_ID,
                EventStatus.PROPOSED,
                null);

        assertEquals(
                List.of("EVT_WITHDRAWN"),
                ids(withdrawnPage));
        assertEquals(
                EventStatus.WITHDRAWN,
                withdrawnPage.getContent().getFirst().getEventStatus());
        assertFalse(ids(proposedPage).contains("EVT_WITHDRAWN"));
    }

    @Test
    void defaultOrderingIsUpdatedAtDescending() {
        Page<ExecutiveEventListProjection> page = find(
                SOCIETY_ID,
                null,
                null);

        assertEquals("EVT_REJECTED",
                page.getContent().getFirst().getEventID());
        assertTrue(page.getContent().getFirst().getUpdatedAt()
                .isAfter(page.getContent().get(1).getUpdatedAt()));
    }

    @Test
    void searchMatchesEventNameAndAuthoritativeVenueName() {
        Page<ExecutiveEventListProjection> byName = find(
                SOCIETY_ID,
                null,
                "%welcome%");
        Page<ExecutiveEventListProjection> byVenue = find(
                SOCIETY_ID,
                null,
                "%innovation%");

        assertEquals(
                List.of("EVT_DRAFT"),
                ids(byName));
        assertEquals(
                List.of("EVT_REJECTED"),
                ids(byVenue));
        assertEquals(
                "Innovation Laboratory",
                byVenue.getContent().getFirst().getVenueName());
        assertEquals(
                120,
                byVenue.getContent().getFirst().getVenueCapacity());
    }

    @Test
    void unknownSocietyReturnsEmptyPage() {
        Page<ExecutiveEventListProjection> page = find(
                "SOC999",
                null,
                null);

        assertTrue(page.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {
            "APPROVED",
            "PUBLISHED",
            "REJECTED",
            "WITHDRAWN",
            "COMPLETED",
            "CANCELLED"
    })
    void pastWorkflowEventsRemainVisible(EventStatus status) {
        Society eventSociety = societyRepository.findById(SOCIETY_ID)
                .orElseThrow();
        Event pastEvent = event(
                "PAST_" + status.name(),
                "Past " + status.name() + " Event",
                "SC001",
                status,
                CREATED_AT.minusDays(1));
        pastEvent.setEventDate(LocalDate.of(2025, 1, 15));
        eventRepository.save(pastEvent);
        hosterRepository.save(hoster(pastEvent, eventSociety));
        entityManager.flush();
        entityManager.clear();

        Page<ExecutiveEventListProjection> page = find(
                SOCIETY_ID,
                status,
                null);

        assertTrue(ids(page).contains("PAST_" + status.name()));
    }

    @Test
    void ownershipDetailQueryLoadsOnlyMatchingSocietyRelationship() {
        assertTrue(hosterRepository.findEventHostedBySociety(
                "EVT_DRAFT",
                SOCIETY_ID).isPresent());
        assertTrue(hosterRepository.findEventHostedBySociety(
                "EVT_DRAFT",
                OTHER_SOCIETY_ID).isEmpty());
    }

    @Test
    void persistedDraftSurvivesClearAndRemainsInSocietyList() {
        entityManager.clear();

        Event reloaded = eventRepository.findById("EVT_DRAFT")
                .orElseThrow();
        Page<ExecutiveEventListProjection> page = find(
                SOCIETY_ID,
                EventStatus.DRAFT,
                null);

        assertEquals(EventStatus.DRAFT, reloaded.getEventStatus());
        assertEquals(List.of("EVT_DRAFT"), ids(page));
    }

    @Test
    void updateKeepsCreatedAtAndAdvancesUpdatedAt() {
        Event draft = eventRepository.findById("EVT_DRAFT")
                .orElseThrow();
        LocalDateTime originalCreatedAt = draft.getCreatedAt();
        LocalDateTime originalUpdatedAt = draft.getUpdatedAt();

        draft.setEventName("Persisted Updated Name");
        eventRepository.saveAndFlush(draft);
        entityManager.clear();

        Event reloaded = eventRepository.findById("EVT_DRAFT")
                .orElseThrow();
        assertEquals(originalCreatedAt, reloaded.getCreatedAt());
        assertTrue(reloaded.getUpdatedAt().isAfter(originalUpdatedAt));
        assertEquals("Persisted Updated Name", reloaded.getEventName());
    }

    private Page<ExecutiveEventListProjection> find(
            String societyID,
            EventStatus status,
            String searchPattern) {
        return eventRepository.findExecutiveEvents(
                societyID,
                status,
                searchPattern,
                PageRequest.of(0, 20));
    }

    private List<String> ids(Page<ExecutiveEventListProjection> page) {
        return page.getContent().stream()
                .map(ExecutiveEventListProjection::getEventID)
                .toList();
    }

    private Society society(String societyID, String name) {
        Society society = new Society();
        society.setSocietyID(societyID);
        society.setSocietyName(name);
        society.setSdoStaffNumber("SDO001");
        society.setActiveStatus(true);
        society.setIsFlagged(false);
        return society;
    }

    private Venue venue(
            String venueCode,
            String venueName,
            int capacity) {
        Venue venue = new Venue();
        venue.setVenueCode(venueCode);
        venue.setVenueName(venueName);
        venue.setVenueType("Auditorium");
        venue.setCampus(Campus.SOUTH_CAMPUS);
        venue.setCapacity(capacity);
        venue.setActive(true);
        return venue;
    }

    @SuppressWarnings("deprecation")
    private Event event(
            String eventID,
            String eventName,
            String venueCode,
            EventStatus status,
            LocalDateTime updatedAt) {
        Event event = new Event();
        event.setEventID(eventID);
        event.setEventName(eventName);
        event.setEventDescription("Complete event description.");
        event.setEventDate(EVENT_DATE);
        event.setEventStartTime(LocalTime.of(10, 0));
        event.setEventEndTime(LocalTime.of(12, 0));
        event.setEventTime(LocalTime.of(10, 0));
        event.setVenueCode(venueCode);
        event.setEventCampus(Campus.SOUTH_CAMPUS);
        event.setEventLimit(100);
        event.setEventStatus(status);
        event.setCreatedAt(CREATED_AT);
        event.setUpdatedAt(updatedAt);
        return event;
    }

    private Hoster hoster(Event event, Society society) {
        Hoster hoster = new Hoster();
        hoster.setId(new HosterId(
                event.getEventID(),
                society.getSocietyID()));
        hoster.setEvent(event);
        hoster.setSociety(society);
        hoster.setIsPrimary(true);
        return hoster;
    }
}
