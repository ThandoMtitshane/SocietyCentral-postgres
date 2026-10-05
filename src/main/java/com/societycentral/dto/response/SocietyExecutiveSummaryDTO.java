package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Public details for a currently serving society executive.
 */
@Getter
@Builder
public class SocietyExecutiveSummaryDTO {
    private String studentNumber;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String position;
    private String profilePictureURL;
    private boolean hasProfilePicture;
    private String profilePictureVersion;
}
