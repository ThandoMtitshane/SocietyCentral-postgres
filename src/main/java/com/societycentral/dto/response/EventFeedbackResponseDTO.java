package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a single student feedback entry.
 * Used in the report PDF and API viewing endpoints.
 */
@Data
@Builder
public class EventFeedbackResponseDTO {

    private String feedbackID;
    private String studentNumber;
    private String studentName;   // firstName + lastName (anonymised if needed)
    private boolean hasProfilePicture;
    private Integer rating;
    private Integer organizationRating;
    private Integer venueRating;
    private Integer contentRating;
    private Boolean wouldRecommend;
    private String highlights;
    private String improvements;
    private String description;
    private LocalDateTime submittedAt;
}
