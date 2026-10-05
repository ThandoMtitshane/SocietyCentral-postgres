package com.societycentral.service;

import com.societycentral.model.Event;
import com.societycentral.model.EventStatus;
import com.societycentral.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Delivers RSVP-open campaigns without changing the persisted event status. */
@Service
@RequiredArgsConstructor
public class EventPublicationScheduler {

    private final EventRepository eventRepository;
    private final EventProposalService eventProposalService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${societycentral.event-publication-scheduler-delay-ms:60000}")
    public void processRsvpOpenCampaigns() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Event event : eventRepository
                .findByEventStatusAndRsvpOpenNoticeSentAtIsNullAndRsvpOpenDateLessThanEqual(
                        EventStatus.PUBLISHED, now)) {
            eventProposalService.processPublicationCommunication(event.getEventID());
        }
    }
}
