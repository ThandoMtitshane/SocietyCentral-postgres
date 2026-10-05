package com.societycentral.service;

import com.societycentral.dto.request.CreateEventRequestDTO;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.exception.VenueUnavailableException;
import com.societycentral.mapper.EventMapper;
import com.societycentral.model.AttendingType;
import com.societycentral.model.Campus;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Hoster;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.model.Venue;
import com.societycentral.repository.AuditLogRepository;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EventServiceEventMediaTests {

    private static final String SOCIETY_ID = "SOC001";
    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";
    private static final String STUDENT_NUMBER = "220000001";
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 23);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 23, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-23T10:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private HosterRepository hosterRepository;
    @Mock
    private SocietyRepository societyRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ExecutiveRepository executiveRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private VenueService venueService;
    @Mock
    private JwtUtil jwtUtil;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository,
                hosterRepository,
                societyRepository,
                studentRepository,
                executiveRepository,
                auditLogRepository,
                venueService,
                jwtUtil,
                new EventMapper());
        eventService.setClock(FIXED_CLOCK);
    }

    @Test
    void rejectsEventCreationWithoutPosterUrl() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setPosterUrl(" ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createEvent(
                        SOCIETY_ID,
                        EXECUTIVE_EMAIL,
                        request));

        assertEquals("Event poster is required.", exception.getMessage());
    }

    @Test
    void rejectsEventCreationWithoutBannerUrl() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setBannerUrl(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createEvent(
                        SOCIETY_ID,
                        EXECUTIVE_EMAIL,
                        request));

        assertEquals("Event banner is required.", exception.getMessage());
    }

    @Test
    @SuppressWarnings("deprecation")
    void createsDraftWithBothMediaUrlsAndPrimaryHost() {
        CreateEventRequestDTO request = validRequest();
        request.setImageUrl("/legacy/client-value.png");
        request.setEventVenue("Untrusted client venue");
        request.setEventTime(LocalTime.of(1, 0));
        authoriseActiveExecutive();
        allowPersistence();

        EventResponseDTO response = eventService.createEvent(
                SOCIETY_ID,
                EXECUTIVE_EMAIL,
                request);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        Event saved = eventCaptor.getValue();
        assertEquals(EventStatus.DRAFT, saved.getEventStatus());
        assertEquals(request.getPosterUrl(), saved.getPosterUrl());
        assertEquals(request.getBannerUrl(), saved.getBannerUrl());
        assertNull(saved.getImageUrl());
        assertEquals("SC001", saved.getVenueCode());
        assertEquals(request.getEventStartTime(), saved.getEventStartTime());
        assertEquals(request.getEventEndTime(), saved.getEventEndTime());
        assertEquals(request.getEventStartTime(), saved.getEventTime());
        assertEquals("Auditorium", saved.getEventVenue());
        assertNotNull(saved.getCreatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertEquals(EventStatus.DRAFT, response.getEventStatus());
        assertEquals(request.getPosterUrl(), response.getPosterUrl());
        assertEquals(request.getBannerUrl(), response.getBannerUrl());
        assertEquals("SC001", response.getVenueCode());
        assertEquals("Auditorium", response.getVenueName());
        assertEquals("Auditorium", response.getVenueType());
        assertEquals(850, response.getVenueCapacity());
        assertEquals(saved.getCreatedAt(), response.getCreatedAt());
        assertEquals(saved.getUpdatedAt(), response.getUpdatedAt());

        ArgumentCaptor<Hoster> hosterCaptor = ArgumentCaptor.forClass(Hoster.class);
        verify(hosterRepository).save(hosterCaptor.capture());
        Hoster primaryHost = hosterCaptor.getValue();
        assertTrue(primaryHost.getIsPrimary());
        assertEquals(SOCIETY_ID, primaryHost.getId().getSocietyID());
        assertEquals(saved.getEventID(), primaryHost.getId().getEventID());
    }

    @Test
    void submittingOwnedDraftStillTransitionsToProposed() {
        Event draft = new Event();
        draft.setEventID("EVT900");
        draft.setEventName("Submission regression event");
        draft.setEventDescription("Preserves the existing event lifecycle.");
        draft.setEventDate(TODAY.plusDays(2));
        draft.setEventStartTime(LocalTime.of(14, 0));
        draft.setEventEndTime(LocalTime.of(16, 0));
        draft.setEventTime(LocalTime.of(14, 0));
        draft.setVenueCode("SC001");
        draft.setEventVenue("Main Hall");
        draft.setEventCampus(Campus.SOUTH_CAMPUS);
        draft.setEventLimit(100);
        draft.setAttendingType(AttendingType.EVERY_STUDENT);
        draft.setPosterUrl(
                "http://localhost:8080/media/events/posters/poster.png");
        draft.setBannerUrl(
                "http://localhost:8080/media/events/banners/banner.png");
        draft.setEventStatus(EventStatus.DRAFT);

        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Computing Society");
        society.setActiveStatus(true);
        society.setIsFlagged(false);

        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EXECUTIVE_EMAIL);

        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                STUDENT_NUMBER,
                SOCIETY_ID,
                TODAY.minusMonths(1)));
        executive.setTermEndDate(null);

        Venue venue = new Venue();
        venue.setVenueCode("SC001");
        venue.setCampus(Campus.SOUTH_CAMPUS);
        venue.setVenueName("Main Hall");
        venue.setVenueType("Hall");
        venue.setCapacity(500);
        venue.setActive(true);

        when(eventRepository.findByIdForUpdate("EVT900"))
                .thenReturn(Optional.of(draft));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                "EVT900",
                SOCIETY_ID)).thenReturn(true);
        when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.of(student));
        when(executiveRepository.findByIdStudentNumber(STUDENT_NUMBER))
                .thenReturn(List.of(executive));
        when(societyRepository.findById(SOCIETY_ID))
                .thenReturn(Optional.of(society));
        when(venueService.lockAndValidateForEventUpdate(
                "SC001",
                Campus.SOUTH_CAMPUS,
                100)).thenReturn(venue);
        when(eventRepository.save(draft)).thenReturn(draft);

        EventResponseDTO response = eventService.submitEventForApproval(
                "EVT900",
                EXECUTIVE_EMAIL);

        assertEquals(EventStatus.PROPOSED, draft.getEventStatus());
        assertEquals(EventStatus.PROPOSED, response.getEventStatus());
        verify(eventRepository).save(draft);
    }

    @Test
    void rejectsEventDateBeforeToday() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setEventDate(TODAY.minusDays(1));

        assertCreationFailure(
                request,
                "Event date cannot be before today.");
        verifyNoInteractions(eventRepository, hosterRepository);
    }

    @Test
    void rejectsReversedStartAndEndOrderBeforeVenueOrPersistence() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setEventStartTime(LocalTime.of(23, 0));
        request.setEventEndTime(LocalTime.of(22, 30));

        assertCreationFailure(
                request,
                "Event end time must be after the start time.");
        verifyNoInteractions(
                venueService,
                eventRepository,
                hosterRepository);
    }

    @Test
    void rejectsEqualStartAndEndTimesBeforeVenueOrPersistence() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setEventEndTime(request.getEventStartTime());

        assertCreationFailure(
                request,
                "Event end time must be after the start time.");
        verifyNoInteractions(
                venueService,
                eventRepository,
                hosterRepository);
    }

    @Test
    void rejectsTodayStartThatIsNotInTheFutureBeforeVenueOrPersistence() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setEventDate(TODAY);
        request.setEventStartTime(LocalTime.of(9, 0));
        request.setEventEndTime(LocalTime.of(11, 0));

        assertCreationFailure(
                request,
                "Event start time must be in the future.");
        verifyNoInteractions(
                venueService,
                eventRepository,
                hosterRepository);
    }

    @Test
    void venueConflictPersistsNeitherEventNorHoster() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        doThrow(new VenueUnavailableException())
                .when(venueService)
                .assertAvailableForEventCreation(
                        "SC001",
                        request.getEventDate(),
                        request.getEventStartTime(),
                        request.getEventEndTime());

        VenueUnavailableException exception = assertThrows(
                VenueUnavailableException.class,
                () -> eventService.createEvent(
                        SOCIETY_ID,
                        EXECUTIVE_EMAIL,
                        request));

        assertEquals(
                "The selected venue is not available for the requested date and time.",
                exception.getMessage());
        verifyNoInteractions(eventRepository, hosterRepository);
    }

    @Test
    void acceptsEventScheduledForTodayWhenStartIsStillFuture() {
        authoriseActiveExecutive();
        allowPersistence();
        CreateEventRequestDTO request = validRequest();
        request.setEventDate(TODAY);
        request.setEventStartTime(LocalTime.of(14, 0));
        request.setEventEndTime(LocalTime.of(15, 0));

        EventResponseDTO response = eventService.createEvent(
                SOCIETY_ID,
                EXECUTIVE_EMAIL,
                request);

        assertEquals(TODAY, response.getEventDate());
        assertEquals(EventStatus.DRAFT, response.getEventStatus());
    }

    @Test
    void rejectsRsvpOpeningInThePast() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setRsvpOpenDate(NOW.minusMinutes(1));
        request.setRsvpCloseDate(NOW.plusHours(1));

        assertCreationFailure(
                request,
                "RSVP opening cannot be in the past.");
    }

    @Test
    void rejectsRsvpOpeningEqualToClosing() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        LocalDateTime timestamp = NOW.plusHours(1);
        request.setRsvpOpenDate(timestamp);
        request.setRsvpCloseDate(timestamp);

        assertCreationFailure(
                request,
                "RSVP opening must be before RSVP closing.");
    }

    @Test
    void rejectsRsvpOpeningAfterClosing() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setRsvpOpenDate(NOW.plusHours(2));
        request.setRsvpCloseDate(NOW.plusHours(1));

        assertCreationFailure(
                request,
                "RSVP opening must be before RSVP closing.");
    }

    @Test
    void rejectsRsvpClosingEqualToEventStart() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        LocalDateTime eventStart = eventStart(request);
        request.setRsvpOpenDate(eventStart.minusHours(2));
        request.setRsvpCloseDate(eventStart);

        assertCreationFailure(
                request,
                "RSVP closing must be before the event starts.");
    }

    @Test
    void rejectsRsvpClosingAfterEventStart() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        LocalDateTime eventStart = eventStart(request);
        request.setRsvpOpenDate(eventStart.minusHours(2));
        request.setRsvpCloseDate(eventStart.plusMinutes(1));

        assertCreationFailure(
                request,
                "RSVP closing must be before the event starts.");
    }

    @Test
    void rejectsOnlyOneRsvpBoundary() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setRsvpOpenDate(NOW.plusHours(1));

        assertCreationFailure(
                request,
                "Provide both RSVP opening and closing times, or leave both empty.");
    }

    @Test
    void acceptsValidRsvpWindowBeforeEventStart() {
        authoriseActiveExecutive();
        allowPersistence();
        CreateEventRequestDTO request = validRequest();
        LocalDateTime eventStart = eventStart(request);
        request.setRsvpOpenDate(NOW.plusHours(1));
        request.setRsvpCloseDate(eventStart.minusMinutes(1));

        EventResponseDTO response = eventService.createEvent(
                SOCIETY_ID,
                EXECUTIVE_EMAIL,
                request);

        assertEquals(EventStatus.DRAFT, response.getEventStatus());
        assertEquals(request.getRsvpOpenDate(), response.getRsvpOpenDate());
        assertEquals(request.getRsvpCloseDate(), response.getRsvpCloseDate());
    }

    @Test
    void scheduleValidationFailurePersistsNeitherEventNorHoster() {
        authoriseActiveExecutive();
        CreateEventRequestDTO request = validRequest();
        request.setRsvpOpenDate(NOW.minusSeconds(1));
        request.setRsvpCloseDate(NOW.plusHours(1));

        assertCreationFailure(
                request,
                "RSVP opening cannot be in the past.");

        verifyNoInteractions(
                venueService,
                eventRepository,
                hosterRepository);
    }

    private void assertCreationFailure(
            CreateEventRequestDTO request,
            String expectedMessage) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createEvent(
                        SOCIETY_ID,
                        EXECUTIVE_EMAIL,
                        request));

        assertEquals(expectedMessage, exception.getMessage());
    }

    private LocalDateTime eventStart(CreateEventRequestDTO request) {
        return LocalDateTime.of(
                request.getEventDate(),
                request.getEventStartTime());
    }

    private void authoriseActiveExecutive() {
        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EXECUTIVE_EMAIL);

        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Computing Society");
        society.setActiveStatus(true);
        society.setIsFlagged(false);

        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                STUDENT_NUMBER,
                SOCIETY_ID,
                TODAY.minusMonths(1)));
        executive.setTermEndDate(null);

        when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.of(student));
        when(societyRepository.findById(SOCIETY_ID))
                .thenReturn(Optional.of(society));
        when(executiveRepository.findByIdStudentNumber(STUDENT_NUMBER))
                .thenReturn(List.of(executive));

        Venue venue = new Venue();
        venue.setVenueCode("SC001");
        venue.setCampus(Campus.SOUTH_CAMPUS);
        venue.setVenueName("Auditorium");
        venue.setVenueType("Auditorium");
        venue.setCapacity(850);
        venue.setActive(true);
        lenient().when(venueService.lockAndValidateForEventCreation(
                        "SC001",
                        Campus.SOUTH_CAMPUS,
                        100))
                .thenReturn(venue);
    }

    private void allowPersistence() {
        when(eventRepository.count()).thenReturn(0L);
        when(eventRepository.existsById("EVT001")).thenReturn(false);
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(hosterRepository.save(any(Hoster.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateEventRequestDTO validRequest() {
        CreateEventRequestDTO request = new CreateEventRequestDTO();
        request.setEventName("Welcome Event");
        request.setEventDate(TODAY.plusDays(3));
        request.setEventStartTime(LocalTime.of(12, 0));
        request.setEventEndTime(LocalTime.of(14, 0));
        request.setVenueCode("SC001");
        request.setEventCampus(Campus.SOUTH_CAMPUS);
        request.setEventDescription("Welcome event for society members.");
        request.setEventLimit(100);
        request.setAttendingType(AttendingType.MEMBERS);
        request.setPosterUrl(
                "http://localhost:8080/media/events/posters/poster.png");
        request.setBannerUrl(
                "http://localhost:8080/media/events/banners/banner.png");
        return request;
    }
}
