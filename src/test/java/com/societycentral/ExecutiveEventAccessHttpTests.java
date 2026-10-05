package com.societycentral;

import com.societycentral.config.AdminInitializer;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import com.societycentral.security.JwtUtil;
import com.societycentral.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real HTTP/security/controller/service wiring; isolated database and mocked external effects. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:event-access-http;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=event-access-test-only-secret-at-least-32-bytes",
        "jwt.expiration-ms=60000"
})
class ExecutiveEventAccessHttpTests {
    @LocalServerPort int port;
    @Autowired JwtUtil jwt;
    @MockitoBean StudentRepository students;
    @MockitoBean ExecutiveRepository executives;
    @MockitoBean EventRepository events;
    @MockitoBean HosterRepository hosts;
    @MockitoBean SocietyRepository societies;
    @MockitoBean SocietyMemberRepository members;
    @MockitoBean RSVPRepository rsvps;
    @MockitoBean UserDetailsService users;
    @MockitoBean EmailService email;
    @MockitoBean RSVPTicketService tickets;
    @MockitoBean AdminInitializer adminInitializer;
    @MockitoBean EventPublicationScheduler eventScheduler;
    @MockitoBean AnnouncementPublicationScheduler announcementScheduler;
    @MockitoBean EventFeedbackScheduler feedbackScheduler;
    @MockitoBean EventReportScheduler reportScheduler;
    @MockitoBean SocietyManagementService societyManagement;
    @MockitoBean TaskService tasks;

    @BeforeEach void setup() {
        String address = "exec@event-access.test";
        when(users.loadUserByUsername(address)).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername(address)
                        .password("unused").roles("STUDENT").build());
        Student student = new Student(); student.setStudentNumber("220000001"); student.setEmail(address);
        when(students.findByEmail(address)).thenReturn(Optional.of(student));
        Executive executive = new Executive();
        executive.setId(new ExecutiveId("220000001", "SOC001", LocalDate.now().minusDays(1)));
        when(executives.findActiveExecutiveRoles(eq("220000001"), any())).thenReturn(List.of(executive));
        when(executives.findByIdStudentNumber("220000001")).thenReturn(List.of(executive));
        Society own = new Society(); own.setSocietyID("SOC001");
        when(societies.findById("SOC001")).thenReturn(Optional.of(own));
        Society other = new Society(); other.setSocietyID("SOC010"); other.setSocietyName("Other Society");
        Event event = new Event(); event.setEventID("EVT011"); event.setEventName("Published Other Society Event");
        event.setEventStatus(EventStatus.PUBLISHED); event.setAttendingType(AttendingType.EVERY_STUDENT);
        event.setEventDate(LocalDate.now().plusDays(2));
        event.setRsvpOpenDate(LocalDateTime.now().minusDays(1));
        event.setRsvpCloseDate(LocalDateTime.now().plusDays(1)); event.setEventLimit(10);
        when(events.findVisiblePublishedEvent(eq("EVT011"), any())).thenReturn(Optional.of(event));
        when(events.findByIdForUpdate("EVT011")).thenReturn(Optional.of(event));
        when(events.existsById("EVT011")).thenReturn(true);
        Hoster host = new Hoster(); host.setId(new HosterId("EVT011", "SOC010"));
        host.setSociety(other); host.setEvent(event); host.setIsPrimary(true);
        when(hosts.findByIdEventID("EVT011")).thenReturn(List.of(host));
    }

    private HttpResponse<String> request(String path, boolean post) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + jwt.generateToken("exec@event-access.test", "STUDENT"));
        if (post) builder.POST(HttpRequest.BodyPublishers.noBody());
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void studentJwtExecutiveGetsOtherSocietyPublishedDetailsOverHttp() throws Exception {
        var result = request("/api/events/EVT011", false);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("SOC010"));
        verifyNoInteractions(members);
    }
    @Test void sameStudentJwtStillGetsManagement403() throws Exception {
        var result = request("/api/executive/events/EVT011", false);
        assertEquals(403, result.statusCode(), result.body());
        assertTrue(result.body().contains("The event does not belong to your society."));
    }
    @Test void sameExecutiveCanRsvpThroughOrdinaryHttpEndpoint() throws Exception {
        var result = request("/api/events/EVT011/rsvp", true);
        assertEquals(201, result.statusCode(), result.body());
        verify(rsvps).save(any());
    }
}
