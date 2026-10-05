package com.societycentral.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Student-supplied details for a new membership application.
 *
 * <p>Student identity is intentionally excluded and is resolved from the
 * authenticated email address.</p>
 */
@Getter
@Setter
public class SubmitMembershipApplicationRequestDTO {

    @NotBlank(message = "Motivation is required.")
    @Size(max = 500, message = "Motivation must not exceed 500 characters.")
    private String motivation;
}
