package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import lombok.Data;

/**
 * Response returned when retrieving a Student Development Officer.
 *
 * This DTO is sent to the frontend to populate the
 * Update Student Development Officer form.
 *
 * IMPORTANT:
 * Sensitive information such as passwords must NEVER be returned
 * to the client.
 */
@Data
public class GetSDOResponse {

    /**
     * Unique staff number assigned to the Student Development Officer.
     */
    private String staffNumber;

    /**
     * Title of the Student Development Officer.
     */
    private String title;

    /**
     * First name of the Student Development Officer.
     */
    private String firstName;

    /**
     * Last name of the Student Development Officer.
     */
    private String lastName;

    /**
     * Email address of the Student Development Officer.
     */
    private String email;

    /**
     * Campus where the Student Development Officer is assigned.
     */
    private Campus campus;

    /**
     * Office number of the Student Development Officer.
     */
    private String officeNumber;

    /**
     * Telephone extension of the Student Development Officer.
     */
    private String phoneExtension;
}
