package com.societycentral.dto.request;

import com.societycentral.model.Campus;
import com.societycentral.model.School;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Registration payload for STUDENT self-registration.
 * <p>
 * NOTE: Per business rules, SDOs are registered by an Admin
 * ("A101: Register SDO" use case) - not self-registered. SDO account
 * creation belongs in a separate admin-only flow/DTO, not here.
 * <p>
 * This creates rows in BOTH User and Student tables - see
 * AuthService.register().
 */
@Setter
@Getter
public class RegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank(message = "Password is required.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$",
            message = "Password must be at least 8 characters and include an uppercase letter, lowercase letter, number, and special character."
    )
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @Size(max = 10, message = "Title is too long.")
    private String title;
    private Campus campus;
    @NotBlank(message = "Study city is required.")
    private String studyCity;

    // Student-specific fields
    @NotBlank
    @Pattern(regexp = "\\d{9}", message = "Student number must contain exactly 9 digits.")
    private String studentNumber;

    private String course;
    private String programmeCode;
    private String level;
    @NotBlank(message = "Nationality is required.")
    private String nationality;
    @NotNull(message = "Gender is required.")
    private com.societycentral.model.Gender gender;
    private String residence;
    private School school;   // faculty is derived from school
    @Size(max = 10, message = "Cell phone number must contain no more than 10 digits.")
    @Pattern(regexp = "^$|\\d{1,10}$", message = "Cell phone number must contain numeric digits only.")
    private String cellPhoneNumber;
    private Integer accommodationTypeID;
    private Integer residenceID;
    private Integer offCampusPropertyID;
    private String otherAccommodationName;



    public RegisterRequest() {
    }
}
