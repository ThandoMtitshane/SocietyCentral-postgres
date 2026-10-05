package com.societycentral.service;

import com.societycentral.dto.request.SubmitMembershipApplicationRequestDTO;
import com.societycentral.dto.response.MembershipApplicationResponseDTO;
import com.societycentral.dto.response.MembershipApplicationStatusResponseDTO;
import com.societycentral.dto.response.MembershipState;
import com.societycentral.dto.response.SocietyMembershipEligibilityResponseDTO;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.MembershipApplication;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.model.Notification;
import com.societycentral.model.NotificationType;
import com.societycentral.model.Society;
import com.societycentral.model.Student;
import com.societycentral.model.User;
import com.societycentral.repository.ExecutiveRepository;
import com.societycentral.repository.MembershipApplicationRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.SocietyRepository;
import com.societycentral.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements A100 membership application submission and profile eligibility.
 */
@Service
@RequiredArgsConstructor
public class MembershipApplicationService {

    private static final int MAX_MOTIVATION_LENGTH = 500;
    private static final DateTimeFormatter TRACKING_DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter NOTIFICATION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH);

    private final MembershipApplicationRepository membershipApplicationRepository;
    private final StudentRepository studentRepository;
    private final SocietyRepository societyRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final ExecutiveRepository executiveRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Submits a pending application for the authenticated student.
     *
     * @param authenticatedEmail email from the authenticated principal
     * @param societyID selected society identifier
     * @param request student-supplied application details
     * @return persisted application confirmation, including the unpaid fee
     */
    @Transactional
    public MembershipApplicationResponseDTO submitApplication(
            String authenticatedEmail,
            String societyID,
            SubmitMembershipApplicationRequestDTO request) {

        // 1. Validate the authenticated email
        validateAuthenticatedEmail(authenticatedEmail);

        // 2. Find the authenticated student
        Student student = findStudent(authenticatedEmail);

        // 3. Find the selected society
        Society society = findSociety(societyID);

        // 4. Validate society activity and flag status
        if (!acceptsMembershipApplications(society)) {
            throw new IllegalStateException(
                    "Membership applications are unavailable for this society.");
        }

        LocalDate today = LocalDate.now(clock);

        // 5. Prevent an application from an active member
        if (societyMemberRepository.existsActiveMembership(
                student.getStudentNumber(), societyID, today)) {
            throw new IllegalStateException(
                    "You are already a member of this society.");
        }

        // 6. Prevent a duplicate pending application
        if (membershipApplicationRepository
                .existsByStudentNumberAndSocietyIDAndStatus(
                        student.getStudentNumber(),
                        societyID,
                        MembershipApplicationStatus.PENDING)) {
            throw new IllegalStateException(
                    "You already have a pending membership application for this society.");
        }

        // 7. Validate and normalise the request data
        String motivation = validateMotivation(request);

        // 8. Generate the application UUID
        String applicationID = UUID.randomUUID().toString();

        // 9. Create a non-sequential public tracking reference
        String trackingReference = generateTrackingReference(today);
        LocalDateTime submittedAt = LocalDateTime.now(clock);

        // 10. Build a new PENDING application without creating membership
        MembershipApplication application = new MembershipApplication();
        application.setApplicationID(applicationID);
        application.setTrackingReference(trackingReference);
        application.setStudentNumber(student.getStudentNumber());
        application.setSocietyID(societyID);
        application.setStatus(MembershipApplicationStatus.PENDING);
        application.setMotivation(motivation);
        application.setApplicationDate(submittedAt);
        application.setLastUpdatedAt(submittedAt);

        // 11. Save only the application; fees remain unprocessed
        MembershipApplication saved =
                membershipApplicationRepository.save(application);

        // 12. Notify current executives and queue non-blocking student email
        notifyExecutives(student, society, saved, today);

        eventPublisher.publishEvent(new MembershipApplicationSubmittedEvent(
                student.getEmail(),
                society.getSocietyName(),
                "Pending",
                saved.getTrackingReference(),
                submittedAt.format(NOTIFICATION_DATE_FORMAT)));

        // 13. Return the application response
        return mapToResponse(saved, society);
    }

    /**
     * Resolves the complete Join Society button state in one backend call.
     *
     * @param authenticatedEmail email from the authenticated principal
     * @param societyID selected society identifier
     * @return authoritative membership and application eligibility state
     */
    @Transactional(readOnly = true)
    public SocietyMembershipEligibilityResponseDTO getEligibility(
            String authenticatedEmail,
            String societyID) {

        // 1. Resolve the authenticated student and selected society
        validateAuthenticatedEmail(authenticatedEmail);
        Student student = findStudent(authenticatedEmail);
        Society society = findSociety(societyID);

        // 2. Resolve the authoritative student membership state
        return buildEligibility(student, society);
    }

    @Transactional(readOnly = true)
    public MembershipApplicationStatusResponseDTO getApplicationStatus(
            String authenticatedEmail,
            String applicationID) {
        validateAuthenticatedEmail(authenticatedEmail);
        Student student = findStudent(authenticatedEmail);
        MembershipApplication application = membershipApplicationRepository
                .findByApplicationIDAndStudentNumber(applicationID, student.getStudentNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Membership application not found."));
        Society society = findSociety(application.getSocietyID());
        SocietyMembershipEligibilityResponseDTO eligibility = buildEligibility(student, society);
        return MembershipApplicationStatusResponseDTO.builder()
                .applicationID(application.getApplicationID())
                .trackingReference(application.getTrackingReference())
                .societyID(application.getSocietyID())
                .societyName(society.getSocietyName())
                .status(application.getStatus())
                .submittedAt(application.getApplicationDate())
                .updatedAt(application.getLastUpdatedAt())
                .rejectionReason(application.getRejectionReason())
                .canReapply(eligibility.getMembershipState() == MembershipState.REAPPLY_ALLOWED)
                .build();
    }

    /**
     * Resolves profile eligibility for any authenticated browse user.
     *
     * <p>Students receive their authoritative state. SDO and administrator
     * accounts cannot apply and therefore receive {@code UNAVAILABLE}.</p>
     *
     * @param authenticatedEmail email from the authenticated principal
     * @param society active society being viewed
     * @return caller-specific profile eligibility
     */
    @Transactional(readOnly = true)
    public SocietyMembershipEligibilityResponseDTO getProfileEligibility(
            String authenticatedEmail,
            Society society) {
        validateAuthenticatedEmail(authenticatedEmail);
        if (society == null) {
            throw new ResourceNotFoundException("Society not found.");
        }

        return studentRepository.findByEmail(authenticatedEmail)
                .map(student -> buildEligibility(student, society))
                .orElseGet(() -> unavailableEligibility(society));
    }

    SocietyMembershipEligibilityResponseDTO getProfileEligibility(
            Student student,
            Society society) {
        if (student == null) {
            throw new ResourceNotFoundException("Student not found.");
        }
        if (society == null) {
            throw new ResourceNotFoundException("Society not found.");
        }
        return buildEligibility(student, society);
    }

    private SocietyMembershipEligibilityResponseDTO buildEligibility(
            Student student,
            Society society) {
        LocalDate today = LocalDate.now(clock);

        // 1. Resolve current membership and pending-application state
        boolean activeMembership = societyMemberRepository.existsActiveMembership(
                student.getStudentNumber(), society.getSocietyID(), today);
        Optional<MembershipApplication> pendingApplication =
                membershipApplicationRepository
                        .findFirstByStudentNumberAndSocietyIDAndStatus(
                                student.getStudentNumber(),
                                society.getSocietyID(),
                                MembershipApplicationStatus.PENDING);
        Optional<MembershipApplication> latestApplication =
                membershipApplicationRepository
                        .findFirstByStudentNumberAndSocietyIDOrderByApplicationDateDesc(
                                student.getStudentNumber(),
                                society.getSocietyID());

        // 2. Determine whether a new submission is currently permitted
        boolean applicationsAvailable = acceptsMembershipApplications(society)
                && !activeMembership
                && pendingApplication.isEmpty();
        MembershipState membershipState = resolveMembershipState(
                society,
                activeMembership,
                pendingApplication.isPresent(),
                latestApplication);

        MembershipApplication latest = latestApplication.orElse(null);
        return SocietyMembershipEligibilityResponseDTO.builder()
                .societyID(society.getSocietyID())
                .membershipFee(membershipFee(society))
                .membershipState(membershipState)
                .activeMembership(activeMembership)
                .pendingApplication(pendingApplication.isPresent())
                .latestApplicationID(
                        latest == null ? null : latest.getApplicationID())
                .latestTrackingReference(
                        latest == null ? null : latest.getTrackingReference())
                .applicationsAvailable(applicationsAvailable)
                .build();
    }

    private SocietyMembershipEligibilityResponseDTO unavailableEligibility(
            Society society) {
        return SocietyMembershipEligibilityResponseDTO.builder()
                .societyID(society.getSocietyID())
                .membershipFee(membershipFee(society))
                .membershipState(MembershipState.UNAVAILABLE)
                .activeMembership(false)
                .pendingApplication(false)
                .applicationsAvailable(false)
                .build();
    }

    private void validateAuthenticatedEmail(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new BadCredentialsException("Authentication is required.");
        }
    }

    private Student findStudent(String authenticatedEmail) {
        return studentRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Student not found."));
    }

    private Society findSociety(String societyID) {
        if (societyID == null || societyID.isBlank()) {
            throw new ResourceNotFoundException("Society not found.");
        }
        return societyRepository.findById(societyID)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Society not found."));
    }

    private boolean acceptsMembershipApplications(Society society) {
        return Boolean.TRUE.equals(society.getActiveStatus())
                && !Boolean.TRUE.equals(society.getIsFlagged());
    }

    private String validateMotivation(
            SubmitMembershipApplicationRequestDTO request) {
        if (request == null || request.getMotivation() == null
                || request.getMotivation().isBlank()) {
            throw new IllegalArgumentException("Motivation is required.");
        }

        String motivation = request.getMotivation().trim();
        if (motivation.length() > MAX_MOTIVATION_LENGTH) {
            throw new IllegalArgumentException(
                    "Motivation must not exceed 500 characters.");
        }
        return motivation;
    }

    private String generateTrackingReference(LocalDate today) {
        String trackingReference;
        do {
            String randomSuffix = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase(Locale.ROOT);
            trackingReference = "MEM-"
                    + today.format(TRACKING_DATE_FORMAT)
                    + "-"
                    + randomSuffix;
        } while (membershipApplicationRepository
                .findByTrackingReference(trackingReference)
                .isPresent());
        return trackingReference;
    }

    private void notifyExecutives(
            Student student,
            Society society,
            MembershipApplication application,
            LocalDate currentDate) {
        String studentName = studentDisplayName(student);
        String submittedAt = application.getApplicationDate()
                .format(NOTIFICATION_DATE_FORMAT);
        String message = String.format(
                "%s submitted a membership application to %s. "
                        + "Tracking reference: %s. Submitted: %s.",
                studentName,
                society.getSocietyName(),
                application.getTrackingReference(),
                submittedAt);

        executiveRepository.findCurrentExecutiveEmailsBySocietyID(
                        society.getSocietyID(), currentDate)
                .forEach(recipientEmail -> {
                    if (notificationService.notificationExists(
                            recipientEmail,
                            NotificationType.MEMBERSHIP_APPLICATION_SUBMITTED,
                            application.getApplicationID())) {
                        return;
                    }
                    Notification notification = new Notification();
                    notification.setNotificationID(generateNotificationID());
                    notification.setRecipientEmail(recipientEmail);
                    notification.setTitle("New membership application");
                    notification.setMessage(message);
                    notification.setNotifType(
                            NotificationType.MEMBERSHIP_APPLICATION_SUBMITTED);
                    notification.setRelatedID(application.getApplicationID());
                    notificationService.create(notification);
                });
    }

    private String studentDisplayName(Student student) {
        User user = student.getUser();
        if (user == null) {
            return student.getStudentNumber();
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName();
        String lastName = user.getLastName() == null ? "" : user.getLastName();
        String displayName = (firstName + " " + lastName).trim();
        return displayName.isBlank() ? student.getStudentNumber() : displayName;
    }

    private String generateNotificationID() {
        return "NTF" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 17)
                .toUpperCase(Locale.ROOT);
    }

    private MembershipState resolveMembershipState(
            Society society,
            boolean activeMembership,
            boolean pendingApplication,
            Optional<MembershipApplication> latestApplication) {
        if (activeMembership) {
            return MembershipState.ACTIVE_MEMBER;
        }
        if (pendingApplication) {
            return MembershipState.PENDING;
        }
        if (!acceptsMembershipApplications(society)) {
            return MembershipState.UNAVAILABLE;
        }
        if (latestApplication
                .map(MembershipApplication::getStatus)
                .filter(status -> status == MembershipApplicationStatus.REJECTED
                        || status == MembershipApplicationStatus.WITHDRAWN)
                .isPresent()) {
            return MembershipState.REAPPLY_ALLOWED;
        }
        return MembershipState.ELIGIBLE;
    }

    private MembershipApplicationResponseDTO mapToResponse(
            MembershipApplication application,
            Society society) {
        return MembershipApplicationResponseDTO.builder()
                .applicationID(application.getApplicationID())
                .trackingReference(application.getTrackingReference())
                .societyID(application.getSocietyID())
                .societyName(society.getSocietyName())
                .societyLogoUrl(society.getLogoUrl())
                .membershipFee(membershipFee(society))
                .status(application.getStatus())
                .motivation(application.getMotivation())
                .applicationDate(application.getApplicationDate())
                .lastUpdatedAt(application.getLastUpdatedAt())
                .rejectionReason(application.getRejectionReason())
                .build();
    }

    private BigDecimal membershipFee(Society society) {
        return society.getMembershipFee() == null
                ? BigDecimal.ZERO
                : society.getMembershipFee();
    }
}
