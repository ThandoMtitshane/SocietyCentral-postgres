package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for an SDO reviewing (approving or rejecting) an event proposal.
 */
@Data
public class ReviewEventRequestDTO {

    /**
     * The review action: "APPROVE" or "REJECT".
     */
    @NotBlank(message = "Review action is required (APPROVE or REJECT).")
    private String action;

    /**
     * SDO notes/feedback on the event proposal.
     * Required when rejecting; optional when approving.
     */
    @Size(max = 1000, message = "Review notes may not exceed 1000 characters.")
    private String reviewNotes;
}
