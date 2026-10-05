package com.societycentral.repository;

import com.societycentral.model.Announcement;
import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.Hoster;
import com.societycentral.model.HosterId;
import com.societycentral.model.SDO;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyImageType;
import com.societycentral.model.SocietyHighlight;
import com.societycentral.model.SocietyMedia;
import com.societycentral.model.SocietyMember;
import com.societycentral.model.SocietyMemberId;
import com.societycentral.model.Student;
import com.societycentral.model.TargetType;
import com.societycentral.model.Task;
import com.societycentral.model.TaskAllocation;
import com.societycentral.model.TaskAllocationId;
import com.societycentral.model.TaskStatus;
import com.societycentral.model.TaskTargetType;
import com.societycentral.model.User;
import com.societycentral.model.UserType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:society-profile-tests;"
                + "MODE=MSSQLServer;DB_CLOSE_DELAY=-1;"
                + "NON_KEYWORDS=USER,EVENT,YEAR,MONTH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SocietyProfileRepositoryTests {

    private static final String SOCIETY_ID = "SOC001";
    private static final String SDO_EMAIL = "sdo@nmu.ac.za";
    private static final LocalDate TODAY = LocalDate.of(2030, 8, 11);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2030, 8, 11, 8, 30);

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
    private SocietyMemberRepository societyMemberRepository;
    @Autowired
    private SocietyMediaRepository societyMediaRepository;
    @Autowired
    private SocietyHighlightRepository societyHighlightRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private HosterRepository hosterRepository;
    @Autowired
    private AnnouncementRepository announcementRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskAllocationRepository taskAllocationRepository;
    @Autowired
    private EntityManager entityManager;

    private Society society;
    private Student activeExecutiveStudent;

    @BeforeEach
    void seedProfileData() {
        User sdoUser = user(SDO_EMAIL, UserType.SDO, "Sam", "Officer");
        User executiveUser = user(
                "executive@nmu.ac.za",
                UserType.STUDENT,
                "Alex",
                "Smith");
        User expiredExecutiveUser = user(
                "former@nmu.ac.za",
                UserType.STUDENT,
                "Former",
                "Leader");
        User futureExecutiveUser = user(
                "future@nmu.ac.za",
                UserType.STUDENT,
                "Future",
                "Leader");
        User currentMemberUser = user(
                "member@nmu.ac.za",
                UserType.STUDENT,
                "Current",
                "Member");
        User expiredMemberUser = user(
                "expired@nmu.ac.za",
                UserType.STUDENT,
                "Expired",
                "Member");
        userRepository.saveAll(List.of(
                sdoUser,
                executiveUser,
                expiredExecutiveUser,
                futureExecutiveUser,
                currentMemberUser,
                expiredMemberUser));

        SDO sdo = new SDO();
        sdo.setStaffNumber("SDO001");
        sdo.setEmail(SDO_EMAIL);
        sdoRepository.save(sdo);

        society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Computing Society");
        society.setSdoStaffNumber("SDO001");
        society.setActiveStatus(true);
        societyRepository.save(society);

        activeExecutiveStudent = student(
                "220000001", executiveUser.getEmail());
        Student expiredExecutiveStudent = student(
                "220000002", expiredExecutiveUser.getEmail());
        Student futureExecutiveStudent = student(
                "220000005", futureExecutiveUser.getEmail());
        Student currentMember = student(
                "220000003", currentMemberUser.getEmail());
        Student expiredMember = student(
                "220000004", expiredMemberUser.getEmail());
        studentRepository.saveAll(List.of(
                activeExecutiveStudent,
                expiredExecutiveStudent,
                futureExecutiveStudent,
                currentMember,
                expiredMember));

        executiveRepository.saveAll(List.of(
                executive(
                        activeExecutiveStudent,
                        TODAY.minusMonths(1),
                        null,
                        "President"),
                executive(
                        expiredExecutiveStudent,
                        TODAY.minusYears(1),
                        TODAY.minusDays(1),
                        "Former President"),
                executive(
                        futureExecutiveStudent,
                        TODAY.plusMonths(1),
                        null,
                        "President Elect")));

        societyMemberRepository.saveAll(List.of(
                membership(currentMember, null),
                membership(expiredMember, TODAY.minusDays(1))));

        Event later = event(
                "EVT_LATER",
                EventStatus.PUBLISHED,
                TODAY.plusDays(2),
                LocalTime.of(10, 0));
        Event earlier = event(
                "EVT_EARLIER",
                EventStatus.PUBLISHED,
                TODAY,
                LocalTime.of(9, 0));
        Event draft = event(
                "EVT_DRAFT",
                EventStatus.DRAFT,
                TODAY.plusDays(1),
                LocalTime.of(8, 0));
        Event past = event(
                "EVT_PAST",
                EventStatus.PUBLISHED,
                TODAY.minusDays(1),
                LocalTime.of(8, 0));
        Event endedToday = event(
                "EVT_ENDED_TODAY",
                EventStatus.PUBLISHED,
                TODAY,
                LocalTime.of(6, 0));
        eventRepository.saveAll(List.of(
                later, earlier, draft, past, endedToday));
        hosterRepository.saveAll(List.of(
                hoster(later),
                hoster(earlier),
                hoster(draft),
                hoster(past),
                hoster(endedToday)));

        announcementRepository.saveAll(List.of(
                announcement(
                        "ANN_SPECIFIC",
                        TargetType.STUDENTS,
                        society,
                        SDO_EMAIL,
                        NOW.minusHours(1),
                        NOW.plusDays(1)),
                announcement(
                        "ANN_GLOBAL",
                        TargetType.STUDENTS,
                        null,
                        SDO_EMAIL,
                        NOW.minusHours(2),
                        NOW.plusDays(1)),
                announcement(
                        "ANN_MEMBERS",
                        TargetType.MEMBERS,
                        society,
                        SDO_EMAIL,
                        NOW,
                        NOW.plusDays(1)),
                announcement(
                        "ANN_EXPIRED",
                        TargetType.STUDENTS,
                        society,
                        SDO_EMAIL,
                        NOW.plusHours(1),
                        NOW.minusMinutes(1))));

        Task pending = task("TSK_PENDING", TaskStatus.PENDING,
                TODAY.plusDays(3));
        Task overdue = task("TSK_OVERDUE", TaskStatus.PENDING,
                TODAY.minusDays(1));
        Task complete = task("TSK_COMPLETE", TaskStatus.COMPLETE,
                TODAY.plusDays(3));
        taskRepository.saveAll(List.of(pending, overdue, complete));
        taskAllocationRepository.saveAll(List.of(
                allocation(pending),
                allocation(overdue),
                allocation(complete)));
        taskAllocationRepository.flush();
        entityManager.clear();
    }

    @Test
    void activeExecutiveQueryUsesTermEndRuleAndFetchesPublicIdentity() {
        List<Executive> executives = executiveRepository
                .findActiveExecutivesForSocietyProfile(
                        SOCIETY_ID, TODAY);

        assertEquals(1, executives.size());
        assertEquals("Alex",
                executives.getFirst().getStudent().getUser().getFirstName());
        assertTrue(executiveRepository.existsActiveExecutiveRole(
                activeExecutiveStudent.getStudentNumber(),
                SOCIETY_ID,
                TODAY));
        assertFalse(executiveRepository.existsActiveExecutiveRole(
                "220000005",
                SOCIETY_ID,
                TODAY));
        assertFalse(executiveRepository.existsActiveExecutiveRole(
                "220000002",
                SOCIETY_ID,
                TODAY));
        assertEquals(1, executiveRepository.findActiveExecutiveRoles(
                activeExecutiveStudent.getStudentNumber(), TODAY).size());
        assertTrue(executiveRepository.findActiveExecutiveRoles(
                "220000005", TODAY).isEmpty());
    }

    @Test
    void memberCountUsesOnlyCurrentApprovedRows() {
        assertEquals(1L,
                societyMemberRepository.countCurrentMembersBySocietyID(
                        SOCIETY_ID, TODAY));
        assertFalse(societyMemberRepository.existsActiveMembership(
                "220000004", SOCIETY_ID, TODAY));
    }

    @Test
    void eventPreviewContainsOnlyUpcomingPublishedEventsInOrder() {
        List<Event> events = eventRepository
                .findUpcomingPublishedEventsForSocietyProfile(
                        SOCIETY_ID,
                        TODAY,
                        NOW.toLocalTime(),
                        PageRequest.of(0, 5));

        assertEquals(
                List.of("EVT_EARLIER", "EVT_LATER"),
                events.stream().map(Event::getEventID).toList());
        assertTrue(events.stream().allMatch(event ->
                event.getEventStatus() == EventStatus.PUBLISHED));
        assertFalse(events.stream().anyMatch(event ->
                event.getEventDate().isBefore(TODAY)));
    }

    @Test
    void announcementPreviewContainsOnlyActiveStudentAnnouncements() {
        List<Announcement> announcements = announcementRepository
                .findProfileAnnouncements(
                        SOCIETY_ID,
                        "SDO001",
                        TargetType.STUDENTS,
                        NOW,
                        PageRequest.of(0, 3));

        assertEquals(
                List.of("ANN_SPECIFIC"),
                announcements.stream()
                        .map(Announcement::getAnnouncementID)
                        .toList());
    }

    @Test
    void futureMembershipIsNotCurrent() {
        User futureMemberUser = user(
                "future-member@nmu.ac.za", UserType.STUDENT, "Future", "Member");
        userRepository.save(futureMemberUser);
        Student futureMember = student("220000099", futureMemberUser.getEmail());
        studentRepository.save(futureMember);
        SocietyMember membership = membership(futureMember, TODAY.plusMonths(2));
        membership.setJoinDate(TODAY.plusDays(1));
        societyMemberRepository.saveAndFlush(membership);

        assertFalse(societyMemberRepository.existsActiveMembership(
                futureMember.getStudentNumber(), SOCIETY_ID, TODAY));
        assertFalse(societyMemberRepository.findActiveSocietyIDsForStudent(
                futureMember.getStudentNumber(), TODAY).contains(SOCIETY_ID));
    }

    @Test
    void announcementVisibilityEnforcesLifecycleAudienceAndSocietyScope() {
        Society otherSociety = new Society();
        otherSociety.setSocietyID("SOC010");
        otherSociety.setSocietyName("Other Society");
        otherSociety.setSdoStaffNumber("SDO001");
        otherSociety.setActiveStatus(true);
        societyRepository.save(otherSociety);

        Announcement valid = announcement("ANN_VALID", TargetType.EXECUTIVES,
                society, SDO_EMAIL, NOW.minusMinutes(5), NOW.plusHours(1));
        valid.setPublishAt(NOW.minusMinutes(10));
        Announcement future = announcement("ANN_FUTURE", TargetType.EXECUTIVES,
                society, SDO_EMAIL, NOW, NOW.plusHours(2));
        future.setPublishAt(NOW.plusMinutes(1));
        Announcement expired = announcement("ANN_PAST", TargetType.EXECUTIVES,
                society, SDO_EMAIL, NOW.minusHours(2), NOW);
        expired.setPublishAt(NOW.minusHours(2));
        Announcement removed = announcement("ANN_REMOVED", TargetType.EXECUTIVES,
                society, SDO_EMAIL, NOW.minusHours(1), NOW.plusHours(1));
        removed.setPublishAt(NOW.minusHours(1));
        removed.setRemoved(true);
        Announcement other = announcement("ANN_OTHER", TargetType.EXECUTIVES,
                otherSociety, SDO_EMAIL, NOW.minusHours(1), NOW.plusHours(1));
        other.setPublishAt(NOW.minusHours(1));
        announcementRepository.saveAllAndFlush(
                List.of(valid, future, expired, removed, other));
        entityManager.clear();

        List<String> visibleIDs = announcementRepository.findProfileAnnouncements(
                        SOCIETY_ID,
                        List.of(TargetType.EXECUTIVES),
                        NOW,
                        PageRequest.of(0, 20)).stream()
                .map(Announcement::getAnnouncementID)
                .toList();

        assertEquals(List.of("ANN_VALID"), visibleIDs);
        assertTrue(announcementRepository.findAll().stream()
                .anyMatch(item -> item.getAnnouncementID().equals("ANN_REMOVED")
                        && item.isRemoved()));
    }

    @Test
    void pendingTaskCountReusesExistingFutureIncompleteRule() {
        assertEquals(1L,
                taskAllocationRepository.countPendingTasksForSociety(
                        SOCIETY_ID,
                        TaskStatus.COMPLETE,
                TODAY));
    }

    @Test
    void galleryQueryReturnsOnlyRequestedSocietyInDisplayOrder() {
        SocietyMedia second = galleryMedia(
                "MEDIA002", SOCIETY_ID, 2, "Second");
        SocietyMedia first = galleryMedia(
                "MEDIA001", SOCIETY_ID, 0, "First");
        societyMediaRepository.saveAll(List.of(second, first));
        societyMediaRepository.flush();
        entityManager.clear();

        List<SocietyMedia> result = societyMediaRepository
                .findBySocietyIDAndMediaTypeOrderBySortOrderAscUploadedAtAscMediaIDAsc(
                        SOCIETY_ID,
                        SocietyImageType.GALLERY_IMAGE);

        assertEquals(List.of("MEDIA001", "MEDIA002"),
                result.stream().map(SocietyMedia::getMediaID).toList());
        assertEquals(2,
                societyMediaRepository.findMaximumSortOrder(
                        SOCIETY_ID,
                SocietyImageType.GALLERY_IMAGE));
    }

    @Test
    void highlightsPersistArticleParagraphsAndHideDraftsFromPublicQuery() {
        SocietyMedia publishedCover = galleryMedia(
                "COVER001", SOCIETY_ID, 0, null);
        publishedCover.setMediaType(SocietyImageType.HIGHLIGHT_COVER);
        publishedCover.setMediaUrl(
                "/media/societies/highlights/published.png");
        SocietyMedia draftCover = galleryMedia(
                "COVER002", SOCIETY_ID, 1, null);
        draftCover.setMediaType(SocietyImageType.HIGHLIGHT_COVER);
        draftCover.setMediaUrl(
                "/media/societies/highlights/draft.png");
        societyMediaRepository.saveAll(List.of(
                publishedCover, draftCover));

        SocietyHighlight published = highlight(
                "HIGHLIGHT001", publishedCover, 0, true);
        SocietyHighlight draft = highlight(
                "HIGHLIGHT002", draftCover, 1, false);
        societyHighlightRepository.saveAll(List.of(draft, published));
        societyHighlightRepository.flush();
        entityManager.clear();

        List<SocietyHighlight> publicHighlights = societyHighlightRepository
                .findBySocietyIDAndActiveStatusTrueOrderBySortOrderAscPublishedAtDescHighlightIDAsc(
                        SOCIETY_ID);
        List<SocietyHighlight> manageable = societyHighlightRepository
                .findBySocietyIDOrderBySortOrderAscCreatedAtAscHighlightIDAsc(
                        SOCIETY_ID);

        assertEquals(List.of("HIGHLIGHT001"), publicHighlights.stream()
                .map(SocietyHighlight::getHighlightID)
                .toList());
        assertEquals("Opening paragraph.\n\nClosing paragraph.",
                publicHighlights.getFirst().getArticle());
        assertEquals(List.of("HIGHLIGHT001", "HIGHLIGHT002"),
                manageable.stream()
                        .map(SocietyHighlight::getHighlightID)
                        .toList());
        assertEquals("/media/societies/highlights/published.png",
                publicHighlights.getFirst()
                        .getCoverMedia().getMediaUrl());
    }

    private User user(
            String email,
            UserType userType,
            String firstName,
            String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setUserType(userType);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash("hash");
        return user;
    }

    private Student student(String studentNumber, String email) {
        Student student = new Student();
        student.setStudentNumber(studentNumber);
        student.setEmail(email);
        return student;
    }

    private Executive executive(
            Student student,
            LocalDate termStartDate,
            LocalDate termEndDate,
            String position) {
        Executive executive = new Executive();
        executive.setId(new ExecutiveId(
                student.getStudentNumber(),
                SOCIETY_ID,
                termStartDate));
        executive.setStudent(student);
        executive.setSociety(society);
        executive.setTermEndDate(termEndDate);
        executive.setPosition(position);
        return executive;
    }

    private SocietyMember membership(
            Student student,
            LocalDate expireDate) {
        SocietyMember membership = new SocietyMember();
        membership.setId(new SocietyMemberId(
                student.getStudentNumber(), SOCIETY_ID));
        membership.setStudent(student);
        membership.setSociety(society);
        membership.setJoinDate(TODAY.minusMonths(1));
        membership.setExpireDate(expireDate);
        return membership;
    }

    private SocietyMedia galleryMedia(
            String mediaID,
            String societyID,
            int sortOrder,
            String caption) {
        SocietyMedia media = new SocietyMedia();
        media.setMediaID(mediaID);
        media.setSocietyID(societyID);
        media.setMediaUrl(
                "/media/societies/gallery/" + mediaID + ".png");
        media.setMediaType(SocietyImageType.GALLERY_IMAGE);
        media.setCaption(caption);
        media.setSortOrder(sortOrder);
        media.setUploadedAt(NOW.plusMinutes(sortOrder));
        media.setUploadedBy("executive@nmu.ac.za");
        return media;
    }

    private SocietyHighlight highlight(
            String highlightID,
            SocietyMedia cover,
            int sortOrder,
            boolean active) {
        SocietyHighlight highlight = new SocietyHighlight();
        highlight.setHighlightID(highlightID);
        highlight.setSocietyID(SOCIETY_ID);
        highlight.setHeadline("Annual Hackathon " + highlightID);
        highlight.setCaption("Students built practical campus solutions.");
        highlight.setArticle(
                "Opening paragraph.\n\nClosing paragraph.");
        highlight.setCoverMediaID(cover.getMediaID());
        highlight.setCategory("Technology");
        highlight.setSortOrder(sortOrder);
        highlight.setPublishedAt(active ? NOW : null);
        highlight.setActiveStatus(active);
        highlight.setCreatedBy("executive@nmu.ac.za");
        highlight.setCreatedAt(NOW.plusMinutes(sortOrder));
        highlight.setUpdatedAt(NOW.plusMinutes(sortOrder));
        return highlight;
    }

    @SuppressWarnings("deprecation")
    private Event event(
            String eventID,
            EventStatus status,
            LocalDate date,
            LocalTime startTime) {
        Event event = new Event();
        event.setEventID(eventID);
        event.setEventName(eventID);
        event.setEventDate(date);
        event.setEventStartTime(startTime);
        event.setEventTime(startTime);
        event.setEventEndTime(startTime.plusHours(1));
        event.setEventStatus(status);
        event.setCreatedAt(NOW.minusDays(1));
        event.setUpdatedAt(NOW.minusDays(1));
        return event;
    }

    private Hoster hoster(Event event) {
        Hoster hoster = new Hoster();
        hoster.setId(new HosterId(event.getEventID(), SOCIETY_ID));
        hoster.setEvent(event);
        hoster.setSociety(society);
        hoster.setIsPrimary(true);
        return hoster;
    }

    private Announcement announcement(
            String announcementID,
            TargetType targetType,
            Society targetSociety,
            String sentBy,
            LocalDateTime datePosted,
            LocalDateTime expireDate) {
        Announcement announcement = new Announcement();
        announcement.setAnnouncementID(announcementID);
        announcement.setTargetType(targetType);
        announcement.setSociety(targetSociety);
        announcement.setSentBy(sentBy);
        announcement.setSubject(announcementID);
        announcement.setDatePosted(datePosted);
        announcement.setExpireDate(expireDate);
        return announcement;
    }

    private Task task(
            String taskID,
            TaskStatus status,
            LocalDate dueDate) {
        Task task = new Task();
        task.setTaskID(taskID);
        task.setTaskName(taskID);
        task.setIssueDate(TODAY.minusDays(1));
        task.setDueDate(dueDate);
        task.setStatus(status);
        task.setAssignedBy(SDO_EMAIL);
        task.setTargetType(TaskTargetType.SOCIETY);
        return task;
    }

    private TaskAllocation allocation(Task task) {
        TaskAllocationId id = new TaskAllocationId();
        id.setTaskID(task.getTaskID());
        id.setSocietyID(SOCIETY_ID);
        TaskAllocation allocation = new TaskAllocation();
        allocation.setId(id);
        allocation.setTask(task);
        allocation.setSociety(society);
        return allocation;
    }
}
