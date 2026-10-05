package com.societycentral.service;

import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ExecutiveAttendeeRsvpTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final String EMAIL = "executive@example.test";
    private static final String STUDENT = "220000001";
    private final Authentication auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());
    @Mock RSVPRepository rsvps;
    @Mock EventRepository events;
    @Mock StudentRepository students;
    @Mock HosterRepository hosts;
    @Mock VenueRepository venues;
    @Mock JwtUtil jwt;
    @Mock EmailService email;
    @Mock RSVPTicketService tickets;
    @Mock ExecutiveSocietyResolver resolver;
    @Mock ExecutiveRepository executives;
    @Mock SocietyMemberRepository members;
    private RSVPService service;
    private Event event;

    @BeforeEach void setup() {
        service = new RSVPService(rsvps, events, students, hosts, venues, jwt, email, tickets,
                resolver, CLOCK, executives, new EventAttendanceEligibility(hosts, members, CLOCK));
        Student student = new Student();
        student.setStudentNumber(STUDENT);
        student.setEmail(EMAIL);
        when(students.findByEmail(EMAIL)).thenReturn(Optional.of(student));
        event = new Event();
        event.setEventID("EVT011");
        event.setEventName("Other society event");
        event.setEventStatus(EventStatus.PUBLISHED);
        event.setAttendingType(AttendingType.EVERY_STUDENT);
        event.setEventDate(TODAY.plusDays(1));
        event.setRsvpOpenDate(LocalDateTime.now(CLOCK).minusDays(1));
        event.setRsvpCloseDate(LocalDateTime.now(CLOCK).plusDays(1));
        event.setEventLimit(10);
        when(events.findByIdForUpdate("EVT011")).thenReturn(Optional.of(event));
        lenient().when(hosts.findByIdEventID("EVT011")).thenReturn(List.of(host("SOC010", true)));
        // The student is an executive of SOC001 only, never of SOC010.
        lenient().when(executives.existsActiveExecutiveRole(STUDENT, "SOC001", TODAY)).thenReturn(true);
    }
    private Hoster host(String societyID, boolean primary) {
        Society society = new Society(); society.setSocietyID(societyID); society.setSocietyName(societyID);
        Hoster host = new Hoster(); host.setId(new HosterId("EVT011", societyID));
        host.setSociety(society); host.setEvent(event); host.setIsPrimary(primary); return host;
    }
    private void rejected(Class<? extends Exception> type, String message) {
        Exception failure = assertThrows(type, () -> service.confirmRsvp("EVT011", null, auth));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
        verify(rsvps, never()).save(any());
        verifyNoInteractions(email, tickets);
    }
    @Test void otherSocietyEveryStudentAllowsExecutive() {
        assertNotNull(service.confirmRsvp("EVT011", null, auth));
        verify(rsvps).save(any());
        verify(executives).existsActiveExecutiveRole(STUDENT, "SOC010", TODAY);
        verifyNoInteractions(members);
    }
    @Test void primaryHostExecutiveStillRejected() {
        when(hosts.findByIdEventID("EVT011")).thenReturn(List.of(host("SOC001", true)));
        rejected(IllegalStateException.class, "Executives cannot RSVP to their own society events.");
    }
    @Test void otherSocietyMembersEventRejectsNonMemberExecutive() {
        event.setAttendingType(AttendingType.MEMBERS);
        rejected(ForbiddenOperationException.class, "Only current members");
        verify(members).existsActiveMembership(STUDENT, "SOC010", TODAY);
    }
    @Test void otherSocietyMembersEventAllowsCurrentMemberExecutive() {
        event.setAttendingType(AttendingType.MEMBERS);
        when(members.existsActiveMembership(STUDENT, "SOC010", TODAY)).thenReturn(true);
        assertNotNull(service.confirmRsvp("EVT011", null, auth));
        verify(rsvps).save(any());
    }
    @Test void unrelatedMembershipDoesNotGrantMembersEventAttendance() {
        event.setAttendingType(AttendingType.MEMBERS);
        rejected(ForbiddenOperationException.class, "Only current members");
        verify(members, never()).existsActiveMembership(STUDENT, "SOC001", TODAY);
    }
    @Test void cohostExecutiveRetainsExistingPrimaryOnlyRestriction() {
        when(hosts.findByIdEventID("EVT011")).thenReturn(List.of(host("SOC010", true), host("SOC001", false)));
        assertNotNull(service.confirmRsvp("EVT011", null, auth));
        verify(executives, never()).existsActiveExecutiveRole(STUDENT, "SOC001", TODAY);
    }
    @Test void membersOfCohostRemainEligibleAsInOrdinaryBrowsing() {
        event.setAttendingType(AttendingType.MEMBERS);
        when(hosts.findByIdEventID("EVT011")).thenReturn(List.of(host("SOC010", true), host("SOC020", false)));
        when(members.existsActiveMembership(STUDENT, "SOC010", TODAY)).thenReturn(false);
        when(members.existsActiveMembership(STUDENT, "SOC020", TODAY)).thenReturn(true);
        assertNotNull(service.confirmRsvp("EVT011", null, auth));
    }
    @Test void duplicateRejected() {
        when(rsvps.existsById(new RsvpId("EVT011", STUDENT))).thenReturn(true);
        rejected(IllegalStateException.class, "already RSVP'd");
    }
    @Test void beforeOpeningRejected() {
        event.setRsvpOpenDate(LocalDateTime.now(CLOCK).plusSeconds(1));
        rejected(IllegalStateException.class, "RSVP opens on");
    }
    @Test void afterClosingRejected() {
        event.setRsvpCloseDate(LocalDateTime.now(CLOCK).minusSeconds(1));
        rejected(IllegalStateException.class, "closed");
    }
    @Test void exactClosingRejected() {
        event.setRsvpCloseDate(LocalDateTime.now(CLOCK));
        rejected(IllegalStateException.class, "closed");
    }
    @Test void capacityStillRejected() {
        when(rsvps.countByEventIDWithLock("EVT011")).thenReturn(10L);
        rejected(IllegalStateException.class, "full capacity");
    }
    @Test void unpublishedRejected() {
        event.setEventStatus(EventStatus.DRAFT);
        rejected(IllegalStateException.class, "not currently accepting");
    }
}
