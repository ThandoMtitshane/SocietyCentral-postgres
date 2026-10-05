package com.societycentral.service;

import com.societycentral.config.ClockConfig;
import com.societycentral.mapper.EventMapper;
import com.societycentral.model.BudgetRequest;
import com.societycentral.model.BudgetRequestStatus;
import com.societycentral.model.BudgetRequestType;
import com.societycentral.model.Event;
import com.societycentral.model.EventFeedback;
import com.societycentral.model.EventOutcome;
import com.societycentral.model.EventOutcomeId;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.model.RSVP;
import com.societycentral.model.RsvpId;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.model.User;
import com.societycentral.model.UserType;
import com.societycentral.repository.BudgetRequestRepository;
import com.societycentral.repository.EventFeedbackRepository;
import com.societycentral.repository.EventOutcomeRepository;
import com.societycentral.repository.EventRepository;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.HosterRepository;
import com.societycentral.repository.RSVPRepository;
import com.societycentral.repository.SDORepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.UserRepository;
import com.societycentral.security.JwtUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:event-draft-deletion-tests;"
                + "MODE=MSSQLServer;DB_CLOSE_DELAY=-1;"
                + "NON_KEYWORDS=USER,EVENT,YEAR,MONTH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect="
                + "org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import({
        EventService.class,
        EventMapper.class,
        ClockConfig.class
})
class EventDraftDeletionPersistenceTests {

    private static final String EVENT_ID = "EVT_DELETE";
    private static final String SOCIETY_ID = "SOC_DELETE";
    private static final String EXECUTIVE_EMAIL =
            "delete.executive@nmu.ac.za";
    private static final String STUDENT_NUMBER = "229999999";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SDORepository sdoRepository;
    @Autowired
    private SocietyRepository societyRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private ExecutiveRepository executiveRepository;
    @Autowired
    private BudgetRequestRepository budgetRequestRepository;
    @Autowired
    private EventFeedbackRepository eventFeedbackRepository;
    @Autowired
    private EventOutcomeRepository eventOutcomeRepository;
    @Autowired
    private RSVPRepository rsvpRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private HosterRepository hosterRepository;
    @Autowired
    private EventService eventService;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void seedOwnedDraft() {
        User sdoUser = user(
                "delete.sdo@nmu.ac.za",
                UserType.SDO
        );
        User executiveUser = user(
                EXECUTIVE_EMAIL,
                UserType.STUDENT
        );
        userRepository.save(sdoUser);
        userRepository.save(executiveUser);

        SDO sdo = new SDO();
        sdo.setStaffNumber("SDO_DELETE");
        sdo.setEmail(sdoUser.getEmail());
        sdoRepository.save(sdo);

        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Deletion Test Society");
        society.setSdoStaffNumber(sdo.getStaffNumber());
        society.setActiveStatus(true);
        society.setIsFlagged(false);
        societyRepository.save(society);

        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(EXECUTIVE_EMAIL);
        studentRepository.save(student);

        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                STUDENT_NUMBER,
                SOCIETY_ID,
                LocalDate.now().minusMonths(1)
        ));
        executive.setStudent(student);
        executive.setSociety(society);
        executive.setTermEndDate(null);
        executiveRepository.save(executive);

        Event draft = new Event();
        draft.setEventID(EVENT_ID);
        draft.setEventName("Persistent Draft");
        draft.setEventStatus(EventStatus.DRAFT);
        eventRepository.save(draft);

        Hoster hoster = new Hoster();
        hoster.setId(new HosterId(
                EVENT_ID,
                SOCIETY_ID
        ));
        hoster.setEvent(draft);
        hoster.setSociety(society);
        hoster.setIsPrimary(true);
        hosterRepository.save(hoster);

        BudgetRequest budgetRequest = new BudgetRequest();
        budgetRequest.setBudgetRequestID("BR_DELETE");
        budgetRequest.setEventID(EVENT_ID);
        budgetRequest.setSocietyID(SOCIETY_ID);
        budgetRequest.setRequestingStudentNumber(
                STUDENT_NUMBER
        );
        budgetRequest.setName("Draft venue quote");
        budgetRequest.setAmount(
                BigDecimal.valueOf(500)
        );
        budgetRequest.setType(
                BudgetRequestType.VENUE_AND_EQUIPMENT
        );
        budgetRequest.setStatus(
                BudgetRequestStatus.PENDING
        );
        budgetRequestRepository.save(budgetRequest);

        RSVP rsvp = new RSVP();
        rsvp.setId(new RsvpId(
                EVENT_ID,
                STUDENT_NUMBER
        ));
        rsvp.setEvent(draft);
        rsvp.setStudent(student);
        rsvpRepository.save(rsvp);

        EventFeedback feedback = new EventFeedback();
        feedback.setFeedbackID("FDB_DELETE");
        feedback.setStudentNumber(STUDENT_NUMBER);
        feedback.setEventID(EVENT_ID);
        feedback.setRating(5);
        eventFeedbackRepository.save(feedback);

        EventOutcome outcome = new EventOutcome();
        outcome.setId(new EventOutcomeId(
                EVENT_ID,
                SOCIETY_ID
        ));
        outcome.setStudentNumber(STUDENT_NUMBER);
        outcome.setTermStartDate(
                executive.getId().getTermStartDate()
        );
        outcome.setDescription("Legacy draft child row");
        eventOutcomeRepository.save(outcome);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void deletionRemovesTheDraftAndItsHosterRowsFromTheDatabase() {
        assertTrue(eventRepository.existsById(EVENT_ID));
        assertFalse(
                hosterRepository
                        .findByIdEventID(EVENT_ID)
                        .isEmpty()
        );
        assertFalse(
                budgetRequestRepository
                        .findByEventID(EVENT_ID)
                        .isEmpty()
        );
        assertFalse(
                rsvpRepository
                        .findByIdEventID(EVENT_ID)
                        .isEmpty()
        );
        assertFalse(
                eventFeedbackRepository
                        .findByEventID(EVENT_ID)
                        .isEmpty()
        );
        assertFalse(
                eventOutcomeRepository
                        .findByIdEventID(EVENT_ID)
                        .isEmpty()
        );

        /*
         * Begin the service call with a clean persistence context, matching a
         * normal request transaction rather than retaining assertion-loaded
         * child entities across the bulk-delete queries.
         */
        entityManager.clear();

        eventService.deleteDraftEvent(
                EVENT_ID,
                EXECUTIVE_EMAIL
        );

        entityManager.flush();
        entityManager.clear();

        assertFalse(eventRepository.existsById(EVENT_ID));
        assertTrue(
                hosterRepository
                        .findByIdEventID(EVENT_ID)
                        .isEmpty()
        );
        assertTrue(
                budgetRequestRepository
                        .findByEventID(EVENT_ID)
                        .isEmpty()
        );
        assertTrue(
                rsvpRepository
                        .findByIdEventID(EVENT_ID)
                        .isEmpty()
        );
        assertTrue(
                eventFeedbackRepository
                        .findByEventID(EVENT_ID)
                        .isEmpty()
        );
        assertTrue(
                eventOutcomeRepository
                        .findByIdEventID(EVENT_ID)
                        .isEmpty()
        );
    }

    private User user(
            String email,
            UserType type) {

        User user = new User();
        user.setEmail(email);
        user.setFirstName("Deletion");
        user.setLastName("Tester");
        user.setUserType(type);
        user.setPasswordHash("hash");
        return user;
    }
}
