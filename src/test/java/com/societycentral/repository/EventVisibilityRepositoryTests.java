package com.societycentral.repository;

import com.societycentral.model.AttendingType;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyMember;
import com.societycentral.model.SocietyMemberId;
import com.societycentral.model.Student;
import com.societycentral.model.User;
import com.societycentral.model.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:event-visibility-tests;"
                + "MODE=MSSQLServer;DB_CLOSE_DELAY=-1;"
                + "NON_KEYWORDS=USER,EVENT,YEAR,MONTH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventVisibilityRepositoryTests {

    private static final String MEMBER_SOCIETY_ID = "SOC001";
    private static final String OTHER_SOCIETY_ID = "SOC002";
    private static final String STUDENT_NUMBER = "220000001";
    private static final LocalDate CURRENT_DATE =
            LocalDate.of(2030, 8, 3);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SDORepository sdoRepository;
    @Autowired
    private SocietyRepository societyRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private SocietyMemberRepository societyMemberRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private HosterRepository hosterRepository;

    @BeforeEach
    void seedEvents() {
        User sdoUser = new User();
        sdoUser.setEmail("sdo@nmu.ac.za");
        sdoUser.setFirstName("Sam");
        sdoUser.setLastName("Officer");
        sdoUser.setUserType(UserType.SDO);
        sdoUser.setPasswordHash("hash");
        userRepository.save(sdoUser);

        User studentUser = new User();
        studentUser.setEmail("student@nmu.ac.za");
        studentUser.setFirstName("Stu");
        studentUser.setLastName("Dent");
        studentUser.setUserType(UserType.STUDENT);
        studentUser.setPasswordHash("hash");
        userRepository.save(studentUser);

        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(studentUser.getEmail());
        studentRepository.save(student);

        SDO sdo = new SDO();
        sdo.setStaffNumber("SDO001");
        sdo.setEmail(sdoUser.getEmail());
        sdoRepository.save(sdo);

        Society memberSociety = society(
                MEMBER_SOCIETY_ID,
                "Computing Society"
        );
        Society otherSociety = society(
                OTHER_SOCIETY_ID,
                "Robotics Society"
        );
        societyRepository.saveAll(List.of(
                memberSociety,
                otherSociety
        ));

        SocietyMember activeMembership = membership(
                student,
                memberSociety,
                CURRENT_DATE);
        SocietyMember expiredMembership = membership(
                student,
                otherSociety,
                CURRENT_DATE.minusDays(1));
        societyMemberRepository.saveAll(List.of(
                activeMembership,
                expiredMembership));

        Event memberEvent = event(
                "EVT_MEMBER",
                EventStatus.PUBLISHED,
                AttendingType.MEMBERS
        );
        Event openEvent = event(
                "EVT_OPEN",
                EventStatus.PUBLISHED,
                AttendingType.EVERY_STUDENT
        );
        Event privateEvent = event(
                "EVT_PRIVATE",
                EventStatus.PUBLISHED,
                AttendingType.MEMBERS
        );
        Event draftEvent = event(
                "EVT_DRAFT",
                EventStatus.DRAFT,
                AttendingType.EVERY_STUDENT
        );
        Event rejectedEvent = event(
                "EVT_REJECTED",
                EventStatus.REJECTED,
                AttendingType.EVERY_STUDENT
        );
        Event withdrawnEvent = event(
                "EVT_WITHDRAWN",
                EventStatus.WITHDRAWN,
                AttendingType.EVERY_STUDENT
        );
        Event proposedEvent = event(
                "EVT_PROPOSED",
                EventStatus.PROPOSED,
                AttendingType.EVERY_STUDENT
        );
        Event preOpenEvent = event(
                "EVT_PREOPEN",
                EventStatus.PUBLISHED,
                AttendingType.EVERY_STUDENT
        );
        preOpenEvent.setRsvpOpenDate(LocalDateTime.of(2030, 8, 3, 10, 30));

        memberEvent.setEventDate(LocalDate.of(2030, 8, 11));
        openEvent.setEventStartTime(LocalTime.of(12, 0));
        privateEvent.setEventStartTime(LocalTime.of(9, 0));

        eventRepository.saveAll(List.of(
                memberEvent,
                openEvent,
                privateEvent,
                draftEvent,
                rejectedEvent,
                withdrawnEvent,
                proposedEvent,
                preOpenEvent
        ));

        hosterRepository.saveAll(List.of(
                hoster(memberEvent, memberSociety),
                hoster(openEvent, otherSociety),
                hoster(privateEvent, otherSociety),
                hoster(draftEvent, memberSociety),
                hoster(rejectedEvent, memberSociety),
                hoster(withdrawnEvent, memberSociety),
                hoster(proposedEvent, memberSociety)
                ,hoster(preOpenEvent, otherSociety)
        ));
        hosterRepository.flush();
    }

    @Test
    void studentDetailMatchesMembershipAndEveryStudentRules() {
        assertTrue(eventRepository.findVisibleEventForStudent(
                "EVT_MEMBER",
                List.of(MEMBER_SOCIETY_ID)).isPresent());
        assertTrue(eventRepository.findVisibleEventForStudent(
                "EVT_OPEN",
                List.of()).isPresent());
        assertFalse(eventRepository.findVisibleEventForStudent(
                "EVT_PRIVATE",
                List.of(MEMBER_SOCIETY_ID)).isPresent());
    }

    @Test
    void publishedEventRemainsHiddenUntilRsvpOpeningBoundary() {
        assertTrue(eventRepository.findVisibleEventsForStudent(
                List.of(), LocalDateTime.of(2030, 8, 3, 10, 29)).stream()
                .noneMatch(event -> "EVT_PREOPEN".equals(event.getEventID())));
        assertTrue(eventRepository.findVisibleEventsForStudent(
                List.of(), LocalDateTime.of(2030, 8, 3, 10, 30)).stream()
                .anyMatch(event -> "EVT_PREOPEN".equals(event.getEventID())));
    }

    @Test
    void activeMembershipSocietyIDsExcludeExpiredRows() {
        assertEquals(
                List.of(MEMBER_SOCIETY_ID),
                societyMemberRepository.findActiveSocietyIDsForStudent(
                        STUDENT_NUMBER,
                        CURRENT_DATE));
    }

    @Test
    void studentDetailNeverReturnsAnUnpublishedEvent() {
        assertFalse(eventRepository.findVisibleEventForStudent(
                "EVT_DRAFT",
                List.of(MEMBER_SOCIETY_ID)).isPresent());
    }

    @Test
    void unrestrictedListReturnsEveryPublishedEventChronologically() {
        List<Event> publishedEvents =
                eventRepository.findAllVisiblePublishedEvents(
                        LocalDateTime.of(2030, 8, 3, 10, 30));

        assertEquals(
                List.of("EVT_PRIVATE", "EVT_PREOPEN", "EVT_OPEN", "EVT_MEMBER"),
                publishedEvents.stream()
                        .map(Event::getEventID)
                        .toList()
        );
        assertTrue(publishedEvents.stream()
                .allMatch(event ->
                        event.getEventStatus() == EventStatus.PUBLISHED));
    }

    @Test
    void unrestrictedDetailReturnsAnyPublishedEvent() {
        assertTrue(eventRepository.findVisiblePublishedEvent(
                "EVT_PRIVATE", LocalDateTime.of(2030, 8, 3, 10, 30)).isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "EVT_DRAFT",
            "EVT_REJECTED",
            "EVT_WITHDRAWN",
            "EVT_PROPOSED"
    })
    void unrestrictedDetailNeverReturnsUnpublishedEvents(
            String eventID) {
        assertFalse(eventRepository.findPublishedEvent(eventID).isPresent());
    }

    private Society society(
            String societyID,
            String societyName) {
        Society society = new Society();
        society.setSocietyID(societyID);
        society.setSocietyName(societyName);
        society.setSdoStaffNumber("SDO001");
        society.setActiveStatus(true);
        society.setIsFlagged(false);
        return society;
    }

    @SuppressWarnings("deprecation")
    private Event event(
            String eventID,
            EventStatus status,
            AttendingType attendingType) {
        Event event = new Event();
        event.setEventID(eventID);
        event.setEventName(eventID);
        event.setEventDescription("Complete event description.");
        event.setEventDate(LocalDate.of(2030, 8, 10));
        event.setEventStartTime(LocalTime.of(10, 0));
        event.setEventEndTime(LocalTime.of(12, 0));
        event.setEventTime(LocalTime.of(10, 0));
        event.setEventStatus(status);
        event.setAttendingType(attendingType);
        event.setRsvpOpenDate(LocalDateTime.of(2026, 8, 3, 9, 0));
        event.setRsvpCloseDate(LocalDateTime.of(2030, 8, 9, 8, 0));
        event.setCreatedAt(LocalDateTime.of(2026, 8, 3, 9, 0));
        event.setUpdatedAt(LocalDateTime.of(2026, 8, 3, 9, 0));
        return event;
    }

    private Hoster hoster(
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

    private SocietyMember membership(
            Student student,
            Society society,
            LocalDate expireDate) {
        SocietyMember membership = new SocietyMember();
        membership.setId(new SocietyMemberId(
                student.getStudentNumber(),
                society.getSocietyID()));
        membership.setStudent(student);
        membership.setSociety(society);
        membership.setJoinDate(CURRENT_DATE.minusYears(1));
        membership.setExpireDate(expireDate);
        return membership;
    }
}
