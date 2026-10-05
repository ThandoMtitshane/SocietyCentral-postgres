package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.ExecutiveDashboardDTO;
import com.societycentral.service.ExecutiveDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executive")
public class ExecutiveDashboardController {

    private final ExecutiveDashboardService dashboardService;

    @Autowired
    public ExecutiveDashboardController(ExecutiveDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/executive/dashboard
     * Returns everything the executive dashboard needs in one call.
     * Scoped to the authenticated user's first active executive role.
     * Requires a valid JWT.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<ExecutiveDashboardDTO>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {

        ExecutiveDashboardDTO data = dashboardService.getDashboard(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Executive dashboard loaded", data));
    }
}
