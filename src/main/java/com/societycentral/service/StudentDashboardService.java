package com.societycentral.service;

import com.societycentral.dto.response.EventSummaryDTO;
import com.societycentral.dto.response.SocietySummaryDTO;
import com.societycentral.dto.response.StudentDashboardDTO;
import com.societycentral.model.EventStatus;
import com.societycentral.model.Society;
import com.societycentral.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentDashboardService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final SocietyRepository societyRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final NotificationRepository notificationRepository;
    private final RSVPRepository rsvpRepository;
    private final POAService poaService;
    private final Clock clock;

    @Autowired
    public StudentDashboardService(UserRepository userRepository,
                                   StudentRepository studentRepository,
                                   EventRepository eventRepository,
                                   HosterRepository hosterRepository,
                                   SocietyRepository societyRepository,
                                   SocietyMemberRepository societyMemberRepository,
                                   NotificationRepository notificationRepository,
                                   RSVPRepository rsvpRepository,
                                   POAService poaService,
                                   Clock clock) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.eventRepository = eventRepository;
        this.hosterRepository = hosterRepository;
        this.societyRepository = societyRepository;
        this.societyMemberRepository = societyMemberRepository;
        this.notificationRepository = notificationRepository;
        this.rsvpRepository = rsvpRepository;
        this.poaService = poaService;
        this.clock = clock;
    }

    public StudentDashboardDTO getDashboard(String email) {
        var user = userRepository.findById(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        var student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found for email: " + email));

        StudentDashboardDTO dto = new StudentDashboardDTO();
        dto.setFirstName(user.getFirstName());
        dto.setStudentNumber(student.getStudentNumber());
        dto.setProfilePictureURL(user.getProfilePictureURL());

        // Unread notification count
        dto.setUnreadNotificationCount(
                notificationRepository.countByRecipientEmailAndIsReadFalse(email));

        Set<String> memberSocietyIds = societyMemberRepository
                .findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .map(sm -> sm.getId().getSocietyID())
                .collect(Collectors.toSet());

        // Student discovery is governed by PUBLISHED + RSVP opening time.
        List<EventSummaryDTO> upcomingEvents = eventRepository
                .findVisibleEventsForStudent(
                        memberSocietyIds.stream().toList(),
                        LocalDateTime.now(clock))
                .stream()
                .filter(event -> event.getEventDate() != null
                        && !event.getEventDate().isBefore(LocalDate.now(clock)))
                .limit(10)
                .map(event -> {
                    // Get primary hosting society name
                    String primarySocietyName = hosterRepository.findByIdEventID(event.getEventID())
                            .stream()
                            .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                            .findFirst()
                            .map(h -> societyRepository.findById(h.getId().getSocietyID())
                                    .map(Society::getSocietyName)
                                    .orElse(""))
                            .orElse("");

                    EventSummaryDTO summary = new EventSummaryDTO(
                            event.getEventID(),
                            event.getEventName(),
                            event.getEventDate(),
                            event.getEventTime(),
                            event.getEventVenue(),
                            event.getEventCampus() != null ? event.getEventCampus().name() : null,
                            event.getEventStatus().name(),
                            event.getPosterUrl() != null && !event.getPosterUrl().isBlank()
                                    ? event.getPosterUrl()
                                    : event.getImageUrl(),
                            primarySocietyName
                    );
                    if (rsvpRepository.existsById(new com.societycentral.model.RsvpId(
                            event.getEventID(), student.getStudentNumber()))) {
                        summary.setStudentRsvpStatus("CONFIRMED");
                    }
                    return summary;
                })
                .collect(Collectors.toList());

        // Also include visible events the student has RSVP'd to (including after RSVP close).
        Set<String> alreadyIncludedIDs = upcomingEvents.stream()
                .map(EventSummaryDTO::getEventID)
                .collect(Collectors.toSet());

        rsvpRepository.findByIdStudentNumber(student.getStudentNumber())
                .stream()
                .map(rsvp -> eventRepository.findById(rsvp.getId().getEventID()).orElse(null))
                .filter(event -> event != null
                        && event.getEventStatus() == EventStatus.PUBLISHED
                        && event.getRsvpOpenDate() != null
                        && !event.getRsvpOpenDate().isAfter(LocalDateTime.now(clock))
                        && event.getEventDate() != null
                        && !event.getEventDate().isBefore(LocalDate.now())
                        && !alreadyIncludedIDs.contains(event.getEventID()))
                .limit(5)
                .forEach(event -> {
                    String societyName = hosterRepository.findByIdEventID(event.getEventID())
                            .stream()
                            .filter(h -> Boolean.TRUE.equals(h.getIsPrimary()))
                            .findFirst()
                            .map(h -> societyRepository.findById(h.getId().getSocietyID())
                                    .map(Society::getSocietyName).orElse(""))
                            .orElse("");
                    EventSummaryDTO summary = new EventSummaryDTO(
                            event.getEventID(), event.getEventName(),
                            event.getEventDate(), event.getEventTime(),
                            event.getEventVenue(),
                            event.getEventCampus() != null ? event.getEventCampus().name() : null,
                            event.getEventStatus().name(),
                            event.getPosterUrl() != null && !event.getPosterUrl().isBlank()
                                    ? event.getPosterUrl() : event.getImageUrl(),
                            societyName);
                    summary.setStudentRsvpStatus("CONFIRMED");
                    upcomingEvents.add(summary);
                });

        dto.setUpcomingEvents(upcomingEvents);

        List<SocietySummaryDTO> mySocieties = memberSocietyIds.stream()
                .map(id -> societyRepository.findById(id).orElse(null))
                .filter(s -> s != null)
                .map(this::toSocietySummary)
                .collect(Collectors.toList());
        dto.setMySocieties(mySocieties);

        // "More societies" - active societies the student hasn't joined yet (limit 6)
        List<SocietySummaryDTO> moreSocieties = societyRepository.findByActiveStatusTrue()
                .stream()
                .filter(s -> !memberSocietyIds.contains(s.getSocietyID()))
                .limit(6)
                .map(this::toSocietySummary)
                .collect(Collectors.toList());
        dto.setMoreSocieties(moreSocieties);

        return dto;
    }

    private SocietySummaryDTO toSocietySummary(Society s) {
        return new SocietySummaryDTO(
                s.getSocietyID(),
                s.getSocietyName(),
                s.getDescription(),
                s.getSocietyType() != null ? s.getSocietyType().name() : null,
                s.getLogoUrl(),
                eventRepository.countUpcomingPublishedEventsForSociety(
                        s.getSocietyID(), LocalDate.now())
        );
    }
}
