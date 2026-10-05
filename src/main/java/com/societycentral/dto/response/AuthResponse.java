package com.societycentral.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Returned inside ApiResponse<AuthResponse> on successful login.
 *
 * dashboardType drives frontend routing:
 *   STUDENT   → Student Dashboard
 *   EXECUTIVE → Executive Dashboard
 *   SDO       → SDO Dashboard
 *   ADMIN     → Admin Dashboard
 *
 * executivePosition is only populated when dashboardType = "EXECUTIVE".
 * It is used by the frontend to show/hide role-specific sidebar items
 * (e.g. only Presidents/Secretaries can see Create POA, request budget, etc.)
 * Position matching in the frontend is CASE-INSENSITIVE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;

    // User identity
    private String email;
    private String firstName;
    private String lastName;
    private String userType;        // 'STUDENT', 'SDO' or 'ADMIN'

    // Dashboard routing - derived server-side
    private String dashboardType;   // 'STUDENT', 'EXECUTIVE', or 'SDO'

    // Executive-specific - null for non-executives
    // e.g. "President", "Secretary", "Treasurer"
    private String executivePosition;

    // Executive-specific - null for non-executives
    private String societyID;

    // Student-specific - null for SDO
    private String studentNumber;
    private String faculty;
    private String school;
    private String campus;
    private String course;
    private String profilePictureURL;
    private boolean hasProfilePicture;
}
