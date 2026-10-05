package com.societycentral.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Single response object returned by GET /api/sdo/dashboard.
 */
@Data
@AllArgsConstructor
public class SDODashboardDTO {

    private String firstName;
    private String profilePictureURL;
    private long unreadNotificationCount;

    // Stats cards
    private long pendingEventApprovalsCount;
    private long pendingBudgetRequestsCount;
    private long totalSocietiesCount;
    private long flaggedSocietiesCount;

    // Pending event approvals list (PROPOSED status)
    private List<EventSummaryDTO> pendingEvents;

    public SDODashboardDTO() {}

}