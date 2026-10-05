package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;

@Getter @Builder
public class CurrentProfileResponse {
    private String email, title, firstName, lastName, userType, dashboardType, profilePictureURL;
    private boolean hasProfilePicture;
    private Campus campus;
    private String studentNumber, course, level, nationality, residence, school, faculty, cellPhoneNumber;
    private Integer residenceID;
    private String programmeCode, programmeName, facultyCode, facultyName, schoolCode, schoolName, campusCode, campusName;
    private String executivePosition, executiveSocietyID, executiveSocietyName;
    private LocalDate termStartDate, termEndDate;
    private String staffNumber, officeNumber, phoneExtension;
}
