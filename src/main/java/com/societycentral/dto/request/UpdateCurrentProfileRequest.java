package com.societycentral.dto.request;

import com.societycentral.model.Campus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCurrentProfileRequest {
    @NotBlank @Size(max = 10) private String title;
    @NotBlank @Size(max = 50) private String firstName;
    @NotBlank @Size(max = 50) private String lastName;
    @NotNull private Campus campus;
    private String programmeCode;
    @Size(max = 100) private String course;
    @Size(max = 10) private String level;
    @Size(max = 50) private String nationality;
    private Integer residenceID;
    @Pattern(regexp = "^$|\\d{10}$", message = "Enter a 10-digit cell phone number.")
    private String cellPhoneNumber;
    @Pattern(regexp = "^$|\\d{1,6}$", message = "Office number must contain no more than 6 digits.")
    private String officeNumber;
    @Size(max = 10, message = "Phone extension must not exceed 10 digits.")
    @Pattern(regexp = "^$|\\d{1,10}$", message = "Phone extension must contain numeric digits only.")
    private String phoneExtension;
}
