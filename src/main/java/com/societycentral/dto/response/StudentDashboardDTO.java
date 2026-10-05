package com.societycentral.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Single response object returned by GET /api/student/dashboard.
 * Contains everything the student dashboard needs to render in one call.
 */
@Data
@AllArgsConstructor
public class StudentDashboardDTO {

    private String firstName;
    private String studentNumber;
    private String profilePictureURL;
    private long unreadNotificationCount;
    private List<EventSummaryDTO> upcomingEvents;
    private List<SocietySummaryDTO> mySocieties;
    private List<SocietySummaryDTO> moreSocieties;

    public StudentDashboardDTO() {}
}