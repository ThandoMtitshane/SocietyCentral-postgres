package com.societycentral.service;

import com.societycentral.model.*;
import com.societycentral.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Scheduled task that sends feedback request emails to attendees
 * and event report request emails to executives, 1 hour after the
 * event end time.
 *
 * Runs every 15 minutes. Only sends once per event (tracked via feedbackEmailSentAt).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventFeedbackScheduler {

    private final EventRepository eventRepository;
    private final RSVPRepository rsvpRepository;
    private final HosterRepository hosterRepository;
    private final ExecutiveRepository executiveRepository;
    private final StudentRepository studentRepository;
    private final SocietyRepository societyRepository;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Checks every 15 minutes for events that have ended 1+ hour ago
     * and haven't had feedback emails sent yet.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void sendPostEventFeedbackEmails() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        // Find PUBLISHED events where:
        // - eventDate is today or before
        // - eventEndTime + 1 hour has passed
        // - feedbackEmailSentAt is null
        List<Event> candidates = eventRepository.findByEventStatus(EventStatus.PUBLISHED)
                .stream()
                .filter(event -> event.getFeedbackEmailSentAt() == null)
                .filter(event -> event.getEventDate() != null && event.getEventEndTime() != null)
                .filter(event -> {
                    LocalDateTime eventEnd = LocalDateTime.of(event.getEventDate(), event.getEventEndTime());
                    LocalDateTime oneHourAfter = eventEnd.plusHours(1);
                    return now.isAfter(oneHourAfter);
                })
                .toList();

        for (Event event : candidates) {
            try {
                sendFeedbackEmails(event);
                event.setFeedbackEmailSentAt(now);
                eventRepository.save(event);
                log.info("Feedback emails sent for event: {}", event.getEventID());
            } catch (Exception e) {
                log.error("Failed to send feedback emails for event {}: {}",
                        event.getEventID(), e.getMessage());
            }
        }
    }

    private void sendFeedbackEmails(Event event) {
        String eventID = event.getEventID();
        String feedbackLink = baseUrl + "/events/" + eventID + "/feedback";
        String reportLink = baseUrl + "/executive/events/" + eventID + "/report";

        // Resolve society name
        String societyName = hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .map(h -> h.getSociety() != null ? h.getSociety().getSocietyName() : "")
                .orElse("Society");

        // 1. Email ATTENDEES (students with scannedStatus = true)
        List<RSVP> attendedRsvps = rsvpRepository.findByIdEventID(eventID).stream()
                .filter(r -> Boolean.TRUE.equals(r.getScannedStatus()))
                .toList();

        for (RSVP rsvp : attendedRsvps) {
            try {
                Student student = rsvp.getStudent();
                if (student == null) {
                    student = studentRepository.findById(rsvp.getId().getStudentNumber()).orElse(null);
                }
                if (student == null) continue;

                emailService.send(EmailType.EVENT_FEEDBACK_REQUEST, student.getEmail(), Map.of(
                        "eventName", event.getEventName(),
                        "societyName", societyName,
                        "feedbackLink", feedbackLink
                ));
            } catch (Exception e) {
                log.error("Failed to send feedback email to {}: {}",
                        rsvp.getId().getStudentNumber(), e.getMessage());
            }
        }

        // 2. Email EXECUTIVES of the hosting society
        List<Hoster> hosters = hosterRepository.findByIdEventID(eventID);
        for (Hoster hoster : hosters) {
            String societyID = hoster.getId().getSocietyID();

            // Find active executives of this society
            List<Executive> executives = executiveRepository.findAll().stream()
                    .filter(e -> e.getId().getSocietyID().equals(societyID))
                    .filter(e -> e.getTermEndDate() == null
                            || e.getTermEndDate().isAfter(LocalDate.now()))
                    .toList();

            for (Executive exec : executives) {
                try {
                    Student student = studentRepository.findById(exec.getId().getStudentNumber())
                            .orElse(null);
                    if (student == null) continue;

                    String sName = societyRepository.findById(societyID)
                            .map(Society::getSocietyName).orElse(societyName);

                    emailService.send(EmailType.EVENT_REPORT_REQUEST, student.getEmail(), Map.of(
                            "eventName", event.getEventName(),
                            "societyName", sName,
                            "reportLink", reportLink
                    ));
                } catch (Exception e) {
                    log.error("Failed to send report email to exec {}: {}",
                            exec.getId().getStudentNumber(), e.getMessage());
                }
            }
        }

        log.info("Post-event emails: {} attendees + executives notified for event {}",
                attendedRsvps.size(), eventID);
    }
}
