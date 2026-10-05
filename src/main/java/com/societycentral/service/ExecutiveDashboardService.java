package com.societycentral.service;

import com.societycentral.dto.response.EventSummaryDTO;
import com.societycentral.dto.response.ExecutiveDashboardDTO;
import com.societycentral.model.BudgetRequestStatus;
import com.societycentral.model.EventStatus;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.model.Society;
import com.societycentral.model.TaskStatus;
import com.societycentral.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the authenticated executive's society-scoped dashboard response.
 */
@Service
@RequiredArgsConstructor
public class ExecutiveDashboardService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ExecutiveRepository executiveRepository;
    private final SocietyRepository societyRepository;
    private final SocietyMemberRepository societyMemberRepository;
    private final EventRepository eventRepository;
    private final HosterRepository hosterRepository;
    private final TaskRepository taskRepository;
    private final TaskAllocationRepository taskAllocationRepository;
    private final NotificationRepository notificationRepository;
    private final BudgetRequestRepository budgetRequestRepository;
    private final MembershipApplicationRepository membershipApplicationRepository;
    private final Clock clock;

    /**
     * Returns dashboard content for the authenticated executive's active role.
     *
     * @param email authenticated JWT email
     * @return society-scoped executive dashboard
     */
    public ExecutiveDashboardDTO getDashboard(String email) {
        var user = userRepository.findById(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        var student = studentRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + email));

        LocalDate today = LocalDate.now(clock);
        var currentExecRole = executiveRepository
                .findActiveExecutiveRoles(
                        student.getStudentNumber(), today)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No active executive role found for: " + email));

        String societyID = currentExecRole.getId().getSocietyID();
        Society society = societyRepository.findById(societyID)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Society not found: " + societyID));

        ExecutiveDashboardDTO dto = new ExecutiveDashboardDTO();
        dto.setFirstName(user.getFirstName());
        dto.setProfilePictureURL(user.getProfilePictureURL());
        dto.setSocietyID(societyID);
        dto.setSocietyName(society.getSocietyName());
        dto.setUnreadNotificationCount(
                notificationRepository.countByRecipientEmailAndIsReadFalse(email));

        List<String> societyEventIds = hosterRepository.findByIdSocietyID(societyID)
                .stream()
                .map(h -> h.getId().getEventID())
                .collect(Collectors.toList());

        long upcomingCount = societyEventIds.stream()
                .map(id -> eventRepository.findById(id).orElse(null))
                .filter(e -> e != null
                        && !e.getEventDate().isBefore(today)
                        && e.getEventStatus() == EventStatus.PUBLISHED)
                .count();
        dto.setUpcomingEventsCount(upcomingCount);

        dto.setTotalMembersCount(
                societyMemberRepository.findByIdSocietyID(societyID).size());

        var societyTasks = taskAllocationRepository.findByIdSocietyID(societyID)
                .stream()
                .map(ta -> taskRepository.findById(ta.getId().getTaskID()).orElse(null))
                .filter(t -> t != null)
                .toList();

        long pendingTasks = societyTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .count();
        dto.setPendingTasksCount(pendingTasks);

        // Insights Overview: task breakdown by status
        dto.setTaskStatusBreakdown(societyTasks.stream()
                .filter(t -> t.getStatus() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getStatus().name(),
                        Collectors.counting())));

        // Insights Overview: event breakdown by status
        dto.setEventStatusBreakdown(societyEventIds.stream()
                .map(id -> eventRepository.findById(id).orElse(null))
                .filter(e -> e != null && e.getEventStatus() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getEventStatus().name(),
                        Collectors.counting())));
        dto.setPendingMembershipApplicationCount(
                membershipApplicationRepository.countBySocietyIDAndStatus(
                        societyID,
                        MembershipApplicationStatus.PENDING));

        // Real financial data from Society entity
        dto.setCurrentBalance(
                society.getCurrentBalance() != null
                        ? society.getCurrentBalance()
                        : BigDecimal.ZERO);
        dto.setPendingBudgetRequestCount(
                budgetRequestRepository.countBySocietyIDAndStatus(
                        societyID, BudgetRequestStatus.PENDING));

        List<EventSummaryDTO> upcomingEvents = societyEventIds.stream()
                .map(id -> eventRepository.findById(id).orElse(null))
                .filter(e -> e != null
                        && !e.getEventDate().isBefore(today)
                        && e.getEventStatus() == EventStatus.PUBLISHED)
                .limit(5)
                .map(e -> new EventSummaryDTO(
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
                        society.getSocietyName()))
                .collect(Collectors.toList());
        dto.setUpcomingEvents(upcomingEvents);

        return dto;
    }
}
