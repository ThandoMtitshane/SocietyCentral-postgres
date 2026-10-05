package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.ExecutiveTaskPerformanceDTO;
import com.societycentral.dto.response.ExecutiveTeamMemberDTO;
import com.societycentral.service.ExecutiveTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read endpoints for the authenticated executive's own committee ("Executives"
 * page). Auth-scoped: the society is resolved from the caller, never supplied.
 */
@RestController
@RequestMapping("/api/executive/executives")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class ExecutiveTeamController {

    private final ExecutiveTeamService executiveTeamService;

    /** Current executives of the caller's society, with task-performance counts. */
    @GetMapping
    public ApiResponse<List<ExecutiveTeamMemberDTO>> getTeam(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(
                "Executive team retrieved successfully.",
                executiveTeamService.getTeamForExecutive(userDetails.getUsername()));
    }

    /** Detailed task performance for one executive on the caller's committee. */
    @GetMapping("/{studentNumber}/performance")
    public ApiResponse<ExecutiveTaskPerformanceDTO> getPerformance(
            @PathVariable String studentNumber,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.success(
                "Executive performance retrieved successfully.",
                executiveTeamService.getPerformanceForExecutive(
                        userDetails.getUsername(), studentNumber));
    }
}
