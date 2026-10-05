package com.societycentral.controller;

import org.springframework.security.core.Authentication;
import com.societycentral.dto.request.*;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.AuthResponse;
import com.societycentral.dto.response.SDOProfileResponse;
import com.societycentral.service.AuthService;
import com.societycentral.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Public authentication endpoints - permitted without a JWT
 * (see SecurityConfig: "/api/auth/**" is permitAll).
 * thus for that reason: DO NOT PUT OTHER MAPPINGS HERE THAT ARE NOT AUTH RELATED. rather create another controlller.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    @Autowired private AdminService adminService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful. Please log in.", null));
    }



    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully.", null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }

    /**
     * Registers a new Student Development Officer (SDO).
     *
     * This endpoint is used by an Administrator to create
     * a new SDO account.
     *
     * The request creates records in both the User and SDO tables.
     *
     * @param request Contains all information required to register an SDO.
     * @return HTTP 201 (Created) if registration is successful.
     */
    @PostMapping("/register-sdo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> registerSDO(
            @Valid @RequestBody RegisterSDORequest request) {

        // Delegate the registration process to the service layer.
        adminService.registerSDO(request);

        // Return a success response.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SDO registered successfully.", null));
    }
    /**
     * Sends a password reset link to the given email address.
     * Always returns 200 regardless of whether the email exists
     *,  prevents user enumeration.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "If that email is registered, a reset link has been sent.", null));
    }

    /**
     * Resets the user's password using a valid reset token.
     * Token is invalidated after one use and expires after 1 hour.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully. You can now log in.", null));
    }


    /**
     * Searches for an existing Student Development Officer (SDO).
     *
     * Purpose:
     * Allows an Administrator to retrieve the complete profile
     * of an existing Student Development Officer before updating
     * their information.
     *
     * The Administrator may search using either:
     * • Staff Number
     * • Email Address
     *
     * Example Requests:
     *
     * GET /api/auth/search-sdo?staffNumber=SDO001
     *
     * GET /api/auth/search-sdo?email=john.smith@nmu.ac.za
     *
     * @param staffNumber Optional staff number.
     * @param email Optional email address.
     * @return The requested Student Development Officer profile.
     */
    @GetMapping("/search-sdo")
    public ResponseEntity<ApiResponse<SDOProfileResponse>> searchSDO(

            @RequestParam(required = false) String staffNumber,

            @RequestParam(required = false) String email) {

        // Delegate the search request to the service layer.
        SDOProfileResponse response = authService.searchSDO(staffNumber, email);

        // Return the completed profile inside the standard API response wrapper.
        return ResponseEntity.ok(
                ApiResponse.success("Student Development Officer found.", response)
        );
    }


    /**
     * Updates an existing Student Development Officer (SDO).
     *
     * Purpose:
     * Allows an Administrator to update the profile of an
     * existing Student Development Officer.
     *
     * The staff number is supplied as a path variable because
     * it permanently identifies the SDO being updated.
     *
     * The request body contains the updated information.
     *
     * Example Request:
     *
     * PUT /api/auth/update-sdo/SDO001
     *
     * @param staffNumber The permanent staff number of the SDO.
     * @param request Contains the updated SDO information.
     * @return HTTP 200 (OK) if the update is successful.
     */
    @PutMapping("/update-sdo/{staffNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSDO(

            @PathVariable String staffNumber,

            @Valid @RequestBody UpdateSDORequestDTO request) {

        //===========================================================
        // 1. DELEGATE TO THE SERVICE LAYER
        //===========================================================

        /*
         * The service layer performs all business validation,
         * updates the User and SDO records, and creates the
         * necessary audit records.
         */
        adminService.updateSDO(staffNumber, request);

        //===========================================================
        // 2. RETURN SUCCESS RESPONSE
        //===========================================================

        //Return a standard API response indicating that the update completed successfully.
        return ResponseEntity.ok(ApiResponse.success(
                        "Student Development Officer updated successfully.",
                        null
                )
        );
    }
}

