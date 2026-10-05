package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Society-profile membership state resolved entirely by the backend.
 */
@Getter
@Builder
public class SocietyMembershipEligibilityResponseDTO {
    private String societyID;
    private BigDecimal membershipFee;
    private MembershipState membershipState;
    private boolean activeMembership;
    private boolean pendingApplication;
    private String latestApplicationID;
    private String latestTrackingReference;
    private boolean applicationsAvailable;
}
