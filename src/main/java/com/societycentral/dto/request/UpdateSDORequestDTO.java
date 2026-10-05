package com.societycentral.dto.request;

import com.societycentral.model.Campus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO used when updating an existing Student Development Officer.
 *
 * Password is intentionally excluded because password changes
 * should be handled through a dedicated endpoint.
 */
@Getter
@Setter
public class UpdateSDORequestDTO {

    @Email(message = "Email must be valid.")
    @NotBlank(message = "Email is required.")
    private String email;

    @NotBlank(message = "Title is required.")
    private String title;

    @NotBlank(message = "First name is required.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    private String lastName;

    @NotNull(message = "Campus is required.")
    private Campus campus;

    private String location;

    @NotBlank(message = "Office number is required.")
    @Size(max = 6, message = "Office number must not exceed 6 characters.")
    @Pattern(regexp = "\\d{1,6}", message = "Office number must contain numeric digits only.")
    private String officeNumber;

    @NotBlank(message = "Phone extension is required.")
    @Size(max = 10, message = "Phone extension must not exceed 10 digits.")
    @Pattern(regexp = "^$|\\d{1,10}$", message = "Phone extension must contain numeric digits only.")
    private String phoneExtension;
}
