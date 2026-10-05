package com.societycentral.dto.response;

import lombok.Builder;
import lombok.Getter;
import com.societycentral.model.Campus;

@Getter @Builder
public class PublicExecutiveProfileResponse {
    private String studentNumber, title, firstName, lastName, fullName, course, programmeName, level;
    private String faculty, school, executivePosition, executiveSocietyID, executiveSocietyName;
    private Campus campus;
    private boolean hasProfilePicture;
    private String profilePictureVersion;
    private String email, cellPhoneNumber, nationality, residence, accommodationType;
    private String membershipStatus, membershipSocietyID, membershipSocietyName;
    private java.time.LocalDate memberSince;
    private Integer attendedEventCount;
    private java.util.List<AttendedEventSummaryDTO> attendedEvents;
    private java.time.LocalDate executiveTermStart, executiveTermEnd;
}
