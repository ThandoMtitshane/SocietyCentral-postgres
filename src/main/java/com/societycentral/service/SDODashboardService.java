package com.societycentral.service;

import com.societycentral.dto.response.EventSummaryDTO;
import com.societycentral.dto.response.SDODashboardDTO;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Society;
import com.societycentral.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SDODashboardService {

    private final UserRepository userRepository;
    private final SocietyRepository societyRepository;
    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final NotificationRepository notificationRepository;

    @Autowired
    public SDODashboardService(UserRepository userRepository,
                               SocietyRepository societyRepository,
                               EventRepository eventRepository,
                               HosterRepository hosterRepository,
                               NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.societyRepository = societyRepository;
        this.eventRepository = eventRepository;
        this.hosterRepository = hosterRepository;
        this.notificationRepository = notificationRepository;
    }

    public SDODashboardDTO getDashboard(String email) {
        var user = userRepository.findById(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        SDODashboardDTO dto = new SDODashboardDTO();
        dto.setFirstName(user.getFirstName());
        dto.setProfilePictureURL(user.getProfilePictureURL());

        // Unread notifications
        dto.setUnreadNotificationCount(
                notificationRepository.countByRecipientEmailAndIsReadFalse(email));

        // Pending event approvals (PROPOSED status)
        List<EventSummaryDTO> proposedEvents = eventRepository
                .findByEventStatus(EventStatus.PROPOSED)
                .stream()
                .map(e -> {
                    String primarySocietyName = hosterRepository.findByIdEventID(e.getEventID())
                            .stream()
                            .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                            .findFirst()
                            .map(h -> societyRepository.findById(h.getId().getSocietyID())
                                    .map(Society::getSocietyName)
                                    .orElse(""))
                            .orElse("");

                    return new EventSummaryDTO(
                            e.getEventID(),
                            e.getEventName(),
                            e.getEventDate(),
                            e.getEventTime(),
                            e.getEventVenue(),
                            e.getEventCampus() != null ? e.getEventCampus().name() : null,
                            e.getEventStatus().name(),
                            e.getPosterUrl() != null && !e.getPosterUrl().isBlank()
                                    ? e.getPosterUrl()
                                    : e.getImageUrl(),
                            primarySocietyName
                    );
                })
                .collect(Collectors.toList());

        dto.setPendingEventApprovalsCount(proposedEvents.size());
        dto.setPendingEvents(proposedEvents);

        // Total and flagged societies
        List<Society> allSocieties = societyRepository.findAll();
        dto.setTotalSocietiesCount(allSocieties.size());
        dto.setFlaggedSocietiesCount(
                allSocieties.stream().filter(s -> Boolean.TRUE.equals(s.getIsFlagged())).count());

        // TODO: pendingBudgetRequestsCount - replace with real query once Budget is clarified (see @ExecutiveDashboardService)
        dto.setPendingBudgetRequestsCount(0L);

        return dto;
    }
}
