package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import lombok.Data;

/**
 * Response DTO containing summary information for a
 * Student Development Officer.
 *
 * This DTO is used when displaying a list of
 * Student Development Officers.
 */
@Data
public class SDOListResponse {

    /**
     * Unique staff number identifying the
     * Student Development Officer.
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
     * Official university email address.
     */
    private String email;

    /**
     * Campus to which the Student Development Officer belongs.
     */
    private Campus campus;
}