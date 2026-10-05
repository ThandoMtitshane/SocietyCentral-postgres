package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Public society profile returned to an authenticated student.
 *
 * The response contains public society information and the student's
 * membership-application state, but excludes internal society metrics.
 */
@Getter
@Builder
public class SocietyBrowseDetailsResponseDTO {
    private String societyID;
    private String societyName;
    private String acronym;
    private String description;
    private String societyType;
    private String logoUrl;

    private String campus;
    private String faculty;
    private String school;

    private BigDecimal membershipFee;
    private Boolean activeStatus;
    private String vision;
    private String mission;
    private String contactEmail;
    private String contactPhone;

    private String facebookURL;
    private String instagramURL;
    private String tiktokURL;

    private MembershipState membershipState;
    private boolean applicationsAvailable;
    private String latestApplicationID;
    private String latestTrackingReference;
}
