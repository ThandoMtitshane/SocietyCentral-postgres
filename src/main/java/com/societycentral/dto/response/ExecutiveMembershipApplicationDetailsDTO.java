package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import com.societycentral.model.Faculty;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.model.School;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Complete applicant and review details visible to the owning society.
 */
@Data
@Builder
public class ExecutiveMembershipApplicationDetailsDTO {

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
    private String email;
    private String cellPhoneNumber;
    private Faculty faculty;
    private School school;
    private String nationality;
    private String residence;
    private BigDecimal membershipFee;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private String rejectionReason;
}
