package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Public announcement preview shown on a society profile.
 */
@Getter
@Builder
public class SocietyAnnouncementSummaryDTO {
    private String announcementID;
    private String subject;
    private String description;
    private LocalDateTime datePosted;
    private LocalDateTime expireDate;
}
