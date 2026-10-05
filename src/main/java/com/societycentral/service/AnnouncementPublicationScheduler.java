package com.societycentral.service;

import com.societycentral.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Delivers announcement notifications once an announcement is live. */
@Service
@RequiredArgsConstructor
public class AnnouncementPublicationScheduler {
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementService announcementService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${societycentral.announcement-publication-scheduler-delay-ms:60000}")
    public void processDueAnnouncements() {
        LocalDateTime now = LocalDateTime.now(clock);
        announcementRepository.findDueForNotification(now)
                .forEach(announcementService::notifyEligibleRecipients);
    }
}
