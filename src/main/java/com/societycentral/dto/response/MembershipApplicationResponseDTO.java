package com.societycentral.dto.response;

import com.societycentral.model.MembershipApplicationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Confirmation details returned for a submitted membership application.
 */
@Getter
@Builder
public class MembershipApplicationResponseDTO {
    private String applicationID;
    private String trackingReference;
    private String societyID;
    private String societyName;
    private String societyLogoUrl;
    private BigDecimal membershipFee;
    private MembershipApplicationStatus status;
    private String motivation;
    private LocalDateTime applicationDate;
    private LocalDateTime lastUpdatedAt;
    private String rejectionReason;
}
