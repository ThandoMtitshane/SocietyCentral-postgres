package com.societycentral.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * President/Secretary-editable public society profile fields.
 *
 * <p>Identifiers, classification, membership, financial, and oversight
 * fields are intentionally absent from this request contract.</p>
 */
@Getter
@Setter
public class UpdateSocietyPublicProfileRequestDTO {

    @Size(max = 2500, message = "Description cannot exceed 2500 characters.")
    private String description;

    @Size(max = 500, message = "Vision must not exceed 500 characters.")
    private String vision;

    @Size(max = 500, message = "Mission must not exceed 500 characters.")
    private String mission;

    @Email(message = "Contact email must be a valid email address.")
    @Size(max = 100, message = "Contact email must not exceed 100 characters.")
    private String contactEmail;

    @jakarta.validation.constraints.Pattern(
            regexp = "^$|[0-9]{10}",
            message = "Contact number must be 10 digits.")
    private String contactPhone;

    @Size(max = 200,
            message = "Social media links must be valid HTTP or HTTPS URLs.")
    private String facebookURL;

    @Size(max = 200,
            message = "Social media links must be valid HTTP or HTTPS URLs.")
    private String instagramURL;

    @Size(max = 200,
            message = "Social media links must be valid HTTP or HTTPS URLs.")
    private String tiktokURL;
}
