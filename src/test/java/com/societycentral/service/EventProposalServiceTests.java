package com.societycentral.service;

import com.societycentral.dto.request.ReviewEventRequestDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventProposalServiceTests {

    private static final String EVENT_ID = "EVT-200";
    private static final String SDO_EMAIL = "sdo@nmu.ac.za";
    private static final String STAFF_NUMBER = "SDO001";
    private static final String STUDENT_NUMBER = "220000001";

    @Mock EventRepository eventRepository;
    @Mock HosterRepository hosterRepository;
    @Mock StudentRepository studentRepository;
    @Mock ExecutiveRepository executiveRepository;
    @Mock SocietyRepository societyRepository;
    @Mock SDORepository sdoRepository;
    @Mock VenueRepository venueRepository;
    @Mock SocietyMemberRepository societyMemberRepository;
    @Mock EventCoHostInvitationRepository coHostRepo;
    @Mock POAEventRepository poaEventRepository;
    @Mock POAEventCoHostRepository poaCoHostRepository;
    @Mock POARepository poaRepository;
    @Mock BudgetRequestRepository budgetRequestRepository;
    @Mock RSVPRepository rsvpRepository;
    @Mock RSVPService rsvpService;
    @Mock EmailService emailService;
    @Mock JwtUtil jwtUtil;

    private EventProposalService service;
    private Event event;
    private Society society;
    private SDO sdo;

    @BeforeEach
    void setUp() {
        service = new EventProposalService(eventRepository, hosterRepository,
                studentRepository, executiveRepository, societyRepository, sdoRepository,
                venueRepository, societyMemberRepository, coHostRepo, poaEventRepository,
                poaCoHostRepository, poaRepository, budgetRequestRepository, rsvpRepository,
                rsvpService, emailService, jwtUtil,
                Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC));

        event = new Event();
        event.setEventID(EVENT_ID);
        event.setEventName("B200 event");
        event.setEventStatus(EventStatus.PROPOSED);
        event.setSubmittedBy(STUDENT_NUMBER);
        society = new Society();
        society.setSocietyID("SOC001");
        society.setSocietyName("Society");
        society.setSdoStaffNumber(STAFF_NUMBER);
        sdo = new SDO();
        sdo.setEmail(SDO_EMAIL);
        sdo.setStaffNumber(STAFF_NUMBER);

        lenient().when(sdoRepository.findByEmail(SDO_EMAIL)).thenReturn(Optional.of(sdo));
        lenient().when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        lenient().when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(hosterRepository.findByIdEventID(EVENT_ID)).thenReturn(List.of(primaryHoster()));
        lenient().when(coHostRepo.findByEventID(EVENT_ID)).thenReturn(List.of());
        lenient().when(studentRepository.findById(STUDENT_NUMBER)).thenReturn(Optional.empty());
    }

    @Test
    void responsibleSdoCanApproveAndPersistsReviewAuditAndNotifies() {
        ReviewEventRequestDTO request = request(" APPROVE ", null);

        service.reviewProposal(EVENT_ID, SDO_EMAIL, request);

        assertEquals(EventStatus.APPROVED, event.getEventStatus());
        assertEquals(STAFF_NUMBER, event.getReviewedByStaffNumber());
        assertNotNull(event.getReviewedAt());
        verify(eventRepository).save(event);
        verify(emailService, never()).send(any(), any(), any());
    }

    @Test
    void responsibleSdoCanRejectWithTrimmedReasonAndNotifiesSubmitter() {
        Student submitter = new Student();
        submitter.setStudentNumber(STUDENT_NUMBER);
        submitter.setEmail("executive@nmu.ac.za");
        when(studentRepository.findById(STUDENT_NUMBER)).thenReturn(Optional.of(submitter));
        ReviewEventRequestDTO request = request(" REJECT ", "  Needs a safer venue  ");

        service.reviewProposal(EVENT_ID, SDO_EMAIL, request);

        assertEquals(EventStatus.REJECTED, event.getEventStatus());
        assertEquals("Needs a safer venue", event.getRejectionReason());
        assertEquals("Needs a safer venue", event.getSdoReviewNotes());
        assertEquals(STAFF_NUMBER, event.getReviewedByStaffNumber());
        assertNotNull(event.getReviewedAt());
        verify(emailService).send(eq(EmailType.EVENT_REJECTED), eq(submitter.getEmail()), any());
    }

    @Test
    void blankReasonAndInvalidActionDoNotSaveOrNotify() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reviewProposal(EVENT_ID, SDO_EMAIL, request("REJECT", " \t ")));
        assertEquals(EventStatus.PROPOSED, event.getEventStatus());
        verify(eventRepository, never()).save(any());
        assertThrows(IllegalArgumentException.class,
                () -> service.reviewProposal(EVENT_ID, SDO_EMAIL, request("DEFER", null)));
        verifyNoInteractions(emailService);
    }

    @Test
    void onlyProposedEventsCanBeReviewed() {
        event.setEventStatus(EventStatus.APPROVED);
        assertThrows(IllegalStateException.class,
                () -> service.reviewProposal(EVENT_ID, SDO_EMAIL, request("APPROVE", null)));
        event.setEventStatus(EventStatus.REJECTED);
        assertThrows(IllegalStateException.class,
                () -> service.reviewProposal(EVENT_ID, SDO_EMAIL, request("APPROVE", null)));
        verifyNoInteractions(emailService);
    }

    @Test
    void unrelatedSdoIsForbidden() {
        sdo.setStaffNumber("OTHER");
        assertThrows(ForbiddenOperationException.class,
                () -> service.reviewProposal(EVENT_ID, SDO_EMAIL, request("APPROVE", null)));
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    private Hoster primaryHoster() {
        Hoster hoster = new Hoster();
        hoster.setId(new HosterId(EVENT_ID, society.getSocietyID()));
        hoster.setSociety(society);
        hoster.setIsPrimary(true);
        return hoster;
    }

    private ReviewEventRequestDTO request(String action, String notes) {
        ReviewEventRequestDTO request = new ReviewEventRequestDTO();
        request.setAction(action);
        request.setReviewNotes(notes);
        return request;
    }
}
