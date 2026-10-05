package com.societycentral.service;

import com.societycentral.dto.request.RejectMembershipApplicationRequestDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationDetailsDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationListItemDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationPageDTO;
import com.societycentral.exception.ForbiddenOperationException;
import com.societycentral.exception.ResourceNotFoundException;
import com.societycentral.model.FundTransactionDirection;
import com.societycentral.model.FundTransactionReason;
import com.societycentral.model.MembershipApplication;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.model.Notification;
import com.societycentral.model.NotificationType;
import com.societycentral.model.Society;
import com.societycentral.model.SocietyMember;
import com.societycentral.model.SocietyMemberId;
import com.societycentral.model.Student;
import com.societycentral.model.User;
import com.societycentral.repository.MembershipApplicationRepository;
import com.societycentral.repository.SocietyMemberRepository;
import com.societycentral.repository.StudentRepository;
import com.societycentral.repository.projection.ExecutiveMembershipApplicationListProjection;
import com.societycentral.service.ExecutiveSocietyResolver.ActiveExecutiveSociety;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Implements C800 society-scoped membership application review.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveMembershipApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_REJECTION_REASON_LENGTH = 500;
    private static final int MAX_NOTIFICATION_MESSAGE_LENGTH = 500;

    private final ExecutiveSocietyResolver executiveSocietyResolver;
    private final MembershipApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final FundTransactionService fundTransactionService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Returns one filtered, searched page for the executive's society.
     *
     * @param authenticatedEmail email supplied by the JWT principal
     * @param status requested status, defaulting to PENDING
     * @param search optional applicant or tracking-reference search
     * @param page zero-based page number
     * @param size requested page size
     * @return society-scoped application page
     */
    @Transactional(readOnly = true)
    public ExecutiveMembershipApplicationPageDTO getApplications(
            String authenticatedEmail,
            MembershipApplicationStatus status,
            String search,
            int page,
            int size) {

        // 1. Validate pagination and resolve executive ownership
        validatePage(page, size);
        ActiveExecutiveSociety context =
                executiveSocietyResolver.resolve(authenticatedEmail);

        // 2. Normalise filters and select the workflow ordering
        MembershipApplicationStatus resolvedStatus = status == null
                ? MembershipApplicationStatus.PENDING
                : status;
        String searchPattern = normalizeSearchPattern(search);
        Sort sort = resolvedStatus == MembershipApplicationStatus.PENDING
                ? Sort.by(
                        Sort.Order.asc("applicationDate"),
                        Sort.Order.asc("applicationID"))
                : Sort.by(
                        Sort.Order.desc("lastUpdatedAt"),
                        Sort.Order.asc("applicationID"));

        // 3. Query the application, student, user and society in one page
        Page<ExecutiveMembershipApplicationListProjection> result =
                applicationRepository.findExecutiveApplications(
                        context.society().getSocietyID(),
                        resolvedStatus,
                        searchPattern,
                        PageRequest.of(page, size, sort));

        List<ExecutiveMembershipApplicationListItemDTO> content =
                result.getContent().stream()
                        .map(this::mapListItem)
                        .toList();

        // 4. Return a stable frontend page contract
        return ExecutiveMembershipApplicationPageDTO.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    /**
     * Returns full applicant details after verifying society ownership.
     *
     * @param authenticatedEmail email supplied by the JWT principal
     * @param applicationID requested membership application identifier
     * @return full application details
     */
    @Transactional(readOnly = true)
    public ExecutiveMembershipApplicationDetailsDTO getApplication(
            String authenticatedEmail,
            String applicationID) {

        // 1. Resolve the authenticated executive's active society
        ActiveExecutiveSociety context =
                executiveSocietyResolver.resolve(authenticatedEmail);

        // 2. Validate and retrieve the society-owned application
        validateApplicationID(applicationID);
        MembershipApplication application = requireOwnedApplication(
                applicationID, context.society().getSocietyID());

        // 3. Load the applicant and map full details
        Student student = requireStudent(application.getStudentNumber());
        return mapDetails(application, student, context.society());
    }

    /**
     * Approves a pending application, creates membership and credits its fee.
     *
     * @param authenticatedEmail email supplied by the JWT principal
     * @param applicationID requested membership application identifier
     * @return approved application details
     */
    @Transactional
    public ExecutiveMembershipApplicationDetailsDTO approveApplication(
            String authenticatedEmail,
            String applicationID) {

        // 1. Resolve the authenticated executive's active society
        ActiveExecutiveSociety context =
                executiveSocietyResolver.resolve(authenticatedEmail);
        Society society = context.society();

        // 2. Lock and verify the application before changing state
        validateApplicationID(applicationID);
        MembershipApplication application = lockApplication(applicationID);
        assertApplicationOwnership(application, society.getSocietyID());
        assertPending(application);

        // 3. Load the applicant and prevent duplicate approved membership
        Student student = requireStudent(application.getStudentNumber());
        if (societyMemberRepository
                .existsByIdStudentNumberAndIdSocietyID(
                        student.getStudentNumber(), society.getSocietyID())) {
            throw new IllegalStateException(
                    "The student is already a member of this society.");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime reviewedAt = LocalDateTime.now(clock);

        // 4. Create the approved SocietyMember record
        SocietyMember membership = new SocietyMember();
        membership.setId(new SocietyMemberId(
                student.getStudentNumber(), society.getSocietyID()));
        membership.setStudent(student);
        membership.setSociety(society);
        membership.setJoinDate(today);
        societyMemberRepository.save(membership);

        // 5. Maintain the approved-member count only after membership exists
        int currentMemberCount = society.getNumberOfMembers() == null
                ? 0
                : society.getNumberOfMembers();
        society.setNumberOfMembers(currentMemberCount + 1);

        // 6. Record the completed review without deleting application history
        application.setStatus(MembershipApplicationStatus.APPROVED);
        application.setReviewedAt(reviewedAt);
        application.setReviewedBy(
                context.executiveStudent().getStudentNumber());
        application.setRejectionReason(null);
        application.setLastUpdatedAt(reviewedAt);
        applicationRepository.save(application);

        // 7. Credit a valid membership fee through the financial gateway
        BigDecimal membershipFee = society.getMembershipFee();
        if (membershipFee != null
                && membershipFee.compareTo(BigDecimal.ZERO) > 0) {
            fundTransactionService.recordTransaction(
                    society.getSocietyID(),
                    membershipFee,
                    FundTransactionDirection.CREDIT,
                    FundTransactionReason.MEMBERSHIP_FEE,
                    "Membership fee for " + student.getStudentNumber()
                            + " joining " + society.getSocietyName(),
                    student.getStudentNumber(),
                    authenticatedEmail);
        }

        // 8. Notify the student inside the transaction and email after commit
        notifyStudentOfApproval(student, society, application);
        eventPublisher.publishEvent(new MembershipApplicationApprovedEvent(
                student.getEmail(),
                society.getSocietyName(),
                application.getTrackingReference()));

        // 9. Return the updated historical application
        return mapDetails(application, student, society);
    }

    /**
     * Rejects a pending application while preserving it for history.
     *
     * @param authenticatedEmail email supplied by the JWT principal
     * @param applicationID requested membership application identifier
     * @param request required rejection reason
     * @return rejected application details
     */
    @Transactional
    public ExecutiveMembershipApplicationDetailsDTO rejectApplication(
            String authenticatedEmail,
            String applicationID,
            RejectMembershipApplicationRequestDTO request) {

        // 1. Resolve the authenticated executive's active society
        ActiveExecutiveSociety context =
                executiveSocietyResolver.resolve(authenticatedEmail);
        Society society = context.society();

        // 2. Lock and verify the application
        validateApplicationID(applicationID);
        MembershipApplication application = lockApplication(applicationID);
        assertApplicationOwnership(application, society.getSocietyID());
        assertPending(application);

        // 3. Validate and normalise the rejection reason
        String rejectionReason = validateRejectionReason(request);
        Student student = requireStudent(application.getStudentNumber());
        LocalDateTime reviewedAt = LocalDateTime.now(clock);

        // 4. Record rejection without creating membership or a transaction
        application.setStatus(MembershipApplicationStatus.REJECTED);
        application.setRejectionReason(rejectionReason);
        application.setReviewedAt(reviewedAt);
        application.setReviewedBy(
                context.executiveStudent().getStudentNumber());
        application.setLastUpdatedAt(reviewedAt);
        applicationRepository.save(application);

        // 5. Notify the student inside the transaction and email after commit
        notifyStudentOfRejection(
                student, society, application, rejectionReason);


        eventPublisher.publishEvent(new MembershipApplicationRejectedEvent(
                student.getEmail(),
                society.getSocietyName(),
                application.getTrackingReference(),
                rejectionReason));


        // 6. Return the updated historical application
        return mapDetails(application, student, society);
    }

    private MembershipApplication requireOwnedApplication(
            String applicationID,
            String societyID) {
        return applicationRepository
                .findByApplicationIDAndSocietyID(applicationID, societyID)
                .orElseGet(() -> {
                    if (applicationRepository.existsById(applicationID)) {
                        throw new ForbiddenOperationException(
                                "You are not authorised to view this "
                                        + "membership application.");
                    }
                    throw new ResourceNotFoundException(
                            "Membership application not found.");
                });
    }

    private MembershipApplication lockApplication(String applicationID) {
        return applicationRepository.findByApplicationIDForUpdate(applicationID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membership application not found."));
    }

    private void assertApplicationOwnership(
            MembershipApplication application,
            String societyID) {
        if (!societyID.equals(application.getSocietyID())) {
            throw new ForbiddenOperationException(
                    "You are not authorised to view this "
                            + "membership application.");
        }
    }

    private void assertPending(MembershipApplication application) {
        if (application.getStatus() != MembershipApplicationStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending membership applications may be reviewed.");
        }
    }

    private Student requireStudent(String studentNumber) {
        return studentRepository.findById(studentNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Student not found."));
    }

    private String validateRejectionReason(
            RejectMembershipApplicationRequestDTO request) {
        if (request == null || request.getRejectionReason() == null
                || request.getRejectionReason().isBlank()) {
            throw new IllegalArgumentException(
                    "Rejection reason is required.");
        }

        String rejectionReason = request.getRejectionReason().trim();
        if (rejectionReason.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Rejection reason must not exceed 500 characters.");
        }
        return rejectionReason;
    }

    private void validateApplicationID(String applicationID) {
        if (applicationID == null || applicationID.isBlank()) {
            throw new IllegalArgumentException(
                    "Application ID is required.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE + ".");
        }
    }

    private String normalizeSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private void notifyStudentOfApproval(
            Student student,
            Society society,
            MembershipApplication application) {
        Notification notification = baseDecisionNotification(
                student,
                application,
                NotificationType.MEMBERSHIP_APPROVED,
                "Membership application approved");
        notification.setMessage(limitNotificationMessage(
                "Your membership application to "
                        + society.getSocietyName()
                        + " has been approved. Tracking reference: "
                        + application.getTrackingReference() + "."));
        notificationService.create(notification);
    }

    private void notifyStudentOfRejection(
            Student student,
            Society society,
            MembershipApplication application,
            String rejectionReason) {
        Notification notification = baseDecisionNotification(
                student,
                application,
                NotificationType.MEMBERSHIP_REJECTED,
                "Membership application rejected");
        String prefix = "Your membership application to "
                + society.getSocietyName()
                + " was rejected. Reason: ";
        String suffix = " Tracking reference: "
                + application.getTrackingReference() + ".";
        notification.setMessage(decisionMessage(
                prefix, rejectionReason, suffix));
        notificationService.create(notification);
    }

    private Notification baseDecisionNotification(
            Student student,
            MembershipApplication application,
            NotificationType notificationType,
            String title) {
        Notification notification = new Notification();
        notification.setNotificationID(generateNotificationID());
        notification.setRecipientEmail(student.getEmail());
        notification.setTitle(title);
        notification.setNotifType(notificationType);
        notification.setRelatedID(application.getApplicationID());
        return notification;
    }

    private String generateNotificationID() {
        return "NTF" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 17)
                .toUpperCase(Locale.ROOT);
    }

    private String limitNotificationMessage(String message) {
        return message.length() <= MAX_NOTIFICATION_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_NOTIFICATION_MESSAGE_LENGTH);
    }

    private String decisionMessage(
            String prefix,
            String rejectionReason,
            String suffix) {
        int availableReasonLength = MAX_NOTIFICATION_MESSAGE_LENGTH
                - prefix.length()
                - suffix.length();
        if (rejectionReason.length() <= availableReasonLength) {
            return prefix + rejectionReason + suffix;
        }

        int truncatedLength = Math.max(0, availableReasonLength - 3);
        return prefix
                + rejectionReason.substring(0, truncatedLength)
                + "..."
                + suffix;
    }

    private ExecutiveMembershipApplicationListItemDTO mapListItem(
            ExecutiveMembershipApplicationListProjection application) {
        return ExecutiveMembershipApplicationListItemDTO.builder()
                .applicationID(application.getApplicationID())
                .trackingReference(application.getTrackingReference())
                .status(application.getStatus())
                .applicationDate(application.getApplicationDate())
                .lastUpdatedAt(application.getLastUpdatedAt())
                .studentNumber(application.getStudentNumber())
                .firstName(application.getStudentFirstName())
                .lastName(application.getStudentLastName())
                .fullName(fullName(
                        application.getStudentFirstName(),
                        application.getStudentLastName()))
                .course(application.getCourse())
                .level(application.getLevel())
                .campus(application.getCampus())
                .motivation(application.getMotivation())
                .societyID(application.getSocietyID())
                .societyName(application.getSocietyName())
                .build();
    }

    private ExecutiveMembershipApplicationDetailsDTO mapDetails(
            MembershipApplication application,
            Student student,
            Society society) {
        User user = student.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("Student not found.");
        }

        return ExecutiveMembershipApplicationDetailsDTO.builder()
                .applicationID(application.getApplicationID())
                .trackingReference(application.getTrackingReference())
                .status(application.getStatus())
                .applicationDate(application.getApplicationDate())
                .lastUpdatedAt(application.getLastUpdatedAt())
                .studentNumber(student.getStudentNumber())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(fullName(
                        user.getFirstName(), user.getLastName()))
                .course(student.getCourse())
                .level(student.getLevel())
                .campus(user.getCampus())
                .motivation(application.getMotivation())
                .societyID(society.getSocietyID())
                .societyName(society.getSocietyName())
                .email(student.getEmail())
                .cellPhoneNumber(student.getCellPhoneNumber())
                .faculty(student.getFaculty())
                .school(student.getSchool())
                .nationality(student.getNationality())
                .residence(student.getResidence())
                .membershipFee(membershipFee(society))
                .reviewedAt(application.getReviewedAt())
                .reviewedBy(application.getReviewedBy())
                .rejectionReason(application.getRejectionReason())
                .build();
    }

    private String fullName(String firstName, String lastName) {
        String safeFirstName = firstName == null ? "" : firstName;
        String safeLastName = lastName == null ? "" : lastName;
        return (safeFirstName + " " + safeLastName).trim();
    }

    private BigDecimal membershipFee(Society society) {
        return society.getMembershipFee() == null
                ? BigDecimal.ZERO
                : society.getMembershipFee();
    }
}
