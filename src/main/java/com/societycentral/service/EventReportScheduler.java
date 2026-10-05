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
import java.util.List;
import java.util.Map;

/**
 * Scheduled task that generates and emails event report PDFs
 * 7 days after an event has ended.
 *
 * Runs once per day at 08:00.
 * Only processes events that have PUBLISHED status, have a date exactly
 * 7 days ago, and have not already had their report emailed.
 *
 * The PDF is sent to:
 * - The SDO supervising the hosting society
 * - The executives of the hosting society
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventReportScheduler {

    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final SocietyRepository societyRepository;
    private final SDORepository sdoRepository;
    private final UserRepository userRepository;
    private final ExecutiveRepository executiveRepository;
    private final EventReportService eventReportService;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Runs daily at 08:00. Finds events that ended 7+ days ago and
     * generates + emails their reports.
     *
     * We use the Event.eventStatus = PUBLISHED + eventDate <= 7 days ago
     * + no report-email already sent (we'll track via Event.eventStatus
     * transitioning to COMPLETED after report generation).
     */
    @Scheduled(cron = "0 0 8 * * *") // 08:00 daily
    @Transactional
    public void generateAndSendEventReports() {
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);

        // Find PUBLISHED events whose eventDate is exactly 7 days ago
        // (we process on the exact day so we don't re-send)
        List<Event> candidates = eventRepository.findByEventStatus(EventStatus.PUBLISHED)
                .stream()
                .filter(event -> event.getEventDate() != null)
                .filter(event -> event.getEventDate().equals(sevenDaysAgo))
                .toList();

        log.info("EventReportScheduler: Found {} events from 7 days ago to process", candidates.size());

        for (Event event : candidates) {
            try {
                processEventReport(event);
            } catch (Exception e) {
                log.error("Failed to generate/send report for event {}: {}",
                        event.getEventID(), e.getMessage(), e);
            }
        }
    }

    private void processEventReport(Event event) {
        String eventID = event.getEventID();

        // Generate PDF
        byte[] pdfBytes = eventReportService.generatePDF(eventID);
        String pdfFileName = "Event_Report_" + event.getEventName()
                .replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

        // Find primary hosting society
        Hoster primaryHoster = hosterRepository.findByIdEventID(eventID).stream()
                .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                .findFirst()
                .orElse(hosterRepository.findByIdEventID(eventID).stream().findFirst().orElse(null));

        if (primaryHoster == null) {
            log.warn("No hoster found for event {}, skipping report email", eventID);
            return;
        }

        String societyID = primaryHoster.getId().getSocietyID();
        Society society = societyRepository.findById(societyID).orElse(null);
        if (society == null) {
            log.warn("Society {} not found for event {}", societyID, eventID);
            return;
        }

        Map<String, String> emailVars = Map.of(
                "eventName", event.getEventName(),
                "societyName", society.getSocietyName(),
                "reportLink", baseUrl + "/sdo/events/" + eventID + "/report"
        );

        // 1. Send to SDO
        sdoRepository.findById(society.getSdoStaffNumber()).ifPresent(sdo -> {
            userRepository.findById(sdo.getEmail()).ifPresent(user -> {
                emailService.sendWithAttachment(
                        EmailType.EVENT_REPORT_GENERATED,
                        sdo.getEmail(),
                        emailVars,
                        pdfBytes,
                        pdfFileName
                );
                log.info("Report PDF sent to SDO: {} for event {}", sdo.getEmail(), eventID);
            });
        });

        // 2. Send to active executives of the hosting society
        List<String> execEmails = executiveRepository.findCurrentExecutiveEmailsBySocietyID(
                societyID, LocalDate.now());
        for (String execEmail : execEmails) {
            emailService.sendWithAttachment(
                    EmailType.EVENT_REPORT_GENERATED,
                    execEmail,
                    emailVars,
                    pdfBytes,
                    pdfFileName
            );
            log.info("Report PDF sent to executive: {} for event {}", execEmail, eventID);
        }

        // 3. Mark event as COMPLETED (report has been generated)
        event.setEventStatus(EventStatus.COMPLETED);
        eventRepository.save(event);
        log.info("Event {} marked as COMPLETED after report generation", eventID);
    }
}
