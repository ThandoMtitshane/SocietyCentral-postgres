package com.societycentral.controller;

import com.societycentral.dto.request.SubmitMembershipApplicationRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.MembershipApplicationResponseDTO;
import com.societycentral.dto.response.MembershipApplicationStatusResponseDTO;
import com.societycentral.dto.response.SocietyMembershipEligibilityResponseDTO;
import com.societycentral.service.MembershipApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student endpoints for A100 submission and society-profile eligibility.
 */
@RestController
@RequestMapping("/api/student/societies")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class MembershipApplicationController {

    private final MembershipApplicationService membershipApplicationService;

    /**
     * Creates a pending application using the authenticated student's identity.
     *
     * @param societyID selected society identifier
     * @param request application motivation
     * @param authentication authenticated Spring Security principal
     * @return HTTP 201 application confirmation
     */
    @PostMapping("/{societyID}/membership-applications")
    public ResponseEntity<ApiResponse<MembershipApplicationResponseDTO>>
    submitMembershipApplication(
            @PathVariable String societyID,
            @Valid @RequestBody SubmitMembershipApplicationRequestDTO request,
            Authentication authentication) {

        MembershipApplicationResponseDTO response =
                membershipApplicationService.submitApplication(
                        authentication.getName(), societyID, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Membership application submitted successfully.",
                        response));
    }

    /**
     * Returns one authoritative Join Society button state for the profile page.
     *
     * @param societyID selected society identifier
     * @param authentication authenticated Spring Security principal
     * @return current membership eligibility state
     */
    @GetMapping("/{societyID}/membership-eligibility")
    public ResponseEntity<ApiResponse<SocietyMembershipEligibilityResponseDTO>>
    getMembershipEligibility(
            @PathVariable String societyID,
            Authentication authentication) {

        SocietyMembershipEligibilityResponseDTO response =
                membershipApplicationService.getEligibility(
                        authentication.getName(), societyID);

        return ResponseEntity.ok(ApiResponse.success(
                "Membership eligibility retrieved successfully.",
                response));
    }

    @GetMapping("/membership-applications/{applicationID}/status")
    public ResponseEntity<ApiResponse<MembershipApplicationStatusResponseDTO>>
    getMembershipApplicationStatus(
            @PathVariable String applicationID,
            Authentication authentication) {
        MembershipApplicationStatusResponseDTO response =
                membershipApplicationService.getApplicationStatus(
                        authentication.getName(), applicationID);
        return ResponseEntity.ok(ApiResponse.success(
                "Membership application status retrieved successfully.", response));
    }
}
