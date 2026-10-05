package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Executive-supplied reason for rejecting a membership application.
 */
@Getter
@Setter
public class RejectMembershipApplicationRequestDTO {

    @NotBlank(message = "Rejection reason is required.")
    @Size(max = 500,
            message = "Rejection reason must not exceed 500 characters.")
    private String rejectionReason;
}
