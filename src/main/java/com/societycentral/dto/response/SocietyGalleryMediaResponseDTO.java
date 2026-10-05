package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Public gallery slide returned as part of a society profile.
 */
@Getter
@Builder
public class SocietyGalleryMediaResponseDTO {
    private String mediaID;
    private String mediaUrl;
    private String caption;
    private Integer sortOrder;
    private LocalDateTime uploadedAt;
}
