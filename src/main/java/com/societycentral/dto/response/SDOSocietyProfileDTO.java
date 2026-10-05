package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full society profile returned to a Student Development Officer.
 *
 * SDOs may view internal oversight information for all societies,
 * including society identifiers, membership numbers and operational data.
 */
@Getter
@Builder
public class SDOSocietyProfileDTO implements SocietyProfileResponse {
    private String societyID;
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
    private Integer numberOfMembers;
    private BigDecimal annualBudgetAllocation;
    private BigDecimal currentBalance;
    private Boolean activeStatus;
    private Boolean flagged;
    private Boolean atRisk;
    private Integer pendingTasks;
    private boolean canManageProfile;
    private boolean canViewInternalData;
    private Integer executiveTermYear;
    private List<SocietyExecutiveSummaryDTO> executives;
    private List<StudentEventSummaryDTO> upcomingEvents;
    private boolean hasMoreUpcomingEvents;
    private List<SocietyHighlightResponseDTO> highlights;
    private List<SocietyGalleryMediaResponseDTO> gallery;
    private List<SocietyAnnouncementSummaryDTO> announcements;
    private boolean hasMoreAnnouncements;
}
