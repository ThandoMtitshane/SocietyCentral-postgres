package com.societycentral.service;

import com.societycentral.mapper.EventMapper;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDraftDeletionServiceTests {

    private static final String EVENT_ID = "EVT100";
    private static final String SOCIETY_ID = "SOC001";
    private static final String EXECUTIVE_EMAIL =
            "executive@nmu.ac.za";
    private static final String STUDENT_NUMBER = "220000001";
    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 30);

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
                new EventMapper()
        );
        eventService.setClock(Clock.fixed(
                Instant.parse("2026-07-30T10:00:00Z"),
                ZoneOffset.UTC
        ));
    }

    @Test
    void ownedDraftAndAllHosterRowsArePermanentlyDeleted() {
        authorizeExecutive();
        Event draft = event(EventStatus.DRAFT);
        Hoster primaryHost = hoster(draft, SOCIETY_ID, true);
        Hoster coHost = hoster(draft, "SOC002", false);
        List<Hoster> eventHosters =
                List.of(primaryHost, coHost);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(draft));
        when(hosterRepository
                .existsByIdEventIDAndIdSocietyID(
                        EVENT_ID,
                        SOCIETY_ID
                ))
                .thenReturn(true);
        when(hosterRepository.findByIdEventID(EVENT_ID))
                .thenReturn(eventHosters);

        eventService.deleteDraftEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL
        );

        verify(eventRepository)
                .deleteBudgetRequestsByEventID(EVENT_ID);
        verify(eventRepository)
                .deleteEventOutcomesByEventID(EVENT_ID);
        verify(eventRepository)
                .deleteRsvpsByEventID(EVENT_ID);
        verify(eventRepository)
                .deleteEventFeedbackByEventID(EVENT_ID);
        verify(hosterRepository)
                .findByIdEventID(EVENT_ID);

        InOrder deletionOrder =
                inOrder(hosterRepository, eventRepository);
        deletionOrder.verify(hosterRepository)
                .deleteAll(eventHosters);
        deletionOrder.verify(eventRepository)
                .delete(draft);
    }

    @ParameterizedTest
    @EnumSource(
            value = EventStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "DRAFT"
    )
    void nonDraftEventsCannotBeDeleted(EventStatus status) {
        authorizeExecutive();
        Event event = event(status);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(event));
        when(hosterRepository
                .existsByIdEventIDAndIdSocietyID(
                        EVENT_ID,
                        SOCIETY_ID
                ))
                .thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> eventService.deleteDraftEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL
                )
        );

        assertEquals(
                "Only draft events may be deleted.",
                exception.getMessage()
        );
        verify(hosterRepository, never())
                .findByIdEventID(EVENT_ID);
        verify(hosterRepository, never())
                .deleteAll(any());
        verify(eventRepository, never())
                .deleteBudgetRequestsByEventID(EVENT_ID);
        verify(eventRepository, never())
                .deleteEventOutcomesByEventID(EVENT_ID);
        verify(eventRepository, never())
                .deleteRsvpsByEventID(EVENT_ID);
        verify(eventRepository, never())
                .deleteEventFeedbackByEventID(EVENT_ID);
        verify(eventRepository, never())
                .delete(any(Event.class));
    }

    @Test
    void anotherSocietyCannotDeleteTheDraft() {
        authorizeExecutive();
        Event draft = event(EventStatus.DRAFT);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(draft));
        when(hosterRepository
                .existsByIdEventIDAndIdSocietyID(
                        EVENT_ID,
                        SOCIETY_ID
                ))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.deleteDraftEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL
                )
        );

        assertEquals(
                "You are not authorised to delete this draft.",
                exception.getMessage()
        );
        verify(eventRepository, never())
                .delete(any(Event.class));
        verify(hosterRepository, never())
                .deleteAll(any());
    }

    @Test
    void missingEventReturnsTheRequiredArgumentFailure() {
        authorizeExecutive();
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.deleteDraftEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL
                )
        );

        assertEquals(
                "Event not found.",
                exception.getMessage()
        );
        verifyNoInteractions(hosterRepository);
        verify(eventRepository, never())
                .delete(any(Event.class));
    }

    @Test
    void invalidExecutiveReturnsAnArgumentFailure() {
        when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.deleteDraftEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL
                )
        );

        assertEquals(
                "Invalid executive.",
                exception.getMessage()
        );
        verifyNoInteractions(
                executiveRepository,
                societyRepository,
                eventRepository,
                hosterRepository
        );
    }

    @Test
    void blankInputsAreRejectedBeforeRepositoryAccess() {
        IllegalArgumentException eventException = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.deleteDraftEvent(
                        " ",
                        EXECUTIVE_EMAIL
                )
        );
        IllegalArgumentException executiveException = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.deleteDraftEvent(
                        EVENT_ID,
                        " "
                )
        );

        assertEquals(
                "Event ID is required.",
                eventException.getMessage()
        );
        assertEquals(
                "Executive email is required.",
                executiveException.getMessage()
        );
        verifyNoInteractions(
                studentRepository,
                executiveRepository,
                societyRepository,
                eventRepository,
                hosterRepository
        );
    }

    private void authorizeExecutive() {
        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EXECUTIVE_EMAIL);

        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                STUDENT_NUMBER,
                SOCIETY_ID,
                TODAY.minusMonths(1)
        ));
        executive.setTermEndDate(null);

        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);

        when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.of(student));
        when(executiveRepository
                .findByIdStudentNumber(STUDENT_NUMBER))
                .thenReturn(List.of(executive));
        when(societyRepository.findById(SOCIETY_ID))
                .thenReturn(Optional.of(society));
    }

    private Event event(EventStatus status) {
        Event event = new Event();
        event.setEventID(EVENT_ID);
        event.setEventName("Draft Event");
        event.setEventStatus(status);
        return event;
    }

    private Hoster hoster(
            Event event,
            String societyID,
            boolean primary) {

        Hoster hoster = new Hoster();
        hoster.setId(new HosterId(
                event.getEventID(),
                societyID
        ));
        hoster.setEvent(event);
        hoster.setIsPrimary(primary);
        return hoster;
    }
}
