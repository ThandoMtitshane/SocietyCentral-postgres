package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.SDODashboardDTO;
import com.societycentral.service.SDODashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sdo")
public class SDODashboardController {

    private final SDODashboardService dashboardService;

    @Autowired
    public SDODashboardController(SDODashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/sdo/dashboard
     * Returns everything the SDO dashboard needs in one call.
     * Requires a valid JWT with ROLE_SDO.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<SDODashboardDTO>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {

        SDODashboardDTO data = dashboardService.getDashboard(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("SDO dashboard loaded", data));
    }
}