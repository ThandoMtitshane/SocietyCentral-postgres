package com.societycentral.service;

import com.societycentral.dto.request.RejectMembershipApplicationRequestDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationDetailsDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationPageDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.model.Campus;
import com.societycentral.model.Executive;
import com.societycentral.model.ExecutiveId;
import com.societycentral.model.FundTransactionDirection;
import com.societycentral.model.FundTransactionReason;
import com.societycentral.model.MembershipApplication;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.model.Notification;
import com.societycentral.model.NotificationType;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyMember;
import com.societycentral.model.Student;
import com.societycentral.model.User;
import com.societycentral.repository.MembershipApplicationRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.projection.ExecutiveMembershipApplicationListProjection;
import com.societycentral.service.ExecutiveSocietyResolver.ActiveExecutiveSociety;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutiveMembershipApplicationServiceTests {

    private static final String EXECUTIVE_EMAIL = "executive@nmu.ac.za";
    private static final String EXECUTIVE_NUMBER = "220000001";
    private static final String STUDENT_NUMBER = "220000002";
    private static final String STUDENT_EMAIL = "applicant@nmu.ac.za";
    private static final String SOCIETY_ID = "SOC001";
    private static final String APPLICATION_ID =
            "c0f6b48a-e2ad-4b89-b9c6-7de7a2b22112";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2030, 8, 3, 10, 15, 30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2030-08-03T10:15:30Z"),
            ZoneOffset.UTC);

    @Mock
    private ExecutiveSocietyResolver executiveSocietyResolver;
    @Mock
    private MembershipApplicationRepository applicationRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SocietyMemberRepository societyMemberRepository;
    @Mock
    private FundTransactionService fundTransactionService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExecutiveMembershipApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ExecutiveMembershipApplicationService(
                executiveSocietyResolver,
                applicationRepository,
                studentRepository,
                societyMemberRepository,
                fundTransactionService,
                notificationService,
                eventPublisher,
                CLOCK);
    }

    @Test
    void pendingListIsSocietyScopedSearchedAndOldestFirst() {
        Society society = society();
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society));

        ExecutiveMembershipApplicationListProjection projection =
                mock(ExecutiveMembershipApplicationListProjection.class);
        when(projection.getApplicationID()).thenReturn(APPLICATION_ID);
        when(projection.getTrackingReference())
                .thenReturn("MEM-20300803-A1B2C3");
        when(projection.getStatus())
                .thenReturn(MembershipApplicationStatus.PENDING);
        when(projection.getApplicationDate())
                .thenReturn(NOW.minusDays(1));
        when(projection.getLastUpdatedAt())
                .thenReturn(NOW.minusDays(1));
        when(projection.getStudentNumber()).thenReturn(STUDENT_NUMBER);
        when(projection.getStudentFirstName()).thenReturn("Akhona");
        when(projection.getStudentLastName()).thenReturn("Mbeki");
        when(projection.getCourse()).thenReturn("BSc IT");
        when(projection.getLevel()).thenReturn("3");
        when(projection.getCampus()).thenReturn(Campus.SOUTH_CAMPUS);
        when(projection.getMotivation()).thenReturn("I want to contribute.");
        when(projection.getSocietyID()).thenReturn(SOCIETY_ID);
        when(projection.getSocietyName()).thenReturn("Computing Society");

        when(applicationRepository.findExecutiveApplications(
                eq(SOCIETY_ID),
                eq(MembershipApplicationStatus.PENDING),
                eq("%akhona%"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(projection),
                        PageRequest.of(0, 20),
                        1));

        ExecutiveMembershipApplicationPageDTO response =
                applicationService.getApplications(
                        EXECUTIVE_EMAIL,
                        null,
                        "  Akhona  ",
                        0,
                        20);

        assertEquals(1, response.getTotalElements());
        assertEquals("Akhona", response.getContent().getFirst().getFirstName());
        assertEquals("Mbeki", response.getContent().getFirst().getLastName());
        assertEquals(
                "Akhona Mbeki",
                response.getContent().getFirst().getFullName());

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        verify(applicationRepository).findExecutiveApplications(
                eq(SOCIETY_ID),
                eq(MembershipApplicationStatus.PENDING),
                eq("%akhona%"),
                pageableCaptor.capture());
        assertTrue(pageableCaptor.getValue().getSort()
                .getOrderFor("applicationDate").isAscending());
    }

    @Test
    void detailsForAnotherSocietyAreForbidden() {
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society()));
        when(applicationRepository.findByApplicationIDAndSocietyID(
                APPLICATION_ID, SOCIETY_ID))
                .thenReturn(Optional.empty());
        when(applicationRepository.existsById(APPLICATION_ID))
                .thenReturn(true);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> applicationService.getApplication(
                        EXECUTIVE_EMAIL, APPLICATION_ID));

        assertEquals(
                "You are not authorised to view this membership application.",
                exception.getMessage());
        verifyNoInteractions(studentRepository);
    }

    @Test
    void approvalCreatesOneMemberCreditsFeeAndUpdatesHistory() {
        Society society = society();
        Student applicant = applicant();
        MembershipApplication application = pendingApplication();
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society));
        when(applicationRepository.findByApplicationIDForUpdate(
                APPLICATION_ID))
                .thenReturn(Optional.of(application));
        when(studentRepository.findById(STUDENT_NUMBER))
                .thenReturn(Optional.of(applicant));
        when(societyMemberRepository
                .existsByIdStudentNumberAndIdSocietyID(
                        STUDENT_NUMBER, SOCIETY_ID))
                .thenReturn(false);

        ExecutiveMembershipApplicationDetailsDTO response =
                applicationService.approveApplication(
                        EXECUTIVE_EMAIL, APPLICATION_ID);

        assertEquals(MembershipApplicationStatus.APPROVED,
                application.getStatus());
        assertEquals(NOW, application.getReviewedAt());
        assertEquals(EXECUTIVE_NUMBER, application.getReviewedBy());
        assertNull(application.getRejectionReason());
        assertEquals(MembershipApplicationStatus.APPROVED,
                response.getStatus());
        assertEquals("Akhona", response.getFirstName());
        assertEquals(STUDENT_EMAIL, response.getEmail());
        assertEquals(13, society.getNumberOfMembers());

        ArgumentCaptor<SocietyMember> memberCaptor =
                ArgumentCaptor.forClass(SocietyMember.class);
        verify(societyMemberRepository).save(memberCaptor.capture());
        SocietyMember membership = memberCaptor.getValue();
        assertEquals(STUDENT_NUMBER, membership.getId().getStudentNumber());
        assertEquals(SOCIETY_ID, membership.getId().getSocietyID());
        assertEquals(LocalDate.of(2030, 8, 3), membership.getJoinDate());
        assertNull(membership.getExpireDate());

        verify(fundTransactionService).recordTransaction(
                eq(SOCIETY_ID),
                eq(new BigDecimal("75.00")),
                eq(FundTransactionDirection.CREDIT),
                eq(FundTransactionReason.MEMBERSHIP_FEE),
                any(String.class),
                eq(STUDENT_NUMBER),
                eq(EXECUTIVE_EMAIL));

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).create(notificationCaptor.capture());
        assertEquals(NotificationType.MEMBERSHIP_APPROVED,
                notificationCaptor.getValue().getNotifType());
        assertEquals(STUDENT_EMAIL,
                notificationCaptor.getValue().getRecipientEmail());
        verify(eventPublisher).publishEvent(
                any(MembershipApplicationApprovedEvent.class));
        verify(applicationRepository).save(application);
    }

    @Test
    void existingMemberCannotBeApprovedAgain() {
        MembershipApplication application = pendingApplication();
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society()));
        when(applicationRepository.findByApplicationIDForUpdate(
                APPLICATION_ID))
                .thenReturn(Optional.of(application));
        when(studentRepository.findById(STUDENT_NUMBER))
                .thenReturn(Optional.of(applicant()));
        when(societyMemberRepository
                .existsByIdStudentNumberAndIdSocietyID(
                        STUDENT_NUMBER, SOCIETY_ID))
                .thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationService.approveApplication(
                        EXECUTIVE_EMAIL, APPLICATION_ID));

        assertEquals(
                "The student is already a member of this society.",
                exception.getMessage());
        assertEquals(MembershipApplicationStatus.PENDING,
                application.getStatus());
        verify(societyMemberRepository, never()).save(any());
        verifyNoInteractions(
                fundTransactionService,
                notificationService,
                eventPublisher);
    }

    @Test
    void reviewedApplicationCannotBeReviewedAgain() {
        MembershipApplication application = pendingApplication();
        application.setStatus(MembershipApplicationStatus.APPROVED);
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society()));
        when(applicationRepository.findByApplicationIDForUpdate(
                APPLICATION_ID))
                .thenReturn(Optional.of(application));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationService.approveApplication(
                        EXECUTIVE_EMAIL, APPLICATION_ID));

        assertEquals(
                "Only pending membership applications may be reviewed.",
                exception.getMessage());
        verifyNoInteractions(
                studentRepository,
                societyMemberRepository,
                fundTransactionService,
                notificationService,
                eventPublisher);
    }

    @Test
    void rejectionStoresReasonWithoutCreatingMembershipOrFee() {
        MembershipApplication application = pendingApplication();
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society()));
        when(applicationRepository.findByApplicationIDForUpdate(
                APPLICATION_ID))
                .thenReturn(Optional.of(application));
        when(studentRepository.findById(STUDENT_NUMBER))
                .thenReturn(Optional.of(applicant()));
        RejectMembershipApplicationRequestDTO request =
                new RejectMembershipApplicationRequestDTO();
        request.setRejectionReason("  Capacity has been reached.  ");

        ExecutiveMembershipApplicationDetailsDTO response =
                applicationService.rejectApplication(
                        EXECUTIVE_EMAIL, APPLICATION_ID, request);

        assertEquals(MembershipApplicationStatus.REJECTED,
                application.getStatus());
        assertEquals("Capacity has been reached.",
                application.getRejectionReason());
        assertEquals(NOW, application.getReviewedAt());
        assertEquals(EXECUTIVE_NUMBER, application.getReviewedBy());
        assertEquals("Capacity has been reached.",
                response.getRejectionReason());

        verify(applicationRepository).save(application);
        verifyNoInteractions(
                societyMemberRepository,
                fundTransactionService);

        ArgumentCaptor<Notification> notificationCaptor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).create(notificationCaptor.capture());
        Notification notification = notificationCaptor.getValue();
        assertEquals(NotificationType.MEMBERSHIP_REJECTED,
                notification.getNotifType());
        assertTrue(notification.getMessage()
                .contains("Capacity has been reached."));
        verify(eventPublisher).publishEvent(
                any(MembershipApplicationRejectedEvent.class));
    }

    @Test
    void blankRejectionReasonChangesNothing() {
        MembershipApplication application = pendingApplication();
        when(executiveSocietyResolver.resolve(EXECUTIVE_EMAIL))
                .thenReturn(context(society()));
        when(applicationRepository.findByApplicationIDForUpdate(
                APPLICATION_ID))
                .thenReturn(Optional.of(application));
        RejectMembershipApplicationRequestDTO request =
                new RejectMembershipApplicationRequestDTO();
        request.setRejectionReason("   ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicationService.rejectApplication(
                        EXECUTIVE_EMAIL, APPLICATION_ID, request));

        assertEquals("Rejection reason is required.", exception.getMessage());
        assertEquals(MembershipApplicationStatus.PENDING,
                application.getStatus());
        assertNull(application.getReviewedAt());
        verify(applicationRepository, never()).save(any());
        verifyNoInteractions(
                studentRepository,
                societyMemberRepository,
                fundTransactionService,
                notificationService,
                eventPublisher);
    }

    private ActiveExecutiveSociety context(Society society) {
        Student executive = new Student();
        executive.setStudentNumber(EXECUTIVE_NUMBER);
        executive.setEmail(EXECUTIVE_EMAIL);
        Executive executiveRole = new Executive();
        executiveRole.setId(new ExecutiveId(
                EXECUTIVE_NUMBER,
                society.getSocietyID(),
                NOW.toLocalDate().minusMonths(1)));
        executiveRole.setStudent(executive);
        executiveRole.setSociety(society);
        executiveRole.setPosition("President");
        return new ActiveExecutiveSociety(
                executive, society, executiveRole);
    }

    private Society society() {
        Society society = new Society();
        society.setSocietyID(SOCIETY_ID);
        society.setSocietyName("Computing Society");
        society.setSdoStaffNumber("SDO001");
        society.setActiveStatus(true);
        society.setNumberOfMembers(12);
        society.setMembershipFee(new BigDecimal("75.00"));
        society.setCurrentBalance(new BigDecimal("1000.00"));
        return society;
    }

    private Student applicant() {
        User user = new User();
        user.setEmail(STUDENT_EMAIL);
        user.setFirstName("Akhona");
        user.setLastName("Mbeki");
        user.setCampus(Campus.SOUTH_CAMPUS);

        Student student = new Student();
        student.setStudentNumber(STUDENT_NUMBER);
        student.setEmail(STUDENT_EMAIL);
        student.setUser(user);
        student.setCourse("BSc IT");
        student.setLevel("3");
        student.setCellPhoneNumber("0820000000");
        student.setNationality("South African");
        student.setResidence("Campus residence");
        return student;
    }

    private MembershipApplication pendingApplication() {
        MembershipApplication application = new MembershipApplication();
        application.setApplicationID(APPLICATION_ID);
        application.setTrackingReference("MEM-20300803-A1B2C3");
        application.setStudentNumber(STUDENT_NUMBER);
        application.setSocietyID(SOCIETY_ID);
        application.setMotivation("I want to contribute.");
        application.setStatus(MembershipApplicationStatus.PENDING);
        application.setApplicationDate(NOW.minusDays(1));
        application.setLastUpdatedAt(NOW.minusDays(1));
        return application;
    }
}
