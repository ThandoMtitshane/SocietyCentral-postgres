package com.societycentral.dto.request;

import com.societycentral.model.Campus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;



@Data
public class RegisterSDORequest {

    /**
     * Email address of the SDO.
     *
     * This value will also be used as the primary key
     * in the User table.
     */
    @NotBlank(message = "Email is required.")
    @Email(message = "Please enter a valid email address.")
    private String email;

    /** Location selected by the administrator; validated against CampusReference.city. */
    @NotBlank(message = "Location is required.")
    private String location;

    /**
     * SDO's first name.
     */
    @NotBlank(message = "First name is required.")
    private String firstName;

    /**
     * SDO's surname.
     */
    @NotBlank(message = "Last name is required.")
    private String lastName;

    /**
     * Optional title.
     */
    private String title;
    @NotNull(message = "Campus is required.")
    private Campus campus;
    @NotBlank(message = "Staff number is required.")
    private String staffNumber;
    @Size(max = 6, message = "Office number must not exceed 6 characters.")
    @Pattern(regexp = "\\d{1,6}", message = "Office number must contain numeric digits only.")
    private String officeNumber;
    @Size(max = 10, message = "Phone extension must not exceed 10 digits.")
    @Pattern(regexp = "^$|\\d{1,10}$", message = "Phone extension must contain numeric digits only.")
    private String phoneExtension;
}
