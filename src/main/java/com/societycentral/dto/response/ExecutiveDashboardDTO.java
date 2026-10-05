package com.societycentral.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Single response object returned by GET /api/executive/dashboard.
 * Scoped to the authenticated user's first active executive role.
 */
@Data
public class ExecutiveDashboardDTO {

    private String firstName;
    private String profilePictureURL;
    private long unreadNotificationCount;

    // Society context
    private String societyID;
    private String societyName;

    // Stats cards
    private long upcomingEventsCount;
    private long totalMembersCount;
    private long pendingTasksCount;
    // PENDING membership applications for this executive's society only
    private long pendingMembershipApplicationCount;

    // Financial - replaces the old hardcoded budgetApproved/totalBudget
    private BigDecimal currentBalance;           // society's available funds right now
    private long pendingBudgetRequestCount;       // how many budget requests awaiting approval

    // Insights Overview — counts keyed by status name (e.g. {"PUBLISHED": 3, ...})
    private Map<String, Long> eventStatusBreakdown;
    private Map<String, Long> taskStatusBreakdown;

    // Content sections
    private List<EventSummaryDTO> upcomingEvents;
}
