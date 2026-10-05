package com.societycentral.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Sends A100 and C800 membership emails only after persistence commits.
 */
@Service
@RequiredArgsConstructor
public class MembershipApplicationEmailListener {

    private final EmailService emailService;

    /**
     * Sends the typed membership-application confirmation email.
     *
     * @param event committed membership application details
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void sendSubmissionConfirmation(
            MembershipApplicationSubmittedEvent event) {
        emailService.send(
                EmailType.MEMBERSHIP_APPLICATION_SUBMITTED,
                event.studentEmail(),
                Map.of(
                        "societyName", event.societyName(),
                        "status", event.status(),
                        "trackingReference", event.trackingReference(),
                        "submissionDate", event.submissionDate()));
    }

    /**
     * Sends confirmation after an approval transaction commits.
     *
     * @param event committed membership approval details
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void sendApprovalConfirmation(
            MembershipApplicationApprovedEvent event) {
        emailService.send(
                EmailType.MEMBERSHIP_APPROVED,
                event.studentEmail(),
                Map.of(
                        "societyName", event.societyName(),
                        "trackingReference", event.trackingReference()));
    }

    /**
     * Sends confirmation after a rejection transaction commits.
     *
     * @param event committed membership rejection details
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void sendRejectionConfirmation(
            MembershipApplicationRejectedEvent event) {
        emailService.send(
                EmailType.MEMBERSHIP_REJECTED,
                event.studentEmail(),
                Map.of(
                        "societyName", event.societyName(),
                        "trackingReference", event.trackingReference(),
                        "reason", event.rejectionReason()));
    }
}

/**
 * Immutable data published when an A100 transaction is ready to commit.
 */
record MembershipApplicationSubmittedEvent(
        String studentEmail,
        String societyName,
        String status,
        String trackingReference,
        String submissionDate) {
}

/**
 * Immutable data published when a C800 approval transaction commits.
 */
record MembershipApplicationApprovedEvent(
        String studentEmail,
        String societyName,
        String trackingReference) {
}

/**
 * Immutable data published when a C800 rejection transaction commits.
 */
record MembershipApplicationRejectedEvent(
        String studentEmail,
        String societyName,
        String trackingReference,
        String rejectionReason) {
}
