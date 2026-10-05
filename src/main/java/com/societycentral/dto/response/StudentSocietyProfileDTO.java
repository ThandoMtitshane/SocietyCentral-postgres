package com.societycentral.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public society profile returned to students.
 *
 * This DTO intentionally excludes internal society information such as
 * society codes, member counts, performance data and financial metrics.
 */
@Getter
@Builder
public class StudentSocietyProfileDTO implements SocietyProfileResponse {
    private String societyName;
    private String acronym;
    private String description;
    private String societyType;
    private String logoUrl;
    private String bannerUrl;
    private String campus;
    private String faculty;
    private String school;
    private String vision;
    private String mission;
    private BigDecimal membershipFee;
    private String contactEmail;
    private String contactPhone;
    private String facebookURL;
    private String instagramURL;
    private String tiktokURL;
    private MembershipState membershipState;
    private boolean applicationsAvailable;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean canManageProfile;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean canViewInternalData;
    private String latestApplicationID;
    private String latestTrackingReference;
    private Integer executiveTermYear;
    private List<SocietyExecutiveSummaryDTO> executives;
    private List<StudentEventSummaryDTO> upcomingEvents;
    private boolean hasMoreUpcomingEvents;
    private List<SocietyHighlightResponseDTO> highlights;
    private List<SocietyGalleryMediaResponseDTO> gallery;
    private List<SocietyAnnouncementSummaryDTO> announcements;
    private boolean hasMoreAnnouncements;
}
