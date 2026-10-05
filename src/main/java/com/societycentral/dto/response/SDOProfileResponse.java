package com.societycentral.dto.response;

import com.societycentral.model.Campus;
import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO returned when searching for a Student Development Officer (SDO).
 *
 * Purpose:
 * This object combines information from both the User table and the SDO table
 * into a single response that can be sent back to the frontend.
 *
 * Why do we need this DTO?
 * ------------------------
 * The User and SDO information is stored in two separate database tables.
 * The frontend should not have to make two separate API calls or understand
 * the database relationships.
 *
 * Instead, the backend combines the required information into one object and
 * returns it to the client.
 *
 * This DTO is used when an Administrator searches for an SDO before updating
 * their information (A102).
 *
 * Data Flow
 * ---------
 * SQL Server
 *      │
 *      ▼
 * User + SDO Entities
 *      │
 *      ▼
 * AuthService
 *      │
 *      ▼
 * SDOProfileResponse
 *      │
 *      ▼
 * AuthController
 *      │
 *      ▼
 * JSON Response
 *      │
 *      ▼
 * React / Android Frontend
 */
@Getter
@Setter
public class SDOProfileResponse {


    private String email;

    private String title;

    private String firstName;

    private String lastName;

    private Campus campus;

    private String staffNumber;

    private String officeNumber;

    private String phoneExtension;

}
