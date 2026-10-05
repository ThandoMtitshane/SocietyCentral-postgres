package com.societycentral.service;

import com.societycentral.dto.request.CreateEventRequestDTO;
import com.societycentral.dto.response.EventResponseDTO;
import com.societycentral.dto.response.ExecutiveEventPageDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.VenueUnavailableException;
import com.societycentral.mapper.EventMapper;
import com.societycentral.model.AuditLog;
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
import com.societycentral.repository.projection.ExecutiveEventListProjection;
import com.societycentral.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveEventManagementServiceTests {

    private static final String SOCIETY_ID = "SOC001";
    private static final String EVENT_ID = "EVT100";
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
    private Society society;

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
        society = society(SOCIETY_ID, "Computing Society");
    }

    @Test
    void listUsesAuthenticatedExecutiveSocietyStatusSearchAndPagination() {
        authorizeCurrentSociety();
        ExecutiveEventListProjection projection =
                mock(ExecutiveEventListProjection.class);
        when(projection.getEventID()).thenReturn(EVENT_ID);
        when(projection.getEventName()).thenReturn("Welcome Evening");
        when(projection.getEventDescription())
                .thenReturn("A society welcome event.");
        when(projection.getEventStatus()).thenReturn(EventStatus.DRAFT);
        when(projection.getSocietyID()).thenReturn(SOCIETY_ID);
        when(projection.getSocietyName()).thenReturn("Computing Society");

        when(eventRepository.findExecutiveEvents(
                SOCIETY_ID,
                EventStatus.DRAFT,
                "%welcome%",
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(projection),
                        PageRequest.of(0, 20),
                        1));

        ExecutiveEventPageDTO page = eventService.getExecutiveEvents(
                EXECUTIVE_EMAIL,
                EventStatus.DRAFT,
                0,
                20,
                " Welcome ");

        assertEquals(1, page.getTotalElements());
        assertEquals(EVENT_ID, page.getContent().getFirst().getEventID());
        assertEquals(SOCIETY_ID,
                page.getContent().getFirst().getSocietyID());
        assertTrue(page.isFirst());
        verify(eventRepository).findExecutiveEvents(
                SOCIETY_ID,
                EventStatus.DRAFT,
                "%welcome%",
                PageRequest.of(0, 20));
    }

    @Test
    void withdrawnListMappingRetainsEnumStatus() {
        authorizeCurrentSociety();
        ExecutiveEventListProjection projection =
                mock(ExecutiveEventListProjection.class);
        when(projection.getEventID()).thenReturn(EVENT_ID);
        when(projection.getEventStatus())
                .thenReturn(EventStatus.WITHDRAWN);
        when(projection.getSocietyID()).thenReturn(SOCIETY_ID);
        when(projection.getSocietyName())
                .thenReturn("Computing Society");
        when(eventRepository.findExecutiveEvents(
                SOCIETY_ID,
                EventStatus.WITHDRAWN,
                null,
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(projection),
                        PageRequest.of(0, 20),
                        1));

        ExecutiveEventPageDTO page = eventService.getExecutiveEvents(
                EXECUTIVE_EMAIL,
                EventStatus.WITHDRAWN,
                0,
                20,
                null);

        assertEquals(
                EventStatus.WITHDRAWN,
                page.getContent().getFirst().getEventStatus());
    }

    @Test
    void emptySocietyEventListReturnsSuccessfulEmptyPageData() {
        authorizeCurrentSociety();
        when(eventRepository.findExecutiveEvents(
                SOCIETY_ID,
                null,
                null,
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0));

        ExecutiveEventPageDTO page = eventService.getExecutiveEvents(
                EXECUTIVE_EMAIL,
                null,
                0,
                20,
                null);

        assertTrue(page.isEmpty());
        assertEquals(List.of(), page.getContent());
    }

    @Test
    void detailIncludesResolvedVenueAndOwnershipData() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        Hoster hoster = hoster(draft, society);
        Venue venue = venue();
        draft.setRejectionReason("Clarify the safety plan.");

        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.of(hoster));
        when(venueService.findByVenueCode("SC001"))
                .thenReturn(Optional.of(venue));

        EventResponseDTO response = eventService.getExecutiveEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL);

        assertEquals(EVENT_ID, response.getEventID());
        assertEquals("SC001", response.getVenueCode());
        assertEquals("Auditorium", response.getVenueName());
        assertEquals("Auditorium", response.getVenueType());
        assertEquals(850, response.getVenueCapacity());
        assertEquals(Campus.SOUTH_CAMPUS, response.getCampus());
        assertEquals(Campus.SOUTH_CAMPUS, response.getEventCampus());
        assertEquals(SOCIETY_ID, response.getSocietyID());
        assertEquals(draft.getEventDescription(),
                response.getEventDescription());
        assertEquals(draft.getEventStartTime(),
                response.getEventStartTime());
        assertEquals(draft.getEventEndTime(),
                response.getEventEndTime());
        assertEquals(draft.getPosterUrl(), response.getPosterUrl());
        assertEquals(draft.getBannerUrl(), response.getBannerUrl());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
        assertEquals("Clarify the safety plan.",
                response.getRejectionReason());
    }

    @Test
    void withdrawnDetailMappingRetainsEnumStatus() {
        authorizeCurrentSociety();
        Event withdrawn = editableEvent(EventStatus.WITHDRAWN);
        Hoster primaryHost = hoster(withdrawn, society);
        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.of(primaryHost));
        when(venueService.findByVenueCode("SC001"))
                .thenReturn(Optional.of(venue()));

        EventResponseDTO response = eventService.getExecutiveEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL);

        assertEquals(
                EventStatus.WITHDRAWN,
                response.getEventStatus());
    }

    @Test
    void anotherSocietyEventCannotBeRead() {
        authorizeCurrentSociety();
        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> eventService.getExecutiveEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL));

        assertEquals(
                "The event does not belong to your society.",
                exception.getMessage());
        verifyNoInteractions(venueService);
    }

    @Test
    void draftUpdateKeepsExistingVenueAndChangesSameEventWithoutAnotherHoster() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        LocalDateTime createdAt = NOW.minusDays(3);
        draft.setCreatedAt(createdAt);
        draft.setUpdatedAt(NOW.minusDays(2));
        CreateEventRequestDTO request = validRequest();
        request.setEventName("Updated Event Name");
        allowOwnedMutation(draft, request);

        EventResponseDTO response = eventService.updateExecutiveEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL,
                request);

        assertEquals(EVENT_ID, response.getEventID());
        assertSame(draft, savedEvent());
        assertEquals("Updated Event Name", draft.getEventName());
        assertEquals("SC001", draft.getVenueCode());
        assertEquals(createdAt, draft.getCreatedAt());
        assertTrue(draft.getUpdatedAt().isAfter(NOW.minusDays(2)));
        verify(venueService).lockAndValidateForEventUpdate(
                "SC001",
                Campus.SOUTH_CAMPUS,
                100);
        verify(hosterRepository, never()).save(any(Hoster.class));
    }

    @Test
    void rejectedRevisionMovesToDraftAndPreservesFeedback() {
        authorizeCurrentSociety();
        Event rejected = editableEvent(EventStatus.REJECTED);
        rejected.setRejectionReason("Provide a clearer risk assessment.");
        CreateEventRequestDTO request = validRequest();
        request.setEventDescription("Revised risk assessment included.");
        allowOwnedMutation(rejected, request);

        EventResponseDTO response = eventService.updateExecutiveEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL,
                request);

        assertEquals(EventStatus.DRAFT, rejected.getEventStatus());
        assertEquals(
                "Provide a clearer risk assessment.",
                response.getRejectionReason());
        verify(hosterRepository, never()).save(any(Hoster.class));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {
            "PROPOSED",
            "WITHDRAWN",
            "APPROVED",
            "PUBLISHED",
            "CANCELLED",
            "COMPLETED"
    })
    void nonEditableStatusesAreRejected(EventStatus status) {
        authorizeCurrentSociety();
        Event event = editableEvent(status);
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(event));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> eventService.updateExecutiveEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL,
                        validRequest()));

        assertEquals(
                "Only draft or rejected events may be edited.",
                exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
        verifyNoInteractions(venueService);
    }

    @Test
    void updateExcludesCurrentEventFromVenueConflictCheck() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        CreateEventRequestDTO request = validRequest();
        allowOwnedMutation(draft, request);

        eventService.updateExecutiveEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL,
                request);

        verify(venueService).assertAvailableForEventUpdate(
                EVENT_ID,
                "SC001",
                request.getEventDate(),
                request.getEventStartTime(),
                request.getEventEndTime());
    }

    @Test
    void conflictWithAnotherEventDoesNotMutateOrPersistDraft() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        String originalName = draft.getEventName();
        CreateEventRequestDTO request = validRequest();
        request.setEventName("Must not be applied");

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(draft));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);
        when(venueService.lockAndValidateForEventUpdate(
                "SC001",
                Campus.SOUTH_CAMPUS,
                100)).thenReturn(venue());
        doThrow(new VenueUnavailableException(
                "The selected venue is already booked for this date and time."))
                .when(venueService)
                .assertAvailableForEventUpdate(
                        EVENT_ID,
                        "SC001",
                        request.getEventDate(),
                        request.getEventStartTime(),
                        request.getEventEndTime());

        assertThrows(
                VenueUnavailableException.class,
                () -> eventService.updateExecutiveEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL,
                        request));

        assertEquals(originalName, draft.getEventName());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void updateCapacityIsValidatedThroughLockedDatabaseVenue() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        CreateEventRequestDTO request = validRequest();
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(draft));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);
        when(venueService.lockAndValidateForEventUpdate(
                "SC001",
                Campus.SOUTH_CAMPUS,
                100)).thenThrow(new IllegalArgumentException(
                        "Expected attendance cannot exceed the selected venue capacity of 80."));

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateExecutiveEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL,
                        request));

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void draftSubmissionRevalidatesAndTransitionsToProposed() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        allowOwnedSubmission(draft);

        EventResponseDTO response = eventService.submitEventForApproval(
                EVENT_ID,
                EXECUTIVE_EMAIL);

        assertEquals(EventStatus.PROPOSED, draft.getEventStatus());
        assertEquals(EventStatus.PROPOSED, response.getEventStatus());
        verify(venueService).assertAvailableForEventUpdate(
                EVENT_ID,
                "SC001",
                draft.getEventDate(),
                draft.getEventStartTime(),
                draft.getEventEndTime());
    }

    @Test
    void proposedEventOwnedByPrimarySocietyIsWithdrawnWithoutDeletingRows() {
        authorizeCurrentSociety();
        Event proposed = editableEvent(EventStatus.PROPOSED);
        Hoster primaryHost = hoster(proposed, society);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(proposed));
        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.of(primaryHost));
        when(eventRepository.save(proposed)).thenReturn(proposed);
        when(venueService.findByVenueCode("SC001"))
                .thenReturn(Optional.of(venue()));

        EventResponseDTO response = eventService.withdrawEventProposal(
                EVENT_ID,
                EXECUTIVE_EMAIL);

        assertEquals(EventStatus.WITHDRAWN, proposed.getEventStatus());
        assertEquals(EventStatus.WITHDRAWN, response.getEventStatus());
        assertEquals(NOW, proposed.getUpdatedAt());
        assertEquals(EVENT_ID, response.getEventID());
        verify(eventRepository).save(proposed);

        org.mockito.ArgumentCaptor<AuditLog> auditCaptor =
                org.mockito.ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertEquals("SDO001", auditLog.getStaffNumber());
        assertEquals("Event", auditLog.getEntityName());
        assertEquals("UPDATE", auditLog.getOperation());
        assertEquals("eventStatus", auditLog.getFieldChanged());
        assertEquals("PROPOSED", auditLog.getOldValue());
        assertEquals("WITHDRAWN", auditLog.getNewValue());
        assertEquals(EXECUTIVE_EMAIL, auditLog.getChangedBy());
        assertEquals(NOW, auditLog.getChangedDate());
        assertEquals(
                "EVENT_PROPOSAL_WITHDRAWN: " + EVENT_ID,
                auditLog.getReason());

        verify(eventRepository, never()).delete(any(Event.class));
        verify(eventRepository, never()).deleteById(any(String.class));
        verify(hosterRepository, never()).delete(any(Hoster.class));
        verify(hosterRepository, never()).deleteAll(anyList());
    }

    @ParameterizedTest
    @EnumSource(
            value = EventStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "PROPOSED")
    void onlyProposedEventsMayBeWithdrawn(EventStatus status) {
        authorizeCurrentSociety();
        Event event = editableEvent(status);
        Hoster primaryHost = hoster(event, society);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(event));
        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.of(primaryHost));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> eventService.withdrawEventProposal(
                        EVENT_ID,
                        EXECUTIVE_EMAIL));

        assertEquals(
                "Only proposed events may be withdrawn.",
                exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));
        verifyNoInteractions(venueService);
    }

    @Test
    void anotherSocietyExecutiveCannotWithdrawProposal() {
        authorizeCurrentSociety();
        Event proposed = editableEvent(EventStatus.PROPOSED);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(proposed));
        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> eventService.withdrawEventProposal(
                        EVENT_ID,
                        EXECUTIVE_EMAIL));

        assertEquals(
                "You are not authorised to withdraw this event proposal.",
                exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void coHostingSocietyExecutiveCannotWithdrawPrimaryProposal() {
        authorizeCurrentSociety();
        Event proposed = editableEvent(EventStatus.PROPOSED);
        Hoster coHost = hoster(proposed, society);
        coHost.setIsPrimary(false);

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(proposed));
        when(hosterRepository.findEventHostedBySociety(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(Optional.of(coHost));

        assertThrows(
                ForbiddenOperationException.class,
                () -> eventService.withdrawEventProposal(
                        EVENT_ID,
                        EXECUTIVE_EMAIL));

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void validRejectedRevisionCanBeSavedThenExplicitlySubmitted() {
        authorizeCurrentSociety();
        Event rejected = editableEvent(EventStatus.REJECTED);
        rejected.setRejectionReason("Correct the schedule.");
        CreateEventRequestDTO request = validRequest();

        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(rejected));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);
        when(venueService.lockAndValidateForEventUpdate(
                "SC001",
                Campus.SOUTH_CAMPUS,
                100)).thenReturn(venue());
        when(eventRepository.save(rejected)).thenReturn(rejected);

        EventResponseDTO revised = eventService.updateExecutiveEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL,
                request);
        EventResponseDTO submitted = eventService.submitEventForApproval(
                EVENT_ID,
                EXECUTIVE_EMAIL);

        assertEquals(EventStatus.DRAFT, revised.getEventStatus());
        assertEquals(EventStatus.PROPOSED, submitted.getEventStatus());
        assertEquals("Correct the schedule.",
                submitted.getRejectionReason());
        verify(eventRepository, times(2)).save(rejected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROPOSED", "APPROVED", "PUBLISHED"})
    void repeatedOrLaterWorkflowSubmissionFails(String statusName) {
        authorizeCurrentSociety();
        Event event = editableEvent(EventStatus.valueOf(statusName));
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(event));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> eventService.submitEventForApproval(
                        EVENT_ID,
                        EXECUTIVE_EMAIL));

        assertEquals(
                "Only events in DRAFT status may be submitted for approval.",
                exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void incompleteDraftCannotBeSubmitted() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        draft.setEventDescription(" ");
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(draft));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> eventService.submitEventForApproval(
                        EVENT_ID,
                        EXECUTIVE_EMAIL));

        assertEquals("Event description is required.",
                exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
        verifyNoInteractions(venueService);
    }

    @Test
    void eventOwnedByAnotherSocietyCannotBeUpdated() {
        authorizeCurrentSociety();
        Event draft = editableEvent(EventStatus.DRAFT);
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(draft));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(false);

        assertThrows(
                ForbiddenOperationException.class,
                () -> eventService.updateExecutiveEvent(
                        EVENT_ID,
                        EXECUTIVE_EMAIL,
                        validRequest()));

        verify(eventRepository, never()).save(any(Event.class));
        verifyNoInteractions(venueService);
    }

    private Event savedEvent() {
        org.mockito.ArgumentCaptor<Event> captor =
                org.mockito.ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        return captor.getValue();
    }

    private void allowOwnedMutation(
            Event event,
            CreateEventRequestDTO request) {
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(event));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);
        when(venueService.lockAndValidateForEventUpdate(
                request.getVenueCode(),
                request.getEventCampus(),
                request.getEventLimit())).thenReturn(venue());
        when(eventRepository.save(event)).thenReturn(event);
    }

    private void allowOwnedSubmission(Event event) {
        when(eventRepository.findByIdForUpdate(EVENT_ID))
                .thenReturn(Optional.of(event));
        when(hosterRepository.existsByIdEventIDAndIdSocietyID(
                EVENT_ID,
                SOCIETY_ID)).thenReturn(true);
        when(venueService.lockAndValidateForEventUpdate(
                "SC001",
                Campus.SOUTH_CAMPUS,
                100)).thenReturn(venue());
        when(eventRepository.save(event)).thenReturn(event);
    }

    private void authorizeCurrentSociety() {
        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EXECUTIVE_EMAIL);

        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                STUDENT_NUMBER,
                SOCIETY_ID,
                TODAY.minusMonths(1)));
        executive.setTermEndDate(null);

        lenient().when(studentRepository.findByEmail(EXECUTIVE_EMAIL))
                .thenReturn(Optional.of(student));
        lenient().when(executiveRepository.findByIdStudentNumber(
                STUDENT_NUMBER)).thenReturn(List.of(executive));
        lenient().when(societyRepository.findById(SOCIETY_ID))
                .thenReturn(Optional.of(society));
    }

    private Event editableEvent(EventStatus status) {
        Event event = new Event();
        event.setEventID(EVENT_ID);
        event.setEventName("Original Event");
        event.setEventDescription("Complete event description.");
        event.setEventDate(TODAY.plusDays(5));
        event.setEventStartTime(LocalTime.of(12, 0));
        event.setEventEndTime(LocalTime.of(14, 0));
        event.setEventTime(LocalTime.of(12, 0));
        event.setVenueCode("SC001");
        event.setEventVenue("Auditorium");
        event.setEventCampus(Campus.SOUTH_CAMPUS);
        event.setEventLimit(100);
        event.setAttendingType(AttendingType.MEMBERS);
        event.setPosterUrl(
                "http://localhost:8080/media/events/posters/poster.png");
        event.setBannerUrl(
                "http://localhost:8080/media/events/banners/banner.png");
        event.setEventStatus(status);
        event.setCreatedAt(NOW.minusDays(3));
        event.setUpdatedAt(NOW.minusDays(2));
        return event;
    }

    private CreateEventRequestDTO validRequest() {
        CreateEventRequestDTO request = new CreateEventRequestDTO();
        request.setEventName("Updated Event");
        request.setEventDescription("Updated complete event description.");
        request.setEventDate(TODAY.plusDays(6));
        request.setEventStartTime(LocalTime.of(13, 0));
        request.setEventEndTime(LocalTime.of(15, 0));
        request.setVenueCode("SC001");
        request.setEventCampus(Campus.SOUTH_CAMPUS);
        request.setEventLimit(100);
        request.setAttendingType(AttendingType.EVERY_STUDENT);
        request.setPosterUrl(
                "http://localhost:8080/media/events/posters/poster-new.png");
        request.setBannerUrl(
                "http://localhost:8080/media/events/banners/banner-new.png");
        return request;
    }

    private Venue venue() {
        Venue venue = new Venue();
        venue.setVenueCode("SC001");
        venue.setCampus(Campus.SOUTH_CAMPUS);
        venue.setVenueName("Auditorium");
        venue.setVenueType("Auditorium");
        venue.setCapacity(850);
        venue.setActive(true);
        return venue;
    }

    private Hoster hoster(Event event, Society eventSociety) {
        Hoster hoster = new Hoster();
        hoster.setEvent(event);
        hoster.setSociety(eventSociety);
        hoster.setIsPrimary(true);
        return hoster;
    }

    private Society society(String societyID, String name) {
        Society eventSociety = new Society();
        eventSociety.setSocietyID(societyID);
        eventSociety.setSocietyName(name);
        eventSociety.setSdoStaffNumber("SDO001");
        eventSociety.setActiveStatus(true);
        eventSociety.setIsFlagged(false);
        return eventSociety;
    }
}
