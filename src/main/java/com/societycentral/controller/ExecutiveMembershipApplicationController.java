package com.societycentral.controller;

import com.societycentral.dto.request.RejectMembershipApplicationRequestDTO;
import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.ExecutiveMembershipApplicationDetailsDTO;
import com.societycentral.dto.response.ExecutiveMembershipApplicationPageDTO;
import com.societycentral.model.MembershipApplicationStatus;
import com.societycentral.service.ExecutiveMembershipApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Executive endpoints for C800 membership application review.
 */
@RestController
@RequestMapping("/api/executive/membership-applications")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class ExecutiveMembershipApplicationController {

    private final ExecutiveMembershipApplicationService applicationService;

    /**
     * Lists applications belonging to the authenticated executive's society.
     *
     * @param status workflow-status filter, defaulting to PENDING
     * @param search optional applicant or tracking-reference search
     * @param page zero-based page number
     * @param size requested page size
     * @param userDetails authenticated JWT principal
     * @return paged society membership applications
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ExecutiveMembershipApplicationPageDTO>>
    getApplications(
            @RequestParam(defaultValue = "PENDING")
            MembershipApplicationStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        ExecutiveMembershipApplicationPageDTO response =
                applicationService.getApplications(
                        userDetails.getUsername(), status, search, page, size);

        return ResponseEntity.ok(ApiResponse.success(
                "Membership applications retrieved successfully.",
                response));
    }

    /**
     * Loads one application after verifying society ownership.
     *
     * @param applicationID membership application identifier
     * @param userDetails authenticated JWT principal
     * @return complete applicant and application details
     */
    @GetMapping("/{applicationID}")
    public ResponseEntity<ApiResponse<ExecutiveMembershipApplicationDetailsDTO>>
    getApplication(
            @PathVariable String applicationID,
            @AuthenticationPrincipal UserDetails userDetails) {

        ExecutiveMembershipApplicationDetailsDTO response =
                applicationService.getApplication(
                        userDetails.getUsername(), applicationID);

        return ResponseEntity.ok(ApiResponse.success(
                "Membership application loaded successfully.",
                response));
    }

    /**
     * Approves one pending application and creates approved membership.
     *
     * @param applicationID membership application identifier
     * @param userDetails authenticated JWT principal
     * @return reviewed application details
     */
    @PostMapping("/{applicationID}/approve")
    public ResponseEntity<ApiResponse<ExecutiveMembershipApplicationDetailsDTO>>
    approveApplication(
            @PathVariable String applicationID,
            @AuthenticationPrincipal UserDetails userDetails) {

        ExecutiveMembershipApplicationDetailsDTO response =
                applicationService.approveApplication(
                        userDetails.getUsername(), applicationID);

        return ResponseEntity.ok(ApiResponse.success(
                "Membership application approved successfully.",
                response));
    }

    /**
     * Rejects one pending application with an executive-supplied reason.
     *
     * @param applicationID membership application identifier
     * @param request required rejection reason
     * @param userDetails authenticated JWT principal
     * @return reviewed application details
     */
    @PostMapping("/{applicationID}/reject")
    public ResponseEntity<ApiResponse<ExecutiveMembershipApplicationDetailsDTO>>
    rejectApplication(
            @PathVariable String applicationID,
            @Valid @RequestBody RejectMembershipApplicationRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        ExecutiveMembershipApplicationDetailsDTO response =
                applicationService.rejectApplication(
                        userDetails.getUsername(), applicationID, request);

        return ResponseEntity.ok(ApiResponse.success(
                "Membership application rejected successfully.",
                response));
    }
}
