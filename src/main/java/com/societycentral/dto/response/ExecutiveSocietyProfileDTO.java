package com.societycentral.dto.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Internal society profile returned to an executive for their own society.
 *
 * This DTO includes operational information that is not exposed through
 * the student-facing society profile.
 */
@Getter
@Builder
public class ExecutiveSocietyProfileDTO implements SocietyProfileResponse {
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
    private BigDecimal currentBalance;
    private BigDecimal annualBudgetAllocation;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SocietyHighlightResponseDTO> manageableHighlights;
    private List<SocietyGalleryMediaResponseDTO> gallery;
    private List<SocietyAnnouncementSummaryDTO> announcements;
    private boolean hasMoreAnnouncements;

    /**
     * Compatibility name for consumers that call the capability
     * canEditProfile. It deliberately delegates to the single authoritative
     * canManageProfile value rather than duplicating permission logic.
     */
    @JsonGetter("canEditProfile")
    public boolean canEditProfile() {
        return canManageProfile;
    }
}
