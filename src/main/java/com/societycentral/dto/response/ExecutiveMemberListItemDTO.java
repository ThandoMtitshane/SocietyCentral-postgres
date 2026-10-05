package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class ExecutiveMemberListItemDTO {
    String studentNumber;
    String firstName;
    String lastName;
    String email;
    String profilePictureURL;
    boolean hasProfilePicture;
    String course;
    String level;
    String campus;
    LocalDate joinDate;
    LocalDate expireDate;
}
