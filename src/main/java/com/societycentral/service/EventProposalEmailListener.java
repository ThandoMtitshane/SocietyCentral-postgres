package com.societycentral.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventProposalEmailListener {

    private final EmailService emailService;
    private final EventProposalPdfService pdfService;
    private final EventProposalService proposalService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendSubmission(EventProposalSubmittedEvent event) {
        try {
            Map<String, String> vars = Map.of(
                    "eventName", event.proposal().getEventName(),
                    "societyName", event.proposal().getSocietyName(),
                    "submittedBy", event.submitterName());
            emailService.sendWithAttachment(EmailType.EVENT_PROPOSAL_SUBMITTED,
                    event.sdoEmail(), vars,
                    pdfService.generateProposalPdf(event.proposal()),
                    "EventProposal_" + event.proposal().getEventID() + ".pdf");
        } catch (Exception ex) {
            log.error("Failed to send event proposal email after commit: {}", ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendReview(EventProposalReviewedEvent event) {
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("eventName", event.eventName());
            vars.put("societyName", event.societyName());
            if (event.rejectionReason() != null) vars.put("reason", event.rejectionReason());
            emailService.send(event.emailType(), event.executiveEmail(), vars);
        } catch (Exception ex) {
            log.error("Failed to send event proposal review email after commit: {}", ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPublicationInvitations(EventPublishedEvent event) {
        try {
            proposalService.processPublicationCommunication(event.eventID());
        } catch (Exception ex) {
            log.error("Failed to send event publication invitations after commit: {}", ex.getMessage());
        }
    }
}
