package com.societycentral.controller;

import com.societycentral.dto.response.ApiResponse;
import com.societycentral.dto.response.StudentDashboardDTO;
import com.societycentral.service.StudentDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentDashboardController {

    private final StudentDashboardService dashboardService;

    @Autowired
    public StudentDashboardController(StudentDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * GET /api/student/dashboard
     * Returns everything the student dashboard needs in one call.
     * Requires a valid JWT with ROLE_STUDENT.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<StudentDashboardDTO>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {

        StudentDashboardDTO data = dashboardService.getDashboard(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Student dashboard loaded", data));
    }
}