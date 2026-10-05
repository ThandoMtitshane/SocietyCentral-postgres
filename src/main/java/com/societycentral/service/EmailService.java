package com.societycentral.service;

import com.resend.Resend;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;


/**
 * Central email service for SocietyCentral.
 *
 * Uses the Resend Java SDK (com.resend:resend-java).
 * All outgoing emails go through this class — never call Resend directly
 * from a controller or service.
 *
 * To add a new email type:
 *   1. Add the type to the EmailType enum
 *   2. Add a case in buildSubject() and buildBody()
 *   3. Call emailService.send(EmailType.YOUR_TYPE, recipient, vars) from
 *      the relevant service method
 *
 * application.properties keys:
 *   resend.api.key=re_xxxxxxxxx          ← your Resend API key; leave blank
 *                                          to disable email locally
 *   resend.from.address=onboarding@resend.dev  ← use resend.dev for testing,
 *                                               your own domain for production
 *   resend.from.name=SocietyCentral      ← display name shown in inbox
 */
@Service
@Slf4j
public class EmailService {

    private final String apiKey;
    private final String fromAddress;
    private final String fromName;

    public EmailService(
            @Value("${resend.api.key:}") String apiKey,
            @Value("${resend.from.address:onboarding@resend.dev}") String fromAddress,
            @Value("${resend.from.name:SocietyCentral}") String fromName) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    /**
     * Send a typed email.
     *
     * @param type      the EmailType — determines subject and HTML body template
     * @param toEmail   recipient email address
     * @param vars      template variables (key → value), e.g. "societyName" → "CS Society"
     *                  Each type documents which vars it expects in buildBody().
     */
    public boolean send(EmailType type, String toEmail, Map<String, String> vars) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn(
                    "Email not sent [{}] to {} because resend.api.key is not configured",
                    type,
                    toEmail
            );
            return false;
        }

        try {
            Resend resend = new Resend(apiKey);

            String subject = buildSubject(type, vars);
            String html = buildBody(type, vars);

            CreateEmailOptions req = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromAddress + ">")
                    .to(toEmail)
                    .subject(subject)
                    .html(html)
                    .build();

            CreateEmailResponse response = resend.emails().send(req);
            log.info("Email sent [{}] to {} | id={}", type, toEmail, response.getId());
            return true;

        } catch (Exception e) {
            // Log and swallow — email failure should never crash the main flow
            log.error("Failed to send email [{}] to {}: {}", type, toEmail, e.getMessage());
            return false;
        }
    }

    /** Convenience overload — no template vars needed */
    public void send(EmailType type, String toEmail) {
        send(type, toEmail, Map.of());
    }

    /**
     * Send a typed email with a PDF attachment.
     *
     * @param type          the EmailType
     * @param toEmail       recipient email address
     * @param vars          template variables
     * @param pdfBytes      the PDF file content as a byte array
     * @param pdfFileName   the filename for the attachment (e.g. "ticket.pdf")
     */
    public void sendWithAttachment(EmailType type, String toEmail, Map<String, String> vars,
                                    byte[] pdfBytes, String pdfFileName) {
        try {
            Resend resend = new Resend(apiKey);

            String subject = buildSubject(type, vars);
            String html = buildBody(type, vars);

            // Encode PDF as Base64 for Resend attachment API
            String base64Content = Base64.getEncoder().encodeToString(pdfBytes);

            Attachment attachment = Attachment.builder()
                    .fileName(pdfFileName)
                    .content(base64Content)
                    .contentType("application/pdf")
                    .build();

            CreateEmailOptions req = CreateEmailOptions.builder()
                    .from(fromName + " <" + fromAddress + ">")
                    .to(toEmail)
                    .subject(subject)
                    .html(html)
                    .attachments(attachment)
                    .build();

            CreateEmailResponse response = resend.emails().send(req);
            log.info("Email with attachment sent [{}] to {} | id={}", type, toEmail, response.getId());

        } catch (Exception e) {
            log.error("Failed to send email with attachment [{}] to {}: {}", type, toEmail, e.getMessage());
        }
    }

    // ── Subject lines ─────────────────────────────────────────────────────────

    private String buildSubject(EmailType type, Map<String, String> vars) {
        String societyName = vars.getOrDefault("societyName", "Your Society");
        String year = vars.getOrDefault("year", String.valueOf(
                java.time.LocalDate.now().getYear()));

        return switch (type) {
            case FORGOT_PASSWORD ->
                    "[SocietyCentral] Password Reset Request";
            case SDO_ONBOARDING ->
                    "[SocietyCentral] Complete your SocietyCentral account setup";
            case POA_SUBMITTED_TO_SDO ->
                    "[SocietyCentral] POA Submitted for Review — " + societyName + " " + year;
            case POA_APPROVED ->
                    "[SocietyCentral] Your POA Has Been Approved — " + societyName;
            case POA_REVISION_REQUESTED ->
                    "[SocietyCentral] POA Revision Requested — " + societyName;
            case POA_REMINDER ->
                    "[SocietyCentral] Reminder: POA Submission Due — " + societyName;
            case POA_REQUEST_FROM_SDO ->
                    "[SocietyCentral] Action Required: Please Submit Your POA — " + societyName;
            case POA_COHOST_INVITE ->
                    "[SocietyCentral] Co-Host Invitation — " + vars.getOrDefault("eventName", "an event");
            case POA_COHOST_ACCEPTED ->
                    "[SocietyCentral] Co-Host Invitation Accepted";
            case POA_COHOST_DECLINED ->
                    "[SocietyCentral] Co-Host Invitation Declined";
            case MEMBERSHIP_APPROVED ->
                    "[SocietyCentral] Membership Approved — " + societyName;
            case MEMBERSHIP_REJECTED ->
                    "[SocietyCentral] Membership Update — " + societyName;
            case EVENT_PROPOSAL_SUBMITTED ->
                    "[SocietyCentral] Event Proposal Submitted for Review";
            case MEMBERSHIP_APPLICATION_SUBMITTED ->
                    "[SocietyCentral] Membership Application Received,  "
                            + societyName;
            case EVENT_APPROVED ->
                    "[SocietyCentral] Event Approved — " + vars.getOrDefault("eventName", "Your Event");
            case EVENT_REJECTED ->
                    "[SocietyCentral] Event Proposal Update";
            case RSVP_CONFIRMATION ->
                    "[SocietyCentral] RSVP Confirmed — " + vars.getOrDefault("eventName", "Your Event");
            case EVENT_RSVP_INVITE ->
                    "[SocietyCentral] You're Invited — " + vars.getOrDefault("eventName", "Event") + " by " + vars.getOrDefault("societyName", "your society");
            case EVENT_UPCOMING ->
                    "[SocietyCentral] Upcoming Event — " + vars.getOrDefault("eventName", "Event");
            case EVENT_RSVP_OPEN ->
                    "[SocietyCentral] RSVP Now Open — " + vars.getOrDefault("eventName", "Event");
            case BUDGET_REQUEST_SUBMITTED ->
                    "[SocietyCentral] Budget Request Submitted for Review";
            case BUDGET_REQUEST_APPROVED ->
                    "[SocietyCentral] Budget Request Approved";
            case BUDGET_REQUEST_REJECTED ->
                    "[SocietyCentral] Budget Request Update";
            case TASK_REMINDER ->
                    "[SocietyCentral] Reminder: Task Due Soon — " + vars.getOrDefault("taskTitle", "Your Task");
            case TASK_ASSIGNED ->
                    "[SocietyCentral] New Task Assigned to You";
            case ANNOUNCEMENT ->
                    "[SocietyCentral] " + vars.getOrDefault("announcementTitle", "New Announcement");
            case EVENT_FEEDBACK_REQUEST ->
                    "[SocietyCentral] How was " + vars.getOrDefault("eventName", "the event") + "? Share your feedback";
            case EVENT_REPORT_REQUEST ->
                    "[SocietyCentral] Post-Event Report Required — " + vars.getOrDefault("eventName", "Event");
            case EVENT_REPORT_GENERATED ->
                    "[SocietyCentral] Event Report — " + vars.getOrDefault("eventName", "Event") + " (" + vars.getOrDefault("societyName", "") + ")";
        };
    }

    // ── HTML body templates ──────────────────────────────────────────────────

    private String buildBody(EmailType type, Map<String, String> vars) {
        return switch (type) {

            case FORGOT_PASSWORD -> template(
                    "Password Reset Request",
                    "We received a request to reset your SocietyCentral password.",
                    "<p>Click the button below to reset your password. "
                            + "This link expires in 1 hour.</p>"
                            + actionButton(vars.getOrDefault("resetLink", "#"),
                            "Reset Password"),
                    "If you didn't request this, you can safely ignore this email."
            );
            case SDO_ONBOARDING -> template(
                    "Complete your SocietyCentral account setup",
                    "Your SocietyCentral Student Development Officer account has been created.",
                    "<p>Hello <strong>" + vars.getOrDefault("firstName", "there") + "</strong>,</p>"
                            + "<p>Registered email: <strong>" + vars.getOrDefault("email", "-") + "</strong></p>"
                            + "<p><strong>Temporary password:</strong> " + vars.getOrDefault("temporaryPassword", "-") + "</p>"
                            + "<p>Your registered staff number is <strong>" + vars.getOrDefault("staffNumber", "-") + "</strong>" 
                            + "and your assigned location/campus is <strong>" + vars.getOrDefault("location", "-") + " / " + vars.getOrDefault("campus", "-") + "</strong>.</p>"
                            + "<p>Verify your email before signing in. You can change the temporary password after login. "
                            + "This verification link expires in 1 hour.</p>"
                            + actionButton(vars.getOrDefault("verificationLink", "#"), "Verify email"),
                    "If you did not expect this account, contact SocietyCentral administration."
            );

            case POA_SUBMITTED_TO_SDO -> template(
                    "POA Submitted for Review",
                    vars.getOrDefault("societyName", "A society")
                            + " has submitted their Plan of Action for "
                            + vars.getOrDefault("year", "this year") + ".",
                    "<p>The POA was submitted by <strong>"
                            + vars.getOrDefault("submittedBy", "an executive")
                            + "</strong> and is ready for your review.</p>"
                            + actionButton(vars.getOrDefault("reviewLink", "#"),
                            "Review POA"),
                    "Log in to SocietyCentral to approve or request revisions."
            );

            case POA_APPROVED -> template(
                    "Your POA Has Been Approved 🎉",
                    "Great news! Your Plan of Action for "
                            + vars.getOrDefault("year", "this year") + " has been approved.",
                    "<p>Your SDO <strong>"
                            + vars.getOrDefault("sdoName", "your SDO")
                            + "</strong> has reviewed and approved the POA for "
                            + "<strong>"
                            + vars.getOrDefault("societyName", "your society")
                            + "</strong>.</p>",
                    "You can now proceed with planning your events as outlined in the POA."
            );

            case POA_REVISION_REQUESTED -> template(
                    "POA Revision Requested",
                    "Your SDO has reviewed your POA and requested some changes.",
                    "<p><strong>Review Notes from SDO:</strong></p>"
                            + "<blockquote style='border-left:4px solid #F5A623;"
                            + "padding-left:12px;color:#555;margin:12px 0;'>"
                            + vars.getOrDefault("reviewNotes", "Please check the system for details.")
                            + "</blockquote>"
                            + "<p>Please log in to SocietyCentral, review the comments, "
                            + "update your POA and resubmit.</p>"
                            + actionButton(vars.getOrDefault("updateLink", "#"),
                            "Update POA"),
                    "Only the specific items flagged need to be changed."
            );

            case POA_REMINDER -> template(
                    "POA Reminder",
                    "This is a reminder regarding your Plan of Action for "
                            + vars.getOrDefault("year", "this year") + ".",
                    "<p>Your SDO <strong>"
                            + vars.getOrDefault("sdoName", "your SDO")
                            + "</strong> is reminding you to "
                            + vars.getOrDefault("reminderReason",
                            "please submit or update your POA.")
                            + "</p>"
                            + actionButton(vars.getOrDefault("poaLink", "#"),
                            "Go to POA"),
                    "Please action this as soon as possible."
            );

            case POA_REQUEST_FROM_SDO -> template(
                    "Action Required: Please Submit Your POA",
                    "Your SDO has requested that you submit your Plan of Action for "
                            + vars.getOrDefault("year", "this year") + ".",
                    "<p>As an executive of <strong>"
                            + vars.getOrDefault("societyName", "your society")
                            + "</strong>, please log in to SocietyCentral and complete "
                            + "your POA submission as soon as possible.</p>"
                            + actionButton(vars.getOrDefault("poaLink", "#"),
                            "Create POA"),
                    "The POA outlines your society's planned events for the year."
            );

            case POA_COHOST_INVITE -> template(
                    "Co-Host Event Invitation",
                    vars.getOrDefault("invitingSociety", "Another society")
                            + " has invited your society to co-host an event.",
                    "<p><strong>Event:</strong> "
                            + vars.getOrDefault("eventName", "an event") + "</p>"
                            + "<p><strong>Month:</strong> "
                            + vars.getOrDefault("eventMonth", "TBC") + "</p>"
                            + "<p><strong>Venue:</strong> "
                            + vars.getOrDefault("eventVenue", "TBC") + "</p>"
                            + "<p>Log in to SocietyCentral to accept or decline this invitation.</p>"
                            + actionButton(vars.getOrDefault("respondLink", "#"),
                            "Respond to Invitation"),
                    "If you accept, this event will appear in your society's POA."
            );

            case POA_COHOST_ACCEPTED -> template(
                    "Co-Host Invitation Accepted",
                    vars.getOrDefault("acceptingSociety", "A society")
                            + " has accepted your co-host invitation.",
                    "<p><strong>"
                            + vars.getOrDefault("acceptingSociety", "The society")
                            + "</strong> has agreed to co-host <strong>"
                            + vars.getOrDefault("eventName", "your event")
                            + "</strong>. The event will now reflect both societies.</p>",
                    "The co-host will appear in your POA event details."
            );

            case POA_COHOST_DECLINED -> template(
                    "Co-Host Invitation Declined",
                    vars.getOrDefault("decliningeSociety", "A society")
                            + " has declined your co-host invitation.",
                    "<p><strong>"
                            + vars.getOrDefault("decliningeSociety", "The society")
                            + "</strong> has declined the invitation to co-host <strong>"
                            + vars.getOrDefault("eventName", "your event")
                            + "</strong>. They have been removed from the event.</p>",
                    "You can invite another society or proceed without a co-host."
            );

            case TASK_REMINDER -> template(
                    "Task Reminder",
                    "This is a reminder about a task assigned to you.",
                    "<p><strong>Task:</strong> "
                            + vars.getOrDefault("taskTitle", "—") + "</p>"
                            + "<p><strong>Description:</strong> "
                            + vars.getOrDefault("taskDescription", "—") + "</p>"
                            + "<p><strong>Due:</strong> "
                            + vars.getOrDefault("dueDate", "TBC") + "</p>"
                            + "<p><strong>Issued:</strong> "
                            + vars.getOrDefault("issueDate", "—") + "</p>"
                            + "<p><strong>From:</strong> "
                            + vars.getOrDefault("issuedBy", "SocietyCentral") + "</p>"
                            + actionButton(vars.getOrDefault("taskLink", "#"),
                            "View Task"),
                    "Please complete this task before the deadline."
            );

            case MEMBERSHIP_APPROVED -> template(
                    "Membership Approved 🎉",
                    "Welcome to "
                            + vars.getOrDefault("societyName", "the society") + "!",
                    "<p>Your membership request for <strong>"
                            + vars.getOrDefault("societyName", "the society")
                            + "</strong> has been approved.</p>"
                            + "<p>You can now view society events, RSVPs, and announcements.</p>",
                    "We look forward to having you as a member."
            );

            case MEMBERSHIP_REJECTED -> template(
                    "Membership Update",
                    "Your membership request has been reviewed.",
                    "<p>Unfortunately your membership request for <strong>"
                            + vars.getOrDefault("societyName", "the society")
                            + "</strong> was not approved at this time.</p>"
                            + (vars.containsKey("reason")
                            ? "<p><strong>Reason:</strong> " + vars.get("reason") + "</p>"
                            : ""),
                    "You may contact the society executives for more information."
            );

            case EVENT_PROPOSAL_SUBMITTED -> template(
                    "Event Proposal Submitted",
                    "An event proposal is awaiting your review.",
                    "<p><strong>"
                            + vars.getOrDefault("societyName", "A society")
                            + "</strong> has submitted an event proposal for <strong>"
                            + vars.getOrDefault("eventName", "an event")
                            + "</strong>.</p>"
                            + actionButton(vars.getOrDefault("reviewLink", "#"),
                            "Review Event"),
                    "Log in to SocietyCentral to approve or reject."
            );

            case EVENT_APPROVED -> template(
                    "Event Approved ✓",
                    "Your event has been approved.",
                    "<p>Your event <strong>"
                            + vars.getOrDefault("eventName", "your event")
                            + "</strong> has been approved by your SDO. "
                            + "You may now publish it for students to RSVP.</p>",
                    "Log in to SocietyCentral to publish the event."
            );

            case EVENT_REJECTED -> template(
                    "Event Proposal Update",
                    "Your event proposal was not approved.",
                    "<p>Your event <strong>"
                            + vars.getOrDefault("eventName", "your event")
                            + "</strong> was not approved."
                            + (vars.containsKey("reason")
                            ? " <strong>Reason:</strong> " + vars.get("reason")
                            : "")
                            + "</p>",
                    "Please contact your SDO for clarification."
            );

            case RSVP_CONFIRMATION -> template(
                    "RSVP Confirmed ✓",
                    "You're going to "
                            + vars.getOrDefault("eventName", "an event") + "!",
                    "<p><strong>Event:</strong> "
                            + vars.getOrDefault("eventName", "—") + "</p>"
                            + "<p><strong>Date:</strong> "
                            + vars.getOrDefault("eventDate", "—") + "</p>"
                            + "<p><strong>Venue:</strong> "
                            + vars.getOrDefault("eventVenue", "—") + "</p>"
                            + "<p>Your QR code ticket: <strong>"
                            + vars.getOrDefault("qrCode", "See app") + "</strong></p>",
                    "Please present this QR code at the event entrance."
            );

            case EVENT_RSVP_INVITE -> template(
                    "You're Invited!",
                    vars.getOrDefault("societyName", "Your society")
                            + " is hosting " + vars.getOrDefault("eventName", "an event") + ".",
                    "<p>You're invited to attend <strong>"
                            + vars.getOrDefault("eventName", "an event")
                            + "</strong> hosted by <strong>"
                            + vars.getOrDefault("societyName", "your society") + "</strong>.</p>"
                            + "<p><strong>Date:</strong> "
                            + vars.getOrDefault("eventDate", "TBC") + "</p>"
                            + "<p><strong>Venue:</strong> "
                            + vars.getOrDefault("eventVenue", "See event details") + "</p>"
                            + "<p>Confirm your attendance using the button below:</p>"
                            + actionButton(vars.getOrDefault("rsvpLink", "#"), "RSVP Now"),
                    "This link is unique to you. Do not share it with others."
            );

            case EVENT_UPCOMING -> template(
                    "Upcoming Event",
                    vars.getOrDefault("eventName", "An event") + " is coming up.",
                    "<p><strong>" + vars.getOrDefault("eventName", "An event")
                            + "</strong> hosted by <strong>"
                            + vars.getOrDefault("societyName", "your society") + "</strong>.</p>"
                            + "<p><strong>Date:</strong> " + vars.getOrDefault("eventDate", "TBC") + "</p>"
                            + "<p><strong>Time:</strong> " + vars.getOrDefault("eventTime", "TBC") + "</p>"
                            + "<p><strong>Venue:</strong> " + vars.getOrDefault("eventVenue", "See event details") + "</p>"
                            + "<p>RSVP opens on <strong>" + vars.getOrDefault("rsvpOpen", "the scheduled date")
                            + "</strong> and closes on <strong>" + vars.getOrDefault("rsvpClose", "the scheduled date") + "</strong>.</p>"
                            + "<p>" + vars.getOrDefault("description", "") + "</p>",
                    "You will receive another message when RSVP opens."
            );

            case EVENT_RSVP_OPEN -> template(
                    "RSVP Now Open",
                    "RSVP is now open for " + vars.getOrDefault("eventName", "this event") + ".",
                    "<p>RSVP is now open for <strong>" + vars.getOrDefault("eventName", "this event")
                            + "</strong> hosted by <strong>" + vars.getOrDefault("societyName", "your society") + "</strong>.</p>"
                            + "<p><strong>Date:</strong> " + vars.getOrDefault("eventDate", "TBC") + "</p>"
                            + "<p><strong>Time:</strong> " + vars.getOrDefault("eventTime", "TBC") + "</p>"
                            + "<p><strong>Venue:</strong> " + vars.getOrDefault("eventVenue", "See event details") + "</p>"
                            + "<p>RSVP closes on <strong>" + vars.getOrDefault("rsvpClose", "the scheduled date") + "</strong>.</p>"
                            + actionButton(vars.getOrDefault("rsvpLink", "#"), "RSVP Now"),
                    "This link is unique to you. Do not share it with others."
            );

            case BUDGET_REQUEST_SUBMITTED -> template(
                    "Budget Request Submitted",
                    "A budget request is awaiting your review.",
                    "<p><strong>"
                            + vars.getOrDefault("societyName", "A society")
                            + "</strong> has submitted a budget request of <strong>R "
                            + vars.getOrDefault("amount", "0.00")
                            + "</strong> for <strong>"
                            + vars.getOrDefault("eventName", "an event")
                            + "</strong>.</p>"
                            + actionButton(vars.getOrDefault("reviewLink", "#"),
                            "Review Request"),
                    "Log in to SocietyCentral to approve or reject."
            );

            case BUDGET_REQUEST_APPROVED -> template(
                    "Budget Request Approved ✓",
                    "Your budget request has been approved.",
                    "<p>Your budget request of <strong>R "
                            + vars.getOrDefault("amount", "0.00")
                            + "</strong> for <strong>"
                            + vars.getOrDefault("eventName", "your event")
                            + "</strong> has been approved.</p>",
                    "The amount will be reflected in your society's fund history."
            );

            case BUDGET_REQUEST_REJECTED -> template(
                    "Budget Request Update",
                    "Your budget request was not approved.",
                    "<p>Your budget request of <strong>R "
                            + vars.getOrDefault("amount", "0.00")
                            + "</strong> for <strong>"
                            + vars.getOrDefault("eventName", "your event")
                            + "</strong> was not approved."
                            + (vars.containsKey("reason")
                            ? " <strong>Reason:</strong> " + vars.get("reason")
                            : "")
                            + "</p>",
                    "Please contact your SDO for clarification."
            );
            case MEMBERSHIP_APPLICATION_SUBMITTED -> template(
                    "Membership Application Received",
                    "Your application to "
                            + vars.getOrDefault("societyName", "the society")
                            + " was submitted successfully.",
                    "<p><strong>Status:</strong> "
                            + vars.getOrDefault("status", "Pending") + "</p>"
                            + "<p><strong>Tracking reference:</strong> "
                            + vars.getOrDefault("trackingReference", "-") + "</p>"
                            + "<p><strong>Submission date:</strong> "
                            + vars.getOrDefault("submissionDate", "-") + "</p>",
                    "The society executives will review your application. "
                            + "Keep the tracking reference for future enquiries."
            );


            case TASK_ASSIGNED -> template(
                    "New Task Assigned",
                    "A new task has been assigned to you.",
                    "<p><strong>Task:</strong> "
                            + vars.getOrDefault("taskTitle", "—") + "</p>"
                            + "<p><strong>Due:</strong> "
                            + vars.getOrDefault("dueDate", "TBC") + "</p>"
                            + (vars.containsKey("description")
                            ? "<p><strong>Description:</strong> "
                              + vars.get("description") + "</p>"
                            : "")
                            + actionButton(vars.getOrDefault("taskLink", "#"),
                            "View Task"),
                    "Log in to SocietyCentral to view and respond to this task."
            );

            case ANNOUNCEMENT -> template(
                    vars.getOrDefault("announcementTitle", "New Announcement"),
                    vars.getOrDefault("announcementTitle", "You have a new announcement."),
                    "<p>" + vars.getOrDefault("message",
                            vars.getOrDefault("announcementContent", "")) + "</p>",
                    "This announcement was sent by "
                            + vars.getOrDefault("sentBy", "SocietyCentral administration") + "."
            );

            case EVENT_FEEDBACK_REQUEST -> template(
                    "How was the event?",
                    "We'd love to hear your thoughts on "
                            + vars.getOrDefault("eventName", "the event") + ".",
                    "<p>Thank you for attending <strong>"
                            + vars.getOrDefault("eventName", "the event")
                            + "</strong> hosted by <strong>"
                            + vars.getOrDefault("societyName", "your society") + "</strong>.</p>"
                            + "<p>Please take a moment to share your feedback. "
                            + "Your input helps societies improve future events.</p>"
                            + actionButton(vars.getOrDefault("feedbackLink", "#"),
                            "Submit Feedback"),
                    "This feedback form is only available to students who attended."
            );

            case EVENT_REPORT_REQUEST -> template(
                    "Post-Event Report Required",
                    "Please submit your event report for "
                            + vars.getOrDefault("eventName", "the event") + ".",
                    "<p>As an executive of <strong>"
                            + vars.getOrDefault("societyName", "your society")
                            + "</strong>, please submit your post-event report for <strong>"
                            + vars.getOrDefault("eventName", "the event") + "</strong>.</p>"
                            + "<p>This report helps the SDO assess event outcomes "
                            + "and supports future planning.</p>"
                            + actionButton(vars.getOrDefault("reportLink", "#"),
                            "Submit Event Report"),
                    "Only one report per society per event is required."
            );

            case EVENT_REPORT_GENERATED -> template(
                    "Event Report Generated",
                    "The post-event report for "
                            + vars.getOrDefault("eventName", "an event")
                            + " is now available.",
                    "<p>The event report for <strong>"
                            + vars.getOrDefault("eventName", "the event")
                            + "</strong> hosted by <strong>"
                            + vars.getOrDefault("societyName", "the society")
                            + "</strong> has been generated and is attached to this email as a PDF.</p>"
                            + "<p>You can also view it online:</p>"
                            + actionButton(vars.getOrDefault("reportLink", "#"),
                            "View Report Online"),
                    "This report was automatically generated 7 days after the event."
            );
        };
    }

    // ── HTML template wrapper ─────────────────────────────────────────────────

    /**
     * Wraps email body content in a clean, consistently styled HTML layout.
     * All emails share this look — branded with navy + gold colours.
     */
    private String template(String title, String lead,
                            String body, String footer) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#F7F8FA;
                         font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="background:#F7F8FA;padding:32px 0;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:12px;
                                overflow:hidden;
                                box-shadow:0 2px 12px rgba(0,0,0,0.08);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#1E3A5F;padding:24px 32px;
                                 text-align:center;">
                        <h1 style="color:#F5A623;margin:0;font-size:22px;
                                   letter-spacing:1px;">
                          SOCIETY CENTRAL
                        </h1>
                        <p style="color:#9CA8C2;margin:4px 0 0;font-size:12px;">
                          Nelson Mandela University
                        </p>
                      </td>
                    </tr>

                    <!-- Title bar -->
                    <tr>
                      <td style="background:#F5A623;padding:14px 32px;">
                        <h2 style="color:#1E3A5F;margin:0;font-size:16px;">
                          %s
                        </h2>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:28px 32px;">
                        <p style="color:#374151;font-size:15px;margin:0 0 16px;
                                  font-weight:600;">
                          %s
                        </p>
                        <div style="color:#6B7280;font-size:14px;line-height:1.7;">
                          %s
                        </div>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#F7F8FA;padding:20px 32px;
                                 border-top:1px solid #E5E7EB;">
                        <p style="color:#9CA3AF;font-size:12px;margin:0;">
                          %s
                        </p>
                        <p style="color:#9CA3AF;font-size:11px;margin:8px 0 0;">
                          This is an automated message from SocietyCentral.
                          Please do not reply to this email.
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(title, lead, body, footer);
    }

    private String actionButton(String link, String label) {
        return "<div style='margin:20px 0;text-align:center;'>"
                + "<a href='" + link + "' "
                + "style='background:#1E3A5F;color:#F5A623;"
                + "padding:12px 28px;border-radius:8px;"
                + "text-decoration:none;font-weight:700;"
                + "font-size:14px;display:inline-block;'>"
                + label
                + "</a></div>";
    }
}
