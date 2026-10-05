package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * One executive on the "Executives" team page. Enriched with contact and
 * profile-picture info plus a task-performance summary so the card can show
 * a glanceable strip (e.g. "3 pending · 8 complete") without extra calls.
 */
@Data
@Builder
public class ExecutiveTeamMemberDTO {

    private String studentNumber;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String position;

    private boolean hasProfilePicture;
    private String profilePictureVersion;

    // Task performance (individual tasks assigned to this executive)
    private long pendingTaskCount;
    private long completeTaskCount;
    private long totalTaskCount;
}
