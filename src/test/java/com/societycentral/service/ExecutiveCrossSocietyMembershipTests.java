package com.societycentral.service;

import com.societycentral.controller.SocietyBrowseController;
import com.societycentral.dto.request.SubmitMembershipApplicationRequestDTO;
import com.societycentral.dto.response.MembershipState;
import com.societycentral.dto.response.StudentSocietyProfileDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.*;
import com.societycentral.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Isolated fixtures only. Membership rows are created through normal approval. */
@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:cross-membership;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,EVENT,YEAR,MONTH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExecutiveCrossSocietyMembershipTests {
    private static final String EMAIL = "executive@example.test";
    private static final String REVIEWER = "reviewer@example.test";
    private static final String NUMBER = "220088888";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-08-03T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired SDORepository sdos;
    @Autowired SocietyRepository societies;
    @Autowired ExecutiveRepository executives;
    @Autowired SocietyMemberRepository members;
    @Autowired MembershipApplicationRepository applications;
    @Autowired EntityManager entityManager;
    private MembershipApplicationService submissions;
    private ExecutiveMembershipApplicationService reviews;
    private ExecutiveSocietyResolver resolver;
    private SocietyBrowseService browse;
    private AnnouncementAudienceResolver audiences;

    @BeforeEach void setup() {
        User officer = user("sdo@example.test", UserType.SDO);
        SDO sdo = new SDO(); sdo.setStaffNumber("SDO001"); sdo.setEmail(officer.getEmail()); sdos.save(sdo);
        Society own = society("SOC001"); Society other = society("SOC005"); society("SOC006");
        Student applicant = student(NUMBER, EMAIL);
        Student reviewer = student("220099999", REVIEWER);
        executive(applicant, own); executive(reviewer, other);
        entityManager.flush();
        resolver = new ExecutiveSocietyResolver(students, executives, societies, CLOCK);
        var notifications = mock(NotificationService.class);
        var publisher = mock(ApplicationEventPublisher.class);
        submissions = new MembershipApplicationService(applications, students, societies, members,
                executives, notifications, publisher, CLOCK);
        reviews = new ExecutiveMembershipApplicationService(resolver, applications, students, members,
                mock(FundTransactionService.class), notifications, publisher, CLOCK);
        audiences = new AnnouncementAudienceResolver(students, executives, members, CLOCK);
        browse = new SocietyBrowseService(societies, students, executives, members,
                mock(SocietyMediaRepository.class), mock(EventRepository.class), mock(AnnouncementRepository.class),
                mock(TaskAllocationRepository.class), mock(UserProfilePictureRepository.class), submissions,
                new SocietyProfileEditAuthorizationService(resolver), mock(SocietyHighlightService.class), audiences, CLOCK);
    }
    private User user(String email, UserType type) {
        User user = new User(); user.setEmail(email); user.setFirstName("Test"); user.setLastName("User");
        user.setUserType(type); user.setPasswordHash("test-only"); return users.save(user);
    }
    private Student student(String number, String email) {
        User user = user(email, UserType.STUDENT);
        Student student = new Student(); student.setStudentNumber(number); student.setEmail(email); student.setUser(user);
        return students.save(student);
    }
    private Society society(String id) {
        Society society = new Society(); society.setSocietyID(id); society.setSocietyName(id);
        society.setActiveStatus(true); society.setIsFlagged(false); society.setSdoStaffNumber("SDO001");
        society.setMembershipFee(BigDecimal.ZERO); society.setCurrentBalance(new BigDecimal("9876.54"));
        return societies.save(society);
    }
    private void executive(Student student, Society society) {
        Executive role = new Executive();
        role.setId(new ExecutiveId(student.getStudentNumber(), society.getSocietyID(), TODAY.minusDays(1)));
        role.setStudent(student); role.setSociety(society); role.setPosition("President"); executives.save(role);
    }
    private String submit(String societyID) {
        SubmitMembershipApplicationRequestDTO request = new SubmitMembershipApplicationRequestDTO();
        request.setMotivation("I want to contribute as an ordinary member.");
        return submissions.submitApplication(EMAIL, societyID, request).getApplicationID();
    }
    private void approveOther() { reviews.approveApplication(REVIEWER, submit("SOC005")); }

    @Test void executiveReceivesOtherSocietyPublicProfileAndEligibility() {
        var auth = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        var result = new SocietyBrowseController(browse).getSocietyProfile("SOC005", auth).getBody().getData();
        var profile = assertInstanceOf(StudentSocietyProfileDTO.class, result);
        assertEquals("SOC005", profile.getSocietyName());
        assertEquals(MembershipState.ELIGIBLE, profile.getMembershipState());
        assertTrue(profile.isApplicationsAvailable());
        assertFalse(profile.isCanViewInternalData());
        assertFalse(profile.isCanManageProfile());
        assertFalse(browse.isActiveExecutiveOfSociety(EMAIL, "SOC005"));
        assertTrue(browse.isActiveExecutiveOfSociety(EMAIL, "SOC001"));
    }
    @Test void otherSocietyPrivateProfileDeniedIndependently() {
        assertThrows(ForbiddenOperationException.class, () -> browse.getExecutiveSocietyProfile("SOC005", EMAIL));
    }
    @Test void executiveCanSubmitOtherSocietyAndReadOwnApplication() {
        String id = submit("SOC005");
        var status = submissions.getApplicationStatus(EMAIL, id);
        assertEquals("SOC005", status.getSocietyID());
        assertEquals(MembershipApplicationStatus.PENDING, status.getStatus());
        assertEquals(NUMBER, applications.findById(id).orElseThrow().getStudentNumber());
        assertFalse(members.existsActiveMembership(NUMBER, "SOC005", TODAY));
        assertThrows(ResourceNotFoundException.class, () -> submissions.getApplicationStatus(REVIEWER, id));
    }
    @Test void duplicatePendingApplicationIsSocietySpecific() {
        submit("SOC005");
        assertEquals(MembershipState.PENDING, submissions.getEligibility(EMAIL, "SOC005").getMembershipState());
        assertThrows(IllegalStateException.class, () -> submit("SOC005"));
        assertEquals(MembershipState.ELIGIBLE, submissions.getEligibility(EMAIL, "SOC006").getMembershipState());
        assertNotNull(submit("SOC006"));
    }
    @Test void approvedMembershipBlocksOnlyTheSameSociety() {
        approveOther();
        assertEquals(MembershipState.ACTIVE_MEMBER, submissions.getEligibility(EMAIL, "SOC005").getMembershipState());
        assertThrows(IllegalStateException.class, () -> submit("SOC005"));
        assertNotNull(submit("SOC006"));
    }
    @Test void approvalPersistsMembershipAndPreservesExecutiveAndUserType() {
        approveOther(); entityManager.flush(); entityManager.clear();
        assertTrue(members.existsActiveMembership(NUMBER, "SOC005", TODAY));
        assertTrue(executives.existsActiveExecutiveRole(NUMBER, "SOC001", TODAY));
        assertFalse(executives.existsActiveExecutiveRole(NUMBER, "SOC005", TODAY));
        assertEquals(1, executives.findByIdStudentNumber(NUMBER).size());
        assertEquals(UserType.STUDENT, users.findById(EMAIL).orElseThrow().getUserType());
        assertEquals("SOC001", resolver.resolve(EMAIL).society().getSocietyID());
    }
    @Test void ordinaryMembershipsInSeveralSocietiesCoexistWithExecutiveRole() {
        approveOther();
        executive(student("220077777", "reviewer6@example.test"), societies.findById("SOC006").orElseThrow());
        reviews.approveApplication("reviewer6@example.test", submit("SOC006"));
        entityManager.flush(); entityManager.clear();
        assertTrue(members.existsActiveMembership(NUMBER, "SOC005", TODAY));
        assertTrue(members.existsActiveMembership(NUMBER, "SOC006", TODAY));
        assertTrue(executives.existsActiveExecutiveRole(NUMBER, "SOC001", TODAY));
    }
    @Test void approvalDoesNotGrantProfileEditOrPrivateData() {
        approveOther();
        assertThrows(ForbiddenOperationException.class, () -> resolver.resolve(EMAIL, "SOC005"));
        assertThrows(ForbiddenOperationException.class, () -> new SocietyProfileEditAuthorizationService(resolver)
                .requireCanEditSocietyProfile(EMAIL, "SOC005"));
        assertThrows(ForbiddenOperationException.class, () -> browse.getExecutiveSocietyProfile("SOC005", EMAIL));
    }
    @Test void applicantExecutiveCannotReviewOtherSocietyApplication() {
        String id = submit("SOC005");
        assertThrows(ForbiddenOperationException.class, () -> reviews.getApplication(EMAIL, id));
        assertThrows(ForbiddenOperationException.class, () -> reviews.approveApplication(EMAIL, id));
        assertEquals(MembershipApplicationStatus.PENDING, applications.findById(id).orElseThrow().getStatus());
    }
    @Test void executiveCannotRemoveTheOtherSocietyMembership() {
        approveOther();
        var memberService = new SocietyMemberService(members, resolver, CLOCK, mock(UserProfilePictureRepository.class));
        assertThrows(IllegalArgumentException.class, () -> memberService.removeMemberForExecutive(EMAIL, NUMBER));
        assertTrue(members.existsActiveMembership(NUMBER, "SOC005", TODAY));
    }
    @Test void approvedMemberGetsMemberAnnouncementsButNotOtherSocietyExecutiveAudience() {
        approveOther();
        assertEquals(List.of(TargetType.STUDENTS, TargetType.MEMBERS), audiences.resolveForEmail(EMAIL, "SOC005"));
        assertEquals(List.of(TargetType.STUDENTS, TargetType.MEMBERS, TargetType.EXECUTIVES), audiences.resolveForEmail(EMAIL, "SOC001"));
    }
}
