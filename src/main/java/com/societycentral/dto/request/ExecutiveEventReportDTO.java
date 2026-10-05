package com.societycentral.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Executive post-event report submission.
 * Only 1 executive per society per event may submit.
 */
@Data
public class ExecutiveEventReportDTO {

    /**
     * Event ID - set from path variable by the controller, not from request body.
     */
    private String eventID;

    /** What were your expectations for this event? */
    @Size(max = 1000, message = "Expectations cannot exceed 1000 characters.")
    private String expectations;

    /** Were expectations met? YES / PARTIALLY / NO */
    @NotBlank(message = "Please indicate if expectations were met.")
    private String expectationsMet;

    /** Do you consider the event a success? SUCCESS / PARTIAL / FAILURE */
    @NotBlank(message = "Please assess the event's success.")
    private String successAssessment;

    /** Why do you consider it a success/failure? */
    @Size(max = 1000, message = "Success reason cannot exceed 1000 characters.")
    private String successReason;

    /** What would you improve if doing it again? */
    @Size(max = 1000, message = "Improvements cannot exceed 1000 characters.")
    private String improvements;

    /** Advice for someone hosting a similar event? */
    @Size(max = 1000, message = "Advice cannot exceed 1000 characters.")
    private String advice;

    /** Estimated number of attendees */
    @Min(value = 0, message = "Attendee count cannot be negative.")
    private Integer attendeeCount;

    /** Overall rating of the event 1-5 */
    @Min(1) @Max(5)
    private Integer overallRating;

    /** Any additional notes */
    @Size(max = 1000, message = "Additional notes cannot exceed 1000 characters.")
    private String additionalNotes;
}
