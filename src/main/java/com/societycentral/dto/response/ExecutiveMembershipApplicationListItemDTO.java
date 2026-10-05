package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import com.societycentral.model.MembershipApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Applicant summary displayed in the executive membership-review queue.
 */
@Data
@Builder
public class ExecutiveMembershipApplicationListItemDTO {

    private String applicationID;
    private String trackingReference;
    private MembershipApplicationStatus status;
    private LocalDateTime applicationDate;
    private LocalDateTime lastUpdatedAt;
    private String studentNumber;
    private String firstName;
    private String lastName;
    private String fullName;
    private String course;
    private String level;
    private Campus campus;
    private String motivation;
    private String societyID;
    private String societyName;
}
