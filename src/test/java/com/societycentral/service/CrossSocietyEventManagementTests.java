package com.societycentral.service;


import com.societycentral.model.*;
import com.societycentral.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrossSocietyEventManagementTests {
    private final EventRepository events = mock(EventRepository.class);
    private final HosterRepository hosts = mock(HosterRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final ExecutiveRepository executives = mock(ExecutiveRepository.class);
    private final SocietyRepository societies = mock(SocietyRepository.class);
    private final RSVPRepository rsvps = mock(RSVPRepository.class);
    private EventProposalService service;
    private Event event;

    @BeforeEach void setup() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
        service = new EventProposalService(events, hosts, students, executives, societies,
                mock(SDORepository.class), mock(VenueRepository.class), mock(SocietyMemberRepository.class),
                mock(EventCoHostInvitationRepository.class), mock(POAEventRepository.class),
                mock(POAEventCoHostRepository.class), mock(POARepository.class), mock(BudgetRequestRepository.class),
                rsvps, mock(RSVPService.class), mock(EmailService.class), mock(com.societycentral.security.JwtUtil.class), clock);
        Student student = new Student(); student.setStudentNumber("220000001");
        when(students.findByEmail("exec@test")).thenReturn(Optional.of(student));
        Executive executive = new Executive();
        executive.setId(new ExecutiveId("220000001", "SOC001", LocalDate.of(2026, 1, 1)));
        when(executives.findByIdStudentNumber("220000001")).thenReturn(List.of(executive));
        Society society = new Society(); society.setSocietyID("SOC001"); society.setActiveStatus(true);
        when(societies.findById("SOC001")).thenReturn(Optional.of(society));
        event = new Event(); event.setEventID("EVT011"); event.setEventStatus(EventStatus.PUBLISHED);
        when(events.findByIdForUpdate("EVT011")).thenReturn(Optional.of(event));
        when(events.findById("EVT011")).thenReturn(Optional.of(event));
        // No Hoster relation between EVT011 (SOC010) and SOC001.
    }
    @Test void editOtherSocietyPublishedEventForbidden() {
        assertThrows(IllegalStateException.class,
                () -> service.editPublishedEvent("EVT011", "exec@test", Map.of("eventName", "Changed")));
        verify(events, never()).save(any());
    }
    @Test void cancelOtherSocietyPublishedEventForbidden() {
        assertThrows(IllegalStateException.class,
                () -> service.cancelPublishedEvent("EVT011", "exec@test", "Reason"));
        assertEquals(EventStatus.PUBLISHED, event.getEventStatus());
        verify(events, never()).save(any());
    }
    @Test void otherSocietyPrivateGuestListForbidden() {
        assertThrows(IllegalStateException.class,
                () -> service.getEventRsvps("EVT011", "exec@test"));
        verifyNoInteractions(rsvps);
    }
}
