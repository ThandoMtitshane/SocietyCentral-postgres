package com.societycentral.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request payload for publishing an APPROVED event.
 *
 * This is Phase 2 of the event proposal workflow:
 * After SDO approval, the executive provides RSVP dates and capacity
 * to make the event visible to students.
 */
@Data
public class PublishEventRequestDTO {

    /**
     * Date and time from which students may begin submitting RSVPs.
     */
    @NotNull(message = "RSVP open date is required for publishing.")
    private LocalDateTime rsvpOpenDate;

    /**
     * Date and time after which RSVPs will no longer be accepted.
     */
    @NotNull(message = "RSVP close date is required for publishing.")
    private LocalDateTime rsvpCloseDate;

    /**
     * Maximum number of attendees allowed at the event.
     */
    @NotNull(message = "Event capacity is required for publishing.")
    @Min(value = 1, message = "Event capacity must be at least 1.")
    private Integer eventLimit;
}
