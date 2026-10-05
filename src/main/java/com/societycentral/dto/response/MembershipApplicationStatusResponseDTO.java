package com.societycentral.dto.response;

import com.societycentral.model.MembershipApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Applicant-safe current status for A300. */
@Getter
@Builder
public class MembershipApplicationStatusResponseDTO {
    private String applicationID;
    private String trackingReference;
    private String societyID;
    private String societyName;
    private MembershipApplicationStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private String rejectionReason;
    private boolean canReapply;
}
