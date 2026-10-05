package com.societycentral.service;

import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.mapper.EventMapper;
import com.societycentral.model.AttendingType;
import com.societycentral.model.Campus;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Executive;
import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.model.Venue;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventViewServiceTests {

    private static final String EVENT_ID = "EVT100";
    private static final String STUDENT_EMAIL = "student@nmu.ac.za";
    private static final String SOCIETY_ID = "SOC001";
    private static final LocalDate TODAY = LocalDate.of(2030, 8, 3);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2030-08-03T12:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SocietyMemberRepository societyMemberRepository;
    @Mock
    private ExecutiveRepository executiveRepository;
    @Mock
    private HosterRepository hosterRepository;
    @Mock
    private RSVPRepository rsvpRepository;
    @Mock
    private VenueService venueService;

    private EventViewService eventViewService;

    @BeforeEach
    void setUp() {
        eventViewService = new EventViewService(
                eventRepository,
                studentRepository,
                societyMemberRepository,
                executiveRepository,
                hosterRepository,
                rsvpRepository,
                venueService,
                new EventMapper(),
                CLOCK
        );
    }

    @Test
    void studentDetailUsesMembershipScopeAndReturnsCompleteEvent() {
        Student student = student("220000001", STUDENT_EMAIL);
        Society society = society();
        Event event = event();
        Venue venue = venue();

        when(studentRepository.findByEmail(STUDENT_EMAIL))
                .thenReturn(Optional.of(student));
        when(executiveRepository.findActiveExecutiveRoles(
                student.getStudentNumber(), TODAY))
                .thenReturn(List.of());
        when(societyMemberRepository.findActiveSocietyIDsForStudent(
                student.getStudentNumber(), TODAY))
                .thenReturn(List.of(SOCIETY_ID));
        when(eventRepository.findVisibleEventForStudent(
                EVENT_ID,
                List.of(SOCIETY_ID), LocalDateTime.now(CLOCK)))
                .thenReturn(Optional.of(event));
        when(hosterRepository.findByIdEventID(EVENT_ID))
                .thenReturn(List.of(primaryHost(event, society)));
        when(venueService.findByVenueCode("SC001"))
                .thenReturn(Optional.of(venue));

        EventResponseDTO response =
                eventViewService.getVisibleEventForStudent(
                        EVENT_ID,
                        STUDENT_EMAIL
                );

        assertEquals(EVENT_ID, response.getEventID());
        assertEquals(EventStatus.PUBLISHED, response.getEventStatus());
        assertEquals("South Campus Auditorium", response.getVenueName());
        assertEquals(850, response.getVenueCapacity());
        assertEquals(SOCIETY_ID, response.getPrimarySocietyID());
        assertEquals("Computing Society", response.getPrimarySocietyName());
        verify(eventRepository).findVisibleEventForStudent(
                EVENT_ID,
                List.of(SOCIETY_ID), LocalDateTime.now(CLOCK));
    }

    @Test
    void studentCannotLoadAnEventOutsideTheVisibleQuery() {
        Student student = student("220000001", STUDENT_EMAIL);
        Society society = society();

        when(studentRepository.findByEmail(STUDENT_EMAIL))
                .thenReturn(Optional.of(student));
        when(executiveRepository.findActiveExecutiveRoles(
                student.getStudentNumber(), TODAY))
                .thenReturn(List.of());
        when(societyMemberRepository.findActiveSocietyIDsForStudent(
                student.getStudentNumber(), TODAY))
                .thenReturn(List.of(SOCIETY_ID));
        when(eventRepository.findVisibleEventForStudent(
                "EVT_PRIVATE",
                List.of(SOCIETY_ID), LocalDateTime.now(CLOCK)))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> eventViewService.getVisibleEventForStudent(
                        "EVT_PRIVATE",
                        STUDENT_EMAIL
                ));

        assertEquals("Event not found.", exception.getMessage());
        verifyNoInteractions(hosterRepository, venueService);
        verify(eventRepository, never()).findById("EVT_PRIVATE");
    }

    @Test
    void allPublishedEventsMapsRepositoryResultsWithoutStudentFiltering() {
        Event firstEvent = event("EVT100");
        Event secondEvent = event("EVT200");
        Society society = society();

        when(eventRepository.findAllVisiblePublishedEvents(LocalDateTime.now(CLOCK)))
                .thenReturn(List.of(firstEvent, secondEvent));
        when(hosterRepository.findByIdEventID("EVT100"))
                .thenReturn(List.of(primaryHost(firstEvent, society)));
        when(hosterRepository.findByIdEventID("EVT200"))
                .thenReturn(List.of(primaryHost(secondEvent, society)));

        var summaries = eventViewService.getAllPublishedEvents();
        List<String> eventIDs = summaries.stream()
                .map(summary -> summary.getEventID())
                .toList();

        assertEquals(List.of("EVT100", "EVT200"), eventIDs);
        assertEquals(200, summaries.getFirst().getEventLimit());
        assertEquals(
                LocalDateTime.of(2030, 8, 3, 11, 0),
                summaries.getFirst().getRsvpOpenDate());
        assertEquals(
                LocalDateTime.of(2030, 8, 9, 8, 0),
                summaries.getFirst().getRsvpCloseDate());
        assertEquals(
                "/media/events/banner.jpg",
                summaries.getFirst().getBannerUrl());
        verify(eventRepository).findAllVisiblePublishedEvents(LocalDateTime.now(CLOCK));
        verifyNoInteractions(studentRepository, societyMemberRepository);
    }

    @Test
    void activeExecutiveListReturnsAllPublishedEventsUsingStudentIdentity() {
        Student executiveStudent = student("220000002", "executive@nmu.ac.za");
        Event event = event();
        Society society = society();

        when(studentRepository.findByEmail("executive@nmu.ac.za"))
                .thenReturn(Optional.of(executiveStudent));
        when(executiveRepository.findActiveExecutiveRoles(
                executiveStudent.getStudentNumber(), TODAY))
                .thenReturn(List.of(new Executive()));
        when(eventRepository.findAllVisiblePublishedEvents(LocalDateTime.now(CLOCK)))
                .thenReturn(List.of(event));
        when(hosterRepository.findByIdEventID(EVENT_ID))
                .thenReturn(List.of(primaryHost(event, society)));

        List<String> eventIDs = eventViewService
                .getVisibleEventsForStudent("executive@nmu.ac.za")
                .stream()
                .map(summary -> summary.getEventID())
                .toList();

        assertEquals(List.of(EVENT_ID), eventIDs);
        verify(eventRepository).findAllVisiblePublishedEvents(LocalDateTime.now(CLOCK));
        verifyNoInteractions(societyMemberRepository);
    }

    @Test
    void activeExecutiveDetailReturnsAnyPublishedEventUsingStudentIdentity() {
        Student executiveStudent = student("220000002", "executive@nmu.ac.za");
        Event event = event();
        Society society = society();
        Venue venue = venue();

        when(studentRepository.findByEmail("executive@nmu.ac.za"))
                .thenReturn(Optional.of(executiveStudent));
        when(executiveRepository.findActiveExecutiveRoles(
                executiveStudent.getStudentNumber(), TODAY))
                .thenReturn(List.of(new Executive()));
        when(eventRepository.findVisiblePublishedEvent(EVENT_ID, LocalDateTime.now(CLOCK)))
                .thenReturn(Optional.of(event));
        when(hosterRepository.findByIdEventID(EVENT_ID))
                .thenReturn(List.of(primaryHost(event, society)));
        when(venueService.findByVenueCode("SC001"))
                .thenReturn(Optional.of(venue));

        EventResponseDTO response = eventViewService
                .getVisibleEventForStudent(
                        EVENT_ID,
                        "executive@nmu.ac.za");

        assertEquals(EVENT_ID, response.getEventID());
        verify(eventRepository).findVisiblePublishedEvent(EVENT_ID, LocalDateTime.now(CLOCK));
        verifyNoInteractions(societyMemberRepository);
    }

    @Test
    void publishedDetailReturnsAnyPublishedEvent() {
        Society society = society();
        Event event = event();
        Venue venue = venue();

        when(eventRepository.findVisiblePublishedEvent(EVENT_ID, LocalDateTime.now(CLOCK)))
                .thenReturn(Optional.of(event));
        when(hosterRepository.findByIdEventID(EVENT_ID))
                .thenReturn(List.of(primaryHost(event, society)));
        when(venueService.findByVenueCode("SC001"))
                .thenReturn(Optional.of(venue));

        EventResponseDTO response =
                eventViewService.getPublishedEvent(EVENT_ID);

        assertSame(EventStatus.PUBLISHED, response.getEventStatus());
        assertEquals(SOCIETY_ID, response.getSocietyID());
        verify(eventRepository).findVisiblePublishedEvent(EVENT_ID, LocalDateTime.now(CLOCK));
        verifyNoInteractions(studentRepository, societyMemberRepository);
    }

    @Test
    void unpublishedDetailIsNotMapped() {
        when(eventRepository.findVisiblePublishedEvent("EVT_DRAFT", LocalDateTime.now(CLOCK)))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> eventViewService.getPublishedEvent("EVT_DRAFT"));

        assertEquals("Event not found.", exception.getMessage());
        verifyNoInteractions(hosterRepository, venueService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SOC001", "SOC010"})
    void soc001ExecutiveCanBrowsePublishedEventOfEitherSociety(String hostSocietyID) {
        Student student = student("220000001", STUDENT_EMAIL);
        Executive executive = new Executive();
        executive.setId(new com.societycentral.model.ExecutiveId(
                student.getStudentNumber(), "SOC001", TODAY.minusDays(1)));
        Society host = society();
        host.setSocietyID(hostSocietyID);
        Event event = event();
        event.setEventID("EVT011");
        when(studentRepository.findByEmail(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(executiveRepository.findActiveExecutiveRoles(student.getStudentNumber(), TODAY))
                .thenReturn(List.of(executive));
        when(eventRepository.findVisiblePublishedEvent("EVT011", LocalDateTime.now(CLOCK)))
                .thenReturn(Optional.of(event));
        when(hosterRepository.findByIdEventID("EVT011")).thenReturn(List.of(primaryHost(event, host)));

        EventResponseDTO result = eventViewService.getVisibleEventForStudent("EVT011", STUDENT_EMAIL);
        assertEquals("EVT011", result.getEventID());
        assertEquals(hostSocietyID, result.getPrimarySocietyID());
        verifyNoInteractions(societyMemberRepository);
    }

    private Student student(
            String studentNumber,
            String email) {
        Student student = new Student();
        student.setStudentNumber(studentNumber);
        student.setEmail(email);
        return student;
    }

    private Society society() {
        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Computing Society");
        society.setSdoStaffNumber("SDO001");
        return society;
    }

    private Hoster primaryHost(
            Event event,
            Society society) {
        Hoster hoster = new Hoster();
        hoster.setId(new HosterId(
                event.getEventID(),
                society.getSocietyID()
        ));
        hoster.setEvent(event);
        hoster.setSociety(society);
        hoster.setIsPrimary(true);
        return hoster;
    }

    @SuppressWarnings("deprecation")
    private Event event() {
        return event(EVENT_ID);
    }

    @SuppressWarnings("deprecation")
    private Event event(
            String eventID) {
        Event event = new Event();
        event.setEventID(eventID);
        event.setEventName("Welcome Evening");
        event.setEventDescription("Complete event description.");
        event.setEventDate(LocalDate.of(2030, 8, 10));
        event.setEventStartTime(LocalTime.of(10, 0));
        event.setEventEndTime(LocalTime.of(12, 0));
        event.setEventTime(LocalTime.of(10, 0));
        event.setVenueCode("SC001");
        event.setEventVenue("Legacy Venue");
        event.setEventCampus(Campus.SOUTH_CAMPUS);
        event.setEventStatus(EventStatus.PUBLISHED);
        event.setAttendingType(AttendingType.MEMBERS);
        event.setEventLimit(200);
        event.setRsvpOpenDate(LocalDateTime.of(2030, 8, 3, 11, 0));
        event.setRsvpCloseDate(LocalDateTime.of(2030, 8, 9, 8, 0));
        event.setPosterUrl("/media/events/poster.jpg");
        event.setBannerUrl("/media/events/banner.jpg");
        return event;
    }

    private Venue venue() {
        Venue venue = new Venue();
        venue.setVenueCode("SC001");
        venue.setVenueName("South Campus Auditorium");
        venue.setVenueType("Auditorium");
        venue.setCampus(Campus.SOUTH_CAMPUS);
        venue.setCapacity(850);
        venue.setActive(true);
        return venue;
    }
}
