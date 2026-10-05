package com.societycentral.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Student event feedback submission.
 * Only students who attended (QR scanned) may submit.
 */
@Data
public class EventFeedbackRequestDTO {

    /**
     * Event ID - set from path variable by the controller, not from request body.
     */
    private String eventID;

    /** Overall event rating 1-5 */
    @NotNull(message = "Overall rating is required.")
    @Min(value = 1, message = "Rating must be between 1 and 5.")
    @Max(value = 5, message = "Rating must be between 1 and 5.")
    private Integer rating;

    /** Organization/logistics rating 1-5 */
    @Min(1) @Max(5)
    private Integer organizationRating;

    /** Venue suitability rating 1-5 */
    @Min(1) @Max(5)
    private Integer venueRating;

    /** Content/program quality rating 1-5 */
    @Min(1) @Max(5)
    private Integer contentRating;

    /** Would you recommend this event to others? */
    private Boolean wouldRecommend;

    /** What were the highlights? */
    @Size(max = 500, message = "Highlights cannot exceed 500 characters.")
    private String highlights;

    /** What could be improved? */
    @Size(max = 500, message = "Improvements cannot exceed 500 characters.")
    private String improvements;

    /** General feedback/comments */
    @Size(max = 500, message = "Description cannot exceed 500 characters.")
    private String description;
}
