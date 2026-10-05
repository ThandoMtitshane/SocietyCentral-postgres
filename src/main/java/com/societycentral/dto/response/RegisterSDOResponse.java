package com.societycentral.dto.response;

import lombok.Data;

/**
 * Response returned after an SDO has been successfully registered.
 *
 * This DTO is sent back to the frontend to confirm that the
 * registration was successful.
 *
 * IMPORTANT:
 * Sensitive information such as passwords must NEVER be returned
 * to the client.
 */
@Data
public class RegisterSDOResponse {

    /**
     * Unique staff number assigned to the SDO.
     */
    private String staffNumber;

    /**
     * Email address of the newly registered SDO.
     */
    private String email;

    /**
     * First name of the SDO.
     */
    private String firstName;

    /**
     * Last name of the SDO.
     */
    private String lastName;

    /**
     * Confirmation message displayed to the user.
     */
    private String message;

    private boolean emailSent;
}
